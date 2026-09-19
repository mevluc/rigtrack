"""Blender --background --python tools/blender/test_in_blender.py -- fixture.vfxtrack [marker-output.vfxtrack]"""
import importlib.util
import sys
import csv
import io
import json
import math
import tempfile
import zipfile
from pathlib import Path
import bpy
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

spec = importlib.util.spec_from_file_location("import_vfxtrack", Path(__file__).with_name("import_vfxtrack.py"))
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
arguments = sys.argv[sys.argv.index("--") + 1:]
path = Path(arguments[0])
marker_output = Path(arguments[1]) if len(arguments) > 1 else None


def assert_vector(actual, expected, tolerance=1e-5):
    assert (Vector(actual) - Vector(expected)).length < tolerance, f"{tuple(actual)} != {expected}"


def transform(location, quaternion=(0.0, 0.0, 0.0, 1.0)):
    return {
        "t": dict(zip(("x", "y", "z"), location)),
        "q": dict(zip(("x", "y", "z", "w"), quaternion)),
    }


def reference(marker_id, location, quaternion=(0.0, 0.0, 0.0, 1.0), **extra):
    value = {
        "id": marker_id,
        "sizeM": 0.15,
        "T_anchor_marker": transform(location, quaternion),
        "observations": 30 + marker_id,
    }
    value.update(extra)
    return value


def synthetic_marker_map():
    s = math.sin(math.pi / 4)
    c = math.cos(math.pi / 4)
    return {
        "name": "Synthetic Stage",
        "dictionary": 20,
        "references": [
            reference(1, (1, 0, 0)),
            reference(2, (0, 1, 0)),
            reference(3, (0, 0, -2)),
            reference(4, (2, 0, 0), (0, s, 0, c)),
            reference(5, (2, 1, 0), (s, 0, 0, c)),
            reference(6, (2, 0, -1), (0, 0, s, c)),
            reference(7, (0, 0, 2), confidence=0.8, reprojectionError=0.4),
            reference(97, (0, 0, 0), (0, 0, 0, 0)),
            reference(98, ("Infinity", 0, 0)),
            {"id": 99, "sizeM": 0.15, "T_anchor_marker": {"t": {"x": "NaN", "y": 0, "z": 0}}},
        ],
        "originNote": "Synthetic importer regression map",
        "markerSizesM": {"1": 0.15},
    }


def rewrite_marker_map(source, target, marker_map, omit=False):
    target.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source) as incoming, zipfile.ZipFile(target, "w") as outgoing:
        for info in incoming.infolist():
            if info.filename != "marker_map.json":
                outgoing.writestr(info, incoming.read(info))
        if not omit:
            outgoing.writestr("marker_map.json", json.dumps(marker_map).encode("utf-8"))


def rewrite_package(source, target, replacements):
    with zipfile.ZipFile(source) as incoming, zipfile.ZipFile(target, "w") as outgoing:
        for info in incoming.infolist():
            if info.filename not in replacements:
                outgoing.writestr(info, incoming.read(info))
        for name, data in replacements.items():
            if data is not None:
                outgoing.writestr(name, data.encode("utf-8") if isinstance(data, str) else data)


def create_test_movie(target):
    sample = Path(__file__).parents[2] / "output" / "reference-video-round" / "reference_player_sample.mp4"
    target.write_bytes(sample.read_bytes())
    assert target.is_file() and target.stat().st_size > 0


def root_for(camera):
    return bpy.data.collections[camera["rigtrack_collection"]]


def marker_collection(root):
    return next(collection for collection in root.children if collection.name.startswith("RigTrack_Markers"))


def marker_by_id(root, marker_id):
    return next(obj for obj in marker_collection(root).objects if obj.get("rigtrack_marker_id") == marker_id)


