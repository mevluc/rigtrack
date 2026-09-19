"""Install as a Blender add-on, or run: blender --background --python this.py -- take.vfxtrack."""
bl_info = {"name": "RigTrack (.vfxtrack)", "author": "RigTrack", "version": (1, 3, 0), "blender": (3, 6, 0), "location": "File > Import", "category": "Import-Export"}
import bisect
import csv
import hashlib
import io
import json
import math
import os
import shutil
import tempfile
import zipfile


MARKER_MAP_LIMIT = 4 * 1024 * 1024


def read_marker_map(path):
    """Return a marker map when present and readable; marker data is optional."""
    try:
        with zipfile.ZipFile(path) as archive:
            try:
                info = archive.getinfo("marker_map.json")
            except KeyError:
                print("No marker map found.")
                return None
            if info.file_size > MARKER_MAP_LIMIT:
                print("Ignored oversized marker map.")
                return None
            marker_map = json.loads(archive.read(info))
    except (OSError, UnicodeError, ValueError, zipfile.BadZipFile) as error:
        print(f"Ignored invalid marker map: {error}")
        return None
    if not isinstance(marker_map, dict):
        print("No marker map found.")
        return None
    if not isinstance(marker_map.get("references", []), list):
        print("Ignored invalid marker map references.")
        return None
    return marker_map


def _finite_values(values):
    result = tuple(float(value) for value in values)
    if not all(math.isfinite(value) for value in result):
        raise ValueError("non-finite transform")
    return result


def _normalized_quaternion_xyzw(values):
    from mathutils import Quaternion
    x, y, z, w = _finite_values(values)
    quaternion = Quaternion((w, x, y, z))
    if quaternion.magnitude < 1e-12:
        raise ValueError("zero quaternion")
    quaternion.normalize()
    return quaternion


def convert_arcore_transform_to_blender(transform):
    """Convert T_anchor_object to Blender world with the same B*T basis as camera export."""
    from mathutils import Matrix, Vector
    translation = transform["t"]
    rotation = transform["q"]
    location = Vector(_finite_values(translation[key] for key in ("x", "y", "z")))
    quaternion = _normalized_quaternion_xyzw(rotation[key] for key in ("x", "y", "z", "w"))
    source = quaternion.to_matrix().to_4x4()
    source.translation = location
    basis = Matrix.Rotation(math.pi / 2, 4, 'X')
    converted = basis @ source
    return converted.translation.copy(), converted.to_quaternion().normalized()


def apply_blender_transform(obj, location, quaternion, scale):
    """Apply one Blender-space rigid transform and a shared translation scale."""
    obj.location = tuple(float(value) * scale for value in location)
    obj.rotation_mode = 'QUATERNION'
    obj.rotation_quaternion = quaternion


def _new_import_collections(scene):
    import bpy
    root = bpy.data.collections.new("RigTrack")
    scene.collection.children.link(root)
    cameras = bpy.data.collections.new("RigTrack_Cameras")
    root.children.link(cameras)
    return root, cameras


def _create_tracking_origin(root, scale):
    import bpy
    origin = bpy.data.objects.new("RigTrack_Origin", None)
    root.objects.link(origin)
    origin.empty_display_type = 'PLAIN_AXES'
    origin.empty_display_size = 0.25 * abs(scale)
    origin.location = (0.0, 0.0, 0.0)
    origin.rotation_mode = 'QUATERNION'
    origin.rotation_quaternion = (1.0, 0.0, 0.0, 0.0)
    origin.show_name = True
    origin["rigtrack_tracking_origin"] = True
    return origin


def _optional_number(source, *keys):
    for key in keys:
        if key in source:
            try:
                value = float(source[key])
                if math.isfinite(value):
                    return value
            except (TypeError, ValueError, OverflowError):
                pass
    return None


