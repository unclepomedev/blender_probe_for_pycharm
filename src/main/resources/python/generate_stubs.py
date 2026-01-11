import importlib
import inspect
import keyword
import os
import pkgutil
import sys
import traceback
from dataclasses import dataclass, field

import bpy


@dataclass(frozen=True)
class GeneratorConfig:
    output_dir: str
    extra_modules: list[str] = field(default_factory=lambda: [
        "addon_utils", "bl_ui", "blf", "gpu", "gpu_extras", "bmesh",
        "mathutils", "bpy_extras", "aud", "imbuf", "idprop",
    ])
    force_modules: list[str] = field(default_factory=lambda: [
        "gpu_extras.batch", "gpu_extras.presets", "mathutils.noise",
        "mathutils.geometry", "mathutils.bvhtree", "mathutils.kdtree",
        "mathutils.interpolate", "bpy_extras.anim_utils",
        "bpy_extras.object_utils", "bpy_extras.io_utils",
        "bpy_extras.image_utils", "bpy_extras.mesh_utils",
        "bpy_extras.node_utils", "bpy_extras.view3d_utils",
        "bpy_extras.keyconfig_utils",
    ])
    gpu_submodules: list[str] = field(default_factory=lambda: [
        "shader", "types", "matrix", "state", "texture", "platform", "select", "capabilities"
    ])
    app_submodules: list[str] = field(default_factory=lambda: [
        "handlers", "translations", "timers", "icons"
    ])
    no_docs_modules: set[str] = field(default_factory=lambda: {
        "bl_ui", "addon_utils"
    })
    common_headers: list[str] = field(default_factory=lambda: [
        "# noinspection PyPep8Naming",
        "# noinspection PyUnresolvedReferences",
        "# noqa: N801",
        "# pylint: disable=invalid-name",
        "",
    ])
    # --- Manual Injections ---
    # NOTE: We use 'Any' for complex types in Context/Struct injections
    # to avoid missing import errors in the generated .pyi files.
    manual_injections: dict[str, list[str]] = field(default_factory=lambda: {
        "bpy_struct": [
            "    def __getattr__(self, name) -> Any: ...",
            "    def temp_override(self, window=None, area=None, region=None, **kwargs) -> Any: ...",
            "    def as_pointer(self) -> int: ...",
            "    def driver_add(self, path: str, index: int = -1) -> Any: ...",
            "    def driver_remove(self, path: str, index: int = -1) -> bool: ...",
            "    def get(self, key: str, default: Any = None) -> Any: ...",
            "    def items(self) -> list[tuple[str, Any]]: ...",
            "    def keys(self) -> list[str]: ...",
            "    def values(self) -> list[Any]: ...",
            "    def path_from_id(self, property: str = '') -> str: ...",
            "    id_data: Any",
        ],
        "Context": [
            "    selected_objects: list[Any]",
            "    active_object: Any",
            "    view_layer: Any",
            "    scene: Any",
            "    screen: Any",
            "    area: Any",
            "    region: Any",
            "    window: Any",
            "    window_manager: Any",
            "    preferences: Any",
            "    def temp_override(self, window=None, area=None, region=None, **kwargs) -> Any: ...",
            "    def __getattr__(self, name) -> Any: ...",
        ],
        # Object methods usually handled by C
        "Object": [
            "    def select_set(self, state: bool) -> None: ...",
            "    def select_get(self) -> bool: ...",
            "    def hide_set(self, state: bool) -> None: ...",
            "    def hide_get(self) -> bool: ...",
            "    def hide_viewport_set(self, state: bool) -> None: ...",
            "    def hide_render_set(self, state: bool) -> None: ...",
            "    def temp_override(self, window=None, area=None, region=None, **kwargs) -> Any: ...",
        ],
        "Collection": [
            "    def temp_override(self, window=None, area=None, region=None, **kwargs) -> Any: ...",
        ],
        "ID": [
            "    name: str",
        ],
        # SpaceView3D static handlers
        "SpaceView3D": [
            "    @classmethod",
            "    def draw_handler_add(cls, callback: Callable, args: tuple, region_type: str, draw_type: str) -> object: ...",
            "    @classmethod",
            "    def draw_handler_remove(cls, handler: object, region_type: str) -> None: ...",
        ],
        "KeyConfigurations": [
            "    addon: Any",
            "    user: Any",
            "    active: Any",
        ],
    })

    @property
    def bpy_dir(self) -> str:
        return os.path.join(self.output_dir, "bpy")

    @property
    def bpy_types_dir(self) -> str:
        return os.path.join(self.bpy_dir, "types")


