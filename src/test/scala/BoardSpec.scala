import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class BoardSpec extends AnyFlatSpec with ChiselScalatestTester {
  // This test assumes the chess board is not hard coded
  "Board" should "initialize correctly" in {
    test(new Board(depth = 4, color = true)) { dut =>
      val initBoard = Seq(
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 6, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0),
        Seq(0, 0, 0, 0, 0, 0, 0, 0)
      )

      // Check that the board is initialized to all zeros
      for (r <- 0 until 8; c <- 0 until 8) {
        // print(initBoard(r)(c))
        dut.io.board(r)(c).expect(initBoard(r)(c).U)
      }
    }
  }

}
