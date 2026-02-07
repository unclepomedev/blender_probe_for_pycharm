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
from typing import Any, Iterator

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
        f"# Blender Probe Generated Stub for Blender {bpy.app.version_string}",
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
        "Object": template_loader.get_injection("Object"),
        "Collection": template_loader.get_injection("Collection"),
        "ID": template_loader.get_injection("ID"),
        "SpaceView3D": template_loader.get_injection("SpaceView3D"),
        "KeyConfigurations": template_loader.get_injection("KeyConfigurations"),
        "BMLoop": template_loader.get_injection("BMLoop"),
        "BMVert": template_loader.get_injection("BMVert"),
        "BMEdge": template_loader.get_injection("BMEdge"),
        "BMFace": template_loader.get_injection("BMFace"),
    })

    math_types_whitelist: set[str] = field(default_factory=lambda: {
        "Vector", "Matrix", "Quaternion", "Euler", "Color"
    })

    @property
    def bpy_dir(self) -> str:
        return os.path.join(self.output_dir, "bpy")

    @property
    def bpy_types_dir(self) -> str:
        return os.path.join(self.bpy_dir, "types")


class StubContext:
    def __init__(self, config: GeneratorConfig):
        self.config = config
        self.collection_mapping: dict[str, str] = {}

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

    def map_rna_type(self, prop) -> str:
        try:
            t = prop.type
            if t == 'STRING':
                return "str"
            if t == 'BOOLEAN':
                if getattr(prop, "is_array", False):
                    return "list[bool]"
                return "bool"
            if t == 'INT':
                if getattr(prop, "is_array", False):
                    return "list[int]"
                return "int"
            if t == 'FLOAT':
                if getattr(prop, "is_array", False):
                    return "list[float]"
                return "float"
            if t == 'ENUM':
                if getattr(prop, "is_enum_flag", False):
                    return "set[str]"
                return "str"
            if t == 'POINTER':
                if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                    return f"'{prop.fixed_type.identifier}'"
                return "Any"
            if t == 'COLLECTION':
                if hasattr(prop, "srna") and prop.srna:
                    srna_id = prop.srna.identifier
                    if hasattr(bpy.types, srna_id):
                        return f"'{srna_id}'"
                if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                    return f"bpy_prop_collection['{prop.fixed_type.identifier}']"
                return "bpy_prop_collection[Any]"
            return "Any"
        except Exception:
            return "Any"


class StubWriter:
    def __init__(self, context: StubContext):
        self.context = context

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

    def make_doc_block(self, module_name: str, indent: str = "    ") -> str:
        url = self.context.get_api_docs_link(module_name)
        if not url:
            return ""
        return f'{indent}"""\n{indent}Online Documentation:\n{indent}{url}\n{indent}"""'

    def get_member_signature(self, obj) -> str:
        try:
            sig = inspect.signature(obj)
            new_sig = sig.replace(return_annotation=inspect.Signature.empty)
            return str(new_sig)
        except Exception:
            return "(*args, **kwargs)"

    def get_math_methods(self, class_name: str) -> list[str]:
        methods = []
        ops = ['add', 'sub', 'mul', 'truediv', 'floordiv', 'mod', 'pow']
        for op in ops:
            methods.append(f"    def __{op}__(self, other: Any) -> Any: ...")
            methods.append(f"    def __r{op}__(self, other: Any) -> Any: ...")
            methods.append(f"    def __i{op}__(self, other: Any) -> Any: ...")

        for op in ['neg', 'pos', 'abs', 'invert']:
            methods.append(f"    def __{op}__(self) -> '{class_name}': ...")

        methods.append(f"    def __eq__(self, other: Any) -> bool: ...")
        methods.append(f"    def __ne__(self, other: Any) -> bool: ...")
        methods.append(f"    def __lt__(self, other: Any) -> bool: ...")
        methods.append(f"    def __le__(self, other: Any) -> bool: ...")
        methods.append(f"    def __gt__(self, other: Any) -> bool: ...")
        methods.append(f"    def __ge__(self, other: Any) -> bool: ...")

        methods.append(f"    def __len__(self) -> int: ...")
        methods.append(f"    def __getitem__(self, key: int) -> float: ...")
        methods.append(f"    def __setitem__(self, key: int, value: float): ...")
        methods.append(f"    def __iter__(self) -> Iterator[float]: ...")
        return methods