def create_marker_objects(marker_map, root, scale):
    """Create valid mapped markers independently; one bad reference never cancels camera import."""
    import bpy
    references = marker_map.get("references", [])
    marker_collection = bpy.data.collections.new("RigTrack_Markers")
    root.children.link(marker_collection)
    marker_collection.hide_render = True
    markers = []
    skipped = 0
    for reference in references:
        marker_id = reference.get("id", "?") if isinstance(reference, dict) else "?"
        try:
            if not isinstance(reference, dict):
                raise ValueError("reference is not an object")
            marker_id = int(reference["id"])
            location, quaternion = convert_arcore_transform_to_blender(reference["T_anchor_marker"])
        except (KeyError, TypeError, ValueError, OverflowError) as error:
            skipped += 1
            print(f"Skipped invalid marker ID {marker_id}: {error}")
            continue
        marker = bpy.data.objects.new(f"Marker_{marker_id:03d}", None)
        marker_collection.objects.link(marker)
        apply_blender_transform(marker, location, quaternion, scale)
        marker.empty_display_type = 'PLAIN_AXES'
        physical_size = _optional_number(reference, "sizeM", "physicalSizeM", "physical_size_m")
        marker.empty_display_size = (physical_size if physical_size is not None and physical_size > 0 else 0.1) * abs(scale)
        marker.show_name = True
        marker.show_in_front = True
        marker["rigtrack_marker_id"] = marker_id
        if isinstance(marker_map.get("dictionary"), (int, float, str)):
            marker["rigtrack_dictionary"] = marker_map["dictionary"]
        if physical_size is not None and physical_size > 0:
            marker["rigtrack_physical_size_m"] = physical_size
        try:
            if "observations" in reference:
                marker["rigtrack_observation_count"] = int(reference["observations"])
        except (TypeError, ValueError, OverflowError):
            pass
        confidence = _optional_number(reference, "confidence")
        if confidence is not None:
            marker["rigtrack_confidence"] = confidence
        error = _optional_number(reference, "reprojectionError", "reprojection_error")
        if error is not None:
            marker["rigtrack_reprojection_error"] = error
        markers.append(marker)
    return markers, skipped


def _track_candidates(camera, requested):
    prefix = "blender_camera" if camera == "film" else "blender_reference_camera"
    names = {"REFINED": f"{prefix}_refined.csv", "FUSED": f"{prefix}.csv", "RAW": f"{prefix}_raw.csv"}
    order = {"REFINED": ("REFINED", "FUSED", "RAW"), "FUSED": ("FUSED", "RAW"), "RAW": ("RAW",)}[requested]
    return [(source, names[source]) for source in order]


def _read_selected_track(archive, camera, requested):
    for actual, name in _track_candidates(camera, requested):
        rows = _read_csv(archive, name)
        if rows is not None:
            if actual != requested:
                print(f"RigTrack {camera} track fallback: requested {requested}, using {actual} ({name}).")
            return rows, actual, name
    raise ValueError(f"No usable {camera} camera track for requested source {requested}")


def read_track_with_source(path, fused=True, track_source=None):
    """Parse in memory without extracting arbitrary ZIP paths. Reject future versions."""
    requested = (track_source or ("FUSED" if fused else "RAW")).upper()
    if requested not in ("REFINED", "FUSED", "RAW"):
        raise ValueError(f"Unknown track source {requested}")
    with zipfile.ZipFile(path) as archive:
        if archive.getinfo("metadata.json").file_size > 1024 * 1024:
            raise ValueError("Oversized metadata")
        metadata = json.loads(archive.read("metadata.json"))
        if metadata.get("format_version") != 1:
            raise ValueError("Unsupported .vfxtrack format version")
        rows, actual, name = _read_selected_track(archive, "film", requested)
    for row in rows:
        if row["valid"].lower() == "true":
            values = [float(row[k]) for k in ("tx", "ty", "tz", "qx", "qy", "qz", "qw")]
            if not all(math.isfinite(v) for v in values):
                raise ValueError("Track contains non-finite values")
            if sum(v*v for v in values[3:]) < 1e-12:
                raise ValueError("Zero quaternion")
    return metadata, rows, requested, actual, name


def read_track(path, fused=True, track_source=None):
    metadata, rows, _, _, _ = read_track_with_source(path, fused, track_source)
    return metadata, rows


def _read_csv(archive, name, limit=256 * 1024 * 1024):
    try:
        info = archive.getinfo(name)
    except KeyError:
        return None
    if info.file_size > limit:
        raise ValueError(f"Oversized {name}")
    with archive.open(info) as stream:
        return list(csv.DictReader(io.TextIOWrapper(stream, encoding="utf-8")))


def _row_pose(row, keys=("tx", "ty", "tz", "qx", "qy", "qz", "qw")):
    values = tuple(float(row[key]) for key in keys)
    if not all(math.isfinite(value) for value in values):
        raise ValueError("non-finite pose")
    return values


