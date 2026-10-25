import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec


class BoardSpec extends AnyFlatSpec with ChiselScalatestTester {
    // This test assumes the chess board is not hard coded
    "Board" should "initialize correctly" in {
        test(new Board(depth = 4, color = true)) { dut =>
        // Check that the board is initialized to all zeros
        for (r <- 0 until 8; c <- 0 until 8) {
            dut.io.board(r)(c).expect(0.U)
        }
        // Check that the material counts are initialized to zero
        // dut.io.whiteMaterial.expect(0.U)
        // dut.io.blackMaterial.expect(0.U)
        // Check that isLateReg is initialized to false
        dut.io.isLate.expect(false.B)
        }
    }

}