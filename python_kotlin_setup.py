import socket
import threading
import sys

def recv_loop(conn):
    """Receive messages from Kotlin app"""
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                print("\nApp disconnected.")
                break
            print(f"\nApp: {data.decode().strip()}")
            print("Python> ", end="", flush=True)
    except Exception as e:
        print(f"\nRecv error: {e}")
    finally:
        try:
            conn.close()
        except:
            pass
        sys.exit(0)

def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.bind(("127.0.0.1", 5000))  # must match adb reverse tcp:5000 tcp:5000
    srv.listen(1)
    print("Python: Waiting for app to connect...")

    conn, addr = srv.accept()
    print("Python: App connected.")

    # Start thread for receiving messages
    threading.Thread(target=recv_loop, args=(conn,), daemon=True).start()

    # Interactive loop to send videoKeys
    while True:
        try:
            video_key = input("Python> Enter videoKey (or 'exit'): ").strip()
            if not video_key:
                continue
            if video_key.lower() == "exit":
                conn.sendall(b"exit\n")
                conn.close()
                break

            # Send videoKey to app
            conn.sendall((video_key + "\n").encode())
        except (BrokenPipeError, ConnectionResetError):
            print("Connection lost.")
            break
        except EOFError:
            break

    try:
        srv.close()
    except:
        pass

if __name__ == "__main__":
    main()