class StubAnalyzer:
    def __init__(self, context: StubContext):
        self.context = context

    def analyze_collections(self):
        print("Analyzing collection relationships...")
        for name in dir(bpy.types):
            cls = getattr(bpy.types, name)
            if not inspect.isclass(cls) or not hasattr(cls, "bl_rna"):
                continue

            for prop in cls.bl_rna.properties:
                if prop.type == 'COLLECTION':
                    if hasattr(prop, "srna") and prop.srna and prop.fixed_type:
                        container_id = prop.srna.identifier
                        element_id = getattr(prop.fixed_type, "identifier", None)
                        if container_id and element_id and hasattr(bpy.types, container_id):
                            self.context.collection_mapping[container_id] = element_id


class BpyTypesGenerator:
    def __init__(self, context: StubContext, writer: StubWriter):
        self.context = context
        self.writer = writer

    def generate(self):
        print(f"Generating types to: {self.context.config.bpy_types_dir}")
        self._generate_prop_collection_stub()

        classes_to_export = ["bpy_prop_collection"]
        for name in dir(bpy.types):
            if name == "bpy_prop_collection":
                continue
            cls = getattr(bpy.types, name)
            if not inspect.isclass(cls):
                continue

            self._generate_single_type(name, cls)
            classes_to_export.append(name)

        self._generate_init_file(classes_to_export)

    def _generate_prop_collection_stub(self):
        content = list(self.context.config.common_headers)
        content.extend(template_loader.read_lines("types/bpy_prop_collection.pyi"))
        self.writer.write_file(self.context.config.bpy_types_dir, "bpy_prop_collection.pyi", content)

    def _generate_init_file(self, classes: list[str]):
        content = list(self.context.config.common_headers)
        content.extend([f"from .{c} import {c} as {c}" for c in classes])
        self.writer.write_file(self.context.config.bpy_types_dir, "__init__.pyi", content)

    def _generate_single_type(self, name: str, cls: type):
        """Generates the .pyi file for a single bpy.types class."""
        content = list(self.context.config.common_headers)
        content.extend([
            "import sys", "import typing",
            "from typing import Any, Optional, Union, Sequence, Callable, Iterator",
            "from .bpy_prop_collection import bpy_prop_collection", ""
        ])

        content.extend(self._build_imports(name, cls))

        bases = list(dict.fromkeys([b.__name__ for b in cls.__bases__ if b is not object]))
        base_str = f"({', '.join(bases)})" if bases else ""
        content.append(f"class {name}{base_str}:")

        doc = getattr(cls, "__doc__", None)
        if isinstance(doc, str):
            content.append(self.writer.format_docstring(doc))

        body_lines = self._build_class_body(name, cls)
        if not body_lines:
            content.append("    pass")
        else:
            content.extend(body_lines)

        self.writer.write_file(self.context.config.bpy_types_dir, f"{name}.pyi", content)

    def _build_imports(self, name: str, cls: type) -> list[str]:
        imports = []
        bases = list(dict.fromkeys([b.__name__ for b in cls.__bases__ if b is not object]))
        dependencies = set()

        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type": continue

                if prop.type in ('POINTER', 'COLLECTION'):
                    if prop.fixed_type and hasattr(prop.fixed_type, 'identifier'):
                        dep = prop.fixed_type.identifier
                        if dep != name and hasattr(bpy.types, dep):
                            dependencies.add(dep)

                    if prop.type == 'COLLECTION' and hasattr(prop, "srna") and prop.srna:
                        srna_id = prop.srna.identifier
                        if srna_id != name and hasattr(bpy.types, srna_id):
                            dependencies.add(srna_id)

        if name in self.context.collection_mapping:
            element_type = self.context.collection_mapping[name]
            if element_type != name and hasattr(bpy.types, element_type):
                dependencies.add(element_type)

        for base in bases:
            imports.append(f"from .{base} import {base}")
        for dep in sorted(dependencies - set(bases)):
            imports.append(f"from .{dep} import {dep}")

        return imports

    def _build_class_body(self, name: str, cls: type) -> list[str]:
        lines = []

        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier != "rna_type" and not keyword.iskeyword(prop.identifier):
                    lines.append(f"    {prop.identifier}: {self.context.map_rna_type(prop)}")

            for func in cls.bl_rna.functions:
                if not keyword.iskeyword(func.identifier):
                    lines.append(f"    def {func.identifier}(self, *args, **kwargs) -> Any: ...")

        if name in self.context.collection_mapping:
            lines.extend(self._get_iterable_methods(name))

        if name in self.context.config.math_types_whitelist:
            lines.extend(self.writer.get_math_methods(name))

        if name in self.context.config.manual_injections:
            lines.append("    # --- Injected Methods ---")
            lines.extend(self.context.config.manual_injections[name])

        return lines

    def _get_iterable_methods(self, name: str) -> list[str]:
        element_type = self.context.collection_mapping[name]
        has_manual_iter = False
        if name in self.context.config.manual_injections:
            for line in self.context.config.manual_injections[name]:
                if "__iter__" in line:
                    has_manual_iter = True
                    break

        if has_manual_iter:
            return []

        return [
            f"    def __contains__(self, key: Union[str, int]) -> bool: ...",
            f"    def __iter__(self) -> Iterator['{element_type}']: ...",
            f"    def __getitem__(self, key: Union[str, int]) -> '{element_type}': ...",
            f"    def __len__(self) -> int: ..."
        ]


