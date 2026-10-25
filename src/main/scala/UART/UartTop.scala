import chisel3._
import chisel3.util._

class UartTop(baudRate: Int, clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        //val rx = Input(Bool())
        val busy = Output(Bool()) // State of the UART transmitter
        val board = Input(Vec(8, Vec(8, UInt(16.W)))) // 8x8 chess board input to be printed
        val sendData = Input(Bool())
    })

    val uartTx = Module(new UartTx(baudRate, clockFreq))
    io.tx := uartTx.io.tx
    uartTx.io.sendData := io.sendData
    when(uartTx.io.busy) {
        uartTx.io.sendData := false.B // Prevent sending new data while busy
    }
    io.busy := uartTx.io.busy
    uartTx.io.board := io.board
}