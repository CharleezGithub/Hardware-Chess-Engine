import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

object MoveGeneratorTestData {
    val whitePawn = 1
    val whiteQueen = 5
    val whiteKing = 6
    val blackPawn = 7
    val blackQueen = 11
    val blackKing = 12

    def square(name: String): Int = (8 - name(1).asDigit) * 8 + (name(0) - 'a')
    def move(from: String, to: String): Int = (square(from) << 6) | square(to)
    def position(pieces: (String, Int)*): Seq[Seq[Int]] = {
        val occupied = pieces.map { case (name, piece) => square(name) -> piece }.toMap
        Seq.tabulate(8, 8)((row, col) => occupied.getOrElse(row * 8 + col, 0))
    }
    def load(board: Vec[Vec[UInt]], position: Seq[Seq[Int]]): Unit = {
        // Avoid re-poking unchanged squares when resuming the same position.
        for (row <- 0 until 8; col <- 0 until 8) {
            if (board(row)(col).peek().litValue != position(row)(col)) {
                board(row)(col).poke(position(row)(col).U)
            }
        }
    }
}

class MoveGeneratorTest extends AnyFlatSpec with ChiselScalatestTester {
    import MoveGeneratorTestData._

    def request(dut: MoveGenerator, board: Seq[Seq[Int]], white: Boolean, cursor: Int): Unit = {
        dut.io.request.poke(false.B)
        dut.clock.step()
        load(dut.io.board, board)
        dut.io.whiteTurn.poke(white.B)
        dut.io.cursor.poke(cursor.U)
        dut.io.request.poke(true.B)
        dut.clock.step(2) // Latch the cursor, then check its first candidate.
    }

    def waitForResult(dut: MoveGenerator): Unit = {
        var cycles = 0
        while (!dut.io.moveValid.peek().litToBoolean && !dut.io.moveFinished.peek().litToBoolean && cycles < 4200) {
            dut.clock.step(8)
            cycles += 8
        }
        assert(cycles < 4200, "The generator did not finish scanning")
    }

    "MoveGenerator" should "return every move once, resume its cursor, and hold its answer" in {
        test(new MoveGenerator) { dut =>
            dut.clock.setTimeout(0)
            val board = position("a1" -> whiteKing, "d2" -> whitePawn,
                "h8" -> blackKing, "c3" -> blackPawn, "e3" -> blackPawn)
            val expected = Seq(move("a1", "a2"), move("a1", "b1"),
                move("d2", "d3"), move("d2", "d4"), move("d2", "c3"), move("d2", "e3")).sorted

            // Rotate the position and swap colors to test black's direction too.
            val mirrored = board.reverse.map(_.reverse.map {
                case 0 => 0
                case piece if piece <= 6 => piece + 6
                case piece => piece - 6
            })
            val mirroredMoves = expected.map { m => ((63 - (m >> 6)) << 6) | (63 - (m & 63)) }.sorted

            for ((position, white, moves) <- Seq((board, true, expected), (mirrored, false, mirroredMoves))) {
                var cursor = 0
                for (expectedMove <- moves) {
                    request(dut, position, white, cursor)
                    waitForResult(dut)
                    dut.io.moveValid.expect(true.B)
                    dut.io.moveFinished.expect(false.B)
                    dut.io.generatedMove.expect(expectedMove.U)
                    dut.io.nextCursor.expect((expectedMove + 1).U)
                    dut.clock.step(3)
                    dut.io.generatedMove.expect(expectedMove.U)
                    dut.io.moveValid.expect(true.B)
                    cursor = dut.io.nextCursor.peek().litValue.toInt
                }
                request(dut, position, white, cursor)
                waitForResult(dut)
                dut.io.moveFinished.expect(true.B)
                dut.io.moveValid.expect(false.B)
                dut.io.nextCursor.expect(4096.U)
            }

            // Returning from another branch supplies the old board AND its bookmark.
            request(dut, board, true, expected.head + 1)
            waitForResult(dut)
            dut.io.generatedMove.expect(expected(1).U)

            // Dropping request also cancels an unfinished scan.
            request(dut, board, true, 0)
            dut.io.moveValid.expect(false.B)
            request(dut, board, true, expected.head)
            dut.io.moveValid.expect(true.B)
            dut.io.generatedMove.expect(expected.head.U)

            for (endCursor <- Seq(4096, 8191)) {
                request(dut, board, true, endCursor)
                dut.io.moveFinished.expect(true.B)
                dut.io.moveValid.expect(false.B)
            }
            dut.io.request.poke(false.B)
            dut.io.moveValid.expect(false.B)
            dut.io.moveFinished.expect(false.B)
        }
    }

