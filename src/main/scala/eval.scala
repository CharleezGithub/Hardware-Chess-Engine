import chisel3._
import chisel3.util._

class MatrixBuffer8x8(val width: Int = 16) extends Module {
  val io = IO(new Bundle {
    // INPUT
    // Board position
    // Is it late-game?
    val boardState = Input(Vec(8, Vec(8, UInt(4.W))))

    // OUTPUT
    // Eval Score
    val score = Output(SInt(16.W))
  })

  // Piece-square tables
  // https://chessprogramming.org/Simplified_Evaluation_Function
  val pawnTable = VecInit(
    Seq(
      VecInit(Seq(0, 0, 0, 0, 0, 0, 0, 0).map(_.S(8.W))),
      VecInit(Seq(50, 50, 50, 50, 50, 50, 50, 50).map(_.S(8.W))),
      VecInit(Seq(10, 10, 20, 30, 30, 20, 10, 10).map(_.S(8.W))),
      VecInit(Seq(5, 5, 10, 25, 25, 10, 5, 5).map(_.S(8.W))),
      VecInit(Seq(0, 0, 0, 20, 20, 0, 0, 0).map(_.S(8.W))),
      VecInit(Seq(5, -5, -10, 0, 0, -10, -5, 5).map(_.S(8.W))),
      VecInit(Seq(5, 10, 10, -20, -20, 10, 10, 5).map(_.S(8.W))),
      VecInit(Seq(0, 0, 0, 0, 0, 0, 0, 0).map(_.S(8.W)))
    )
  )

  val knightTable = VecInit(
    Seq(
      VecInit(Seq(-50, -40, -30, -30, -30, -30, -40, -50).map(_.S(8.W))),
      VecInit(Seq(-40, -20, 0, 0, 0, 0, -20, -40).map(_.S(8.W))),
      VecInit(Seq(-30, 0, 10, 15, 15, 10, 0, -30).map(_.S(8.W))),
      VecInit(Seq(-30, 5, 15, 20, 20, 15, 5, -30).map(_.S(8.W))),
      VecInit(Seq(-30, 0, 15, 20, 20, 15, 0, -30).map(_.S(8.W))),
      VecInit(Seq(-30, 5, 10, 15, 15, 10, 5, -30).map(_.S(8.W))),
      VecInit(Seq(-40, -20, 0, 5, 5, 0, -20, -40).map(_.S(8.W))),
      VecInit(Seq(-50, -40, -30, -30, -30, -30, -40, -50).map(_.S(8.W)))
    )
  )

