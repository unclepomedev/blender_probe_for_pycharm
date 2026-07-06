"""Test fixtures for the in-Blender probe server.

``probe_server.py`` runs inside Blender and does ``import bpy`` at module load,
so it cannot be imported directly under a normal Python interpreter. These tests
stand in a fake ``bpy`` module that reproduces the one behaviour we care about:
Blender removes non-persistent ``bpy.app.timers`` whenever a .blend file is
loaded. That is the exact rule the hot-reload regression hinged on, and faking
it lets us test the fix without launching Blender.
"""

import os
import sys
import types

import pytest

# probe_server.py lives under the plugin resources, not on the import path.
_PYTHON_SRC = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "..", "..", "main", "resources", "python")
)
if _PYTHON_SRC not in sys.path:
    sys.path.insert(0, _PYTHON_SRC)


class FakeTimers:
    """Stand-in for ``bpy.app.timers``.

    Records registrations and, like real Blender, drops every non-persistent
    timer when a file is loaded (see :meth:`simulate_file_load`).
    """

    def __init__(self):
        self.registered = []  # list of {"func", "persistent", "first_interval"}

    def register(self, func, first_interval=None, persistent=False):
        self.registered.append(
            {"func": func, "persistent": persistent, "first_interval": first_interval}
        )

    def unregister(self, func):
        self.registered = [r for r in self.registered if r["func"] is not func]

    def is_registered(self, func):
        return any(r["func"] is func for r in self.registered)

    # --- test helpers ---------------------------------------------------
    def simulate_file_load(self):
        """Mimic Blender unloading non-persistent timers on file open/new."""
        self.registered = [r for r in self.registered if r["persistent"]]

    def call(self, func):
        """Invoke a registered timer the way Blender's event loop would."""
        for r in self.registered:
            if r["func"] is func:
                return r["func"]()
        raise AssertionError(f"timer {func!r} is not registered")


def _make_fake_bpy():
    bpy = types.ModuleType("bpy")
    bpy.app = types.SimpleNamespace(timers=FakeTimers())
    bpy.context = types.SimpleNamespace(preferences=types.SimpleNamespace(addons={}))

    class _AddonOps:
        def __init__(self):
            self.enabled = []

        def addon_enable(self, module=None):
            self.enabled.append(module)

    bpy.ops = types.SimpleNamespace(preferences=_AddonOps())
    return bpy


# Inject the fake before any test imports probe_server.
sys.modules.setdefault("bpy", _make_fake_bpy())


@pytest.fixture
def probe(monkeypatch):
    """Import probe_server with a clean, isolated state for each test."""
    import probe_server

    # Fresh timer registry so tests don't leak registrations into each other.
    probe_server.bpy.app.timers = FakeTimers()

    # register() would otherwise bind a real TCP socket and spawn a thread.
    monkeypatch.setattr(probe_server, "start_socket_server", lambda: None)

    # Keep register() on its minimal path (no debugger attach, no auto-enable).
    for var in (
        "BLENDER_PROBE_DEBUG_PORT",
        "BLENDER_PROBE_PYDEVD_PATH",
        "BLENDER_PROBE_PROJECT_ROOT",
        "BLENDER_PROBE_ADDON_NAME",
    ):
        monkeypatch.delenv(var, raising=False)

    while not probe_server.execution_queue.empty():
        probe_server.execution_queue.get_nowait()
    probe_server.server_running = False

    return probe_server
