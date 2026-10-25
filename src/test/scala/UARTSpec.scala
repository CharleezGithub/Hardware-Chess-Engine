import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class UARTSpec extends AnyFlatSpec with ChiselScalatestTester {
    val countMax = (1000000 / 115200)
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
        val totalBits = 8 * 8 * 0.5 // 8x8 board half a byte per square
        // loop through the test board
        for (r <- 0 until 8; c <- 0 until 4) {
            val squareVal1 = testBoard(r)(c*2)
            val squareVal2 = testBoard(r)(2*c + 1)
            val byte = (squareVal1 << 4) | squareVal2 // Combine two 4-bit values into one byte
        
            while (dut.io.tx.peek().litToBoolean) { // trap until tx goes low
                dut.clock.step(1)
            }
            dut.clock.step(countMax) // Wait for the duration of one baud period
    
                // Data bits
            for (i <- 0 until 8) { // Only 4 bits for each square
                val expectedBit = (byte >> i) & 1
                dut.io.tx.expect((expectedBit == 1).B)
                dut.clock.step(countMax)
            }
    
                // Stop bit
            dut.io.tx.expect(true.B)
            dut.clock.step(countMax)
            
        }
        dut.io.busy.expect(false.B) // Ensure busy is false after sending all bytes
    }
  }
  /* TODO: Implement UART receive test
  "UART" should "receive data correctly" in {
    test(new UartRx(baudRate = 115200, clockFreq = 1000000)) { dut =>
        val testBytes = Seq(0x12, 0x34, 0x56, 0x78)
        val countMax = (1000000 / 115200)
        for (byte <- testBytes) {
            // Start bit
            dut.io.rx.poke(false.B)
            dut.clock.step(countMax)
    
            // Data bits
            for (i <- 0 until 8) {
                val bitToSend = (byte >> i) & 1
                dut.io.rx.poke((bitToSend == 1).B)
                dut.clock.step(countMax)
            }
    
            // Stop bit
            dut.io.rx.poke(true.B)
            dut.clock.step(countMax)
    
            // Check if the received byte is correct
            dut.clock.step(countMax) // Wait for the receiver to process the stop bit
            dut.io.dataValid.expect(true.B)
            dut.io.dataOut.expect(byte.U)
        }
    }
  }*/
}