class BpyOpsGenerator:
    def __init__(self, context: StubContext, writer: StubWriter):
        self.context = context
        self.writer = writer

    def generate(self):
        print(f"Generating full bpy.ops stubs to: {os.path.join(self.context.config.bpy_dir, 'ops')}")
        ops_dir = os.path.join(self.context.config.bpy_dir, "ops")
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
            self._generate_category_file(ops_dir, cat_name, cat_obj)

        self._generate_init_file(ops_dir, categories)

    def _generate_category_file(self, output_dir: str, cat_name: str, cat_obj: Any):
        content = list(self.context.config.common_headers)
        content.extend([
            "import typing", "import bpy",
            "from typing import Any, Optional, Union", ""
        ])

        ops_in_cat = []
        for op_name in dir(cat_obj):
            if op_name.startswith("__"):
                continue

            op_lines = self._generate_op_function(cat_obj, op_name)
            if op_lines:
                content.extend(op_lines)
                ops_in_cat.append(op_name)

        if not ops_in_cat:
            content.append("pass")

        self.writer.write_file(output_dir, f"{cat_name}.pyi", content)

    def _generate_op_function(self, cat_obj: Any, op_name: str) -> list[str]:
        op_func = getattr(cat_obj, op_name)
        rna = getattr(op_func, "get_rna_type", lambda: None)()
        if not rna:
            return []

        args_sig = [
            "override_context: Optional[Union[dict, 'bpy.types.Context']] = None",
            "execution_context: Optional[str] = None",
            "undo: Optional[bool] = None"
        ]

        kw_args = []
        for prop in rna.properties:
            if prop.identifier == "rna_type": continue
            arg_name = self.writer.sanitize_arg_name(prop.identifier)
            arg_type = self.context.map_rna_type(prop)
            kw_args.append(f"{arg_name}: {arg_type} = ...")

        if kw_args:
            args_sig.append("*")
            args_sig.extend(kw_args)

        sig_str = ", ".join(args_sig)
        lines = []
        doc = self.writer.format_docstring(rna.description) if rna.description else ""
        lines.append(f"def {op_name}({sig_str}) -> set[str]:")
        if doc:
            lines.append(doc)
        lines.append("    ...")
        lines.append("")
        return lines

    def _generate_init_file(self, output_dir: str, categories: list[str]):
        content = list(self.context.config.common_headers)
        for cat in sorted(categories):
            content.append(f"from . import {cat} as {cat}")
        self.writer.write_file(output_dir, "__init__.pyi", content)