    it should "check pawn rules, king safety, queen paths, and promotion moves" in {
        test(new MoveGenerator) { dut =>
            dut.clock.setTimeout(0)
            def check(board: Seq[Seq[Int]], from: String, to: String, legal: Boolean,
                      white: Boolean = true, inCheck: Boolean = false): Unit = {
                val candidate = move(from, to)
                request(dut, board, white, candidate)
                dut.io.inCheck.expect(inCheck.B)
                // A legal first candidate is ready after two cycles; an illegal
                // one must still be scanning, without presenting a result.
                dut.io.moveValid.expect(legal.B)
                dut.io.moveFinished.expect(false.B)
                if (legal) {
                    dut.io.generatedMove.expect(candidate.U)
                    dut.io.nextCursor.expect((candidate + 1).U)
                }
            }

            val pawns = position("a1" -> whiteKing, "h8" -> blackKing, "d2" -> whitePawn,
                "d3" -> blackPawn, "e3" -> whitePawn)
            check(pawns, "d2", "d3", legal = false) // Cannot capture forwards.
            check(pawns, "d2", "d4", legal = false) // Cannot jump a blocker.
            check(pawns, "d2", "e3", legal = false) // Friendly destination.
            check(pawns, "d2", "c3", legal = false) // Empty diagonal.
            check(pawns, "d2", "d1", legal = false) // Backwards.
            check(pawns, "e3", "e5", legal = false) // Double step only from home rank.
            check(pawns, "h8", "h7", legal = false) // Opponent's piece.

            val kings = position("h1" -> whiteKing, "h3" -> blackKing)
            check(kings, "h1", "g1", legal = true)
            check(kings, "h1", "h2", legal = false) // Kings cannot become adjacent.
            check(kings, "h1", "g2", legal = false)
            check(kings, "h1", "a1", legal = false) // No wrapping across files.
            check(kings, "h1", "h1", legal = false)

            val pawnAttack = position("a1" -> whiteKing, "h8" -> blackKing, "b3" -> blackPawn)
            check(pawnAttack, "a1", "a2", legal = false) // Pawn attacks an empty square.
            check(pawnAttack, "a1", "b2", legal = true) // Pawn does not attack forwards.
            val whitePawnAttack = position("h1" -> whiteKing, "a8" -> blackKing, "b6" -> whitePawn)
            check(whitePawnAttack, "a8", "a7", legal = false, white = false)
            check(whitePawnAttack, "a8", "b7", legal = true, white = false)
            val besidePawn = position("h1" -> whiteKing, "a7" -> blackKing, "b6" -> whitePawn)
            check(besidePawn, "a7", "a6", legal = true, white = false, inCheck = true)

            val pinned = position("e1" -> whiteKing, "a8" -> blackKing,
                "e8" -> blackQueen, "e2" -> whitePawn, "d3" -> blackPawn)
            check(pinned, "e2", "d3", legal = false) // Exposes own king to the queen.
            check(pinned, "e2", "e3", legal = true)
            val checked = position("e1" -> whiteKing, "a8" -> blackKing,
                "e8" -> blackQueen, "a2" -> whitePawn)
            check(checked, "a2", "a3", legal = false, inCheck = true)
            check(checked, "e1", "d1", legal = true, inCheck = true)

            val defended = position("e4" -> whiteKing, "a8" -> blackKing,
                "d8" -> blackQueen, "d5" -> blackPawn)
            check(defended, "e4", "d5", legal = false, inCheck = true) // Pawn was shielding a queen.
            val undefended = position("e4" -> whiteKing, "a8" -> blackKing, "d5" -> blackPawn)
            check(undefended, "e4", "d5", legal = true, inCheck = true)

            val queens = position("h1" -> whiteKing, "a8" -> blackKing,
                "d4" -> whiteQueen, "d6" -> whitePawn, "f4" -> blackPawn)
            check(queens, "d4", "d5", legal = true)
            check(queens, "d4", "d7", legal = false) // Friendly blocker.
            check(queens, "d4", "f4", legal = true) // Capture first enemy.
            check(queens, "d4", "g4", legal = false) // Cannot jump captured piece.
            check(queens, "d4", "a7", legal = true) // Diagonal.
            check(queens, "d4", "e6", legal = false) // Not a queen direction.

            val promotion = position("h1" -> whiteKing, "h8" -> blackKing,
                "a7" -> whitePawn, "b8" -> blackQueen)
            check(promotion, "a7", "a8", legal = true)
            check(promotion, "a7", "b8", legal = true)
            val blackPromotion = position("h1" -> whiteKing, "h8" -> blackKing,
                "a2" -> blackPawn, "b1" -> whiteQueen)
            check(blackPromotion, "a2", "a1", legal = true, white = false)
            check(blackPromotion, "a2", "b1", legal = true, white = false)

            val kingCapture = position("h1" -> whiteKing, "a8" -> blackKing, "a7" -> whiteQueen)
            check(kingCapture, "a7", "a8", legal = false)
        }
    }

    it should "distinguish checkmate from stalemate when there are no moves" in {
        test(new MoveGenerator) { dut =>
            dut.clock.setTimeout(0)
            val mate = position("a8" -> blackKing, "b7" -> whiteQueen, "c6" -> whiteKing)
            val stalemate = position("a8" -> blackKing, "b6" -> whiteQueen, "c6" -> whiteKing)
            for ((board, checked) <- Seq((mate, true), (stalemate, false))) {
                request(dut, board, white = false, cursor = 0)
                waitForResult(dut)
                dut.io.moveFinished.expect(true.B)
                dut.io.moveValid.expect(false.B)
                dut.io.inCheck.expect(checked.B)
            }
        }
    }
}

