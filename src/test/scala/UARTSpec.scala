import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class UARTSpec extends AnyFlatSpec with ChiselScalatestTester {
  "UART" should "transmit data correctly" in {
    test(new UartTx(baudRate = 115200, clockFreq = 1000000)) { dut =>
        val testBoard = Seq(
            Seq(1, 2, 3, 4, 5, 6, 0, 0),
            Seq(0, 0, 0, 0, 0, 0, 0, 0),
            Seq(0, 0, 0, 0, 0, 0, 0, 0),
            Seq(0, 0, 0, 0, 0, 0, 0, 0),
            Seq(0, 0, 0, 0, 0, 0, 0, 0),
            Seq(0, 0, 0, 0, 0, 0, 0, 0),
            Seq(6, 5, 4, 3, 2, 1, 1, 1),
            Seq(1, 1, 1, 1, 1, 1, 1, 1)
        )
        for (r <- 0 until 8; c <- 0 until 8) {
            dut.io.board(r)(c).poke(testBoard(r)(c).U)
        }
        dut.io.sendData.poke(true.B)
        dut.clock.step(1)
        dut.io.sendData.poke(false.B)
        dut.clock.setTimeout(100000) // Set a timeout to avoid infinite loops in case of failure
        val countMax = (1000000 / 115200)
        var cnt = 0
        val totalBits = 8 * 8 * 2 // 8x8 board, 16 bits per square
        // loop through the test board
        for (r <- 0 until 8; c <- 0 until 8) {
            val squareValue = testBoard(r)(c)
            val highByte = (squareValue >> 8) & 0xFF
            val lowByte = squareValue & 0xFF
            val bytesToSend = Seq(lowByte, highByte)
    
            for (byte <- bytesToSend) {
                // Start bit
                // println(s"Expecting start bit for byte: $byte")
                while (dut.io.tx.peek().litToBoolean) { // trap until tx goes low
                    dut.clock.step(1)
                }
                dut.clock.step(countMax) // Wait for the duration of one baud period
    
                // Data bits
                for (i <- 0 until 8) {
                    val expectedBit = (byte >> i) & 1
                    dut.io.tx.expect((expectedBit == 1).B)
                    dut.clock.step(countMax)
                }
    
                // Stop bit
                dut.io.tx.expect(true.B)
                dut.clock.step(countMax)
            }
        }
        dut.io.busy.expect(false.B) // Ensure busy is false after sending all bytes
    }
  }
}