def _ar_pose_to_blender(values):
    from mathutils import Matrix, Quaternion, Vector
    tx, ty, tz, qx, qy, qz, qw = values
    quaternion = Quaternion((qw, qx, qy, qz))
    if quaternion.magnitude < 1e-12:
        raise ValueError("zero quaternion")
    source = quaternion.normalized().to_matrix().to_4x4()
    source.translation = Vector((tx, ty, tz))
    converted = Matrix.Rotation(math.pi / 2, 4, 'X') @ source
    q = converted.to_quaternion().normalized()
    return (*converted.translation, q.x, q.y, q.z, q.w)


def _fallback_reference_rows(archive, metadata, film_rows, fused):
    """Resample legacy phone poses at the exact film-frame timestamps."""
    from mathutils import Quaternion, Vector
    name = "phone_camera_fused.csv" if fused else "ar_pose.csv"
    source_rows = _read_csv(archive, name)
    if not source_rows:
        return []
    samples = []
    for row in source_rows:
        try:
            if fused:
                pose = _row_pose(row) if row.get("tx") else None
            else:
                keys = ("relative_tx", "relative_ty", "relative_tz", "relative_qx", "relative_qy", "relative_qz", "relative_qw")
                pose = _row_pose(row, keys) if row.get("relative_tx") else None
            samples.append((float(row["time_s"]), pose, row.get("valid", "false").lower() == "true", float(row.get("quality", 0))))
        except (KeyError, TypeError, ValueError, OverflowError):
            continue
    if not samples:
        return []
    times = [sample[0] for sample in samples]
    gap = float(metadata.get("shot", {}).get("gapMaxMs", 100)) / 1000.0
    output = []
    for target in film_rows:
        t = float(target["time_s"])
        index = bisect.bisect_left(times, t)
        pose = None
        quality = 0.0
        interpolated = False
        if index < len(samples) and abs(times[index] - t) < 1e-9:
            _, pose, valid, quality = samples[index]
            pose = pose if valid else None
        elif 0 < index < len(samples):
            left, right = samples[index - 1], samples[index]
            span = right[0] - left[0]
            if left[2] and right[2] and left[1] and right[1] and 0 < span <= gap:
                alpha = (t - left[0]) / span
                lv, rv = left[1], right[1]
                location = Vector(lv[:3]).lerp(Vector(rv[:3]), alpha)
                lq = Quaternion((lv[6], lv[3], lv[4], lv[5])).normalized()
                rq = Quaternion((rv[6], rv[3], rv[4], rv[5])).normalized()
                q = lq.slerp(rq, alpha).normalized()
                pose = (*location, q.x, q.y, q.z, q.w)
                quality = left[3] + (right[3] - left[3]) * alpha
                interpolated = True
        converted = _ar_pose_to_blender(pose) if pose else None
        row = {"frame": target["frame"], "time_s": target["time_s"], "quality": str(quality),
               "valid": str(converted is not None).lower(), "interpolated_gap": str(interpolated).lower()}
        for key, value in zip(("tx", "ty", "tz", "qx", "qy", "qz", "qw"), converted or ("",) * 7):
            row[key] = str(value)
        output.append(row)
    return output


def read_reference_track_with_source(path, metadata, film_rows, requested="FUSED"):
    with zipfile.ZipFile(path) as archive:
        try:
            return (*_read_selected_track(archive, "reference", requested),)
        except ValueError:
            fused = requested != "RAW"
            rows = _fallback_reference_rows(archive, metadata, film_rows, fused)
            if rows:
                actual = "FUSED" if fused else "RAW"
                name = "phone_camera_fused.csv (legacy resample)" if fused else "ar_pose.csv (legacy resample)"
                print(f"RigTrack reference track fallback: requested {requested}, using {actual} ({name}).")
                return rows, actual, name
            raise


def read_reference_track(path, metadata, film_rows, fused=True, track_source=None):
    requested = (track_source or ("FUSED" if fused else "RAW")).upper()
    rows, _, _ = read_reference_track_with_source(path, metadata, film_rows, requested)
    return rows


