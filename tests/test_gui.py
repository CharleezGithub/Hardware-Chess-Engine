"""Exercise board input and the same asynchronous transport used during play."""
import threading
import time
import unittest
from types import SimpleNamespace

from test_client import initial_board, legal_moves, play
from UART import SimulationTransport

try:
    import tkinter as tk
    from chess_gui import ChessWindow
except ImportError:
    tk = None


class FakeTransport:
    def __init__(self):
        self.board = initial_board()
        self.status = 0
        self.commands = []
        self.gate = threading.Event()
        self.gate.set()
        self.closed = False
        self.fail = False

    def exchange(self, frame):
        self.commands.append(tuple(frame[1:4]))
        if not self.gate.wait(5):
            raise TimeoutError("Test transport blocked")
        if self.fail or self.closed:
            raise OSError("Disconnected test device")
        if frame[1] == 1:
            self.board, self.status = initial_board(), 0
        return self.status, self.board.copy()

    def close(self):
        self.closed = True
        self.gate.set()


@unittest.skipIf(tk is None, "Tkinter is not installed")
class BoardTests(unittest.TestCase):
    def make_window(self, factory):
        try:
            root = tk.Tk()
        except tk.TclError as exc:
            self.skipTest(f"Tk display unavailable: {exc}")
        root.withdraw()
        self.app = ChessWindow(root, factory)
        self.addCleanup(self.close_window)
        self.wait_for(lambda: not self.app.busy)
        self.assertTrue(self.app.connected, self.app.detail.get())
        return self.app

    def close_window(self):
        self.app.close()
        self.app.worker.thread.join(3)
        if self.app.worker.closer:
            self.app.worker.closer.join(3)
        self.assertFalse(self.app.worker.alive(), "Transport must stop when the window closes")

    def wait_for(self, condition, timeout=35):
        deadline = time.monotonic() + timeout
        while not condition():
            self.app.root.update()
            if time.monotonic() > deadline:
                self.fail("GUI did not complete the request: " + self.app.detail.get())
            time.sleep(0.01)
        self.app.root.update()

    def event(self, square):
        x, y = self.app.center(square)
        return SimpleNamespace(x=x, y=y)

    def click(self, square):
        event = self.event(square)
        self.app.on_press(event)
        self.app.on_release(event)

    def test_coordinates_flip_and_drop_outside(self):
        transport = FakeTransport()
        app = self.make_window(lambda: transport)
        for flipped in (False, True):
            self.assertEqual(app.flipped, flipped)
            for square in range(64):
                self.assertEqual(app.square_at(*app.center(square)), square)
            self.assertIsNone(app.square_at(-1, -1))
            app.flip()
        app.on_press(self.event(52))
        outside = SimpleNamespace(x=-10, y=-10)
        app.on_drag(outside)
        app.on_release(outside)
        self.assertEqual(transport.commands, [(1, 0, 0)])
        self.assertEqual(app.board, initial_board())

    def test_click_move_waits_for_board_and_prevents_duplicate_input(self):
        transport = FakeTransport()
        app = self.make_window(lambda: transport)
        transport.gate.clear()
        transport.board = play(play(initial_board(), (52 << 6) | 36), (12 << 6) | 28)
        self.click(52)
        self.click(36)
        self.assertTrue(app.busy)
        self.assertEqual(app.board, initial_board())
        self.assertEqual(app.menu.entrycget('New game', 'state'), 'disabled')
        self.click(54)
        self.click(38)
        heartbeat = []
        app.root.after(0, lambda: heartbeat.append(True))
        self.wait_for(lambda: heartbeat)
        transport.gate.set()
        self.wait_for(lambda: not app.busy)
        self.assertEqual(transport.commands, [(1, 0, 0), (3, 52, 36)])
        self.assertEqual(app.board, transport.board)
        self.assertEqual(app.changed, {12, 28, 36, 52})

    def test_drag_illegal_move_and_game_over(self):
        transport = FakeTransport()
        app = self.make_window(lambda: transport)
        transport.status = 5
        app.on_press(self.event(52))
        app.on_drag(self.event(28))
        self.assertTrue(app.dragging)
        app.on_release(self.event(28))
        self.wait_for(lambda: not app.busy)
        self.assertEqual(transport.commands[-1], (3, 52, 28))
        self.assertEqual(app.board, initial_board())
        self.assertIn("Illegal move", app.message.get())
        for status in (2, 3, 4):
            transport.status = status
            app.submit(2)
            self.wait_for(lambda: not app.busy)
            self.assertFalse(app.can_move())
            self.assertEqual(app.menu.entrycget('New game', 'state'), 'normal')
        app.menu.invoke('New game')
        self.wait_for(lambda: not app.busy)
        self.assertTrue(app.can_move())

    def test_disconnect_reconnect_and_close_during_request(self):
        transports = []

        def factory():
            transport = FakeTransport()
            transports.append(transport)
            return transport

        app = self.make_window(factory)
        transports[0].fail = True
        app.submit(2)
        self.wait_for(lambda: not app.busy)
        self.assertFalse(app.connected)
        self.assertIn("Disconnected test device", app.detail.get())
        app.menu.invoke('Reconnect')
        self.wait_for(lambda: not app.busy)
        self.assertTrue(app.connected)
        self.assertTrue(transports[0].closed)
        self.assertEqual(transports[1].commands, [(2, 0, 0)])
        transports[1].gate.clear()
        app.submit(2)
        self.wait_for(lambda: len(transports[1].commands) == 2)
        app.close()
        app.worker.thread.join(3)
        self.assertTrue(transports[1].closed)

    def test_visual_board_plays_against_actual_rtl(self):
        app = self.make_window(lambda: SimulationTransport(30))
        self.assertEqual(app.board, initial_board())
        human_board = play(app.board, (52 << 6) | 36)
        expected = [play(human_board, move) for move in legal_moves(human_board, False)]
        self.click(52)
        self.click(36)
        self.assertTrue(app.busy)
        self.wait_for(lambda: not app.busy)
        self.assertTrue(app.connected, app.detail.get())
        self.assertEqual(app.status, 0)
        self.assertIn(app.board, expected)
        self.assertTrue(app.can_move())


if __name__ == '__main__':
    unittest.main()
