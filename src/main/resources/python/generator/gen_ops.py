import bpy
import os
from typing import Any
from .context import StubContext
from .writer import StubWriter


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