def read_reference_intrinsics(path, metadata):
    """Select the median ARCore CPU-image intrinsics from the dominant image size."""
    with zipfile.ZipFile(path) as archive:
        rows = _read_csv(archive, "intrinsics.csv", 64 * 1024 * 1024) or []
        calibration = None
        try:
            calibration = json.loads(archive.read("calibration.json"))
        except (KeyError, ValueError, UnicodeError):
            pass
    parsed = []
    for row in rows:
        try:
            values = {key: float(row[key]) for key in ("fx", "fy", "cx", "cy")}
            values.update(width=int(row["image_width"]), height=int(row["image_height"]))
            if values["width"] > 0 and values["height"] > 0 and all(math.isfinite(values[key]) and values[key] > 0 for key in ("fx", "fy")):
                parsed.append(values)
        except (KeyError, TypeError, ValueError, OverflowError):
            continue
    if not parsed:
        mapping = metadata.get("referenceCamera", {})
        try:
            parsed = [dict(fx=float(mapping["fx"]), fy=float(mapping["fy"]), cx=float(mapping["cx"]), cy=float(mapping["cy"]),
                           width=int(mapping["intrinsicsWidth"]), height=int(mapping["intrinsicsHeight"]))]
        except (KeyError, TypeError, ValueError, OverflowError):
            return None
    counts = {}
    for value in parsed:
        size = (value["width"], value["height"])
        counts[size] = counts.get(size, 0) + 1
    size = max(counts, key=counts.get)
    same_size = [value for value in parsed if (value["width"], value["height"]) == size]
    result = {"width": size[0], "height": size[1]}
    for key in ("fx", "fy", "cx", "cy"):
        values = sorted(value[key] for value in same_size)
        middle = len(values) // 2
        result[key] = values[middle] if len(values) % 2 else (values[middle - 1] + values[middle]) / 2
    distortion = calibration.get("distortion") if isinstance(calibration, dict) else None
    mapping_distortion = metadata.get("referenceCamera", {}).get("distortion")
    result["distortion"] = distortion if isinstance(distortion, list) else mapping_distortion if isinstance(mapping_distortion, list) else []
    return result


def _metadata_number(metadata, *keys):
    mapping = metadata.get("referenceCamera", {})
    for key in keys:
        value = mapping.get(key, metadata.get(key))
        try:
            number = float(value)
            if math.isfinite(number):
                return number
        except (TypeError, ValueError, OverflowError):
            pass
    return None


def reference_video_geometry(metadata, intrinsics):
    width = _metadata_number(metadata, "videoWidth", "reference_video_width")
    height = _metadata_number(metadata, "videoHeight", "reference_video_height")
    if not width or not height:
        resolution = metadata.get("referenceVideoResolution") or metadata.get("reference_video_resolution")
        try:
            width, height = (float(part) for part in str(resolution).lower().split("x", 1))
        except (TypeError, ValueError):
            width = height = None
    return int(width or intrinsics["width"]), int(height or intrinsics["height"])


def package_has_reference_video(path):
    try:
        with zipfile.ZipFile(path) as archive:
            info = archive.getinfo("reference_video.mp4")
            return info.file_size > 0
    except (OSError, KeyError, zipfile.BadZipFile):
        return False


def adapt_intrinsics_to_video(intrinsics, video_width, video_height, rotation_degrees=0):
    """Map unrotated CPU-image K through center crop/scale and recorded clockwise rotation."""
    rotation = int(rotation_degrees) % 360
    if rotation not in (0, 90, 180, 270):
        rotation = 0
    pre_width, pre_height = (video_height, video_width) if rotation in (90, 270) else (video_width, video_height)
    sw, sh = float(intrinsics["width"]), float(intrinsics["height"])
    source_aspect, target_aspect = sw / sh, pre_width / pre_height
    if abs(source_aspect - target_aspect) <= 1e-4:
        left = top = 0.0
        crop_width, crop_height = sw, sh
        mode = "scale"
    elif source_aspect > target_aspect:
        crop_width, crop_height = sh * target_aspect, sh
        left, top = (sw - crop_width) / 2.0, 0.0
        mode = "center_crop_then_scale_assumption"
    else:
        crop_width, crop_height = sw, sw / target_aspect
        left, top = 0.0, (sh - crop_height) / 2.0
        mode = "center_crop_then_scale_assumption"
    sx, sy = pre_width / crop_width, pre_height / crop_height
    fx, fy = intrinsics["fx"] * sx, intrinsics["fy"] * sy
    cx, cy = (intrinsics["cx"] - left) * sx, (intrinsics["cy"] - top) * sy
    if rotation == 90:
        fx, fy, cx, cy = fy, fx, pre_height - cy, cx
    elif rotation == 180:
        cx, cy = pre_width - cx, pre_height - cy
    elif rotation == 270:
        fx, fy, cx, cy = fy, fx, cy, pre_width - cx
    return dict(fx=fx, fy=fy, cx=cx, cy=cy, width=video_width, height=video_height,
                source_width=int(sw), source_height=int(sh), crop_left=left, crop_top=top,
                crop_width=crop_width, crop_height=crop_height, mode=mode, rotation=rotation,
                distortion=list(intrinsics.get("distortion", [])))


