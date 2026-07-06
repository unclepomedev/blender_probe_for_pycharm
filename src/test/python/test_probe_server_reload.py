"""Tests for the probe server's command loop, focused on the hot-reload
regression: after a .blend file is opened/created, ping and reload commands
must still be processed (previously the draining timer was silently removed).
"""


def test_communication_survives_file_switch(probe, capsys):
    """The queue-draining timer must survive a file load and keep working.

    This is the regression test for the bug where reload/ping stopped working
    after opening or creating a new .blend file in a running Blender session.
    """
    probe.register()

    timers = probe.bpy.app.timers
    entry = next(r for r in timers.registered if r["func"] is probe.main_thread_loop)
    # Persistence is what makes the timer survive a file load in Blender.
    assert entry["persistent"] is True

    # Commands are processed before the switch.
    probe.execution_queue.put({"action": "ping"})
    probe.main_thread_loop()
    assert "Pong" in capsys.readouterr().out

    # Open/create a .blend -> Blender drops non-persistent timers.
    timers.simulate_file_load()

    # The drain timer must still be registered (the actual bug was here).
    assert timers.is_registered(probe.main_thread_loop), (
        "main_thread_loop was unregistered on file load; "
        "reload/ping would silently stop working until Blender restart"
    )

    # And communication still works after the switch.
    probe.execution_queue.put({"action": "ping"})
    timers.call(probe.main_thread_loop)
    assert "Pong" in capsys.readouterr().out


def test_bad_command_does_not_stop_draining(probe, capsys):
    """A failing command must not propagate out of the timer.

    If it did, Blender would unregister the timer and reproduce the same
    silent-death symptom the persistence fix addresses.
    """
    probe.execution_queue.put("not-a-dict")  # process_command will raise
    probe.execution_queue.put({"action": "ping"})

    result = probe.main_thread_loop()

    assert result == 0.1  # returned normally -> Blender keeps the timer alive
    out = capsys.readouterr().out
    assert "Error processing command" in out  # bad item was caught + logged
    assert "Pong" in out  # the good item that followed still ran
    assert probe.execution_queue.empty()  # queue fully drained past the failure


def test_reload_command_dispatches_to_deep_reload(probe, monkeypatch):
    """A reload command (what a file save sends) routes to deep_reload_addon."""
    calls = []
    monkeypatch.setattr(probe, "deep_reload_addon", lambda name: calls.append(name))

    probe.execution_queue.put({"action": "reload", "module_name": "threed_bitmap"})
    probe.main_thread_loop()

    assert calls == ["threed_bitmap"]


def test_reload_without_module_name_is_ignored(probe, monkeypatch):
    """A reload missing module_name should be a no-op, not a crash."""
    calls = []
    monkeypatch.setattr(probe, "deep_reload_addon", lambda name: calls.append(name))

    probe.execution_queue.put({"action": "reload"})
    result = probe.main_thread_loop()

    assert calls == []
    assert result == 0.1
