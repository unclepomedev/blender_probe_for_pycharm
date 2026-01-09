import importlib
import inspect
import keyword
import os
import pkgutil
import sys

import bpy

output_dir = None
args = sys.argv
if "--" in args:
    args = args[args.index("--") + 1:]

if "--output" in args:
    try:
        idx = args.index("--output")
        output_dir = args[idx + 1]
    except (ValueError, IndexError):
        pass

if not output_dir:
    output_dir = os.path.join(os.getcwd(), "typings")

BPY_DIR = os.path.join(output_dir, "bpy")
BPY_TYPES_DIR = os.path.join(BPY_DIR, "types")

EXTRA_MODULES = [
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

FORCE_MODULES = [
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

GPU_SUBMODULES = [
    "shader", "types", "matrix", "state",
    "texture", "platform", "select", "capabilities"
]

APP_SUBMODULES = [
    "handlers", "translations", "timers", "icons"
]


def write_file(directory: str, filename: str, content: list[str]):
    if not os.path.exists(directory):
        os.makedirs(directory)
    filepath = os.path.join(directory, filename)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write("\n".join(content))


def format_docstring(doc_str: str, indent: str = "    ") -> str:
    if not doc_str: return ""
    doc_str = doc_str.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
    if doc_str.endswith('"'): doc_str += " "
    return f'{indent}"""{doc_str}"""'


def get_api_docs_link(module_name: str) -> str:
    """Generate official Blender Python API documentation link."""
    base_url = "https://docs.blender.org/api/current/"

    # Special case: idprop root page doesn't exist, redirect to types
    # see also: https://projects.blender.org/blender/blender/issues/152607
    if module_name == "idprop":
        return f"{base_url}idprop.types.html"

    return f"{base_url}{module_name}.html"


def make_doc_block(module_name: str, indent: str = "    ") -> str:
    """Create a standardized docstring block with the URL."""
    url = get_api_docs_link(module_name)
    return (
        f'{indent}"""\n'
        f'{indent}Online Documentation:\n'
        f'{indent}{url}\n'
        f'{indent}"""'
    )


def map_rna_type(prop) -> str:
    try:
        type_char = prop.type
        if type_char == 'STRING':
            return "str"
        elif type_char == 'BOOLEAN':
            return "bool"
        elif type_char == 'INT':
            return "int"
        elif type_char == 'FLOAT':
            return "float"
        elif type_char == 'ENUM':
            return "str"
        elif type_char == 'POINTER':
            if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                return f"'{prop.fixed_type.identifier}'"
            return "Any"
        elif type_char == 'COLLECTION':
            if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                return f"bpy_prop_collection['{prop.fixed_type.identifier}']"
            return "bpy_prop_collection[Any]"
        else:
            return "Any"
    except Exception:
        return "Any"


def get_member_signature(obj):
    """
    Get the signature of a function/method.
    Returns '(*args, **kwargs)' if inspection fails, ensuring valid syntax.
    """
    try:
        return str(inspect.signature(obj))
    except (ValueError, TypeError):
        pass
    except Exception:
        pass

    return "(*args, **kwargs)"


def generate_module_recursive(module_name: str, base_output_dir: str):
    try:
        mod = importlib.import_module(module_name)
    except ImportError:
        print(f"Skipping {module_name} (ImportError)")
        return

    print(f"Generating stub for: {module_name}")

    parts = module_name.split(".")
    mod_dir = os.path.join(base_output_dir, *parts)

    module_doc = make_doc_block(module_name, indent="")

    content = [
        module_doc,
        "",
        "import sys",
        "import typing",
        "from typing import Any, List, Tuple, Dict, Set, Union, Callable",
        "",
    ]

    is_package = hasattr(mod, "__path__")

    for name, obj in inspect.getmembers(mod):
        if name.startswith("_"): continue
        if inspect.ismodule(obj): continue

        if inspect.isclass(obj):
            content.append(f"class {name}:")

            doc_lines = []
            orig_doc = getattr(obj, "__doc__", None)
            if isinstance(orig_doc, str) and orig_doc.strip():
                doc_lines.append(format_docstring(orig_doc))

            doc_lines.append(make_doc_block(module_name))
            content.extend(doc_lines)

            has_member = False
            for mem_name, mem_obj in inspect.getmembers(obj):
                if mem_name.startswith("_") and mem_name != "__init__": continue
                if inspect.isroutine(mem_obj):
                    sig = get_member_signature(mem_obj)
                    content.append(f"    def {mem_name}{sig}: ...")
                    has_member = True
                elif inspect.isdatadescriptor(mem_obj):
                    content.append(f"    {mem_name}: Any")
                    has_member = True

            if not has_member:
                content.append("    pass")
            content.append("")

        elif inspect.isroutine(obj):
            sig = get_member_signature(obj)
            content.append(f"def {name}{sig} -> Any:")
            content.append(make_doc_block(module_name))
            content.append("    ...")
            content.append("")

        elif isinstance(obj, (int, float, str, bool)):
            if isinstance(obj, str):
                content.append(f"{name} = '{obj}'")
            else:
                content.append(f"{name} = {obj}")
        else:
            content.append(f"{name}: Any")

    submodules = set()

    if is_package:
        for _importer, sub_name, _is_pkg in pkgutil.iter_modules(mod.__path__):
            submodules.add(sub_name)

    for _name, obj in inspect.getmembers(mod):
        if inspect.ismodule(obj):
            if obj.__name__.startswith(module_name + "."):
                sub_name = obj.__name__.split(".")[-1]
                submodules.add(sub_name)

    # Explicitly ensure gpu submodules are present.
    # GPU modules are C-based and often fail dynamic inspection (lazy loading),
    # so we hardcode the known submodule list to ensure stubs are generated.
    if module_name == "gpu":
        submodules.update(GPU_SUBMODULES)
        # currently not open
        if "compute" in submodules:
            submodules.remove("compute")

    if module_name == "bpy.app":
        submodules.update(APP_SUBMODULES)

    prefix = module_name + "."
    for force_mod in FORCE_MODULES:
        if force_mod.startswith(prefix):
            # "gpu_extras.batch" -> prefix="gpu_extras." -> sub="batch"
            sub_name = force_mod[len(prefix):].split(".")[0]
            submodules.add(sub_name)

    for sub_name in sorted(submodules):
        full_sub_name = f"{module_name}.{sub_name}"

        content.append(f"from . import {sub_name} as {sub_name}")
        content.append(f"# Documentation: {get_api_docs_link(full_sub_name)}")

        generate_module_recursive(full_sub_name, base_output_dir)

    write_file(mod_dir, "__init__.pyi", content)


def generate_bpy_types():
    print(f"Generating types to: {BPY_TYPES_DIR}")

    prop_col_content = [
        "import typing",
        "from typing import Any, List, Tuple, Union, Sequence, TypeVar, Generic, Optional",
        "",
        "T = TypeVar('T')",
        "",
        "class bpy_prop_collection(Sequence[T], Generic[T]):",
        "    def values(self) -> List[T]: ...",
        "    def items(self) -> List[Tuple[str, T]]: ...",
        "    def get(self, key: str, default: T = None) -> Optional[T]: ...",
        "    def __getitem__(self, key: Union[str, int]) -> T: ...",
        "    def __iter__(self) -> typing.Iterator[T]: ...",
        "    def __len__(self) -> int: ...",
    ]
    write_file(BPY_TYPES_DIR, "bpy_prop_collection.pyi", prop_col_content)

    classes_to_export = ["bpy_prop_collection"]

    for name in dir(bpy.types):
        if name == "bpy_prop_collection": continue
        cls = getattr(bpy.types, name)
        if not inspect.isclass(cls): continue

        file_content = [
            "import sys",
            "import typing",
            "from typing import Any, List, Set, Dict, Tuple, Optional, Union, Sequence",
            "from .bpy_prop_collection import bpy_prop_collection",
            "",
        ]

        bases = [b.__name__ for b in cls.__bases__ if b is not object]
        # Preserve order while deduping
        bases = list(dict.fromkeys(bases))

        dependencies = set()
        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type": continue
                if prop.type in ('POINTER', 'COLLECTION'):
                    if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                        dep = prop.fixed_type.identifier
                        if dep != name and hasattr(bpy.types, dep):
                            dependencies.add(dep)

        for base in bases:
            file_content.append(f"from .{base} import {base}")

        for dep in sorted(list(dependencies)):
            if dep not in bases:
                file_content.append(f"from .{dep} import {dep}")

        bases_str = f"({', '.join(bases)})" if bases else ""
        file_content.append(f"class {name}{bases_str}:")

        doc = getattr(cls, "__doc__", None)
        if isinstance(doc, str):
            file_content.append(format_docstring(doc))

        props_written = False
        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type": continue
                if keyword.iskeyword(prop.identifier): continue

                py_type = map_rna_type(prop)
                file_content.append(f"    {prop.identifier}: {py_type}")
                props_written = True

            for func in cls.bl_rna.functions:
                if keyword.iskeyword(func.identifier): continue
                file_content.append(f"    def {func.identifier}(self, *args, **kwargs): ...")
                props_written = True

        if not props_written:
            file_content.append("    pass")

        write_file(BPY_TYPES_DIR, f"{name}.pyi", file_content)
        classes_to_export.append(name)

    init_content = [f"from .{cls} import {cls} as {cls}" for cls in classes_to_export]
    write_file(BPY_TYPES_DIR, "__init__.pyi", init_content)


def generate_submodules():
    print("Generating bpy submodules...")

    target_modules = ["bpy.app", "bpy.props", "bpy.utils", "bpy.path", "bpy.msgbus"]

    for mod_name in target_modules:
        generate_module_recursive(mod_name, output_dir)

    # Manual generation for bpy.ops to avoid overhead
    ops_dir = os.path.join(BPY_DIR, "ops")
    write_file(ops_dir, "__init__.pyi", ["from typing import Any", "def __getattr__(name) -> Any: ..."])


def generate_bpy_root():
    print("Generating bpy/__init__.pyi")
    content = [
        "from . import types as types",
        "from . import app as app",
        "from . import props as props",
        "from . import ops as ops",
        "from . import utils as utils",
        "from . import path as path",
        "from . import msgbus as msgbus",
        "", "data: types.BlendData", "context: types.Context",
    ]
    write_file(BPY_DIR, "__init__.pyi", content)


def main():
    try:
        generate_bpy_types()
        generate_submodules()
        generate_bpy_root()

        for mod in EXTRA_MODULES:
            generate_module_recursive(mod, output_dir)

        print("All stubs generated successfully.")
    except Exception:
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