def configure_reference_projection(camera_data, projection):
    sensor_width = 36.0
    camera_data.type = 'PERSP'
    camera_data.sensor_fit = 'HORIZONTAL'
    camera_data.sensor_width = sensor_width
    camera_data.sensor_height = sensor_width * projection["height"] / projection["width"] * projection["fx"] / projection["fy"]
    camera_data.lens = projection["fx"] * sensor_width / projection["width"]
    # Blender: optical axis x_ndc=0.5-shift_x and y_ndc=0.5-shift_y*W/H.
    camera_data.shift_x = 0.5 - projection["cx"] / projection["width"]
    camera_data.shift_y = ((projection["cy"] - projection["height"] / 2.0) / projection["width"]
                           * projection["fx"] / projection["fy"])


def create_reference_scene(root_collection, camera, projection, source_scene):
    """Use a separate scene so exact fx/fy pixel aspect never changes the film render scene."""
    import bpy
    scene = bpy.data.scenes.new("RigTrack_Reference_View")
    scene.collection.children.link(root_collection)
    scene.camera = camera
    scene.render.resolution_x = int(projection["width"])
    scene.render.resolution_y = int(projection["height"])
    scene.render.resolution_percentage = 100
    ratio = projection["fx"] / projection["fy"]
    if ratio >= 1:
        scene.render.pixel_aspect_x = 1.0
        scene.render.pixel_aspect_y = ratio
    else:
        scene.render.pixel_aspect_x = 1.0 / ratio
        scene.render.pixel_aspect_y = 1.0
    scene.render.fps = source_scene.render.fps
    scene.render.fps_base = source_scene.render.fps_base
    scene.frame_start = source_scene.frame_start
    scene.frame_end = source_scene.frame_end
    scene["rigtrack_scene_type"] = "reference_projection_validation"
    scene["rigtrack_fx"] = projection["fx"]
    scene["rigtrack_fy"] = projection["fy"]
    return scene


def _set_constant_interpolation(animated_id):
    action = animated_id.animation_data.action if animated_id.animation_data else None
    if not action:
        return
    curves = []
    if hasattr(action, "fcurves"):
        curves.extend(action.fcurves)
    else:
        for layer in action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    curves.extend(bag.fcurves)
    for curve in curves:
        for key in curve.keyframe_points:
            key.interpolation = 'CONSTANT'


def animate_camera(camera, rows, start_frame, sync_offset_frames, scale, rotation_degrees=0, timeline_markers=False):
    from mathutils import Quaternion
    previous_q = None
    gap_start = None
    invalid_count = 0
    roll = Quaternion((0, 0, 1), math.radians(rotation_degrees))
    for row in rows:
        frame = start_frame + int(row["frame"]) - 1 + sync_offset_frames
        valid = row.get("valid", "false").lower() == "true"
        camera["tracking_valid"] = 1.0 if valid else 0.0
        camera.keyframe_insert(data_path='["tracking_valid"]', frame=frame)
        if not valid:
            invalid_count += 1
            if gap_start is None:
                gap_start = frame
                if timeline_markers:
                    import bpy
                    bpy.context.scene.timeline_markers.new("TRACKING LOST", frame=frame)
            continue
        if gap_start is not None:
            if timeline_markers:
                import bpy
                bpy.context.scene.timeline_markers.new("TRACKING RECOVERED", frame=frame)
            gap_start = None
        q = Quaternion(tuple(float(row[key]) for key in ("qw", "qx", "qy", "qz"))).normalized() @ roll
        if previous_q is not None and previous_q.dot(q) < 0:
            q.negate()
        previous_q = q.copy()
        apply_blender_transform(camera, (float(row[key]) for key in ("tx", "ty", "tz")), q, scale)
        camera.keyframe_insert(data_path="location", frame=frame)
        camera.keyframe_insert(data_path="rotation_quaternion", frame=frame)
    _set_constant_interpolation(camera)
    camera["invalid_frames"] = invalid_count
    return invalid_count


