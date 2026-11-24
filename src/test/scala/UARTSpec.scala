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
        dut.io.responseStatus.poke(0.U) // Set response status to 0 for this test
        dut.clock.setTimeout(100000) // Set a timeout to avoid infinite loops in case of failure
        val countMax = (1000000 / 115200)
        var cnt = 0
        val totalBits = 8 * 8 * 0.5 + 1 // 8x8 board half a byte per square + response status byte
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
                //println(s"Sending byte: ${byte.toHexString}, bit $i: $expectedBit")
                //println(s"TX line: ${dut.io.tx.peek().litValue}")
                dut.io.tx.expect((expectedBit == 1).B)
                dut.clock.step(countMax)
            }
            // Stop bit
            dut.io.tx.expect(true.B)
            dut.clock.step(countMax)
            
        }
        // After sending all board bytes, send the response status byte
        while (dut.io.tx.peek().litToBoolean) { // trap until tx goes low
            dut.clock.step(1)
        }
        dut.clock.step(countMax) // Wait for the duration of one baud period
        for (i <- 0 until 8) {
            val responseStatusByte = dut.io.responseStatus.peek()
            val expectedBit = (responseStatusByte.litValue >> i) & 1
            //println(s"Sending response status byte: ${responseStatusByte.litValue.toString}, bit $i: $expectedBit")
            //println(s"TX line: ${dut.io.tx.peek().litValue}")
            dut.io.tx.expect((expectedBit == 1).B)
            dut.clock.step(countMax)
        }
        // Stop bit for response status byte
        dut.io.tx.expect(true.B)
        dut.clock.step(countMax)

        dut.io.busy.expect(false.B) // Ensure busy is false after sending all bytes
    }
  }
  "UART" should "receive a move" in { // Test the UART receiver
    test(new UartTop(baudRate = 115200, clockFreq = 1000000)) { dut =>
val fromPos = 0x12
    val toPos = 0x34
    val command = 0x03
    val checksum = command ^ fromPos ^ toPos
    val packet = Seq(0xFF, command, fromPos, toPos, checksum)

    def sendByte(byte: Int): Unit = {
      // Start bit
      dut.io.rx.poke(false.B)
      dut.clock.step(countMax)

      // 8 data bits (LSB first)
      for (i <- 0 until 8) {
        val bitToSend = (byte >> i) & 1
        dut.io.rx.poke((bitToSend == 1).B)
        dut.clock.step(countMax)
      }

      // Stop bit
      dut.io.rx.poke(true.B)
      dut.clock.step(countMax)
    }

    // Idle line
    dut.io.rx.poke(true.B)
    dut.io.commandReady.poke(false.B)
    dut.clock.step(countMax)

    // Transmit all 5 bytes
    for (b <- packet) {
      sendByte(b)
    }

    // Allow a few cycles for UartRx's input synchronizer + pending register to update
    dut.clock.step(5)

    // Verify latched command
    dut.io.commandValid.expect(true.B)
    dut.io.command.expect(command.U)
    dut.io.from.expect(fromPos.U)
    dut.io.to.expect(toPos.U)

    // Complete handshake
    dut.io.commandReady.poke(true.B)
    dut.clock.step(1)
    dut.io.commandValid.expect(false.B)
    }
    }
}