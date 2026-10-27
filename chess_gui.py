"""Visual board for the existing UART CLI backend. No chess rules are duplicated."""
import argparse
import queue
import threading
import tkinter as tk
from tkinter import font as tkfont

from UART import SerialTransport, SimulationTransport, STATUS, command_frame

WHITE = (1, 5, 6)
GLYPHS = {1: "♟", 5: "♛", 6: "♚", 7: "♟", 11: "♛", 12: "♚"}
BG, PANEL, TEXT, MUTED = "#112128", "#1b3038", "#f0f1e9", "#a7bbc0"
LIGHT, DARK, ACCENT = "#e7e4d1", "#729286", "#89d6b7"


def square_name(square):
    return chr(ord("a") + square % 8) + str(8 - square // 8)


class EngineWorker:
    """Serialize CLI transport calls off the Tk thread; closing cancels pending I/O."""
    def __init__(self, factory):
        self.commands = queue.Queue()
        self.results = queue.Queue()
        self.stopped = threading.Event()
        self.lock = threading.Lock()
        self.transport = None
        self.closer = None
        self.thread = threading.Thread(target=self._run, args=(factory,), name="chess-engine")
        self.thread.start()

    def _run(self, factory):
        try:
            transport = factory()
            with self.lock:
                self.transport = transport
            while not self.stopped.is_set():
                command = self.commands.get()
                if command is None or self.stopped.is_set():
                    break
                response = transport.exchange(command_frame(*command))
                self.results.put((command, response, None))
        except Exception as exc:
            if not self.stopped.is_set():
                self.results.put((None, None, str(exc)))
        finally:
            self._close_transport()

    def _close_transport(self):
        with self.lock:
            transport, self.transport = self.transport, None
        if transport is not None:
            try:
                transport.close()
            except Exception:
                pass  # Closing a disconnected port/process is best-effort.

    def stop(self):
        self.stopped.set()
        self.commands.put(None)
        if self.closer is None:
            self.closer = threading.Thread(target=self._close_transport, name="chess-close")
            self.closer.start()

    def alive(self):
        return self.thread.is_alive() or (self.closer is not None and self.closer.is_alive())


class ChessWindow:
    def __init__(self, root, transport_factory, connection="RTL simulation"):
        self.root, self.factory = root, transport_factory
        self.board = [0] * 64
        self.status = None
        self.connected = self.busy = self.closed = self.reconnecting = False
        self.flipped = False
        self.selected = self.press_origin = self.ghost = self.pending_move = None
        self.dragging = False
        self.changed = set()
        families = tkfont.families(root)
        self.piece_font = "Segoe UI Symbol" if "Segoe UI Symbol" in families else "DejaVu Sans"

        root.title("Hardware Chess")
        root.configure(bg=BG)
        root.geometry("980x760")
        root.minsize(760, 600)
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.bind("<Escape>", lambda _event: self.clear_selection())

        header = tk.Frame(root, bg=BG)
        header.pack(fill="x", padx=30, pady=(24, 12))
        tk.Label(header, text="HARDWARE CHESS", bg=BG, fg=TEXT,
                 font=("Segoe UI", 21, "bold")).pack(anchor="w")
        tk.Label(header, text="Kings & pawns  /  automatic queen promotion", bg=BG,
                 fg=MUTED, font=("Segoe UI", 11)).pack(anchor="w", pady=(4, 0))

        body = tk.Frame(root, bg=BG)
        body.pack(fill="both", expand=True, padx=(14, 26), pady=(0, 18))
        self.canvas = tk.Canvas(body, bg=BG, highlightthickness=0)
        self.canvas.pack(side="left", fill="both", expand=True)
        self.canvas.bind("<Configure>", lambda _event: self.draw())
        self.canvas.bind("<ButtonPress-1>", self.on_press)
        self.canvas.bind("<B1-Motion>", self.on_drag)
        self.canvas.bind("<ButtonRelease-1>", self.on_release)

        sidebar = tk.Frame(body, bg=PANEL, width=230)
        sidebar.pack(side="right", fill="y", padx=(12, 0), pady=24)
        sidebar.pack_propagate(False)
        tk.Label(sidebar, text=connection, bg=PANEL, fg=ACCENT,
                 font=("Segoe UI", 10, "bold"), wraplength=198).pack(anchor="w", padx=18, pady=(22, 20))
        tk.Label(sidebar, text="YOU  ·  WHITE\nENGINE  ·  BLACK", justify="left",
                 bg=PANEL, fg=TEXT, font=("Segoe UI", 11, "bold")).pack(anchor="w", padx=18)
        self.message = tk.StringVar(value="Connecting…")
        self.status_label = tk.Label(sidebar, textvariable=self.message, wraplength=194,
            justify="left", bg=PANEL, fg=TEXT, font=("Segoe UI", 13, "bold"))
        self.status_label.pack(fill="x", padx=18, pady=(26, 12))
        self.detail = tk.StringVar(value="The same hardware engine as the CLI.")
        tk.Label(sidebar, textvariable=self.detail, wraplength=194, justify="left",
                 bg=PANEL, fg=MUTED, font=("Segoe UI", 10)).pack(fill="x", padx=18, pady=(0, 22))

        self.new_button = self._button(sidebar, "New game", lambda: self.submit(1))
        self.refresh_button = self._button(sidebar, "Refresh board", lambda: self.submit(2))
        self.flip_button = self._button(sidebar, "Flip board", self.flip)
        self.reconnect_button = self._button(sidebar, "Reconnect", self.reconnect)
        tk.Label(sidebar, text="Click a white piece, then its destination.\n\nOr drag and drop.\n\nEsc clears selection.",
                 wraplength=194, justify="left", bg=PANEL, fg=MUTED,
                 font=("Segoe UI", 10)).pack(side="bottom", padx=18, pady=22)

        self.worker = EngineWorker(self.factory)
        self.submit(1, connecting=True)
        root.after(40, self.poll)

    def _button(self, parent, text, command):
        button = tk.Button(parent, text=text, command=command, bg="#29434b", fg=TEXT,
            activebackground="#385c63", activeforeground=TEXT, disabledforeground="#718b91",
            relief="flat", bd=0, cursor="hand2", font=("Segoe UI", 11), pady=9)
        button.pack(fill="x", padx=18, pady=4)
        return button

    def layout(self):
        width, height = max(320, self.canvas.winfo_width()), max(320, self.canvas.winfo_height())
        cell = max(1, (min(width, height) - 52) // 8)
        return (width - cell * 8) / 2, (height - cell * 8) / 2, cell

    def square_at(self, x, y):
        left, top, cell = self.layout()
        col, row = int((x - left) // cell), int((y - top) // cell)
        if not (0 <= row < 8 and 0 <= col < 8):
            return None
        square = row * 8 + col
        return 63 - square if self.flipped else square

    def center(self, square):
        left, top, cell = self.layout()
        display = 63 - square if self.flipped else square
        return left + (display % 8 + 0.5) * cell, top + (display // 8 + 0.5) * cell

    def draw_piece(self, piece, x, y, cell):
        # Solid chess glyphs with contrasting outlines work on either square color.
        fill, outline = ("#fcfaf0", "#233a40") if piece in WHITE else ("#182b32", "#bdcec5")
        font = (self.piece_font, -int(cell * 0.78))
        for dx, dy in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
            self.canvas.create_text(x + dx, y + dy, text=GLYPHS[piece], font=font, fill=outline)
        self.canvas.create_text(x, y, text=GLYPHS[piece], font=font, fill=fill)

    def draw(self):
        if self.closed:
            return
        self.canvas.delete("all")
        left, top, cell = self.layout()
        for display in range(64):
            row, col = divmod(display, 8)
            square = 63 - display if self.flipped else display
            x, y = left + col * cell, top + row * cell
            color = LIGHT if (row + col) % 2 == 0 else DARK
            if square in self.changed:
                color = "#e8d99c" if (row + col) % 2 == 0 else "#a6aa72"
            self.canvas.create_rectangle(x, y, x + cell, y + cell, fill=color, outline="")
            if square == self.selected:
                self.canvas.create_rectangle(x + 2, y + 2, x + cell - 2, y + cell - 2,
                    outline="#f9ca58", width=4)
            if self.pending_move and square in self.pending_move:
                self.canvas.create_rectangle(x + 4, y + 4, x + cell - 4, y + cell - 4,
                    outline="#205d79", width=3, dash=(5, 3))
            piece = self.board[square]
            if piece and not (self.dragging and square == self.selected):
                self.draw_piece(piece, x + cell / 2, y + cell / 2, cell)
        for i in range(8):
            file = chr(ord("h") - i) if self.flipped else chr(ord("a") + i)
            rank = str(i + 1) if self.flipped else str(8 - i)
            self.canvas.create_text(left + (i + 0.5) * cell, top + 8 * cell + 17,
                text=file, fill=MUTED, font=("Segoe UI", 10))
            self.canvas.create_text(left - 17, top + (i + 0.5) * cell,
                text=rank, fill=MUTED, font=("Segoe UI", 10))
        if self.dragging and self.ghost is not None and self.selected is not None:
            self.draw_piece(self.board[self.selected], *self.ghost, cell)

    def can_move(self):
        return self.connected and not self.busy and self.status in (0, 5, 6)

    def on_press(self, event):
        if not self.can_move():
            return
        target = self.square_at(event.x, event.y)
        if target is None:
            self.clear_selection()
        elif self.selected is not None and target != self.selected and self.board[target] not in WHITE:
            self.submit(3, self.selected, target)
        elif self.board[target] in WHITE:
            self.selected = target
            self.press_origin = (event.x, event.y)
            self.draw()
        else:
            self.clear_selection()

    def on_drag(self, event):
        if not self.can_move() or self.selected is None or self.press_origin is None:
            return
        dx, dy = event.x - self.press_origin[0], event.y - self.press_origin[1]
        if dx * dx + dy * dy > 36:
            self.dragging = True
            self.ghost = (event.x, event.y)
            self.draw()

    def on_release(self, event):
        if not self.can_move() or self.press_origin is None:
            return
        source, target, dragged = self.selected, self.square_at(event.x, event.y), self.dragging
        self.press_origin = self.ghost = None
        self.dragging = False
        if dragged and target is not None and target != source:
            self.submit(3, source, target)
        else:
            self.draw()

    def clear_selection(self):
        self.selected = self.press_origin = self.ghost = None
        self.dragging = False
        self.draw()

    def flip(self):
        self.flipped = not self.flipped
        self.clear_selection()

    def update_buttons(self):
        ready = self.connected and not self.busy
        for button in (self.new_button, self.refresh_button):
            button.configure(state="normal" if ready else "disabled")
        self.flip_button.configure(state="normal" if not self.busy else "disabled")
        self.reconnect_button.configure(state="disabled" if self.reconnecting else "normal")

    def submit(self, command, source=0, target=0, connecting=False):
        if self.closed or self.busy or (not connecting and not self.connected):
            return
        if command == 3 and not self.can_move():
            return
        self.busy = True
        self.clear_selection()
        self.pending_move = (source, target) if command == 3 else None
        self.message.set({1: "Starting new game…", 2: "Reading board…", 3: "Engine thinking…"}[command])
        self.status_label.configure(fg=TEXT)
        if command == 3:
            self.detail.set(f"{square_name(source)} → {square_name(target)}\nWaiting for the engine's reply.")
        self.update_buttons()
        self.draw()
        self.worker.commands.put((command, source, target))

    def poll(self):
        if self.closed:
            return
        if not self.reconnecting:
            try:
                command, response, error = self.worker.results.get_nowait()
            except queue.Empty:
                pass
            else:
                self.busy = False
                self.pending_move = None
                if error:
                    self.connected = False
                    self.message.set("Connection interrupted")
                    self.detail.set(error + "\n\nUse Reconnect to try again.")
                    self.status_label.configure(fg="#f2b28d")
                else:
                    self.connected = True
                    self.status, board = response
                    self.changed = {n for n in range(64) if board[n] != self.board[n]} if command[0] == 3 else set()
                    self.board = list(board)
                    self.message.set(STATUS[self.status])
                    self.status_label.configure(fg="#f2b28d" if self.status in (5, 6) else TEXT)
                    if command[0] == 3:
                        self.detail.set("Move rejected; the board is unchanged." if self.status == 5 else
                            f"Played {square_name(command[1])} → {square_name(command[2])}.\nHighlighted squares show what changed.")
                    else:
                        self.detail.set("Select a white piece to play." if self.status == 0 else "Choose New game to play again.")
                self.update_buttons()
                self.draw()
        self.root.after(40, self.poll)

    def reconnect(self):
        if self.closed or self.reconnecting:
            return
        self.reconnecting = self.busy = True
        self.connected = False
        self.pending_move = None
        self.clear_selection()
        self.message.set("Reconnecting…")
        self.worker.stop()
        self.update_buttons()
        self.root.after(150, self.finish_reconnect)

    def finish_reconnect(self):
        if self.closed:
            return
        if self.worker.alive():
            self.root.after(50, self.finish_reconnect)
            return
        self.worker = EngineWorker(self.factory)
        self.reconnecting = self.busy = False
        self.submit(2, connecting=True)

    def close(self):
        if self.closed:
            return
        self.closed = True
        self.worker.stop()
        self.root.destroy()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    connection = parser.add_mutually_exclusive_group()
    connection.add_argument("--simulate", action="store_true", help="Use the actual RTL engine (default)")
    connection.add_argument("--port", help="FPGA serial port, e.g. COM3")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--timeout", type=float, default=30)
    args = parser.parse_args()
    factory = (lambda: SerialTransport(args.port, args.baud, args.timeout)) if args.port else (lambda: SimulationTransport(args.timeout))
    label = f"FPGA · {args.port}" if args.port else "RTL simulation"
    root = tk.Tk()
    ChessWindow(root, factory, label)
    root.mainloop()


if __name__ == "__main__":
    main()
