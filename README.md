# chisel-empty

An almost empty chisel project (and adder) as a starting point for hardware design.

To generate Verilog code for the adder execute:
```bash
make
```

Run the tests with:
```bash
make test
```

Cleanup the repository with:
```bash
make clean
```

# Find USB port for FPGA
```bash
ls -l /dev/serial/by-id/
```

## King-and-pawn move generator

`src/main/scala/MoveGenerator.scala` generates one legal move at a time.
It supports kings, pawn single/double steps and captures, and queens so play can
continue after promotion. It checks king safety, including adjacent kings, pawn
attacks, and blocked queen lines. Castling, en passant, and underpromotion are
not implemented. Positions must contain one king per side and only kings,
pawns, and queens.

Squares are numbered in row order: `0 = a8`, `7 = h8`, `56 = a1`, `63 = h1`.
White pawns move towards row 0; black pawns move towards row 7.
The board uses the existing piece IDs from `pieces` and 16-bit squares to match
minimax. A move is a 12-bit number: `(fromSquare << 6) | toSquare`.
Minimax automatically replaces a pawn reaching the last rank with a queen;
the move encoding needs no extra promotion bits.

The cursor is a bookmark in the 4096 possible from/to pairs, not a list stored
in memory. Start at `0`. After finding candidate `n`, the generator returns the
move and `nextCursor = n + 1`. Cursor `4096` marks the end. Save the next cursor
when exploring a move, then supply it again when returning to that position.

Request sequence:

1. Set `board`, `whiteTurn`, and `cursor`, then hold `request` high.
2. Keep these inputs unchanged while the generator scans candidates over clock
   cycles. It skips starting squares without a supported friendly piece.
3. Wait for `moveValid` (one legal move) or `moveFinished` (no remaining moves).
   `inCheck` describes the supplied board and distinguishes checkmate from
   stalemate when there were no legal moves at all.
4. Read the result and lower `request` for at least one clock cycle before the
   next request. Results remain available while `request` stays high.

Connect it to minimax as follows:

```scala
val search = Module(new minimax(maxDepth = 2))
val generator = Module(new MoveGenerator)

generator.io.request := search.io.moveRequest
generator.io.board := search.io.moveBoard
generator.io.whiteTurn := search.io.moveWhiteTurn
generator.io.cursor := search.io.moveCursor

search.io.generatedMove := generator.io.generatedMove
search.io.nextCursor := generator.io.nextCursor
search.io.moveValid := generator.io.moveValid
search.io.moveFinished := generator.io.moveFinished
search.io.inCheck := generator.io.inCheck
```

The caller also supplies the search's initial board, starting side, and `start`
signal. `Top.scala` is still the UART/board demo; integration into that game loop
is a separate step. `KingPawnSearchHarness` in the tests connects the real
generator and minimax and checks promotion and backtracking at depth 2.

Run the generator and search tests with:

```bash
sbt "testOnly MoveGeneratorTest MoveGeneratorMinimaxTest minimaxTest"
```
