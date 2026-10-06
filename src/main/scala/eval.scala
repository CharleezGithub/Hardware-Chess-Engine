import chisel3._
class MatrixBuffer8x8(val width: Int = 16) extends Module {
  val io = IO(new Bundle {
    // INPUT
    // Board position
    // Is it late-game?

    // OUTPUT
    // Eval Score


  })
  // Piece-square tables

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


}
