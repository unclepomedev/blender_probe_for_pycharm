import inspect
import keyword

import bpy

from .context import StubContext
from .template_loader import template_loader
from .writer import StubWriter


class BpyTypesGenerator:
    def __init__(self, context: StubContext, writer: StubWriter):
        self.context = context
        self.writer = writer
        self.tpl_module_header = template_loader.get_template("core/module_header.pyi")
        self.tpl_class_def = template_loader.get_template("core/class_def.pyi")
        self.tpl_property = template_loader.get_template("core/property.pyi")
        self.tpl_property_readonly = template_loader.get_template(
            "core/property_readonly.pyi"
        )

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
        self.writer.write_file(
            self.context.config.bpy_types_dir, "bpy_prop_collection.pyi", content
        )

    def _generate_init_file(self, classes: list[str]):
        content = list(self.context.config.common_headers)
        content.extend([f"from .{c} import {c} as {c}" for c in classes])
        self.writer.write_file(
            self.context.config.bpy_types_dir, "__init__.pyi", content
        )

    def _generate_single_type(self, name: str, cls: type):
        imports = self._build_imports(name, cls)
        import_str = "\n".join(imports)

        doc = getattr(cls, "__doc__", None)
        doc_str = self.writer.format_docstring(doc) if isinstance(doc, str) else ""

        body_lines = self._build_class_body(name, cls)
        if not body_lines:
            body_str = "    pass"
        else:
            body_str = "\n".join(body_lines)

        bases = list(
            dict.fromkeys([b.__name__ for b in cls.__bases__ if b is not object])
        )
        base_str = f"({', '.join(bases)})" if bases else ""

        module_doc = self.writer.make_doc_block(f"bpy.types.{name}", indent="")

        module_content = self.tpl_module_header.substitute(
            common_headers="\n".join(self.context.config.common_headers),
            imports=import_str,
            module_doc=module_doc,
        )

        class_content = self.tpl_class_def.substitute(
            name=name, bases=base_str, doc=doc_str, body=body_str
        )

        # Remove empty lines if doc is empty to keep it clean (optional but good)
        if not doc_str:
            class_content = class_content.replace("class", "class", 1)  # dummy

        full_content = module_content + "\n" + class_content
        self.writer.write_file(
            self.context.config.bpy_types_dir, f"{name}.pyi", full_content.splitlines()
        )

    def _build_imports(self, name: str, cls: type) -> list[str]:
        imports = []
        bases = list(
            dict.fromkeys([b.__name__ for b in cls.__bases__ if b is not object])
        )
        dependencies = set()

        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type":
                    continue

                if prop.type == "COLLECTION":
                    dependencies.add("bpy_prop_collection")

                if prop.type in ("POINTER", "COLLECTION"):
                    if prop.fixed_type and hasattr(prop.fixed_type, "identifier"):
                        dep = prop.fixed_type.identifier
                        if dep != name and hasattr(bpy.types, dep):
                            dependencies.add(dep)

                    if (
                        prop.type == "COLLECTION"
                        and hasattr(prop, "srna")
                        and prop.srna
                    ):
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
                if prop.identifier == "rna_type" or keyword.iskeyword(prop.identifier):
                    continue

                type_hint = self.context.get_smart_type_hint(prop)
                description = getattr(prop, "description", None)

                if getattr(prop, "is_readonly", False):
                    doc_fmt = (
                        self.writer.format_docstring(description, indent="        ")
                        if description
                        else ""
                    )
                    prop_str = self.tpl_property_readonly.substitute(
                        name=prop.identifier, type_hint=type_hint, doc=doc_fmt
                    )
                    lines.append(prop_str)
                else:
                    doc_fmt = (
                        self.writer.format_docstring(description, indent="    ")
                        if description
                        else ""
                    )
                    prop_str = self.tpl_property.substitute(
                        name=prop.identifier, type_hint=type_hint, doc=doc_fmt
                    )
                    lines.append(prop_str)

            for func in cls.bl_rna.functions:
                if not keyword.iskeyword(func.identifier):
                    lines.append(
                        f"    def {func.identifier}(self, *args, **kwargs) -> Any: ..."
                    )

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
            "    def __contains__(self, key: Union[str, int]) -> bool: ...",
            f"    def __iter__(self) -> Iterator['{element_type}']: ...",
            f"    def __getitem__(self, key: Union[str, int]) -> '{element_type}': ...",
            "    def __len__(self) -> int: ...",
        ]
