import textwrap
from pathlib import Path
from string import Template


class TemplateLoader:
    def __init__(self):
        # We assume this file is in <root>/generator/template_loader.py
        # Templates are in <root>/templates/
        self.base_path = Path(__file__).parent.parent / "templates"

    def read_text(self, relative_path: str) -> str:
        target = self.base_path / relative_path
        if not target.exists():
            return ""
        return target.read_text(encoding="utf-8")

    def read_lines(self, relative_path: str) -> list[str]:
        text = self.read_text(relative_path)
        if not text:
            return []
        return text.splitlines()

    def get_template(self, name: str) -> Template:
        content = self.read_text(name)
        return Template(content)

    def get_injection(self, class_name: str) -> list[str]:
        target = self.base_path / "injections" / f"{class_name}.pyi"
        if not target.exists():
            return []
        content = target.read_text(encoding="utf-8")
        return textwrap.indent(content, "    ").splitlines()


template_loader = TemplateLoader()
