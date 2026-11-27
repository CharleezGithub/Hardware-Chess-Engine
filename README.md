# Hardware chess engine

Human plays white and the engine plays black from the standard 32-piece
starting position. All six piece types can move, capture and give check. The
FPGA uses depth-3 minimax; local RTL simulation uses depth 4 with alpha-beta
pruning. Pawns automatically promote to queens.

The hardware owns the board and validates every human move. It handles pawn
single/double moves and captures, knight jumps, bishop/rook/queen sliding moves
with blockers, king safety, checkmate, stalemate, bare-king draws, promotion,
and new games. Castling, en passant, underpromotion, repetition and the
fifty-move rule remain outside the current implementation.

## Play locally against the actual RTL

Requirements: Java and sbt (the versions in `build.sbt`/`project/build.properties`),
Python 3.9+, and Icarus Verilog (`iverilog` and `vvp` on PATH). On Windows the
test runner also recognizes `C:/Tools/oss-cad-suite`.

```bash
python -m pip install -r requirements.txt
python scripts/test_mvp.py
python UART.py --simulate --timeout 600
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
python chess_gui.py --timeout 600
```

Click a white piece and its destination, or drag and drop it. The engine plays
black. The board highlights changed squares after each turn and reports illegal
moves and game results in the window title. Right-click the board for **New game**,
**Refresh board**, **Flip board**, or **Reconnect**; Escape clears the selection.
The window shows only the board and coordinates. UART does not currently send
an evaluation score, so no score is displayed.

The window imports the shared transport interface from `UART.py`, backed by
`engine_transport.py`. Running `UART.py` without arguments retains the serial
CLI and its custom-board loading helpers. All move
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
- Depth-2 alpha-beta results against unpruned minimax, with cycle savings and
  complete board restoration checked on the same positions.
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
one `MoveGenerator` with minimax. `Top.scala` connects the controller and
UART transport. `UartTop` handles the current serial protocol. The old `Board`
module remains a standalone exercise and is not used in the playable top.

Squares use row order: `0 = a8`, `7 = h8`, `56 = a1`, `63 = h1`.
Piece IDs are `0` empty; `1/2/3/4/5/6` white pawn/bishop/knight/rook/queen/king;
`7/8/9/10/11/12` the corresponding black pieces. UART and search use four bits
per square.
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
Request (5 bytes):  FF command from to checksum
  command: 01 new game, 02 read board, 03 move, 04 set square
  set square: from = square index, to = piece ID
  checksum: command XOR from XOR to

Reply (33 bytes):   board[32] status
  each board byte packs two squares: earlier square in the high nibble
  status: 00 white to move, 02 white wins, 03 black wins, 04 draw,
          05 illegal move, 06 invalid command/checksum
```

Replies have no header or checksum; keep serial requests and replies aligned.

Both sides apply promotion automatically; there is no promotion-choice field.
The reply after a legal human move contains the board after the engine's reply,
or the final board if the game ended before the engine could move.


## Chess engine results in different depth

The on-board FPGA build utilization is determined by Vivado, and the listed values provide a performance overview when generating a more complex search tree for the chess engine. Execution time is measured using the Python `time` module, starting right before packaging the move for UART transmission and stopping when `UART.py` receives the response frame. All time measurements are taken with a target clock of **32 MHz** ($T_{\text{clk}} = 31.25\text{ ns}$).

The maximum operating frequency ($F_{\max}$) is calculated from the target clock period and Worst Negative Slack (WNS):

$$F_{\max} = \frac{1}{T_{\text{clk}} - \text{WNS}} = \frac{1000}{31.25 - \text{WNS}}\text{ MHz}$$

**Available FPGA Resources:** 20,800 LUTs | 41,600 FFs | 106 I/O | 32 BUFG | 5 MMCM

| Search Depth | LUTs (Used / %) | Flip-Flops (Used / %) | I/O | BUFG | MMCM | WNS (ns) | $F_{\max}$ (MHz) | Response Time / Notes |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1** | 10,796 (51.90%) | 1,446 (3.48%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.838 | 34.00 | 0.004 – 0.016 s per move |
| **2** | 11,285 (54.25%) | 1,595 (3.83%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 0.985 | 33.04 | 0.006 – 0.016 s per move |
| **3** | 11,052 (53.13%) | 1,749 (4.20%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.069 | 33.13 | 0.005 – 0.021 s per move |
| **4** | 11,014 (52.95%) | 1,906 (4.58%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 0.733 | 32.77 | 0.02 – 0.041 s per move |
| **5** | 10,859 (52.21%) | 2,070 (4.98%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.078 | 33.14 | 0.061 – 1.318 s per move |
| **6** | 11,210 (53.89%) | 2,229 (5.36%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.664 | 33.80 | 0.568 – 8.071 s per move |
| **7** | 11,719 (56.34%) | 2,382 (5.73%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.565 | 33.69 | 5.025 – 55.117 s per move |
| **8** | 11,046 (53.11%) | 2,537 (6.10%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.026 | 33.09 | 62.748 – 737.251 s per move |
| **9** | 12,038 (57.88%) | 2,687 (6.46%) | 21 (19.81%) | 2 (6.25%) | 1 (20%) | 1.297 | 33.39 | 408.026 – 5945.780 s per move |
The time estimations are based on a sequence of several moves rather than a single measurement. Opening moves are significantly faster because major pieces like the rook and queen are blocked, resulting in a much narrower search tree at the beginning of the game. Because the branching factor widens as pieces develop, a min–max time range is reported across the following fixed move sequence:

1. Pawn `d2` to `d4`
2. Queen `d1` to `d3`
3. Bishop `c1` to `f4`
4. Knight `b1` to `a3`
5. Pawn `g2` to `g3`
6. Bishop `f1` to `h3`

### Analysis & Optimal Depth Selection
In a normal game of chess, each player is given a clock counting down from 10 minutes. Meaning each player has a total of 10 minutes allocated of thinking. Therefore a chess engine of higher depth, for example 9, will able to compute higher quality moves, but lose on time after a few moves. Therefore the response time is a very relevent measurement for the chess engine's perfomance, and needs to be within a reasonable range that doesnt not result in the engine losing in a time out, but still can compute good quality.
At a depth of 7, computation time can last up to nearly 1 minute, while at a depth of 6 it shouldn't exceed 10 seconds. Therefor the most optimal depth of our chess engine is 6.



## Use of Ai 
Generative Ai has been used for this project, mainly as code helper and advisor.
### Python/GUI code assistance
For example the `UART.py` was initially coded completely by hand and would let the user play chess with the FPGA board through the terminal. But `UART.py` did include a lot of spelling errors in the print statement, and the code was nearly unreadable, so AI was used to check for spelling errors and increasing the readability of the code, while the core logic of the python script remained the same. After this script was implemented, we wanted to be able to play chess with a graphical user interface (GUI) instead of the terminal, but since making a GUI is not the main emphasis of the project, we used generative AI to make the `chess_gui.py` script, by using the `UART.py`.
### UART protocol design advice
As previously mentioned, generative AI was used as an advisor, as an example is the Uart Receive logic. It was designed to receive a command and a chess move, in a from-to format. The initial commands only included reset board, get board and move, but we also wanted to be able to load in a board state through the UART, in which we cant load in an entire board with the from-to UART receiver format used. Instead of rewriting the entire UART receiver protocol, we asked generative AI how to implement this with our current design, and it suggested implementing a simple set piece command, where we then can call set piece for each chess square, to load in an entire chess board with the UART.