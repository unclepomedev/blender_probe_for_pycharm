import inspect
import importlib
import pkgutil
import os
from typing import Any
from .context import StubContext
from .writer import StubWriter
from .template_loader import template_loader


class ModuleGenerator:
    def __init__(self, context: StubContext, writer: StubWriter):
        self.context = context
        self.writer = writer
        self.tpl_module_header = template_loader.get_template("core/module_header.pyi")
        self.tpl_class_def = template_loader.get_template("core/class_def.pyi")

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

        module_doc = self.writer.make_doc_block(module_name, indent="")
        content_header = self.tpl_module_header.substitute(
            common_headers="\n".join(self.context.config.common_headers),
            imports="",
            module_doc=module_doc,
        )
        content = content_header.splitlines()

        for name, obj in inspect.getmembers(mod):
            if name.startswith("_") or inspect.ismodule(obj):
                continue

            if inspect.isclass(obj):
                content.extend(self._process_class(module_name, name, obj))
            elif inspect.isroutine(obj):
                content.extend(self._process_function(module_name, name, obj))
            elif isinstance(obj, (int, float, str, bool)):
                val = f"'{obj}'" if isinstance(obj, str) else str(obj)
                content.append(f"{name} = {val}")
            else:
                content.append(f"{name}: Any")

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

    def _process_class(self, module_name: str, name: str, obj: Any) -> list[str]:
        doc = getattr(obj, "__doc__", None)
        doc_str = self.writer.format_doc_with_link(doc, module_name)

        body_lines = []
        has_member = False
        for mem_name, mem_obj in inspect.getmembers(obj):
            if mem_name.startswith("_") and mem_name != "__init__":
                continue

            if inspect.isroutine(mem_obj):
                sig = self.writer.get_member_signature(mem_obj)
                body_lines.append(f"    def {mem_name}{sig} -> Any: ...")
                has_member = True
            elif inspect.isdatadescriptor(mem_obj):
                body_lines.append(f"    {mem_name}: Any")
                has_member = True

        if name in self.context.config.math_types_whitelist:
            body_lines.extend(self.writer.get_math_methods(name))
            has_member = True

        if name in self.context.config.manual_injections:
            body_lines.append("    # --- Injected Methods ---")
            body_lines.extend(self.context.config.manual_injections[name])
            has_member = True

        if not has_member:
            body_lines.append("    pass")

        body_lines.append("")

        body_str = "\n".join(body_lines)

        class_content = self.tpl_class_def.substitute(
            name=name, bases="", doc=doc_str, body=body_str
        )

        return class_content.splitlines()

    def _process_function(self, module_name: str, name: str, obj: Any) -> list[str]:
        lines = []
        sig = self.writer.get_member_signature(obj)
        lines.append(f"def {name}{sig} -> Any:")

        doc = getattr(obj, "__doc__", None)
        doc_str = self.writer.format_doc_with_link(doc, module_name)
        if doc_str:
            lines.append(doc_str)

        lines.extend(["    ...", ""])
        return lines

    def _process_submodules(
        self, mod: Any, module_name: str, base_output_dir: str, content: list[str]
    ):
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
                submodules.add(force_mod[len(prefix) :].split(".")[0])

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
        content.extend(
            [
                "from . import types as types",
                "from . import app as app",
                "from . import props as props",
                "from . import ops as ops",
                "from . import utils as utils",
                "from . import path as path",
                "from . import msgbus as msgbus",
                "",
                "data: types.BlendData",
                "context: types.Context",
            ]
        )
        self.writer.write_file(self.context.config.bpy_dir, "__init__.pyi", content)
