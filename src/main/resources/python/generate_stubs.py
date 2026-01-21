import importlib
import inspect
import keyword
import os
import pkgutil
import sys
import traceback
import textwrap
from dataclasses import dataclass, field
from pathlib import Path

import bpy

class TemplateLoader:
    def __init__(self):
        self.base_path = Path(__file__).parent / "templates"

    def read_lines(self, relative_path: str) -> list[str]:
        target = self.base_path / relative_path
        if not target.exists():
            return []
        return target.read_text(encoding="utf-8").splitlines()

    def get_injection(self, class_name: str) -> list[str]:
        target = self.base_path / "injections" / f"{class_name}.pyi"
        if not target.exists():
            return []
        content = target.read_text(encoding="utf-8")
        return textwrap.indent(content, "    ").splitlines()


template_loader = TemplateLoader()


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
        "bpy_struct": template_loader.get_injection("bpy_struct"),
        "Context": template_loader.get_injection("Context"),
        # Object methods usually handled by C
        "Object": template_loader.get_injection("Object"),
        "Collection": template_loader.get_injection("Collection"),
        "ID": template_loader.get_injection("ID"),
        # SpaceView3D static handlers
        "SpaceView3D": template_loader.get_injection("SpaceView3D"),
        "KeyConfigurations": template_loader.get_injection("KeyConfigurations"),
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

    def sanitize_arg_name(self, name: str) -> str:
        if keyword.iskeyword(name):
            return f"{name}_"
        return name

    def write_file(self, directory: str, filename: str, content: list[str]):
        if not os.path.exists(directory):
            os.makedirs(directory)
        filepath = os.path.join(directory, filename)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write("\n".join(content))

    def format_docstring(self, doc_str: str, indent: str = "    ") -> str:
        if not doc_str:
            return ""
        doc_str = doc_str.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
        if doc_str.endswith('"'):
            doc_str += " "
        return f'{indent}"""{doc_str}"""'

    def get_api_docs_link(self, module_name: str) -> str | None:
        for no_doc_mod in self.config.no_docs_modules:
            if module_name == no_doc_mod or module_name.startswith(no_doc_mod + "."):
                return None
        base_url = "https://docs.blender.org/api/current/"

        # Special case: idprop root page doesn't exist, redirect to types
        # see also: https://projects.blender.org/blender/blender/issues/152607
        if module_name == "idprop":
            return f"{base_url}idprop.types.html"
        return f"{base_url}{module_name}.html"

    def make_doc_block(self, module_name: str, indent: str = "    ") -> str:
        url = self.get_api_docs_link(module_name)
        if not url:
            return ""
        return f'{indent}"""\n{indent}Online Documentation:\n{indent}{url}\n{indent}"""'

    def map_rna_type(self, prop) -> str:
        try:
            t = prop.type
            if t == 'STRING':
                return "str"
            if t == 'BOOLEAN':
                return "bool"
            if t == 'INT':
                return "int"
            if t == 'FLOAT':
                return "float"
            if t == 'ENUM':
                return "str"
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
            content.extend(template_loader.read_lines("modules/bpy.msgbus.pyi"))
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
            if name.startswith("_") or inspect.ismodule(obj):
                continue
            if inspect.isclass(obj):
                content.append(f"class {name}:")
                orig_doc = getattr(obj, "__doc__", None)
                if isinstance(orig_doc, str) and orig_doc.strip():
                    content.append(self.format_docstring(orig_doc))
                link_doc = self.make_doc_block(module_name)
                if link_doc:
                    content.append(link_doc)

                has_member = False
                for mem_name, mem_obj in inspect.getmembers(obj):
                    if mem_name.startswith("_") and mem_name != "__init__":
                        continue
                    if inspect.isroutine(mem_obj):
                        sig = self.get_member_signature(mem_obj)
                        content.append(f"    def {mem_name}{sig} -> Any: ...")
                        has_member = True
                    elif inspect.isdatadescriptor(mem_obj):
                        content.append(f"    {mem_name}: Any")
                        has_member = True
                if not has_member:
                    content.append("    pass")
                content.append("")
            elif inspect.isroutine(obj):
                sig = self.get_member_signature(obj)
                content.append(f"def {name}{sig} -> Any:")
                link_doc = self.make_doc_block(module_name)
                if link_doc:
                    content.append(link_doc)
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
            if "compute" in submodules:
                submodules.remove("compute")
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
                if link:
                    content.append(f"# Documentation: {link}")

        self.write_file(mod_dir, "__init__.pyi", content)
        return True

    def generate_bpy_types(self):
        print(f"Generating types to: {self.config.bpy_types_dir}")
        prop_col_content = list(self.config.common_headers)
        prop_col_content.extend(template_loader.read_lines("types/bpy_prop_collection.pyi"))
        self.write_file(self.config.bpy_types_dir, "bpy_prop_collection.pyi", prop_col_content)

        classes_to_export = ["bpy_prop_collection"]
        for name in dir(bpy.types):
            if name == "bpy_prop_collection":
                continue
            cls = getattr(bpy.types, name)
            if not inspect.isclass(cls):
                continue

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
                            if dep != name and hasattr(bpy.types, dep):
                                dependencies.add(dep)

            for base in bases:
                file_content.append(f"from .{base} import {base}")
            for dep in sorted(dependencies - set(bases)):
                file_content.append(f"from .{dep} import {dep}")

            base_str = f"({', '.join(bases)})" if bases else ""
            file_content.append(f"class {name}{base_str}:")
            doc = getattr(cls, "__doc__", None)
            if isinstance(doc, str):
                file_content.append(self.format_docstring(doc))

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

            if not props_written:
                file_content.append("    pass")
            self.write_file(self.config.bpy_types_dir, f"{name}.pyi", file_content)
            classes_to_export.append(name)

        init_content = list(self.config.common_headers)
        init_content.extend([f"from .{c} import {c} as {c}" for c in classes_to_export])
        self.write_file(self.config.bpy_types_dir, "__init__.pyi", init_content)

    def generate_submodules(self):
        print("Generating bpy submodules...")
        for mod in ["bpy.app", "bpy.props", "bpy.utils", "bpy.path", "bpy.msgbus"]:
            self.generate_module_recursive(mod, self.config.output_dir)
        self.generate_bpy_ops()

    def generate_bpy_ops(self):
        print(f"Generating full bpy.ops stubs to: {os.path.join(self.config.bpy_dir, 'ops')}")
        ops_dir = os.path.join(self.config.bpy_dir, "ops")
        if not os.path.exists(ops_dir):
            os.makedirs(ops_dir)

        categories = []
        for cat_name in dir(bpy.ops):
            if cat_name.startswith("__"):
                continue

            cat_obj = getattr(bpy.ops, cat_name)
            if not hasattr(cat_obj, "__name__"):
                continue

            categories.append(cat_name)
            content = list(self.config.common_headers)
            content.extend([
                "import typing",
                "import bpy",
                "from typing import Any, Optional, Union, Set, Dict",
                ""
            ])

            ops_in_cat = []
            for op_name in dir(cat_obj):
                if op_name.startswith("__"):
                    continue
                op_func = getattr(cat_obj, op_name)
                rna = getattr(op_func, "get_rna_type", lambda: None)()

                if rna:
                    args_sig = [
                        "override_context: Optional[Union[Dict, 'bpy.types.Context']] = None",
                        "execution_context: Optional[str] = None",
                        "undo: Optional[bool] = None"
                    ]

                    kw_args = []
                    for prop in rna.properties:
                        if prop.identifier == "rna_type":
                            continue
                        arg_name = self.sanitize_arg_name(prop.identifier)
                        arg_type = self.map_rna_type(prop)
                        kw_args.append(f"{arg_name}: {arg_type} = ...")

                    if kw_args:
                        args_sig.append("*")
                        args_sig.extend(kw_args)

                    sig_str = ", ".join(args_sig)
                    doc = self.format_docstring(rna.description) if rna.description else ""
                    content.append(f"def {op_name}({sig_str}) -> Set[str]:")
                    if doc:
                        content.append(doc)
                    content.append("    ...")
                    content.append("")
                    ops_in_cat.append(op_name)

            if ops_in_cat:
                self.write_file(ops_dir, f"{cat_name}.pyi", content)
            else:
                self.write_file(ops_dir, f"{cat_name}.pyi", content + ["pass"])

        init_content = list(self.config.common_headers)
        for cat in sorted(categories):
            init_content.append(f"from . import {cat} as {cat}")
        self.write_file(ops_dir, "__init__.pyi", init_content)

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
