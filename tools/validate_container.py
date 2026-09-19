"""Independent, standard-library-only validation: python tools/validate_container.py take.vfxtrack."""
import csv
import io
import json
import math
import sys
import zipfile
from fractions import Fraction


def validate(path):
    required = {"metadata.json", "calibration.json", "rig.json", "marker_map.json", "ar_pose.csv",
                "film_camera_raw.csv", "film_camera_fused.csv", "film_camera_refined.csv", "phone_camera_refined.csv",
                "imu.csv", "markers.csv", "intrinsics.csv", "events.csv", "diagnostics.json", "blender_camera.csv",
                "blender_camera_refined.csv", "blender_reference_camera_refined.csv", "README.txt"}
    with zipfile.ZipFile(path) as archive:
        missing = required - set(archive.namelist())
        assert not missing, f"Missing entries: {missing}"
        assert archive.testzip() is None, "ZIP CRC error"
        metadata = json.loads(archive.read("metadata.json"))
        assert metadata["format_version"] == 1, "Unsupported format"
        derived_tracks = ["blender_camera.csv", "blender_camera_raw.csv", "blender_camera_refined.csv"]
        if "referenceCamera" in metadata:
            assert metadata["referenceCamera"]["source"] == "phone_physical_camera"
            assert metadata["filmCamera"]["source"] == "rig_derived"
            derived_tracks += ["blender_reference_camera.csv", "blender_reference_camera_raw.csv", "blender_reference_camera_refined.csv"]
            assert not (set(derived_tracks) - set(archive.namelist())), "Missing explicit camera tracks"
        fps = metadata["shot"]["film"]["fps"]
        rational = Fraction(fps["numerator"], fps["denominator"])
        for name in ("calibration.json", "rig.json", "marker_map.json", "diagnostics.json"):
            json.loads(archive.read(name))
        counts = {}
        for name in derived_tracks:
            count = 0
            with archive.open(name) as stream:
                for row in csv.DictReader(io.TextIOWrapper(stream, encoding="utf-8")):
                    count += 1
                    assert int(row["frame"]) == count
                    assert abs(float(row["time_s"]) - float(Fraction(count - 1) / rational)) < 1e-9
                    if row["valid"] == "true":
                        quaternion = [float(row[key]) for key in ("qx", "qy", "qz", "qw")]
                        assert all(math.isfinite(value) for value in quaternion)
                        assert abs(sum(value * value for value in quaternion) - 1) < 1e-7
                    else:
                        assert all(row[key] == "" for key in ("tx", "ty", "tz", "qx", "qy", "qz", "qw"))
            counts[name] = count
        with archive.open("ar_pose.csv") as stream:
            timestamps = (int(row["timestamp_ns"]) for row in csv.DictReader(io.TextIOWrapper(stream)))
            previous = None
            for timestamp in timestamps:
                assert previous is None or timestamp > previous
                previous = timestamp
    print(f"VFXTRACK_VALIDATION_PASSED: CRC, required entries, JSON, int64 timestamps, rational FPS, normalized quaternions: {counts}")


if __name__ == "__main__":
    validate(sys.argv[1])
