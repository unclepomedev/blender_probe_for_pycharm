import inspect
import keyword
import os

from .context import StubContext


class StubWriter:
    """
    Handles file writing and string formatting operations for stub generation.
    """
    def __init__(self, context: StubContext):
        """
        Initializes the writer.

        :param context: The shared stub context.
        """
        self.context = context

    @staticmethod
    def sanitize_arg_name(name: str) -> str:
        """
        Sanitizes an argument name to avoid conflicts with Python keywords.

        :param name: The original argument name.
        :return: A safe name (e.g., 'class' becomes 'class_').
        """
        if keyword.iskeyword(name):
            return f"{name}_"
        return name

    @staticmethod
    def write_file(directory: str, filename: str, content: list[str]):
        """
        Writes a list of strings to a file.

        :param directory: The target directory.
        :param filename: The target filename.
        :param content: The list of lines to write.
        """
        if not os.path.exists(directory):
            os.makedirs(directory)
        filepath = os.path.join(directory, filename)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write("\n".join(content))

    @staticmethod
    def format_docstring(doc_str: str, indent: str = "    ") -> str:
        """
        Formats a string into a Python docstring.

        :param doc_str: The raw documentation string.
        :param indent: The indentation string.
        :return: The formatted docstring.
        """
        if not doc_str:
            return ""
        doc_str = doc_str.replace("\\", "\\\\").replace('"""', '\\"\\"\\"')
        if doc_str.endswith('"'):
            doc_str += " "
        return f'{indent}"""{doc_str}"""'

    def format_doc_with_link(
        self, doc_str: str | None, module_name: str, indent: str = "    "
    ) -> str:
        """
        Formats a docstring and appends a link to the online documentation.

        :param doc_str: The raw documentation string.
        :param module_name: The module name for generating the link.
        :param indent: The indentation string.
        :return: The formatted docstring with the link.
        """
        url = self.context.get_api_docs_link(module_name)
        text = doc_str if isinstance(doc_str, str) else ""

        if url:
            if text:
                text += f"\n\n{indent}Online Documentation:\n{indent}{url}"
            else:
                # If only link is present, use a format similar to make_doc_block but without forced indentation
                # to stay consistent with format_docstring
                text = f"\n{indent}Online Documentation:\n{indent}{url}"

        return self.format_docstring(text, indent)

    def make_doc_block(self, module_name: str, indent: str = "    ") -> str:
        """
        Creates a documentation block containing only the online link.

        :param module_name: The module name.
        :param indent: The indentation string.
        :return: The docstring block.
        """
        url = self.context.get_api_docs_link(module_name)
        if not url:
            return ""
        return f'{indent}"""\n{indent}Online Documentation:\n{indent}{url}\n{indent}"""'

    @staticmethod
    def get_member_signature(obj) -> str:
        """
        Retrieves the signature of a python object (function/method).

        :param obj: The object to inspect.
        :return: The signature string.
        """
        try:
            sig = inspect.signature(obj)
            new_sig = sig.replace(return_annotation=inspect.Signature.empty)
            return str(new_sig)
        except Exception:
            return "(*args, **kwargs)"

    @staticmethod
    def get_math_methods(class_name: str) -> list[str]:
        """
        Generates type stubs for standard math magic methods.

        :param class_name: The return type name for operations (e.g., Vector).
        :return: A list of stub definitions for math methods.
        """
        methods = []
        ops = ["add", "sub", "mul", "truediv", "floordiv", "mod", "pow"]
        for op in ops:
            methods.append(f"    def __{op}__(self, other: Any) -> Any: ...")
            methods.append(f"    def __r{op}__(self, other: Any) -> Any: ...")
            methods.append(f"    def __i{op}__(self, other: Any) -> Any: ...")

        for op in ["neg", "pos", "abs", "invert"]:
            methods.append(f"    def __{op}__(self) -> '{class_name}': ...")

        methods.append("    def __eq__(self, other: Any) -> bool: ...")
        methods.append("    def __ne__(self, other: Any) -> bool: ...")
        methods.append("    def __lt__(self, other: Any) -> bool: ...")
        methods.append("    def __le__(self, other: Any) -> bool: ...")
        methods.append("    def __gt__(self, other: Any) -> bool: ...")
        methods.append("    def __ge__(self, other: Any) -> bool: ...")

        methods.append("    def __len__(self) -> int: ...")
        methods.append("    def __getitem__(self, key: int) -> float: ...")
        methods.append("    def __setitem__(self, key: int, value: float): ...")
        methods.append("    def __iter__(self) -> Iterator[float]: ...")
        return methods
