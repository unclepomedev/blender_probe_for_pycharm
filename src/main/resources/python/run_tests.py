import sys
import os
import unittest
import importlib
import traceback


def escape_teamcity(text):
    if not text:
        return ""
    return (
        str(text)
        .replace("|", "||")
        .replace("'", "|'")
        .replace("\n", "|n")
        .replace("\r", "|r")
        .replace("[", "|[")
        .replace("]", "|]")
    )


def tc_print(message_type, **kwargs):
    props = " ".join([f"{k}='{escape_teamcity(v)}'" for k, v in kwargs.items()])
    print(f"##teamcity[{message_type} {props}]")
    sys.stdout.flush()


def auto_register_addon():
    project_root = os.environ.get("BLENDER_PROBE_PROJECT_ROOT")

    if not project_root or not os.path.exists(project_root):
        return

    if project_root not in sys.path:
        sys.path.insert(0, project_root)
        tc_print(
            "message",
            text=f"Added project root to sys.path head: {project_root}",
            status="NORMAL",
        )

    exclude_dirs = {
        "tests",
        "__pycache__",
        ".git",
        ".idea",
        ".blender_stubs",
        "build",
        "dist",
    }
    found_package = False

    try:
        for item_name in os.listdir(project_root):
            if item_name in exclude_dirs or item_name.startswith("."):
                continue

            full_path = os.path.join(project_root, item_name)

            if os.path.isdir(full_path) and os.path.exists(
                os.path.join(full_path, "__init__.py")
            ):
                try:
                    module = importlib.import_module(item_name)
                    if hasattr(module, "register"):
                        module.register()
                        tc_print(
                            "message",
                            text=f"[Blender Probe] Automatically registered addon package: {item_name}",
                            status="NORMAL",
                        )
                        found_package = True
                except ImportError as e:
                    tc_print(
                        "message",
                        text=f"[Blender Probe] Found package '{item_name}' but failed to import: {e}",
                        status="WARNING",
                    )

        if not found_package:
            tc_print(
                "message",
                text=f"[Blender Probe] Warning: No addon package with register() found in {project_root}",
                status="WARNING",
            )

    except Exception as e:
        tc_print(
            "message",
            text=f"[Blender Probe] Failed to auto-register addon: {e}",
            status="WARNING",
        )


class TeamCityTestResult(unittest.TextTestResult):
    def startTest(self, test):
        super().startTest(test)
        tc_print("testStarted", name=str(test))

    def addSuccess(self, test):
        super().addSuccess(test)
        tc_print("testFinished", name=str(test))

    def addError(self, test, err):
        super().addError(test, err)
        self._report_failure(test, err, "Error")

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self._report_failure(test, err, "Failure")

    def _report_failure(self, test, err, status):
        ex_type, ex_value, ex_traceback = err
        full_trace = "".join(
            traceback.format_exception(ex_type, ex_value, ex_traceback)
        )

        tc_print("testFailed", name=str(test), message=status, details=full_trace)
        tc_print("testFinished", name=str(test))


class TeamCityTestRunner(unittest.TextTestRunner):
    def _makeResult(self):
        return TeamCityTestResult(self.stream, self.descriptions, self.verbosity)


def run_tests(test_dir):
    tc_print("blockOpened", name="Blender Probe Setup")
    try:
        auto_register_addon()
    finally:
        tc_print("blockClosed", name="Blender Probe Setup")

    tc_print("testSuiteStarted", name="Blender Tests")

    loader = unittest.TestLoader()
    result = None

    try:
        if not os.path.exists(test_dir):
            tc_print(
                "message", text=f"Test directory not found: {test_dir}", status="ERROR"
            )
            sys.exit(1)

        suite = loader.discover(test_dir)
        runner = TeamCityTestRunner(stream=sys.stdout, verbosity=0)
        result = runner.run(suite)

    except Exception:
        err_msg = traceback.format_exc()
        tc_print(
            "message",
            text=f"Exception during test discovery:\n{err_msg}",
            status="ERROR",
        )
        sys.exit(1)
    finally:
        tc_print("testSuiteFinished", name="Blender Tests")

    if result and not result.wasSuccessful():
        sys.exit(1)


if __name__ == "__main__":
    try:
        argv = sys.argv
        target_dir = os.getcwd()

        if "--" in argv:
            args_after_dash = argv[argv.index("--") + 1 :]
            if args_after_dash:
                target_dir = args_after_dash[0]
            else:
                tc_print(
                    "message",
                    text="Error: No test directory specified after '--'",
                    status="ERROR",
                )
                sys.exit(1)

        run_tests(target_dir)

    except SystemExit:
        raise
    except Exception as e:
        print(f"Critical Error in Test Runner: {e}")
        traceback.print_exc()
        sys.exit(1)