# Existing camera importer regression.
camera, invalid = module.import_track(path, track_source="REFINED")
scene = bpy.context.scene
reference_camera = bpy.data.objects[camera["rigtrack_reference_camera"]]
assert scene.render.fps == 25
assert abs(scene.render.fps_base - 1.0) < 1e-6
assert abs(camera.data.lens - 35) < 1e-6
assert abs(camera.data.sensor_width - 35.6) < 1e-4
assert camera["rigtrack_camera_type"] == "film"
assert camera["rigtrack_requested_track_source"] == "REFINED"
assert camera["rigtrack_actual_track_source"] == "REFINED"
assert reference_camera["rigtrack_actual_track_source"] == "REFINED"
assert reference_camera["rigtrack_camera_type"] == "reference"
assert scene.camera is camera
with zipfile.ZipFile(path) as initial_archive:
    input_has_video = "reference_video.mp4" in initial_archive.namelist()
assert len(reference_camera.data.background_images) == (1 if input_has_video else 0)
assert len(camera.data.background_images) == 0
scene.frame_set(26)
assert_vector(reference_camera.location, camera.location)
assert abs(reference_camera.data.lens - camera.data.lens) > 1.0
assert camera.rotation_mode == 'QUATERNION'
assert camera["rigtrack_marker_count"] == 0
scene.frame_set(26)
assert abs(camera.location.y - 1) < .005
scene.frame_set(51)
assert abs(camera.location.x - 1) < .005
assert abs(camera.location.y - 1) < .005
raw, _ = module.import_track(path, fused=False, start_frame=10, sync_offset_frames=2, scale=2)
scene.frame_set(37)
assert abs(raw.location.y - 2) < 1e-5

# Import UI remains registered and exposes the new default-on scene controls.
module.register()
properties = bpy.ops.import_scene.vfxtrack.get_rna_type().properties
assert properties["import_marker_map"].default is True
assert properties["create_tracking_origin"].default is True
assert properties["import_reference_camera"].default is True
assert properties["attach_reference_movie"].default is True
assert properties["import_film_camera"].default is True
assert properties["track_source"].default == "REFINED"
module.unregister()

