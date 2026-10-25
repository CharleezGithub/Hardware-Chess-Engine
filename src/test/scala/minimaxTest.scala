
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class minimaxTest extends AnyFlatSpec with ChiselScalatestTester {

    // Creates a move using the starting and destination squares
    def makeMove(from: Int, to: Int): Int = {
        (from << 6) | to
    }

    // Fake MoveGenerator used to test Minimax
    def generateMoves(board: Seq[Seq[Int]], whiteTurn: Boolean): Seq[Int] = {

        val whiteRook = (0 until 64).find(i =>
            board(i / 8)(i % 8) == 4
        )

        if (whiteTurn) {

            if (whiteRook.contains(56)) {
                // White rook on a1
                // Try the worse move first
                Seq(
                    makeMove(56, 48), // a1 -> a2
                    makeMove(56, 0)   // a1 -> a8, capture rook
                )

            } else if (whiteRook.contains(0)) {
                // White rook on a8
                Seq(makeMove(0, 8)) // a8 -> a7

            } else if (whiteRook.contains(48)) {
                // White rook on a2
                Seq(makeMove(48, 40)) // a2 -> a3

            } else {
                // White rook has been captured
                Seq(makeMove(60, 59)) // King e1 -> d1
            }

        } else {

            if (whiteRook.contains(0)) {
                // Black king can move
                Seq(
                    makeMove(4, 11), // e8 -> d7
                    makeMove(4, 13)  // e8 -> f7
                )

            } else if (whiteRook.contains(48)) {
                Seq(
                    makeMove(4, 11), // Black king moves
                    makeMove(0, 48)  // Black rook captures white rook
                )

            } else {
                Seq.empty
            }
        }
    }

    def runSearch(depth: Int): Unit = {

        test(new minimax(depth)) { dut =>

            // Starting position
            // Black rook on a8, black king on e8
            // White rook on a1, white king on e1
            val board = Seq(
                Seq(10, 0, 0, 0, 12, 0, 0, 0),
                Seq.fill(8)(0),
                Seq.fill(8)(0),
                Seq.fill(8)(0),
                Seq.fill(8)(0),
                Seq.fill(8)(0),
                Seq.fill(8)(0),
                Seq(4, 0, 0, 0, 6, 0, 0, 0)
            )

            // Send the starting board to Minimax
            for (r <- 0 until 8; c <- 0 until 8) {
                dut.io.board(r)(c).poke(board(r)(c).U)
            }

            dut.io.isWhiteTurn.poke(true.B)
            dut.io.moveValid.poke(false.B)
            dut.io.moveFinished.poke(false.B)
            dut.io.generatedMove.poke(0.U)
            dut.io.nextCursor.poke(0.U)
            dut.io.inCheck.poke(false.B)

            // Start the search
            dut.io.start.poke(true.B)
            dut.clock.step(1)
            dut.io.start.poke(false.B)

            // Allow Minimax to initialize
            dut.clock.step(1)

            var finished = false
            var cycles = 0

            // Simulate the MoveGenerator until Minimax finishes
            while (!finished && cycles < 1000) {

                val currentBoard = (0 until 8).map { r =>
                    (0 until 8).map { c =>
                        dut.io.moveBoard(r)(c).peek().litValue.toInt
                    }
                }

                val whiteTurn =
                    dut.io.moveWhiteTurn.peek().litToBoolean

                val cursor =
                    dut.io.moveCursor.peek().litValue.toInt

                val moves = generateMoves(currentBoard, whiteTurn)

                if (cursor < moves.length) {
                    dut.io.moveValid.poke(true.B)
                    dut.io.moveFinished.poke(false.B)
                    dut.io.generatedMove.poke(moves(cursor).U)
                    dut.io.nextCursor.poke((cursor + 1).U)

                } else {
                    dut.io.moveValid.poke(false.B)
                    dut.io.moveFinished.poke(true.B)
                    dut.io.generatedMove.poke(0.U)
                    dut.io.nextCursor.poke(cursor.U)
                }

                dut.io.inCheck.poke(false.B)

                dut.clock.step(1)
                cycles += 1

                finished = dut.io.done.peek().litToBoolean
            }

            // Minimax must finish within the cycle limit
            assert(finished, s"Minimax did not finish at depth $depth")

            // The expected best move is rook a1 -> a8
            val expectedMove = makeMove(56, 0)

            dut.io.bestMove.expect(expectedMove.U)

            // White should have a positive evaluation
            val score = dut.io.bestScore.peek().litValue.toInt

            assert(
                score > 0,
                s"Expected positive score at depth $depth, got $score"
            )

            dut.io.busy.expect(false.B)
            dut.io.done.expect(true.B)

            println(s"Depth $depth passed")
            println(s"Best move: ${dut.io.bestMove.peek().litValue}")
            println(s"Best score: $score")
            println(s"Clock cycles: $cycles")
        }
    }

    "Minimax" should "find the best move at depth 1" in {
        runSearch(1)
    }

    it should "find the best move at depth 2" in {
        runSearch(2)
    }

    it should "find the best move at depth 3" in {
        runSearch(3)
    }
}
