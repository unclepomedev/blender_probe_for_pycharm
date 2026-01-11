import bpy
import threading
import socket
import json
import queue
import sys
import os
import traceback
import time

HOST = '127.0.0.1'
HEADER_SIZE = 64

execution_queue = queue.Queue()
server_running = False


def log(message):
    print(f"[BlenderProbe] {message}", flush=True)


def start_socket_server():
    global server_running
    log("Debug: Socket thread started.")

    try:
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        log("Attempting to bind socket...")

        server_socket.bind((HOST, 0))
        server_socket.listen()
        server_running = True

        port = server_socket.getsockname()[1]

        print(f"BLENDER_PROBE_PORT::{port}", flush=True)
        log(f"Server listening on {HOST}:{port}")

    except Exception as e:
        log(f"FATAL ERROR in Socket Thread: {e}")
        traceback.print_exc()
        return

    while server_running:
        try:
            conn, addr = server_socket.accept()
            thread = threading.Thread(target=handle_client, args=(conn, addr))
            thread.daemon = True
            thread.start()
        except Exception as e:
            log(f"Socket accept error: {e}")
            break


def handle_client(conn, addr):
    """
    Handles a single connection via simple JSON messaging.
    Protocol: Header(Length of JSON, 64bytes) + JSON Body
    """
    try:
        msg_length_raw = conn.recv(HEADER_SIZE).decode('utf-8')
        if not msg_length_raw:
            return
        msg_length = int(msg_length_raw.strip())

        data = b""
        while len(data) < msg_length:
            packet = conn.recv(msg_length - len(data))
            if not packet:
                break
            data += packet

        message = json.loads(data.decode('utf-8'))
        execution_queue.put(message)
        conn.sendall(b"ACK")

    except Exception as e:
        log(f"Error handling client: {e}")
        traceback.print_exc()
    finally:
        conn.close()


def main_thread_loop():
    """
    Checks the queue for pending actions and executes them in the main thread.
    """
    while not execution_queue.empty():
        try:
            cmd = execution_queue.get_nowait()
            process_command(cmd)
        except queue.Empty:
            break
    return 0.1


def process_command(cmd):
    action = cmd.get("action")
    if action == "ping":
        log("Pong! (Received Ping)")

        def show_pong():
            bpy.ops.wm.report({'INFO'}, "Blender Probe: Connected!")

        try:
            bpy.ops.wm.report({'INFO'}, "Blender Probe Connected")
        except:
            pass

    elif action == "reload":
        log(f"Reload request: {cmd.get('module_name')}")


def register():
    log("Register function called.")

    project_root = os.environ.get("BLENDER_PROBE_PROJECT_ROOT")
    if project_root and project_root not in sys.path:
        sys.path.append(project_root)
        log(f"Added project root to sys.path: {project_root}")

    try:
        server_thread = threading.Thread(target=start_socket_server, daemon=True)
        server_thread.start()
        log("Server thread start command issued.")
    except Exception as e:
        log(f"Failed to start thread: {e}")
        traceback.print_exc()

    if not bpy.app.timers.is_registered(main_thread_loop):
        bpy.app.timers.register(main_thread_loop)
        log("Main thread timer registered.")


if __name__ == "__main__":
    try:
        register()
    except Exception as e:
        print(f"CRITICAL ERROR at script toplevel: {e}", flush=True)
        traceback.print_exc()
