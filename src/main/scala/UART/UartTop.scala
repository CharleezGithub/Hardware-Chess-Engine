import chisel3._
import chisel3.util._

class Uart(baudRate: Int, clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        val rx = Input(Bool())
        val busy = Output(Bool()) // State of the UART transmitter
        val board = Input(Vec(8, Vec(8, UInt(16.W)))) // 8x8 chess board input to be printed
        val sendData = Input(Bool())
    })

    val uartTx = Module(new UartTx(baudRate, clockFreq))
    uartTx.io.tx := io.tx
    io.tx := uartTx.io.tx
    io.busy := uartTx.io.busy
    uartTx.io.board := io.board
    uartTx.io.sendData := io.sendData
}