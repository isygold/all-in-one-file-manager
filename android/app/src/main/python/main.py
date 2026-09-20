"""Android entry point (Chaquopy). filemanager.py is copied here at build time."""
import threading


def start():
    import filemanager
    t = threading.Thread(
        target=filemanager.run,
        kwargs={"host": "127.0.0.1", "port": 8080, "quiet": True},
        daemon=True,
    )
    t.start()
