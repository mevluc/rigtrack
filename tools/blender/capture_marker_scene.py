"""Create a reviewable two-camera + marker .blend and Blender UI screenshot."""
import importlib.util
import sys
from pathlib import Path

import bpy

arguments = sys.argv[sys.argv.index("--") + 1:]
package, screenshot, blend = map(lambda value: Path(value).resolve(), arguments[:3])
module_path = Path(__file__).with_name("import_vfxtrack.py")
spec = importlib.util.spec_from_file_location("import_vfxtrack_validation", module_path)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

for obj in tuple(bpy.data.objects):
    bpy.data.objects.remove(obj, do_unlink=True)

camera, _ = module.import_track(package, track_source="REFINED")
reference_camera = bpy.data.objects[camera["rigtrack_reference_camera"]]
scene = bpy.context.scene
scene.frame_set(1)
root = bpy.data.collections[camera["rigtrack_collection"]]

bpy.ops.object.select_all(action='DESELECT')
for collection in root.children:
    for obj in collection.objects:
        obj.select_set(True)
for obj in root.objects:
    obj.select_set(True)
camera.select_set(True)
reference_camera.select_set(True)
bpy.context.view_layer.objects.active = camera

for area in bpy.context.screen.areas:
    if area.type == 'VIEW_3D':
        region = next(item for item in area.regions if item.type == 'WINDOW')
        area.spaces.active.overlay.show_relationship_lines = True
        area.spaces.active.overlay.show_text = True
        with bpy.context.temp_override(area=area, region=region):
            bpy.ops.view3d.view_selected(use_all_regions=False)
    elif area.type == 'OUTLINER':
        region = next(item for item in area.regions if item.type == 'WINDOW')
        with bpy.context.temp_override(area=area, region=region):
            bpy.ops.outliner.show_hierarchy()

blend.parent.mkdir(parents=True, exist_ok=True)
screenshot.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.save_as_mainfile(filepath=str(blend))


def capture_and_quit():
    bpy.ops.screen.screenshot(filepath=str(screenshot))
    print(f"BLENDER_TWO_CAMERA_REFINED_SCENE_CAPTURED: reference + film cameras, {camera['rigtrack_marker_count']} markers, {blend}, {screenshot}")
    bpy.ops.wm.quit_blender()
    return None


bpy.app.timers.register(capture_and_quit, first_interval=1.0)
