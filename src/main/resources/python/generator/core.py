import sys
import os
import traceback

from .config import GeneratorConfig
from .context import StubContext
from .writer import StubWriter
from .analyzer import StubAnalyzer
from .gen_types import BpyTypesGenerator
from .gen_ops import BpyOpsGenerator
from .gen_modules import ModuleGenerator


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
