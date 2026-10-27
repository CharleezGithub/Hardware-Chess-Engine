import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "scripts"))
import UART
from move_bench import legal_moves


def initial_board():
    board = [0] * 64
    board[4], board[60] = 12, 6
    board[8:16] = [7] * 8
    board[48:56] = [1] * 8
    return board


def response_frame(board, status=0):
    payload = bytes([status] + [(board[n] << 4) | board[n + 1] for n in range(0, 64, 2)])
    checksum = 0
    for byte in payload:
        checksum ^= byte
    return bytes([0x55]) + payload + bytes([checksum])


def play(board, move):
    board = board.copy()
    start, end = move >> 6, move & 63
    piece = board[start]
    board[start] = 0
    board[end] = 5 if piece == 1 and end < 8 else 11 if piece == 7 and end >= 56 else piece
    return board


class ProtocolTests(unittest.TestCase):
    def test_square_mapping_and_input(self):
        self.assertEqual(UART.parse_move("e2 e4"), (52, 36))
        self.assertEqual(UART.square("a8"), 0)
        self.assertEqual(UART.square("h1"), 63)
        for text in ("a9a8", "i2i4", "e2", "", "e0e4"):
            with self.assertRaises(ValueError):
                UART.parse_move(text)

    def test_frames_and_corruption(self):
        self.assertEqual(UART.command_frame(3, 52, 36), bytes([170, 3, 52, 36, 19]))
        frame = response_frame(initial_board())
        self.assertEqual(UART.decode_response(frame), (0, initial_board()))
        with self.assertRaises(ValueError):
            UART.decode_response(frame[:-1] + bytes([frame[-1] ^ 1]))
        with self.assertRaises(ValueError):
            UART.decode_response(frame[:-1])

    def test_serial_receiver_resynchronizes(self):
        good = response_frame(initial_board())
        broken = good[:-1] + bytes([good[-1] ^ 1])

        class FakePort:
            def __init__(self):
                self.data = bytearray(b"noise" + broken + good)
                self.sent = None
            @property
            def in_waiting(self):
                return min(3, len(self.data))
            def read(self, count):
                chunk = bytes(self.data[:count])
                del self.data[:count]
                return chunk
            def write(self, frame):
                self.sent = frame

        transport = object.__new__(UART.SerialTransport)
        transport.port = FakePort()
        transport.buffer = bytearray()
        transport.timeout = 1
        self.assertEqual(transport.exchange(UART.command_frame(2)), (0, initial_board()))


class ActualRTLTests(unittest.TestCase):
    def test_client_and_consecutive_legal_engine_replies(self):
        transport = UART.SimulationTransport(30)
        self.addCleanup(transport.close)
        status, board = transport.exchange(UART.command_frame(1))
        self.assertEqual((status, board), (0, initial_board()))
        for start, end in ((52, 36), (54, 38)):
            human_board = play(board, (start << 6) | end)
            expected_replies = [play(human_board, move) for move in legal_moves(human_board, False)]
            status, board = transport.exchange(UART.command_frame(3, start, end))
            self.assertEqual(status, 0)
            self.assertIn(board, expected_replies)
        self.assertEqual(transport.exchange(UART.command_frame(3, 52, 36)), (5, board))
        self.assertEqual(transport.exchange(UART.command_frame(2)), (0, board))
        self.assertEqual(transport.exchange(UART.command_frame(1)), (0, initial_board()))

    def test_terminal_client_is_playable(self):
        result = subprocess.run([sys.executable, str(ROOT / "UART.py"), "--simulate"],
            input="e2e4\nboard\nquit\n", text=True, capture_output=True, timeout=40, cwd=ROOT)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertGreaterEqual(result.stdout.count("Your turn (white)."), 3)
        self.assertIn("a b c d e f g h", result.stdout)


if __name__ == "__main__":
    unittest.main()
