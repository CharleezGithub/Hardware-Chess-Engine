import chisel3._
import chisel3.util._

class UartTop(baudRate: Int, clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        // UART interface
        val tx = Output(Bool())
        val rx = Input(Bool())

        val board = Input(Vec(8, Vec(8, UInt(4.W)))) // 8x8 chess board input to be printed
        val sendData = Input(Bool())

        val busy = Output(Bool()) // State of the UART transmitter
        val boardOut = Output(Vec(8, Vec(8, UInt(4.W)))) // 8x8 chess board output received from UART
        val boardValid = Output(Bool()) // Indicates when a new board has been received
    })
    // Uart Tx
    val uartTx = Module(new UartTx(baudRate, clockFreq))
    io.tx := uartTx.io.tx
    uartTx.io.sendData := io.sendData
    when(uartTx.io.busy) {
        uartTx.io.sendData := false.B // Prevent sending new data while busy
    }
    io.busy := uartTx.io.busy
    uartTx.io.board := io.board

    // Uart Rx
    val uartRx = Module(new UartRx(baudRate, clockFreq))
    uartRx.io.rx := io.rx

    val boardData = RegInit(VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(4.W))))))
    val byteIndex = RegInit(0.U(5.W)) // 8x8 board, half a byte per square

    when(uartRx.io.dataValid) { // Format the received byte into two squares on the board
        boardData(byteIndex(4, 2))(byteIndex(1, 0) << 1) := uartRx.io.dataOut(7, 4) // High nibble to first square
        boardData(byteIndex(4, 2))((byteIndex(1, 0) << 1) + 1.U) := uartRx.io.dataOut(3, 0) // Low nibble to second square

        when(byteIndex === 31.U) { // If we've filled the entire board
            byteIndex := 0.U // Reset for next board
        }.otherwise {
            byteIndex := byteIndex + 1.U // Move to next square
        }
    }
    val boardValReg = RegNext(byteIndex === 31.U && uartRx.io.dataValid, false.B) // Register to hold the board valid signal for one cycle
    io.boardValid := boardValReg // Indicate when a full board has been received
    io.boardOut := boardData
}