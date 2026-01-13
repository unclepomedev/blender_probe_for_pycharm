# SPDX-License-Identifier: GPL-3.0-or-later

import bpy


class OBJECT_OT_sample_operator(bpy.types.Operator):
    """Sample Operator: Renames the active object and adds a property"""

    bl_idname = "object.sample_operator"
    bl_label = "Sample Operator"
    bl_options = {"REGISTER", "UNDO"}

    @classmethod
    def poll(cls, context: bpy.types.Context) -> bool:
        return context.active_object is not None

    def execute(self, context: bpy.types.Context) -> set[str]:
        obj = context.active_object

        # Logic to be tested:
        # 1. Modify the object name
        original_name = obj.name
        obj.name = f"{original_name}_processed"

        # 2. Add a custom property tag
        obj["is_processed"] = True

        self.report({"INFO"}, f"Processed object: {original_name} -> {obj.name}")
        return {"FINISHED"}


def register():
    bpy.utils.register_class(OBJECT_OT_sample_operator)


def unregister():
    bpy.utils.unregister_class(OBJECT_OT_sample_operator)
