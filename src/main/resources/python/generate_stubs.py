import sys
import os
import traceback

# Ensure the current directory is in sys.path so we can import 'generator'
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

try:
    from generator.core import main

    if __name__ == "__main__":
        main()
except Exception:
    traceback.print_exc()
    sys.exit(1)