class StubGenerator:
    def __init__(self, config: GeneratorConfig):
        self.config = config

    def write_file(self, directory: str, filename: str, content: list[str]):
        if not os.path.exists(directory):
            os.makedirs(directory)
        filepath = os.path.join(directory, filename)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write("\n".join(content))

    def format_docstring(self, doc_str: str, indent: str = "    ") -> str:
        if not doc_str: return ""
        doc_str = doc_str.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
        if doc_str.endswith('"'): doc_str += " "
        return f'{indent}"""{doc_str}"""'

    def get_api_docs_link(self, module_name: str) -> str | None:
        if module_name in self.config.no_docs_modules:
            return None
        base_url = "https://docs.blender.org/api/current/"

        # Special case: idprop root page doesn't exist, redirect to types
        # see also: https://projects.blender.org/blender/blender/issues/152607
        if module_name == "idprop":
            return f"{base_url}idprop.types.html"
        return f"{base_url}{module_name}.html"

    def make_doc_block(self, module_name: str, indent: str = "    ") -> str:
        url = self.get_api_docs_link(module_name)
        if not url: return ""
        return f'{indent}"""\n{indent}Online Documentation:\n{indent}{url}\n{indent}"""'

    def map_rna_type(self, prop) -> str:
        try:
            t = prop.type
            if t == 'STRING': return "str"
            if t == 'BOOLEAN': return "bool"
            if t == 'INT': return "int"
            if t == 'FLOAT': return "float"
            if t == 'ENUM': return "str"
            if t == 'POINTER':
                if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                    return f"'{prop.fixed_type.identifier}'"
                return "Any"
            if t == 'COLLECTION':
                if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                    return f"bpy_prop_collection['{prop.fixed_type.identifier}']"
                return "bpy_prop_collection[Any]"
            return "Any"
        except Exception:
            return "Any"

    def get_member_signature(self, obj) -> str:
        try:
            sig = inspect.signature(obj)
            new_sig = sig.replace(return_annotation=inspect.Signature.empty)
            return str(new_sig)
        except Exception:
            return "(*args, **kwargs)"

    def generate_module_recursive(self, module_name: str, base_output_dir: str) -> bool:
        # exceptional handling for bpy.msgbus
        if module_name == "bpy.msgbus":
            # msgbus tends to fail with importlib, so force it to succeed by writing out a manual definition
            print(f"Generating static stub for: {module_name} (Fallback)")
            parts = module_name.split(".")
            mod_dir = os.path.join(base_output_dir, *parts)
            content = list(self.config.common_headers)
            content.extend([
                "import typing",
                "from typing import Any, Callable",
                "",
                "def subscribe_rna(key: Any, owner: Any, args: Any, notify: Callable[[tuple], None], options: set[str] = None) -> None: ...",
                "def publish_rna(key: Any) -> None: ...",
                "def clear_by_owner(owner: Any) -> None: ...",
            ])
            self.write_file(mod_dir, "__init__.pyi", content)
            return True
        try:
            mod = importlib.import_module(module_name)
        except ImportError:
            print(f"Skipping {module_name} (ImportError)")
            return False

        print(f"Generating stub for: {module_name}")
        parts = module_name.split(".")
        mod_dir = os.path.join(base_output_dir, *parts)

        content = list(self.config.common_headers)
        content.extend(["", "import sys", "import typing",
                        "from typing import Any, Union, Callable, Iterator", ""])

        module_doc = self.make_doc_block(module_name, indent="")
        if module_doc:
            content.insert(0, module_doc)
            content.insert(1, "")

        for name, obj in inspect.getmembers(mod):
            if name.startswith("_") or inspect.ismodule(obj): continue
            if inspect.isclass(obj):
                content.append(f"class {name}:")
                orig_doc = getattr(obj, "__doc__", None)
                if isinstance(orig_doc, str) and orig_doc.strip():
                    content.append(self.format_docstring(orig_doc))
                link_doc = self.make_doc_block(module_name)
                if link_doc: content.append(link_doc)

                has_member = False
                for mem_name, mem_obj in inspect.getmembers(obj):
                    if mem_name.startswith("_") and mem_name != "__init__": continue
                    if inspect.isroutine(mem_obj):
                        sig = self.get_member_signature(mem_obj)
                        content.append(f"    def {mem_name}{sig} -> Any: ...")
                        has_member = True
                    elif inspect.isdatadescriptor(mem_obj):
                        content.append(f"    {mem_name}: Any")
                        has_member = True
                if not has_member: content.append("    pass")
                content.append("")
            elif inspect.isroutine(obj):
                sig = self.get_member_signature(obj)
                content.append(f"def {name}{sig} -> Any:")
                link_doc = self.make_doc_block(module_name)
                if link_doc: content.append(link_doc)
                content.extend(["    ...", ""])
            elif isinstance(obj, (int, float, str, bool)):
                val = f"'{obj}'" if isinstance(obj, str) else str(obj)
                content.append(f"{name} = {val}")
            else:
                content.append(f"{name}: Any")

        submodules = set()
        if hasattr(mod, "__path__"):
            for _, sub_name, _ in pkgutil.iter_modules(mod.__path__):
                submodules.add(sub_name)
        for _, obj in inspect.getmembers(mod):
            if inspect.ismodule(obj) and obj.__name__.startswith(module_name + "."):
                submodules.add(obj.__name__.split(".")[-1])

        # Explicitly ensure gpu submodules are present.
        # GPU modules are C-based and often fail dynamic inspection (lazy loading),
        # so we hardcode the known submodule list to ensure stubs are generated.
        if module_name == "gpu":
            submodules.update(self.config.gpu_submodules)
            # currently not open
            if "compute" in submodules: submodules.remove("compute")
        elif module_name == "bpy.app":
            submodules.update(self.config.app_submodules)

        prefix = module_name + "."
        for force_mod in self.config.force_modules:
            if force_mod.startswith(prefix):
                submodules.add(force_mod[len(prefix):].split(".")[0])

        for sub_name in sorted(submodules):
            full_sub = f"{module_name}.{sub_name}"
            if self.generate_module_recursive(full_sub, base_output_dir):
                content.append(f"from . import {sub_name} as {sub_name}")
                link = self.get_api_docs_link(full_sub)
                if link: content.append(f"# Documentation: {link}")

        self.write_file(mod_dir, "__init__.pyi", content)
        return True

    def generate_bpy_types(self):
        print(f"Generating types to: {self.config.bpy_types_dir}")
        prop_col_content = list(self.config.common_headers)
        prop_col_content.extend([
            "import typing",
            "from typing import Any, Union, Sequence, TypeVar, Generic, Optional, Callable, Iterator",
            "", "T = TypeVar('T')", "",
            "class bpy_prop_collection(Sequence[T], Generic[T]):",
            "    def values(self) -> list[T]: ...",
            "    def items(self) -> list[tuple[str, T]]: ...",
            "    def get(self, key: Union[str, Any], default: T = None) -> Optional[T]: ...",
            "    def __getitem__(self, key: Union[str, int]) -> T: ...",
            "    def __iter__(self) -> Iterator[T]: ...",
            "    def __len__(self) -> int: ...",
            "    def __contains__(self, key: Union[str, Any]) -> bool: ...",
            "", "    # Generic fallbacks (Injected)",
            "    def new(self, name: str = '', *args, **kwargs) -> T:",
            "        \"\"\"\n        Create a new item in this collection.\n        \n        **⚠️ Warning (Stub)**:\n        This is a generic fallback method provided by the IDE plugin.\n        Not all collections support creating new items (e.g., read-only collections).\n        Please verify if this specific collection supports `new()` in the Blender API docs.\n        \"\"\"\n        ...",
            "",
            "    def remove(self, value: T, do_unlink: bool = True, do_id_user: bool = True, do_ui_user: bool = True) -> None:",
            "        \"\"\"\n        Remove an item from this collection.\n        \n        **⚠️ Warning (Stub)**:\n        This is a generic fallback method provided by the IDE plugin.\n        Read-only collections do not support removal.\n        \"\"\"\n        ...",
            "", "    def clear(self) -> None:",
            "        \"\"\"\n        Clear all items from this collection.\n        \n        **⚠️ Warning (Stub)**:\n        This is a generic fallback method. Most Blender collections do not support `clear()`.\n        \"\"\"\n        ...",
            "",
            "    def load(self, filepath: str, link: bool = False, relative: bool = False, assets: bool = False) -> Any:",
            "        \"\"\"\n        Load data from an external blend file (Context Manager).\n        \n        **Note**:\n        This method is typically available on `bpy.data.libraries`, `bpy.data.images`, etc.\n        \"\"\"\n        ...",
            "", "    # For collection.objects.link/unlink",
            "    def link(self, item: T) -> None:",
            "        \"\"\"\n        Add a data-block to this collection (e.g., Objects, Collections).\n        \n        **⚠️ Warning (Stub)**:\n        Valid mainly for `bpy.data.objects` or `collection.children`.\n        \"\"\"\n        ...",
            "", "    def unlink(self, item: T) -> None:",
            "        \"\"\"\n        Remove a data-block from this collection.\n        \n        **⚠️ Warning (Stub)**:\n        Valid mainly for `bpy.data.objects` or `collection.children`.\n        \"\"\"\n        ...",
        ])
        self.write_file(self.config.bpy_types_dir, "bpy_prop_collection.pyi", prop_col_content)

        classes_to_export = ["bpy_prop_collection"]
        for name in dir(bpy.types):
            if name == "bpy_prop_collection": continue
            cls = getattr(bpy.types, name)
            if not inspect.isclass(cls): continue

            file_content = list(self.config.common_headers)
            file_content.extend([
                "import sys", "import typing",
                "from typing import Any, Optional, Union, Sequence, Callable, Iterator",
                "from .bpy_prop_collection import bpy_prop_collection", ""
            ])

            bases = list(dict.fromkeys([b.__name__ for b in cls.__bases__ if b is not object]))
            dependencies = set()
            if hasattr(cls, "bl_rna"):
                for prop in cls.bl_rna.properties:
                    if prop.identifier != "rna_type" and prop.type in ('POINTER', 'COLLECTION'):
                        if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                            dep = prop.fixed_type.identifier
                            if dep != name and hasattr(bpy.types, dep): dependencies.add(dep)

            for base in bases: file_content.append(f"from .{base} import {base}")
            for dep in sorted(dependencies - set(bases)): file_content.append(f"from .{dep} import {dep}")

            base_str = f"({', '.join(bases)})" if bases else ""
            file_content.append(f"class {name}{base_str}:")
            doc = getattr(cls, "__doc__", None)
            if isinstance(doc, str): file_content.append(self.format_docstring(doc))

            props_written = False
            if hasattr(cls, "bl_rna"):
                for prop in cls.bl_rna.properties:
                    if prop.identifier != "rna_type" and not keyword.iskeyword(prop.identifier):
                        file_content.append(f"    {prop.identifier}: {self.map_rna_type(prop)}")
                        props_written = True
                for func in cls.bl_rna.functions:
                    if not keyword.iskeyword(func.identifier):
                        file_content.append(f"    def {func.identifier}(self, *args, **kwargs) -> Any: ...")
                        props_written = True

            if name in self.config.manual_injections:
                file_content.append("    # --- Injected Methods ---")
                file_content.extend(self.config.manual_injections[name])
                props_written = True

            if not props_written: file_content.append("    pass")
            self.write_file(self.config.bpy_types_dir, f"{name}.pyi", file_content)
            classes_to_export.append(name)

        init_content = list(self.config.common_headers)
        init_content.extend([f"from .{c} import {c} as {c}" for c in classes_to_export])
        self.write_file(self.config.bpy_types_dir, "__init__.pyi", init_content)

    def generate_submodules(self):
        print("Generating bpy submodules...")
        for mod in ["bpy.app", "bpy.props", "bpy.utils", "bpy.path", "bpy.msgbus"]:
            self.generate_module_recursive(mod, self.config.output_dir)
        self.write_file(os.path.join(self.config.bpy_dir, "ops"), "__init__.pyi",
                        ["from typing import Any", "def __getattr__(name) -> Any: ..."])

    def generate_bpy_root(self):
        print("Generating bpy/__init__.pyi")
        content = list(self.config.common_headers)
        content.extend([
            "from . import types as types", "from . import app as app", "from . import props as props",
            "from . import ops as ops", "from . import utils as utils", "from . import path as path",
            "from . import msgbus as msgbus", "", "data: types.BlendData", "context: types.Context",
        ])
        self.write_file(self.config.bpy_dir, "__init__.pyi", content)

    def run(self):
        self.generate_bpy_types()
        self.generate_submodules()
        self.generate_bpy_root()
        for mod in self.config.extra_modules:
            self.generate_module_recursive(mod, self.config.output_dir)
        print("All stubs generated successfully.")


def main():
    args = sys.argv
    if "--" in args:
        args = args[args.index("--") + 1:]

    output_dir = None
    if "--output" in args:
        try:
            output_dir = args[args.index("--output") + 1]
        except (ValueError, IndexError):
            pass
    if not output_dir:
        output_dir = os.path.join(os.getcwd(), "typings")

    config = GeneratorConfig(output_dir=output_dir)
    generator = StubGenerator(config)
    try:
        generator.run()
    except Exception:
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
