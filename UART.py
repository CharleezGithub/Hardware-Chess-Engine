"""Play the king-and-pawn MVP against the FPGA or the real RTL simulator."""
import argparse
import os
from pathlib import Path
import queue
import shutil
import subprocess
import threading
import time

ROOT = Path(__file__).resolve().parent
PIECES = {0: ".", 1: "P", 5: "Q", 6: "K", 7: "p", 11: "q", 12: "k"}
STATUS = {0: "Your turn (white).", 2: "Checkmate: white wins.",
          3: "Checkmate: black wins.", 4: "Draw (stalemate or bare kings).",
          5: "Illegal move. Try again.", 6: "Invalid command or checksum."}


def square(name):
    if len(name) != 2 or name[0] not in "abcdefgh" or name[1] not in "12345678":
        raise ValueError("Use squares such as e2 and e4.")
    return (8 - int(name[1])) * 8 + ord(name[0]) - ord("a")


def parse_move(text):
    text = text.lower().replace(" ", "").replace("-", "")
    if len(text) != 4:
        raise ValueError("Enter a move such as e2e4 or e2 e4.")
    return square(text[:2]), square(text[2:])


def command_frame(command, start=0, end=0):
    return bytes((0xAA, command, start, end, command ^ start ^ end))


def decode_response(frame):
    if len(frame) != 35 or frame[0] != 0x55:
        raise ValueError("Invalid response frame")
    checksum = 0
    for byte in frame[1:-1]:
        checksum ^= byte
    if checksum != frame[-1] or frame[1] not in STATUS:
        raise ValueError("Invalid response checksum/status")
    board = [piece for byte in frame[2:34] for piece in (byte >> 4, byte & 15)]
    if any(piece not in PIECES for piece in board):
        raise ValueError("Unexpected piece in response")
    return frame[1], board


def board_to_string(board):
    rows = [f"{8 - row}  " + " ".join(PIECES[p] for p in board[row * 8:row * 8 + 8])
            for row in range(8)]
    return "\n".join(rows + ["   a b c d e f g h"])


class SerialTransport:
    def __init__(self, port, baud, timeout):
        import serial
        self.port = serial.Serial(port, baud, timeout=0.1, write_timeout=2)
        self.timeout = timeout
        self.buffer = bytearray()

    def exchange(self, frame):
        self.port.write(frame)
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            self.buffer.extend(self.port.read(max(1, self.port.in_waiting)))
            while self.buffer:
                if self.buffer[0] != 0x55:
                    del self.buffer[0]
                    continue
                if len(self.buffer) < 35:
                    break
                candidate = bytes(self.buffer[:35])
                try:
                    response = decode_response(candidate)
                except ValueError:
                    del self.buffer[0]
                    continue
                del self.buffer[:35]
                return response
        raise TimeoutError("No valid FPGA reply. Check the bitstream, port and baud rate; use 'board' to resync.")

    def close(self):
        self.port.close()


class SimulationTransport:
    def __init__(self, timeout):
        executable = shutil.which("vvp") or shutil.which("vvp.exe")
        fallback = Path("C:/Tools/oss-cad-suite/bin/vvp.exe")
        if not executable and fallback.exists():
            executable = str(fallback)
        image = ROOT / "generated/sim/interactive.vvp"
        if not executable or not image.exists():
            raise RuntimeError("Run 'python scripts/test_mvp.py' first; Icarus Verilog (iverilog/vvp) is required.")
        env = os.environ.copy()
        env["PATH"] = str(Path(executable).resolve().parent.parent / "lib") + os.pathsep + env["PATH"]
        self.process = subprocess.Popen([executable, str(image)], stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1, cwd=ROOT, env=env)
        self.responses = queue.Queue()
        self.timeout = timeout
        self.messages = []

        def read_output():
            for line in self.process.stdout:
                if line.startswith("FRAME "):
                    self.responses.put(bytes.fromhex(line[6:].strip()))
                else:
                    self.messages.append(line.strip())
            self.responses.put(None)
        self.reader = threading.Thread(target=read_output, daemon=True)
        self.reader.start()

    def exchange(self, frame):
        self.process.stdin.write(" ".join(f"{byte:02x}" for byte in frame) + "\n")
        self.process.stdin.flush()
        try:
            response = self.responses.get(timeout=self.timeout)
        except queue.Empty as exc:
            raise TimeoutError("RTL simulator did not reply in time") from exc
        if response is None:
            raise RuntimeError("RTL simulator exited: " + " ".join(self.messages[-5:]))
        return decode_response(response)

    def close(self):
        if self.process.stdin:
            self.process.stdin.close()
        try:
            self.process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self.process.terminate()
            self.process.wait(timeout=2)
        self.reader.join(timeout=2)
        self.process.stdout.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    connection = parser.add_mutually_exclusive_group(required=True)
    connection.add_argument("--port", help="Serial port, e.g. COM3 or /dev/ttyUSB1")
    connection.add_argument("--simulate", action="store_true", help="Play against the actual Verilog engine locally")
    parser.add_argument("--baud", type=int, default=115200)
    parser.add_argument("--timeout", type=float, default=30)
    args = parser.parse_args()
    transport = None
    try:
        transport = SimulationTransport(args.timeout) if args.simulate else SerialTransport(args.port, args.baud, args.timeout)
        status, board = transport.exchange(command_frame(1))
        print("White: uppercase. Black: lowercase. Queens appear automatically on promotion.")
        print("Commands: e2e4, new, board, quit. Castling and en passant are omitted.")
        while True:
            print("\n" + board_to_string(board))
            print(STATUS[status])
            text = input("> ").strip().lower()
            if text in ("quit", "exit", "q"):
                break
            try:
                if text == "new":
                    frame = command_frame(1)
                elif text == "board":
                    frame = command_frame(2)
                else:
                    if status in (2, 3, 4):
                        print("Game over. Enter 'new' to play again.")
                        continue
                    start, end = parse_move(text)
                    frame = command_frame(3, start, end)
                status, board = transport.exchange(frame)
            except (ValueError, TimeoutError) as exc:
                print(exc)
    except (KeyboardInterrupt, EOFError):
        print("\nGoodbye.")
    except (OSError, RuntimeError, ImportError) as exc:
        parser.exit(1, f"{exc}\n")
    finally:
        if transport:
            transport.close()


if __name__ == "__main__":
    main()