def _extract_reference_video(path):
    with zipfile.ZipFile(path) as archive:
        try:
            info = archive.getinfo("reference_video.mp4")
        except KeyError:
            return None
        if info.file_size <= 0 or info.file_size > 8 * 1024 * 1024 * 1024:
            return None
        signature = f"{os.path.abspath(path)}:{os.path.getmtime(path)}:{os.path.getsize(path)}:{info.file_size}"
        folder = os.path.join(tempfile.gettempdir(), "RigTrack", hashlib.sha256(signature.encode()).hexdigest()[:16])
        os.makedirs(folder, exist_ok=True)
        target = os.path.join(folder, "reference_video.mp4")
        if not os.path.isfile(target) or os.path.getsize(target) != info.file_size:
            with archive.open(info) as source, open(target, "wb") as output:
                shutil.copyfileobj(source, output, 1024 * 1024)
        return target


def attach_reference_video(path, camera, metadata, start_frame, sync_offset_frames, film_fps):
    import bpy
    video_path = _extract_reference_video(path)
    if not video_path:
        return False
    try:
        image = bpy.data.images.load(video_path, check_existing=True)
    except RuntimeError as error:
        print(f"Reference video could not be loaded: {error}")
        return False
    background = camera.data.background_images.new()
    background.source = 'IMAGE'
    background.image = image
    background.frame_method = 'FIT'
    background.alpha = 1.0
    background.image_user.use_auto_refresh = True
    background.image_user.frame_duration = max(1, image.frame_duration)
    record_start = _metadata_number(metadata, "record_start_monotonic_ns") or 0
    video_start = _metadata_number(metadata, "reference_video_start_timestamp_ns") or record_start
    video_start_frame = start_frame + sync_offset_frames + round((video_start - record_start) / 1e9 * film_fps)
    background.image_user.frame_start = video_start_frame
    video_fps = _metadata_number(metadata, "referenceVideoFps", "reference_video_fps") or film_fps
    index = len(camera.data.background_images) - 1
    end = max(video_start_frame, bpy.context.scene.frame_end)
    for frame in range(video_start_frame, end + 1):
        desired = round((frame - video_start_frame) * video_fps / film_fps) + 1
        base = frame - video_start_frame + 1
        background.image_user.frame_offset = desired - base
        camera.data.keyframe_insert(data_path=f"background_images[{index}].image_user.frame_offset", frame=frame)
    _set_constant_interpolation(camera.data)
    camera.data.show_background_images = True
    camera["rigtrack_video_filename"] = "reference_video.mp4"
    camera["rigtrack_video_cache_path"] = video_path
    camera["rigtrack_video_frame_start"] = video_start_frame
    camera["rigtrack_video_fps"] = video_fps
    return True


