import chisel3._
import chisel3.util._


class UartTx(baudRate: Int, clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        val busy = Output(Bool()) // State of the UART transmitter
        val board = Input(Vec(8, Vec(8, UInt(4.W)))) // 8x8 chess board input to be printed
        val sendData = Input(Bool())
    })
    // For now just print out board through UART
    val countMax = (clockFreq / baudRate)
    val cnt = RegInit(0.U(log2Ceil(countMax + 1).W))
    val dataBoard = RegInit(VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(4.W))))))
    
    val byteIndex = RegInit(0.U(5.W)) // 8x8 board, half a byte per square
    val bitCnt = RegInit(0.U(3.W)) // Uart transmits a byte at a time, so we need to count up to 8

    
    val sIdle :: sStart :: sData :: sStop :: Nil = Enum(4)
    val state = RegInit(sIdle)

    val currentByte = Cat(dataBoard(byteIndex(4, 2))(byteIndex(1, 0) << 1), dataBoard(byteIndex(4, 2))((byteIndex(1, 0) << 1) + 1.U)) // Concatenate the two squares to form a byte
    io.tx := true.B // idle state is high
    io.busy := (state =/= sIdle)

    switch(state) {
        is(sIdle) {
            when(io.sendData) {
                dataBoard := io.board // Latch the board data when sendData is high
                byteIndex := 0.U
                bitCnt := 0.U
                cnt := 0.U
                state := sStart
            }
        }
        is(sStart) {
            io.tx := false.B // start bit is low
            when(cnt === (countMax - 1).U) { // stall for one baud period
                cnt := 0.U
                bitCnt := 0.U
                state := sData
            }.otherwise {
                cnt := cnt + 1.U
            }

        }
        is(sData) { // send 8 x 8 board pieces as data
            io.tx := currentByte(bitCnt) 
            when(cnt === (countMax - 1).U) {
                cnt := 0.U
                when(bitCnt === 7.U) {
                    state := sStop
                }.otherwise {
                    bitCnt := bitCnt + 1.U
                }
            }.otherwise {
                cnt := cnt + 1.U
            }
        }
        is(sStop) {
            io.tx := true.B // stop bit is high
            when(cnt === (countMax - 1).U) {
                cnt := 0.U 
                when(byteIndex === 31.U) { // 8 * 8 board, half a byte per square, so 32 bytes total
                    state := sIdle
                }.otherwise {
                    byteIndex := byteIndex + 1.U
                    bitCnt := 0.U
                    state := sStart
                }
            }.otherwise {
                cnt := cnt + 1.U
            }
        }
    }
}