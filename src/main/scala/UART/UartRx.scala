import chisel3._
import chisel3.util._

class UartRx(baudRate: Int, clockFreq: Int) extends Module {
  val io = IO(new Bundle {
    val rx = Input(Bool())
    val dataOut = Output(UInt(8.W)) 
    val dataValid = Output(Bool())
  })
    val countMax = (clockFreq / baudRate)
    val countMID = countMax / 2
    val counter = RegInit(0.U(log2Ceil(countMax).W))

    val bitCounter = RegInit(0.U(3.W))
    val dataReg = RegInit(0.U(8.W))
    
    val sIdle :: sStart :: sData :: sStop :: sDone :: Nil = Enum(5)
    val state = RegInit(sIdle)

    val done = WireDefault(false.B)
    io.dataValid := done
    io.dataOut := 0.U
    
    switch(state) {
        is(sIdle) {
            when (!io.rx) { // Start bit detected
                state := sStart
                counter := 0.U
                bitCounter := 0.U
            }
        }
        is(sStart) {
            when (counter === (countMID - 1).U) { // Wait for half a bit period
                when (io.rx) { // If line is high, it's a false start
                    state := sIdle
                    counter := 0.U
                }.otherwise {
                    state := sData
                    counter := 0.U
                }
            }.otherwise {
                counter := counter + 1.U
            }
        }
        is (sData) {
            when (counter === (countMax - 1).U) { // Wait for a full bit period
                counter := 0.U
                dataReg := Cat(io.rx, dataReg(7, 1))
                when (bitCounter === 7.U) { // If we've received 8 bits, go to stop bit
                    state := sStop
                    bitCounter := 0.U
                }.otherwise {
                    bitCounter := bitCounter + 1.U
                }
            }.otherwise {
                counter := counter + 1.U
            }
        }
        is (sStop) {
            when (counter === (countMax - 1).U) { // Wait for a full bit period
                when(io.rx) { // Stop bit should be high
                    state := sDone
                }.otherwise {
                    state := sIdle // If stop bit is low, go back to idle
                }
                counter := 0.U
            }.otherwise {
                counter := counter + 1.U
            }
        }
        is (sDone) {
            done := true.B
            state := sIdle
            io.dataOut := dataReg
        }
    }
}