// Connect the real modules, without changing the existing UART-only Top.
class KingPawnSearchHarness(depth: Int) extends Module {
    val io = IO(new Bundle {
        val board = Input(Vec(8, Vec(8, UInt(16.W))))
        val whiteTurn = Input(Bool())
        val start = Input(Bool())
        val done = Output(Bool())
        val bestMove = Output(UInt(12.W))
        val bestScore = Output(SInt(32.W))
        val workingBoard = Output(Vec(8, Vec(8, UInt(16.W))))
        val watchSquare = Input(UInt(6.W))
        val watchPiece = Input(UInt(16.W))
        val sawPiece = Output(Bool())
    })
    val search = Module(new minimax(depth))
    val generator = Module(new MoveGenerator)
    search.io.board := io.board
    search.io.isWhiteTurn := io.whiteTurn
    search.io.start := io.start
    generator.io.request := search.io.moveRequest
    generator.io.board := search.io.moveBoard
    generator.io.whiteTurn := search.io.moveWhiteTurn
    generator.io.cursor := search.io.moveCursor
    search.io.generatedMove := generator.io.generatedMove
    search.io.nextCursor := generator.io.nextCursor
    search.io.moveValid := generator.io.moveValid
    search.io.moveFinished := generator.io.moveFinished
    search.io.inCheck := generator.io.inCheck
    io.done := search.io.done
    io.bestMove := search.io.bestMove
    io.bestScore := search.io.bestScore
    io.workingBoard := search.io.moveBoard

    // Record promotion on every hardware clock, even when the test advances
    // several clocks at once. The final board must still be fully restored.
    val sawPiece = RegInit(false.B)
    when(!io.start) {
        sawPiece := false.B
    }.elsewhen(search.io.busy &&
        search.io.moveBoard(io.watchSquare(5, 3))(io.watchSquare(2, 0)) === io.watchPiece) {
        sawPiece := true.B
    }
    io.sawPiece := sawPiece
}

class MoveGeneratorMinimaxTest extends AnyFlatSpec with ChiselScalatestTester {
    import MoveGeneratorTestData._

    "Minimax with MoveGenerator" should "search replies, promote both colors, and undo their moves" in {
        test(new KingPawnSearchHarness(2)) { dut =>
            dut.clock.setTimeout(0)
            val whiteBoard = position("h1" -> whiteKing, "h8" -> blackKing, "a7" -> whitePawn)
            val blackBoard = position("h1" -> whiteKing, "h8" -> blackKing, "a2" -> blackPawn)
            val whiteCapture = position("h1" -> whiteKing, "h8" -> blackKing,
                "a7" -> whitePawn, "b8" -> blackQueen)
            val blackCapture = position("h1" -> whiteKing, "h8" -> blackKing,
                "a2" -> blackPawn, "b1" -> whiteQueen)
            for ((board, white, from, to, queen) <- Seq(
                (whiteBoard, true, "a7", "a8", whiteQueen),
                (blackBoard, false, "a2", "a1", blackQueen),
                (whiteCapture, true, "a7", "b8", whiteQueen),
                (blackCapture, false, "a2", "b1", blackQueen))) {
                dut.io.start.poke(false.B)
                dut.clock.step()
                load(dut.io.board, board)
                dut.io.whiteTurn.poke(white.B)
                dut.io.watchSquare.poke(square(to).U)
                dut.io.watchPiece.poke(queen.U)
                // Keep start high so minimax holds done until we read it.
                dut.io.start.poke(true.B)
                dut.clock.step()

                var cycles = 0
                while (!dut.io.done.peek().litToBoolean && cycles < 20000) {
                    dut.clock.step(32)
                    cycles += 32
                }
                assert(cycles < 20000, "Search failed to finish with the real move generator")
                dut.io.sawPiece.expect(true.B)
                dut.io.bestMove.expect(move(from, to).U)
                val score = dut.io.bestScore.peek().litValue
                assert(if (white) score > 500 else score < -500)
                for (row <- 0 until 8; col <- 0 until 8) {
                    dut.io.workingBoard(row)(col).expect(board(row)(col).U)
                }
            }

            val mate = position("a8" -> blackKing, "b7" -> whiteQueen, "c6" -> whiteKing)
            val stalemate = position("a8" -> blackKing, "b6" -> whiteQueen, "c6" -> whiteKing)
            for ((board, score) <- Seq((mate, 100000), (stalemate, 0))) {
                dut.io.start.poke(false.B)
                dut.clock.step()
                load(dut.io.board, board)
                dut.io.whiteTurn.poke(false.B)
                dut.io.start.poke(true.B)
                dut.clock.step()
                var cycles = 0
                while (!dut.io.done.peek().litToBoolean && cycles < 500) {
                    dut.clock.step(32)
                    cycles += 32
                }
                dut.io.done.expect(true.B)
                dut.io.bestMove.expect(0.U)
                dut.io.bestScore.expect(score.S)
            }
        }
    }
}
