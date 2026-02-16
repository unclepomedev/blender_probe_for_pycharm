import bpy
from .config import GeneratorConfig


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

    @staticmethod
    def map_rna_type(prop) -> str:
        try:
            t = prop.type
            if t == "STRING":
                return "str"
            if t == "BOOLEAN":
                if getattr(prop, "is_array", False):
                    return "list[bool]"
                return "bool"
            if t == "INT":
                if getattr(prop, "is_array", False):
                    return "list[int]"
                return "int"
            if t == "FLOAT":
                if getattr(prop, "is_array", False):
                    return "list[float]"
                return "float"
            if t == "ENUM":
                if getattr(prop, "is_enum_flag", False):
                    return "set[str]"
                return "str"
            if t == "POINTER":
                if prop.fixed_type and hasattr(prop.fixed_type, "identifier"):
                    return f"'{prop.fixed_type.identifier}'"
                return "Any"
            if t == "COLLECTION":
                if hasattr(prop, "srna") and prop.srna:
                    srna_id = prop.srna.identifier
                    if hasattr(bpy.types, srna_id):
                        return f"'{srna_id}'"
                if prop.fixed_type and hasattr(prop.fixed_type, "identifier"):
                    return f"bpy_prop_collection['{prop.fixed_type.identifier}']"
                return "bpy_prop_collection[Any]"
            return "Any"
        except Exception:
            return "Any"

    def get_smart_type_hint(self, prop) -> str:
        try:
            type_hint = self.map_rna_type(prop)

            # Literal for ENUM
            if prop.type == "ENUM" and not getattr(prop, "is_enum_flag", False):
                items = getattr(prop, "enum_items", [])
                if 0 < len(items) < 200:
                    quoted_items = [f"'{item.identifier}'" for item in items]
                    type_hint = f"Literal[{', '.join(quoted_items)}]"

            # Optional for POINTER
            if prop.type == "POINTER":
                if not getattr(prop, "is_never_none", False):
                    if not type_hint.startswith("Optional"):
                        type_hint = f"Optional[{type_hint}]"

            # Annotated
            metadata = []

            subtype = getattr(prop, "subtype", "NONE")
            if subtype != "NONE":
                metadata.append(f"\"subtype='{subtype}'\"")

            unit = getattr(prop, "unit", "NONE")
            if unit != "NONE":
                metadata.append(f"\"unit='{unit}'\"")

            for attr in ["min", "max", "step", "precision"]:
                val = getattr(prop, attr, None)
                if val is not None:
                    metadata.append(f'"{attr}={val}"')

            is_animatable = getattr(prop, "is_animatable", None)
            if is_animatable is False:
                metadata.append('"is_animatable=False"')

            is_argument_optional = getattr(prop, "is_argument_optional", None)
            if is_argument_optional is True:
                metadata.append('"is_argument_optional=True"')

            if metadata:
                type_hint = f"Annotated[{type_hint}, {', '.join(metadata)}]"

            return type_hint
        except Exception:
            return "Any"
