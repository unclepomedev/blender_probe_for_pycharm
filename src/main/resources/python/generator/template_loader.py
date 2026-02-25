import textwrap
from pathlib import Path
from string import Template


class TemplateLoader:
    """
    Manages loading of string templates and code injections from the file system.
    """
    def __init__(self):
        """
        Initializes the loader and sets the base template path.
        """
        # We assume this file is in <root>/generator/template_loader.py
        # Templates are in <root>/templates/
        self.base_path = Path(__file__).parent.parent / "templates"

    def read_text(self, relative_path: str) -> str:
        """
        Reads a text file from the template directory.

        :param relative_path: Path relative to the templates root.
        :return: The file content.
        """
        target = self.base_path / relative_path
        if not target.exists():
            return ""
        return target.read_text(encoding="utf-8")

    def read_lines(self, relative_path: str) -> list[str]:
        """
        Reads a text file and returns it as a list of lines.

        :param relative_path: Path relative to the templates root.
        :return: List of strings.
        """
        text = self.read_text(relative_path)
        if not text:
            return []
        return text.splitlines()

    def get_template(self, name: str) -> Template:
        """
        Loads a file as a string.Template.

        :param name: The template file name.
        :return: The Template object.
        """
        content = self.read_text(name)
        return Template(content)

    def get_injection(self, class_name: str) -> list[str]:
        """
        Loads manually defined injection code for a specific class.

        :param class_name: The name of the class to inject code into.
        :return: List of lines containing the injected code.
        """
        target = self.base_path / "injections" / f"{class_name}.pyi"
        if not target.exists():
            return []
        content = target.read_text(encoding="utf-8")
        return textwrap.indent(content, "    ").splitlines()


template_loader = TemplateLoader()
