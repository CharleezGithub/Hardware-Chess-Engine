import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class EvalSpec extends AnyFlatSpec with ChiselScalatestTester {
    "Pawn evaluation" should "preserve material and advancement bonuses for both colors" in {
        test(new MatrixBuffer8x8()) { dut =>
            val bonuses = Seq(
                Seq(0, 0, 0, 0, 0, 0, 0, 0),
                Seq(50, 50, 50, 50, 50, 50, 50, 50),
                Seq(10, 10, 20, 30, 30, 20, 10, 10),
                Seq(5, 5, 10, 25, 25, 10, 5, 5),
                Seq(0, 0, 0, 20, 20, 0, 0, 0),
                Seq(5, -5, -10, 0, 0, -10, -5, 5),
                Seq(5, 10, 10, -20, -20, 10, 10, 5),
                Seq(0, 0, 0, 0, 0, 0, 0, 0))
            for (row <- 0 until 8; col <- 0 until 8)
                dut.io.boardState(row)(col).poke(0.U)
            for (white <- Seq(true, false); row <- 0 until 8; col <- 0 until 8) {
                dut.io.boardState(row)(col).poke((if (white) 1 else 7).U)
                val bonus = bonuses(if (white) row else 7 - row)(col)
                val expected = (if (white) 1 else -1) * (100 + bonus)
                dut.io.score.expect(expected.S)
                dut.io.boardState(row)(col).poke(0.U)
            }
        }
    }
}
