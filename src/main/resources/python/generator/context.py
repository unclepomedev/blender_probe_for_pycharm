import bpy
from .config import GeneratorConfig


class StubContext:
    """
    Holds the shared state and configuration for the stub generation process.
    Provides utility methods for type mapping and documentation linking.
    """
    def __init__(self, config: GeneratorConfig):
        """
        Initializes the context.

        :param config: The generator configuration.
        """
        self.config = config
        self.collection_mapping: dict[str, str] = {}

    def collect_dependencies(self, name: str, cls: type) -> set[str]:
        """
        Collects dependencies for a given bpy.types class.

        :param name: The name of the class.
        :param cls: The class object itself.
        :return: A set of dependency names.
        """
        dependencies = set()

        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type":
                    continue

                if getattr(prop, "is_deprecated", False):
                    dependencies.add("deprecated")

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

        if name in self.collection_mapping:
            element_type = self.collection_mapping[name]
            if element_type != name and hasattr(bpy.types, element_type):
                dependencies.add(element_type)

        return dependencies

    def get_api_docs_link(self, module_name: str) -> str | None:
        """
        Generates a URL to the official Blender Python API documentation.

        :param module_name: The name of the module or class.
        :return: The documentation URL, or None if documentation is disabled for the module.
        """
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
        """
        Maps a Blender RNA property type to a Python type hint.

        :param prop: The RNA property object.
        :return: The Python type string (e.g., 'int', 'str').
        """
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
        """
        Generates a detailed type hint including metadata (min, max, subtype).

        :param prop: The RNA property object.
        :return: A type hint string using Annotated or basic types.
        """
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