  val bishopTable = VecInit(
    Seq(
      VecInit(Seq(-20, -10, -10, -10, -10, -10, -10, -20).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 0, 0, 0, 0, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 5, 10, 10, 5, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 5, 5, 10, 10, 5, 5, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 10, 10, 10, 10, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 10, 10, 10, 10, 10, 10, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 5, 0, 0, 0, 0, 5, -10).map(_.S(8.W))),
      VecInit(Seq(-20, -10, -10, -10, -10, -10, -10, -20).map(_.S(8.W)))
    )
  )

  val rookTable = VecInit(
    Seq(
      VecInit(Seq(0, 0, 0, 0, 0, 0, 0, 0).map(_.S(8.W))),
      VecInit(Seq(5, 10, 10, 10, 10, 10, 10, 5).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 0, 0, 0, 0, 0, -5).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 0, 0, 0, 0, 0, -5).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 0, 0, 0, 0, 0, -5).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 0, 0, 0, 0, 0, -5).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 0, 0, 0, 0, 0, -5).map(_.S(8.W))),
      VecInit(Seq(0, 0, 0, 5, 5, 0, 0, 0).map(_.S(8.W)))
    )
  )

  val queenTable = VecInit(
    Seq(
      VecInit(Seq(-20, -10, -10, -5, -5, -10, -10, -20).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 0, 0, 0, 0, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 5, 5, 5, 5, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-5, 0, 5, 5, 5, 5, 0, -5).map(_.S(8.W))),
      VecInit(Seq(0, 0, 5, 5, 5, 5, 0, -5).map(_.S(8.W))),
      VecInit(Seq(-10, 5, 5, 5, 5, 5, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-10, 0, 5, 0, 0, 0, 0, -10).map(_.S(8.W))),
      VecInit(Seq(-20, -10, -10, -5, -5, -10, -10, -20).map(_.S(8.W)))
    )
  )

  val kingMiddleGameTable = VecInit(
    Seq(
      VecInit(Seq(-30, -40, -40, -50, -50, -40, -40, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -40, -40, -50, -50, -40, -40, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -40, -40, -50, -50, -40, -40, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -40, -40, -50, -50, -40, -40, -30).map(_.S(8.W))),
      VecInit(Seq(-20, -30, -30, -40, -40, -30, -30, -20).map(_.S(8.W))),
      VecInit(Seq(-10, -20, -20, -20, -20, -20, -20, -10).map(_.S(8.W))),
      VecInit(Seq(20, 20, 0, 0, 0, 0, 20, 20).map(_.S(8.W))),
      VecInit(Seq(20, 30, 10, 0, 0, 10, 30, 20).map(_.S(8.W)))
    )
  )

  val kingEndGameTable = VecInit(
    Seq(
      VecInit(Seq(-50, -40, -30, -20, -20, -30, -40, -50).map(_.S(8.W))),
      VecInit(Seq(-30, -20, -10, 0, 0, -10, -20, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -10, 20, 30, 30, 20, -10, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -10, 30, 40, 40, 30, -10, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -10, 30, 40, 40, 30, -10, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -10, 20, 30, 30, 20, -10, -30).map(_.S(8.W))),
      VecInit(Seq(-30, -30, 0, 0, 0, 0, -30, -30).map(_.S(8.W))),
      VecInit(Seq(-50, -30, -30, -30, -30, -30, -30, -50).map(_.S(8.W)))
    )
  )

  def evaluateSquare(piece: UInt, row: Int, col: Int): SInt = {
    val result = WireDefault(0.S(16.W))

    switch(piece) {
      is(0.U) { // Empty
        result := 0.S(16.W)
      }
      is(1.U) { // White Pawn
        // Widen before adding: 100 + 50 overflows an 8-bit signed sum.
        result := 100.S(16.W) + pawnTable(row)(col)
      }
      is(2.U) { // White Knight
        result := 320.S(16.W) + knightTable(7 - row)(col)
      }
      is(3.U) { // White Bishop
        result := 330.S(16.W) + bishopTable(7 - row)(col)
      }
      is(4.U) { // White Rook
        result := 500.S(16.W) + rookTable(7 - row)(col)
      }
      is(5.U) { // White Queen
        result := 900.S(16.W) + queenTable(row)(col)
      }
      is(6.U) { // White king (endgame).
        result := 20000.S(16.W) + kingEndGameTable(row)(col)
      }
      is(7.U) { // Black Pawn
        result := -100.S(16.W) - pawnTable(7 - row)(col)
      }
      is(8.U) { // Black Knight
        result := -320.S(16.W) - knightTable(row)(col)
      }
      is(9.U) { // Black Bishop
        result := -330.S(16.W) - bishopTable(row)(col)
      }
      is(10.U) { // Black Rook
        result := -500.S(16.W) - rookTable(row)(col)
      }
      is(11.U) { // Black Queen
        result := -900.S(16.W) - queenTable(7 - row)(col)
      }
      is(12.U) { // Black king (endgame).
        result := -20000.S(16.W) - kingEndGameTable(7 - row)(col)
      }

    }
    result
  }

  val evaluations = (0 until 8).flatMap { row =>
    (0 until 8).map { col =>
      evaluateSquare(io.boardState(row)(col), row, col)
    }
  }
  val totalEval = VecInit(evaluations).reduceTree(_ +& _)

  io.score := totalEval

}
