import bpy

try:
    if bpy.context.view_layer:
        bpy.context.view_layer.update()
    bpy.ops.wm.redraw_timer(type='DRAW_WIN_SWAP', iterations=1)
except Exception:
    pass
