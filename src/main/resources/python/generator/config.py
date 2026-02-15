import bpy
from dataclasses import dataclass, field
from .template_loader import template_loader


@dataclass(frozen=True)
class GeneratorConfig:
    output_dir: str
    extra_modules: list[str] = field(
        default_factory=lambda: [
            "addon_utils",
            "bl_ui",
            "blf",
            "gpu",
            "gpu_extras",
            "bmesh",
            "mathutils",
            "bpy_extras",
            "aud",
            "imbuf",
            "idprop",
        ]
    )
    force_modules: list[str] = field(
        default_factory=lambda: [
            "gpu_extras.batch",
            "gpu_extras.presets",
            "mathutils.noise",
            "mathutils.geometry",
            "mathutils.bvhtree",
            "mathutils.kdtree",
            "mathutils.interpolate",
            "bpy_extras.anim_utils",
            "bpy_extras.object_utils",
            "bpy_extras.io_utils",
            "bpy_extras.image_utils",
            "bpy_extras.mesh_utils",
            "bpy_extras.node_utils",
            "bpy_extras.view3d_utils",
            "bpy_extras.keyconfig_utils",
        ]
    )
    gpu_submodules: list[str] = field(
        default_factory=lambda: [
            "shader",
            "types",
            "matrix",
            "state",
            "texture",
            "platform",
            "select",
            "capabilities",
        ]
    )
    app_submodules: list[str] = field(
        default_factory=lambda: ["handlers", "translations", "timers", "icons"]
    )
    no_docs_modules: set[str] = field(default_factory=lambda: {"bl_ui", "addon_utils"})
    common_headers: list[str] = field(
        default_factory=lambda: [
            f"# Blender Probe Generated Stub for Blender {bpy.app.version_string}",
            "# noinspection PyPep8Naming",
            "# noinspection PyUnresolvedReferences",
            "# noqa: N801",
            "# pylint: disable=invalid-name",
            "",
        ]
    )
    # --- Manual Injections ---
    # NOTE: We use 'Any' for complex types in Context/Struct injections
    # to avoid missing import errors in the generated .pyi files.
    manual_injections: dict[str, list[str]] = field(
        default_factory=lambda: {
            "bpy_struct": template_loader.get_injection("bpy_struct"),
            "Context": template_loader.get_injection("Context"),
            "Object": template_loader.get_injection("Object"),
            "Collection": template_loader.get_injection("Collection"),
            "ID": template_loader.get_injection("ID"),
            "SpaceView3D": template_loader.get_injection("SpaceView3D"),
            "KeyConfigurations": template_loader.get_injection("KeyConfigurations"),
            "BMLoop": template_loader.get_injection("BMLoop"),
            "BMVert": template_loader.get_injection("BMVert"),
            "BMEdge": template_loader.get_injection("BMEdge"),
            "BMFace": template_loader.get_injection("BMFace"),
        }
    )

    math_types_whitelist: set[str] = field(
        default_factory=lambda: {"Vector", "Matrix", "Quaternion", "Euler", "Color"}
    )

    @property
    def bpy_dir(self) -> str:
        import os

        return os.path.join(self.output_dir, "bpy")

    @property
    def bpy_types_dir(self) -> str:
        import os

        return os.path.join(self.bpy_dir, "types")