def import_track(path, fused=True, start_frame=1, sync_offset_frames=0, scale=1.0,
                 import_marker_map=True, create_tracking_origin=True, import_reference_camera=True,
                 attach_reference_movie=True, import_film_camera=True, track_source=None):
    import bpy
    metadata, film_rows, requested_source, film_source, film_source_name = read_track_with_source(path, fused, track_source)
    shot = metadata["shot"]
    film = shot["film"]
    fps = film["fps"]
    scene = bpy.context.scene
    # Blender integer FPS is bounded; NTSC uses nominal integer / 1.001.
    numerator, denominator = int(fps["numerator"]), int(fps["denominator"])
    nominal = round(numerator / denominator)
    scene.render.fps = nominal
    scene.render.fps_base = nominal * denominator / numerator
    scene.render.resolution_x = film["width"]
    scene.render.resolution_y = film["height"]
    scene.render.resolution_percentage = 100
    root_collection, camera_collection = _new_import_collections(scene)
    origin = _create_tracking_origin(root_collection, scale) if create_tracking_origin else None
    if film_rows:
        scene.frame_start = max(0, start_frame + sync_offset_frames)
        scene.frame_end = max(scene.frame_start, start_frame + int(film_rows[-1]["frame"]) - 1 + sync_offset_frames)

    film_camera = None
    film_invalid = 0
    if import_film_camera:
        model = "_".join(str(film.get("name", "Film_Camera")).split())
        name = f"RigTrack_Film_Camera_{model}"
        data = bpy.data.cameras.new(name)
        data.lens = film["focalLengthMm"]
        data.sensor_width = film["sensorWidthMm"]
        data.sensor_fit = 'HORIZONTAL'
        if film.get("sensorHeightMm", 0) > 0:
            data.sensor_height = film["sensorHeightMm"]
        if film.get("focusDistanceM", 0) > 0:
            data.dof.focus_distance = film["focusDistanceM"] * scale
        film_camera = bpy.data.objects.new(name, data)
        camera_collection.objects.link(film_camera)
        film_camera.rotation_mode = 'QUATERNION'
        film_camera["rigtrack_camera_type"] = "film"
        film_camera["rigtrack_source"] = f"rig_derived_{film_source.lower()}"
        film_camera["rigtrack_requested_track_source"] = requested_source
        film_camera["rigtrack_actual_track_source"] = film_source
        film_camera["rigtrack_pose_source"] = film_source_name
        film_camera["rigtrack_camera_model"] = film.get("name", "")
        film_camera["rigtrack_sensor_width_mm"] = float(film["sensorWidthMm"])
        film_camera["rigtrack_sensor_height_mm"] = float(film.get("sensorHeightMm", 0))
        film_camera["rigtrack_focal_length_mm"] = float(film["focalLengthMm"])
        film_camera["rigtrack_clock_warning"] = metadata.get("clock_mapping", "")
        film_camera["rigtrack_collection"] = root_collection.name
        film_invalid = animate_camera(film_camera, film_rows, start_frame, sync_offset_frames, scale, timeline_markers=True)
        scene.camera = film_camera

    reference_camera = None
    reference_invalid = 0
    if import_reference_camera:
        reference_rows, reference_source, reference_source_name = read_reference_track_with_source(path, metadata, film_rows, requested_source)
        intrinsics = read_reference_intrinsics(path, metadata)
        data = bpy.data.cameras.new("RigTrack_Reference_Camera")
        reference_camera = bpy.data.objects.new("RigTrack_Reference_Camera", data)
        camera_collection.objects.link(reference_camera)
        reference_camera.rotation_mode = 'QUATERNION'
        reference_camera["rigtrack_camera_type"] = "reference"
        reference_camera["rigtrack_source"] = f"phone_physical_camera_{reference_source.lower()}"
        reference_camera["rigtrack_requested_track_source"] = requested_source
        reference_camera["rigtrack_actual_track_source"] = reference_source
        reference_camera["rigtrack_pose_source"] = reference_source_name
        reference_camera["rigtrack_clock_warning"] = metadata.get("clock_mapping", "")
        reference_camera["rigtrack_collection"] = root_collection.name
        recording_rotation = int(_metadata_number(metadata, "videoRotationDegrees", "reference_video_rotation_degrees") or 0) % 360
        rotation = recording_rotation if package_has_reference_video(path) else 0
        reference_camera["rigtrack_recording_rotation_degrees"] = recording_rotation
        reference_camera["rigtrack_projection_rotation_degrees"] = rotation
        if intrinsics:
            video_width, video_height = reference_video_geometry(metadata, intrinsics)
            projection = adapt_intrinsics_to_video(intrinsics, video_width, video_height, rotation)
            configure_reference_projection(data, projection)
            for key in ("fx", "fy", "cx", "cy", "width", "height", "source_width", "source_height", "crop_left", "crop_top", "crop_width", "crop_height", "rotation"):
                reference_camera[f"rigtrack_{'image_' if key in ('width', 'height') else ''}{key}"] = projection[key]
            reference_camera["rigtrack_intrinsics_mapping"] = projection["mode"]
            reference_camera["rigtrack_distortion"] = projection["distortion"]
            reference_camera["rigtrack_fov_x_degrees"] = math.degrees(2 * math.atan(projection["width"] / (2 * projection["fx"])))
            reference_camera["rigtrack_fov_y_degrees"] = math.degrees(2 * math.atan(projection["height"] / (2 * projection["fy"])))
            reference_scene = create_reference_scene(root_collection, reference_camera, projection, scene)
            reference_camera["rigtrack_reference_scene"] = reference_scene.name
        else:
            reference_camera["rigtrack_projection_warning"] = "No usable phone intrinsics"
        reference_invalid = animate_camera(reference_camera, reference_rows, start_frame, sync_offset_frames, scale, rotation)
        frame_rate = numerator / denominator
        attached = attach_reference_video(path, reference_camera, metadata, start_frame, sync_offset_frames, frame_rate) if attach_reference_movie else False
        reference_camera["rigtrack_video_attached"] = attached
        reference_camera["rigtrack_video_filename"] = "reference_video.mp4"
        if film_camera is None:
            scene.camera = reference_camera

    if film_camera is None and reference_camera is None:
        raise ValueError("Enable at least one camera")
    marker_map = read_marker_map(path) if import_marker_map else None
    markers, skipped_markers = create_marker_objects(marker_map, root_collection, scale) if marker_map is not None else ([], 0)
    camera = film_camera or reference_camera
    for target in (film_camera, reference_camera):
        if target is not None:
            target["rigtrack_marker_count"] = len(markers)
            target["rigtrack_skipped_marker_count"] = skipped_markers
            target["rigtrack_origin_created"] = origin is not None
            target["rigtrack_reference_camera"] = reference_camera.name if reference_camera else ""
            target["rigtrack_film_camera"] = film_camera.name if film_camera else ""
    if import_marker_map and marker_map is not None and not markers:
        print("Marker map contains no valid mapped markers.")
    marker_text = f"{len(markers)} markers" if marker_map is not None else "no marker map"
    origin_text = " + tracking origin" if origin is not None else ""
    video_text = " + reference video" if reference_camera and reference_camera.get("rigtrack_video_attached") else ""
    print(f"RigTrack import complete: {int(reference_camera is not None)} reference camera + {int(film_camera is not None)} film camera + {marker_text}{origin_text}{video_text}.")
    return camera, film_invalid if film_camera else reference_invalid


