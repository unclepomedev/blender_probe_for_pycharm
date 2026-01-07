import sys
import os
import unittest

project_root = os.environ.get("BLENDER_PROBE_PROJECT_ROOT")
if project_root and os.path.exists(project_root):
    if project_root not in sys.path:
        sys.path.insert(0, project_root)
        print(f"##teamcity[message text='Added project root to sys.path head: {project_root}' status='NORMAL']")


class TeamCityTestResult(unittest.TextTestResult):
    def startTest(self, test):
        super().startTest(test)
        print(f"##teamcity[testStarted name='{test}']")
        sys.stdout.flush()

    def addSuccess(self, test):
        super().addSuccess(test)
        print(f"##teamcity[testFinished name='{test}']")
        sys.stdout.flush()

    def addError(self, test, err):
        super().addError(test, err)
        self._report_failure(test, err, "Error")

    def addFailure(self, test, err):
        super().addFailure(test, err)
        self._report_failure(test, err, "Failure")

    def _report_failure(self, test, err, status):
        err_msg = str(err[1]).replace("|", "||").replace("'", "|'").replace("\n", "|n").replace("\r", "|r").replace("[", "|[").replace("]", "|]")
        print(f"##teamcity[testFailed name='{test}' message='{status}' details='{err_msg}']")
        print(f"##teamcity[testFinished name='{test}']")
        sys.stdout.flush()

class TeamCityTestRunner(unittest.TextTestRunner):
    def _makeResult(self):
        return TeamCityTestResult(self.stream, self.descriptions, self.verbosity)

def run_tests(test_dir):
    print("##teamcity[testSuiteStarted name='Blender Tests']")
    sys.stdout.flush()

    loader = unittest.TestLoader()
    try:
        if not os.path.exists(test_dir):
            print(f"##teamcity[message text='Test directory not found: {test_dir}' status='ERROR']")
            sys.exit(1)

        suite = loader.discover(test_dir)

        runner = TeamCityTestRunner(stream=sys.stdout, verbosity=2)
        result = runner.run(suite)

    except Exception as e:
        print(f"##teamcity[message text='Exception during test discovery: {str(e)}' status='ERROR']")
        sys.exit(1)
    finally:
        print("##teamcity[testSuiteStarted name='Blender Tests']") # 念のため
        print("##teamcity[testSuiteFinished name='Blender Tests']")
        sys.stdout.flush()

    if not result.wasSuccessful():
        sys.exit(1)

if __name__ == "__main__":
    argv = sys.argv
    try:
        if "--" in argv:
            args = argv[argv.index("--") + 1:]
            if args:
                target_dir = args[0]
                run_tests(target_dir)
            else:
                print("Error: No test directory specified after '--'")
                sys.exit(1)
        else:
            run_tests(os.getcwd())
    except SystemExit:
        pass
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)