with tempfile.TemporaryDirectory() as temp_name:
    temp = Path(temp_name)

    # New default source falls back explicitly for older v1 packages.
    no_refined = temp / "no-refined.vfxtrack"
    rewrite_package(path, no_refined, {"blender_camera_refined.csv": None, "blender_reference_camera_refined.csv": None,
                                      "film_camera_refined.csv": None, "phone_camera_refined.csv": None})
    fallback_film, _ = module.import_track(no_refined, track_source="REFINED")
    fallback_reference = bpy.data.objects[fallback_film["rigtrack_reference_camera"]]
    assert fallback_film["rigtrack_requested_track_source"] == "REFINED"
    assert fallback_film["rigtrack_actual_track_source"] == "FUSED"
    assert fallback_reference["rigtrack_actual_track_source"] == "FUSED"

    raw_only = temp / "raw-only.vfxtrack"
    rewrite_package(path, raw_only, {"blender_camera_refined.csv": None, "blender_reference_camera_refined.csv": None,
                                     "blender_camera.csv": None, "blender_reference_camera.csv": None,
                                     "film_camera_refined.csv": None, "phone_camera_refined.csv": None,
                                     "film_camera_fused.csv": None, "phone_camera_fused.csv": None})
    raw_fallback_film, _ = module.import_track(raw_only, track_source="REFINED")
    raw_fallback_reference = bpy.data.objects[raw_fallback_film["rigtrack_reference_camera"]]
    assert raw_fallback_film["rigtrack_actual_track_source"] == "RAW"
    assert raw_fallback_reference["rigtrack_actual_track_source"] == "RAW"

    # Exact pinhole, principal point, resolution scaling and center-crop math.
    centered = {"fx": 1000.0, "fy": 1000.0, "cx": 960.0, "cy": 540.0, "width": 1920, "height": 1080}
    scaled = module.adapt_intrinsics_to_video(centered, 1280, 720)
    assert abs(scaled["fx"] - 2000 / 3) < 1e-8
    assert abs(scaled["cx"] - 640) < 1e-8 and abs(scaled["cy"] - 360) < 1e-8
    off_center = dict(centered, cx=900.0, cy=500.0)
    projected = module.adapt_intrinsics_to_video(off_center, 1920, 1080)
    projection_data = bpy.data.cameras.new("Projection_Test")
    projection_object = bpy.data.objects.new("Projection_Test", projection_data)
    scene.collection.objects.link(projection_object)
    module.configure_reference_projection(projection_data, projected)
    old_resolution = scene.render.resolution_x, scene.render.resolution_y
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    def blender_pixel(point):
        ndc = world_to_camera_view(scene, projection_object, Vector(point))
        return ndc.x * 1920, (1 - ndc.y) * 1080
    center_pixel = blender_pixel((0, 0, -1))
    right_pixel = blender_pixel((.1, 0, -1))
    up_pixel = blender_pixel((0, .1, -1))
    assert abs(center_pixel[0] - 900) < 1e-4 and abs(center_pixel[1] - 500) < 1e-4
    assert abs(right_pixel[0] - 1000) < 1e-4 and abs(up_pixel[1] - 400) < 1e-4
    nonsquare = dict(projected, fy=800.0)
    module.configure_reference_projection(projection_data, nonsquare)
    projection_root = bpy.data.collections.new("Projection_Root")
    projection_root.objects.link(projection_object)
    reference_projection_scene = module.create_reference_scene(projection_root, projection_object, nonsquare, scene)
    ndc = world_to_camera_view(reference_projection_scene, projection_object, Vector((0, .1, -1)))
    assert abs(ndc.x * 1920 - 900) < 1e-4
    assert abs((1 - ndc.y) * 1080 - 420) < 1e-4
    crop = module.adapt_intrinsics_to_video(dict(centered, height=1440, cy=720.0), 1920, 1080)
    assert crop["mode"] == "center_crop_then_scale_assumption"
    assert abs(crop["crop_top"] - 180) < 1e-8 and abs(crop["cy"] - 540) < 1e-8
    rotated = module.adapt_intrinsics_to_video(centered, 1080, 1920, 90)
    assert rotated["rotation"] == 90 and abs(rotated["cx"] - 540) < 1e-8 and abs(rotated["cy"] - 960) < 1e-8
    rotated_data = bpy.data.cameras.new("Rotated_Projection_Test")
    rotated_object = bpy.data.objects.new("Rotated_Projection_Test", rotated_data)
    rotated_root = bpy.data.collections.new("Rotated_Projection_Root"); rotated_root.objects.link(rotated_object)
    module.configure_reference_projection(rotated_data, rotated)
    rotated_object.rotation_mode = 'QUATERNION'
    from mathutils import Quaternion
    rotated_object.rotation_quaternion = Quaternion((0, 0, 1), math.pi / 2)
    rotated_scene = module.create_reference_scene(rotated_root, rotated_object, rotated, scene)
    rotated_scene.view_layers[0].update()
    rotated_ndc = world_to_camera_view(rotated_scene, rotated_object, Vector((.1, 0, -1)))
    rotated_pixel = rotated_ndc.x * 1080, (1 - rotated_ndc.y) * 1920
    assert abs(rotated_pixel[0] - 540) < 1e-4, rotated_pixel
    assert abs(rotated_pixel[1] - 1060) < 1e-4, rotated_pixel
    scene.render.resolution_x, scene.render.resolution_y = old_resolution

    # A non-zero rig moves only the film track; reference remains the physical phone track.
    nonzero_package = temp / "nonzero-rig.vfxtrack"
    with zipfile.ZipFile(path) as archive:
        film_csv = list(csv.DictReader(io.StringIO(archive.read("blender_camera.csv").decode())))
        fieldnames = film_csv[0].keys()
        for row in film_csv:
            if row["valid"].lower() == "true":
                row["tx"] = str(float(row["tx"]) + .10)
        stream = io.StringIO(); writer = csv.DictWriter(stream, fieldnames=fieldnames, lineterminator="\n"); writer.writeheader(); writer.writerows(film_csv)
        metadata = json.loads(archive.read("metadata.json"))
    metadata["shot"]["rig"]["name"] = "Synthetic nonzero"
    metadata["shot"]["rig"]["T_phoneCamera_filmCamera"]["t"] = {"x": .10, "y": 0, "z": 0}
    rewrite_package(path, nonzero_package, {"blender_camera.csv": stream.getvalue(), "metadata.json": json.dumps(metadata)})
    nonzero_film, _ = module.import_track(nonzero_package)
    nonzero_reference = bpy.data.objects[nonzero_film["rigtrack_reference_camera"]]
    scene.frame_set(26)
    assert abs(nonzero_film.location.x - nonzero_reference.location.x - .10) < 1e-5

    # Legacy v1 packages without explicit camera mapping/reference-derived CSV still import from phone/ar_pose.
    legacy_package = temp / "legacy-v1.vfxtrack"
    with zipfile.ZipFile(path) as archive:
        legacy_metadata = json.loads(archive.read("metadata.json"))
    legacy_metadata.pop("referenceCamera", None); legacy_metadata.pop("filmCamera", None)
    rewrite_package(path, legacy_package, {"metadata.json": json.dumps(legacy_metadata),
                    "blender_reference_camera.csv": None, "blender_reference_camera_raw.csv": None})
    legacy_film, _ = module.import_track(legacy_package)
    legacy_reference = bpy.data.objects[legacy_film["rigtrack_reference_camera"]]
    scene.frame_set(26)
    assert_vector(legacy_reference.location, legacy_film.location)

    film_only, _ = module.import_track(path, import_reference_camera=False)
    assert film_only["rigtrack_reference_camera"] == ""
    reference_only, _ = module.import_track(path, import_film_camera=False)
    assert reference_only["rigtrack_camera_type"] == "reference"

    # A valid MP4 attaches only to Reference Camera and is time-mapped by timestamps/FPS.
    movie = temp / "reference_video.mp4"
    create_test_movie(movie)
    video_package = temp / "valid-video.vfxtrack"
    with zipfile.ZipFile(path) as archive:
        video_metadata = json.loads(archive.read("metadata.json"))
    video_metadata.update(reference_video_width=1280, reference_video_height=720, reference_video_fps=50,
                          reference_video_rotation_degrees=0,
                          reference_video_start_timestamp_ns=video_metadata["record_start_monotonic_ns"] + 400_000_000,
                          referenceVideoAvailable=True, referenceVideoIncludedInPackage=True)
    rewrite_package(path, video_package, {"metadata.json": json.dumps(video_metadata), "reference_video.mp4": movie.read_bytes()})
    video_film, _ = module.import_track(video_package)
    video_reference = bpy.data.objects[video_film["rigtrack_reference_camera"]]
    assert video_reference["rigtrack_video_attached"] is True
    assert len(video_reference.data.background_images) == 1 and len(video_film.data.background_images) == 0
    assert video_reference["rigtrack_video_frame_start"] == 11
    scene.frame_set(12)
    assert video_reference.data.background_images[0].image_user.frame_offset == 1

    # Fractional FPS behavior remains unchanged.
    for numerator, nominal in ((24000, 24), (30000, 30), (60000, 60)):
        target = temp / f"fps_{numerator}.vfxtrack"
        with zipfile.ZipFile(path) as source, zipfile.ZipFile(target, "w") as output:
            for name in source.namelist():
                data = source.read(name)
                if name == "metadata.json":
                    meta = json.loads(data)
                    meta["shot"]["film"]["fps"] = {"numerator": numerator, "denominator": 1001}
                    data = json.dumps(meta).encode()
                output.writestr(name, data)
        module.import_track(target)
        assert scene.render.fps == nominal
        assert abs(scene.render.fps_base - 1.001) < 1e-6

    # The same synthetic map is tested on whichever video package variant was supplied.
    marker_package = marker_output or temp / "synthetic-marker-map.vfxtrack"
    rewrite_marker_map(path, marker_package, synthetic_marker_map())
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(marker_package) as marked:
        assert ("reference_video.mp4" in source.namelist()) == ("reference_video.mp4" in marked.namelist())

    marker_camera, _ = module.import_track(marker_package)
    root = root_for(marker_camera)
    assert root.name.startswith("RigTrack")
    assert any(collection.name.startswith("RigTrack_Camera") for collection in root.children)
    assert any(collection.name.startswith("RigTrack_Markers") for collection in root.children)
    origin = next(obj for obj in root.objects if obj.get("rigtrack_tracking_origin"))
    assert_vector(origin.location, (0, 0, 0))
    assert_vector(origin.rotation_quaternion, (1, 0, 0, 0))
    assert marker_camera["rigtrack_marker_count"] == 7
    assert marker_camera["rigtrack_skipped_marker_count"] == 3

    # AR X -> Blender X, AR Y -> Blender Z, AR -Z -> Blender +Y.
    marker_1 = marker_by_id(root, 1)
    marker_2 = marker_by_id(root, 2)
    marker_3 = marker_by_id(root, 3)
    marker_7 = marker_by_id(root, 7)
    assert_vector(marker_1.location, (1, 0, 0))
    assert_vector(marker_2.location, (0, 0, 1))
    assert_vector(marker_3.location, (0, 2, 0))
    assert_vector(marker_7.location, (0, -2, 0))

    # Marker local +Z is its plane normal. Identity/yaw/pitch/roll retain orientation through B*T.
    assert_vector(marker_1.rotation_quaternion @ Vector((0, 0, 1)), (0, -1, 0))
    assert_vector(marker_by_id(root, 4).rotation_quaternion @ Vector((0, 0, 1)), (1, 0, 0))
    assert_vector(marker_by_id(root, 5).rotation_quaternion @ Vector((0, 0, 1)), (0, 0, -1))
    assert_vector(marker_by_id(root, 6).rotation_quaternion @ Vector((1, 0, 0)), (0, 0, 1))

    # Camera begins at the origin looking Blender +Y; marker 3 stays two metres in front.
    scene.frame_set(1)
    assert_vector(marker_camera.location, (0, 0, 0))
    assert abs((marker_3.location - marker_camera.location).length - 2.0) < 1e-5
    assert marker_3["rigtrack_marker_id"] == 3
    assert marker_3["rigtrack_dictionary"] == 20
    assert abs(marker_3["rigtrack_physical_size_m"] - 0.15) < 1e-8
    assert marker_3["rigtrack_observation_count"] == 33
    assert abs(marker_7["rigtrack_confidence"] - 0.8) < 1e-8
    assert abs(marker_7["rigtrack_reprojection_error"] - 0.4) < 1e-8

    # Scale applies equally to camera translation, marker translation and display size.
    scaled_camera, _ = module.import_track(marker_package, scale=10)
    scaled_root = root_for(scaled_camera)
    assert scaled_root.name != root.name
    assert marker_by_id(root, 3) is marker_3
    scaled_marker = marker_by_id(scaled_root, 3)
    scene.frame_set(26)
    assert abs(scaled_camera.location.y - 10) < 1e-5
    assert_vector(scaled_marker.location, (0, 20, 0))
    assert abs(scaled_marker.empty_display_size - 1.5) < 1e-5
    assert abs(scaled_marker["rigtrack_physical_size_m"] - 0.15) < 1e-8

    # Options can independently suppress marker-map and origin creation.
    camera_only, _ = module.import_track(marker_package, import_marker_map=False, create_tracking_origin=False)
    camera_only_root = root_for(camera_only)
    assert camera_only["rigtrack_marker_count"] == 0
    assert not any(collection.name.startswith("RigTrack_Markers") for collection in camera_only_root.children)
    assert not any(obj.get("rigtrack_tracking_origin") for obj in camera_only_root.objects)

    # Missing and empty maps are non-fatal and keep camera import intact.
    missing_package = temp / "missing-marker-map.vfxtrack"
    rewrite_marker_map(path, missing_package, None, omit=True)
    missing_camera, _ = module.import_track(missing_package)
    assert missing_camera["rigtrack_marker_count"] == 0
    empty_package = temp / "empty-marker-map.vfxtrack"
    rewrite_marker_map(path, empty_package, {"name": "Empty", "dictionary": 20, "references": []})
    empty_camera, _ = module.import_track(empty_package)
    assert empty_camera["rigtrack_marker_count"] == 0

print("BLENDER_IMPORT_TEST_PASSED: camera, marker positions/rotations, relation, scale, properties, options, compatibility")
