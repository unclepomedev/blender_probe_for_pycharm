import keyword
import inspect
import os
import sys

import bpy

DEFAULT_OUTPUT_DIR = os.path.join(os.path.dirname(bpy.data.filepath) if bpy.data.filepath else os.getcwd(), "typings")

output_dir = DEFAULT_OUTPUT_DIR
args = sys.argv
if "--" in args:
    args = args[args.index("--") + 1:]

if "--output" in args:
    try:
        idx = args.index("--output")
        output_dir = args[idx + 1]
    except (ValueError, IndexError):
        pass

BPY_TYPES_DIR = os.path.join(output_dir, "bpy", "types")


# Type Mapper: RNA -> Python TypeHint
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
            if prop.fixed_type:
                return f"'{prop.fixed_type.identifier}'"
            return "Any"
        elif type_char == 'COLLECTION':
            if prop.fixed_type:
                return f"bpy.types.bpy_prop_collection['{prop.fixed_type.identifier}']"
            return "bpy.types.bpy_prop_collection[Any]"
        else:
            return "Any"
    except Exception:
        return "Any"


def generate_bpy_types():
    if not os.path.exists(BPY_TYPES_DIR):
        os.makedirs(BPY_TYPES_DIR)

    print(f"Start generating stubs to: {BPY_TYPES_DIR}")

    content = [
        "import sys",
        "import typing",
        "from typing import Any, List, Set, Dict, Tuple, Optional, Union, Sequence",
        "import bpy.types",
        "",
        "# Generic definition for collection properties",
        "class bpy_prop_collection(Sequence[Any]):",
        "    def values(self) -> List[Any]: ...",
        "    def items(self) -> List[Tuple[str, Any]]: ...",
        "    def get(self, key: str, default: Any = None) -> Any: ...",
        "    def __getitem__(self, key: Union[str, int]) -> Any: ...",
        "    def __iter__(self) -> typing.Iterator[Any]: ...",
        "    def __len__(self) -> int: ...",
        "",
        "# --- Auto-generated Classes ---",
        ""
    ]

    classes = []
    for name in dir(bpy.types):
        cls = getattr(bpy.types, name)
        if inspect.isclass(cls):
            classes.append((name, cls))

    count = 0
    for name, cls in classes:
        bases = [b.__name__ for b in cls.__bases__]
        bases_str = f"({', '.join(bases)})" if bases else ""

        content.append(f"class {name}{bases_str}:")

        doc = getattr(cls, "__doc__", None)
        if isinstance(doc, str):
            doc_clean = doc.replace('"""', "'''")
            content.append(f'    """{doc_clean}"""')

        props_written = False

        if hasattr(cls, "bl_rna"):
            for prop in cls.bl_rna.properties:
                if prop.identifier == "rna_type": continue

                if keyword.iskeyword(prop.identifier):
                    raise ValueError(f"FATAL: Found reserved keyword '{prop.identifier}' in Official Blender API. This is a Blender bug.")

                py_type = map_rna_type(prop)
                content.append(f"    {prop.identifier}: {py_type}")
                props_written = True

            for func in cls.bl_rna.functions:
                if keyword.iskeyword(func.identifier):
                    raise ValueError(f"FATAL: Found reserved keyword '{func.identifier}' in Official Blender API. This is a Blender bug.")
                content.append(f"    def {func.identifier}(self, *args, **kwargs): ...")
                props_written = True

        if not props_written:
            content.append("    pass")

        content.append("")
        count += 1

    file_path = os.path.join(BPY_TYPES_DIR, "__init__.pyi")
    with open(file_path, "w", encoding="utf-8") as f:
        f.write("\n".join(content))

    print(f"Successfully generated {count} classes in {file_path}")


if __name__ == "__main__":
    generate_bpy_types()