class ModuleGenerator:
    def __init__(self, context: StubContext, writer: StubWriter):
        self.context = context
        self.writer = writer

    def generate_recursive(self, module_name: str, base_output_dir: str) -> bool:
        # exceptional handling for bpy.msgbus
        if module_name == "bpy.msgbus":
            # msgbus tends to fail with importlib, so force it to succeed by writing out a manual definition
            return self._generate_fallback_msgbus(base_output_dir, module_name)

        try:
            mod = importlib.import_module(module_name)
        except ImportError:
            print(f"Skipping {module_name} (ImportError)")
            return False

        print(f"Generating stub for: {module_name}")
        parts = module_name.split(".")
        mod_dir = os.path.join(base_output_dir, *parts)

        # Build content
        content = self._build_module_header(module_name)

        # Iterate members
        for name, obj in inspect.getmembers(mod):
            if name.startswith("_") or inspect.ismodule(obj): continue

            if inspect.isclass(obj):
                content.extend(self._process_class(module_name, name, obj))
            elif inspect.isroutine(obj):
                content.extend(self._process_function(module_name, name, obj))
            elif isinstance(obj, (int, float, str, bool)):
                val = f"'{obj}'" if isinstance(obj, str) else str(obj)
                content.append(f"{name} = {val}")
            else:
                content.append(f"{name}: Any")

        # Handle Submodules
        self._process_submodules(mod, module_name, base_output_dir, content)

        self.writer.write_file(mod_dir, "__init__.pyi", content)
        return True

    def _generate_fallback_msgbus(self, base_output_dir: str, module_name: str) -> bool:
        print(f"Generating static stub for: {module_name} (Fallback)")
        parts = module_name.split(".")
        mod_dir = os.path.join(base_output_dir, *parts)
        content = list(self.context.config.common_headers)
        content.extend(template_loader.read_lines("modules/bpy.msgbus.pyi"))
        self.writer.write_file(mod_dir, "__init__.pyi", content)
        return True

    def _build_module_header(self, module_name: str) -> list[str]:
        content = list(self.context.config.common_headers)
        content.extend(["", "import sys", "import typing",
                        "from typing import Any, Union, Callable, Iterator", ""])
        module_doc = self.writer.make_doc_block(module_name, indent="")
        if module_doc:
            content.insert(0, module_doc)
            content.insert(1, "")
        return content

    def _process_class(self, module_name: str, name: str, obj: Any) -> list[str]:
        lines = [f"class {name}:"]

        orig_doc = getattr(obj, "__doc__", None)
        if isinstance(orig_doc, str) and orig_doc.strip():
            lines.append(self.writer.format_docstring(orig_doc))

        link_doc = self.writer.make_doc_block(module_name)
        if link_doc:
            lines.append(link_doc)

        has_member = False
        for mem_name, mem_obj in inspect.getmembers(obj):
            if mem_name.startswith("_") and mem_name != "__init__": continue

            if inspect.isroutine(mem_obj):
                sig = self.writer.get_member_signature(mem_obj)
                lines.append(f"    def {mem_name}{sig} -> Any: ...")
                has_member = True
            elif inspect.isdatadescriptor(mem_obj):
                lines.append(f"    {mem_name}: Any")
                has_member = True

        if name in self.context.config.math_types_whitelist:
            lines.extend(self.writer.get_math_methods(name))
            has_member = True

        if name in self.context.config.manual_injections:
            lines.append("    # --- Injected Methods ---")
            lines.extend(self.context.config.manual_injections[name])
            has_member = True

        if not has_member:
            lines.append("    pass")
        lines.append("")
        return lines

    def _process_function(self, module_name: str, name: str, obj: Any) -> list[str]:
        lines = []
        sig = self.writer.get_member_signature(obj)
        lines.append(f"def {name}{sig} -> Any:")

        link_doc = self.writer.make_doc_block(module_name)
        if link_doc:
            lines.append(link_doc)

        lines.extend(["    ...", ""])
        return lines

    def _process_submodules(self, mod: Any, module_name: str, base_output_dir: str, content: list[str]):
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
            submodules.update(self.context.config.gpu_submodules)
            # currently not open
            if "compute" in submodules:
                submodules.remove("compute")
        elif module_name == "bpy.app":
            submodules.update(self.context.config.app_submodules)

        prefix = module_name + "."
        for force_mod in self.context.config.force_modules:
            if force_mod.startswith(prefix):
                submodules.add(force_mod[len(prefix):].split(".")[0])

        for sub_name in sorted(submodules):
            full_sub = f"{module_name}.{sub_name}"
            if self.generate_recursive(full_sub, base_output_dir):
                content.append(f"from . import {sub_name} as {sub_name}")
                link = self.context.get_api_docs_link(full_sub)
                if link:
                    content.append(f"# Documentation: {link}")

    def generate_bpy_root(self):
        print("Generating bpy/__init__.pyi")
        content = list(self.context.config.common_headers)
        content.extend([
            "from . import types as types", "from . import app as app", "from . import props as props",
            "from . import ops as ops", "from . import utils as utils", "from . import path as path",
            "from . import msgbus as msgbus", "", "data: types.BlendData", "context: types.Context",
        ])
        self.writer.write_file(self.context.config.bpy_dir, "__init__.pyi", content)


class StubGenerator:
    def __init__(self, config: GeneratorConfig):
        self.config = config
        self.context = StubContext(config)
        self.writer = StubWriter(self.context)
        self.analyzer = StubAnalyzer(self.context)
        self.bpy_types_generator = BpyTypesGenerator(self.context, self.writer)
        self.bpy_ops_generator = BpyOpsGenerator(self.context, self.writer)
        self.module_generator = ModuleGenerator(self.context, self.writer)

    def run(self):
        self.analyzer.analyze_collections()
        self.bpy_types_generator.generate()

        print("Generating bpy submodules...")
        for mod in ["bpy.app", "bpy.props", "bpy.utils", "bpy.path", "bpy.msgbus"]:
            self.module_generator.generate_recursive(mod, self.config.output_dir)

        self.bpy_ops_generator.generate()
        self.module_generator.generate_bpy_root()
        for mod in self.config.extra_modules:
            self.module_generator.generate_recursive(mod, self.config.output_dir)
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
