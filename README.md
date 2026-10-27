# Hardware chess MVP

A playable king-and-pawn game: human plays white, the FPGA plays black with a
depth-2 minimax search. Each side starts with a king and eight pawns. Pawns
automatically promote to queens, and promoted queens can move and give check.

The hardware owns the board and validates every human move. It handles pawn
single/double moves and captures, king safety, checkmate, stalemate, bare-king
draws, promotion, and new games. Castling, en passant, underpromotion,
repetition and the fifty-move rule are outside this MVP.

## Play locally against the actual RTL

Requirements: Java and sbt (the versions in `build.sbt`/`project/build.properties`),
Python 3.9+, and Icarus Verilog (`iverilog` and `vvp` on PATH). On Windows the
test runner also recognizes `C:/Tools/oss-cad-suite`.

```bash
python -m pip install -r requirements.txt
python scripts/test_mvp.py
python UART.py --simulate
```

The first command installs the serial client dependency. The test runner
compiles Scala and emits all RTL in one batch, then runs native simulations and
client tests. It also creates `generated/sim/interactive.vvp`, used by
`--simulate`. This mode runs the real Top, UART receiver/transmitter, move
generator and minimax; it is not a separate Python chess engine.

Enter moves such as `e2e4` or `e2 e4`. Other commands: `board`, `new`, `quit`.
Uppercase pieces are white, lowercase pieces are black. Illegal moves leave
the board unchanged. After game over, enter `new` to restart.

## Play with a visual board

After the setup above, launch the Python window:

```bash
python chess_gui.py
```

Click a white piece and its destination, or drag and drop it. The engine plays
black. The board highlights changed squares after each turn and reports illegal
moves and game results in the window title. Right-click the board for **New game**,
**Refresh board**, **Flip board**, or **Reconnect**; Escape clears the selection.
The window shows only the board and coordinates. UART does not currently send
an evaluation score, so no score is displayed.

The window imports the existing CLI's transport from `UART.py`. All move
validation, promotion, and engine play still run in the hardware RTL. Requests
run in a background thread so the window stays responsive during a search.
Simulation is the default; `--simulate` can also be supplied explicitly.

To play on a programmed FPGA instead:

```bash
python chess_gui.py --port COM3
```

Close any CLI using that serial port first. Opening the window starts a new game.
Reconnect reads the current FPGA board; reconnecting the local simulator starts
a fresh simulation. The GUI uses Tkinter (included with standard Windows Python;
on Linux, install your distribution's `python3-tk` package). No extra Python
packages are required for the window.

## Play on Basys 3

The native test command also generates `generated/fpga/Top.v`. In a shell with
Vivado available, build and program the FPGA:

```bash
vivado -mode batch -source fpga/build.tcl
vivado -mode batch -source fpga/program.tcl
python UART.py --port COM3
```

Replace `COM3` with the board's serial port (`/dev/ttyUSB1`, for example, on
Linux). The baud rate is 115200, 8N1. Use `python -m serial.tools.list_ports` to
list connected ports. The client starts a new game when it connects.

On this Windows setup, Vivado is at
`C:/Xilinx/Vivado/2024.2/bin/vivado.bat` if it is not on PATH.
The build creates `build/fpga/chess.bit`, a routed checkpoint, utilization and
timing reports. It fails on negative setup or hold slack. Programming requires
a connected Basys 3; simulation does not.

`fpga/BasysTop.sv` is the synthesis top. Its MMCM derives a 20 MHz engine clock
from the board's 100 MHz oscillator and synchronizes reset release. Do not wire
the default generated `Top` directly to 100 MHz: its UART is configured for the
20 MHz clock. The existing `basys3.xdc` supplies the pins and oscillator timing.
Switch 15 resets the hardware. Button 0 queues a new game. LEDs 0–2 show game
status, LED 8 shows controller activity, and LED 9 shows UART transmission.
Use `new` to restart while the client is open. After a physical reset, restart
the client to begin a fresh serial session.

## Verification

```bash
python scripts/test_mvp.py
```

This is the fast, bundled MVP check. It covers:

- Complete move enumeration against an independent software rules oracle.
- Mate at the search horizon, promotion for both colors, captures and undo.
- Real UART bits: framing, checksums, truncated-packet recovery and illegal moves.
- Consecutive human/engine turns, both winners, stalemate, bare kings and reset.
- The real Python client and terminal UI playing against the UART RTL simulator.
- Visual-board clicks, dragging, connection recovery, and a full RTL engine turn
  (GUI tests are skipped if no graphical display is available).

`--skip-compile` reuses already emitted RTL and is only appropriate when it
matches the current Scala sources. `sbt test` remains available for the older
ChiselTest suites, but the interpreted simulator can be very slow for complete
searches; use the native runner for routine MVP verification.

## Code and interfaces

`GameController.scala` owns the committed board, validates white moves, runs
black's search, applies the chosen move and reports the game state. It shares
one `MoveGenerator` with minimax. `Top.scala` connects the controller and framed
UART transport. The old `Board`/`UartTop` modules remain as standalone exercises;
they are not used in the playable top.

Squares use row order: `0 = a8`, `7 = h8`, `56 = a1`, `63 = h1`.
Piece IDs are `0` empty; `1/5/6` white pawn/queen/king; `7/11/12` black
pawn/queen/king. UART uses four bits per square; the search interface uses 16.
A move is `(fromSquare << 6) | toSquare`, a 12-bit unsigned number.

The generator's 13-bit cursor is a bookmark among 4096 candidate from/to pairs.
It scans from `cursor`, returns a legal `generatedMove` and `nextCursor`, or
asserts `moveFinished` at 4096. `inCheck` describes the supplied board.
Keep board, side and cursor stable while `request` is high. Read the result
when `moveValid` or `moveFinished` is high, then lower `request` for a clock.
Minimax saves a separate cursor at each depth and asserts `moveRequest` while
waiting, including terminal-position checks at the search limit.

UART uses stop-and-wait requests. Send one packet, then read its reply before
sending another. A packet interrupted for 100 ms is discarded.

```text
Request (5 bytes):  AA command from to checksum
  command: 01 new game, 02 read board, 03 move
  checksum: command XOR from XOR to

Reply (35 bytes):   55 status board[32] checksum
  each board byte packs two squares: earlier square in the high nibble
  checksum: XOR of status and all 32 board bytes
  status: 00 white to move, 02 white wins, 03 black wins, 04 draw,
          05 illegal move, 06 invalid command/checksum
```

Both sides apply promotion automatically; there is no promotion-choice field.
The reply after a legal human move contains the board after the engine's reply,
or the final board if the game ended before the engine could move.