try:
    import bpy
    from bpy_extras.io_utils import ImportHelper
    from bpy.props import BoolProperty, IntProperty, FloatProperty, StringProperty, EnumProperty

    class IMPORT_OT_vfxtrack(bpy.types.Operator, ImportHelper):
        bl_idname = "import_scene.vfxtrack"
        bl_label = "Import RigTrack"
        bl_options = {'UNDO'}
        filename_ext = ".vfxtrack"
        filter_glob: StringProperty(default="*.vfxtrack", options={'HIDDEN'})
        track_source: EnumProperty(name="Track Source", items=(("REFINED", "Refined", "Offline refined track (falls back safely)"),("FUSED", "Fused", "Real-time world-lock corrected track"),("RAW", "Raw", "Uncorrected source track")), default="REFINED")
        import_reference_camera: BoolProperty(name="Import Reference Camera", default=True)
        attach_reference_movie: BoolProperty(name="Attach Reference Video", default=True)
        import_film_camera: BoolProperty(name="Import Film Camera", default=True)
        import_marker_map: BoolProperty(name="Import Marker Map", default=True)
        create_tracking_origin: BoolProperty(name="Create Tracking Origin", default=True)
        start_frame: IntProperty(name="Start Frame", default=1, min=0)
        sync_offset_frames: IntProperty(name="Sync Offset Frames", default=0)
        scale: FloatProperty(name="Scale", default=1.0, min=0.00001)

        def draw(self, context):
            track = self.layout.box()
            track.label(text="Track")
            track.prop(self, "track_source")
            track.prop(self, "start_frame")
            track.prop(self, "sync_offset_frames")
            track.prop(self, "scale")
            scene = self.layout.box()
            scene.label(text="Scene")
            scene.prop(self, "import_reference_camera")
            scene.prop(self, "attach_reference_movie")
            scene.prop(self, "import_film_camera")
            scene.prop(self, "import_marker_map")
            scene.prop(self, "create_tracking_origin")

        def execute(self, context):
            try:
                camera, invalid = import_track(self.filepath, True, self.start_frame, self.sync_offset_frames,
                                               self.scale, self.import_marker_map, self.create_tracking_origin,
                                               self.import_reference_camera, self.attach_reference_movie, self.import_film_camera,
                                               track_source=self.track_source)
                markers = camera.get("rigtrack_marker_count", 0)
                skipped = camera.get("rigtrack_skipped_marker_count", 0)
                message = f"RigTrack import complete: reference + film cameras + {markers} markers; {invalid} invalid film frames"
                if skipped:
                    message += f"; skipped {skipped} invalid markers"
                self.report({'WARNING'} if invalid or skipped else {'INFO'}, message)
                return {'FINISHED'}
            except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
                self.report({'ERROR'}, str(error))
                return {'CANCELLED'}

    def menu(self, context):
        self.layout.operator(IMPORT_OT_vfxtrack.bl_idname, text="RigTrack (.vfxtrack)")

    def register():
        bpy.utils.register_class(IMPORT_OT_vfxtrack)
        bpy.types.TOPBAR_MT_file_import.append(menu)

    def unregister():
        bpy.types.TOPBAR_MT_file_import.remove(menu)
        bpy.utils.unregister_class(IMPORT_OT_vfxtrack)
except ImportError:
    pass

if __name__ == "__main__":
    import sys
    if "--" in sys.argv:
        import_track(sys.argv[sys.argv.index("--") + 1], track_source="REFINED")
    elif "bpy" in globals():
        register()
