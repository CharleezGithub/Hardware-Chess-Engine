import chisel3._
import chisel3.util._

class UartTop(baudRate: Int, clockFreq: Int) extends Module {
    val io = IO(new Bundle {
        // UART interface
        val tx = Output(Bool())
        val rx = Input(Bool())

        // Tx inputs
        val board = Input(Vec(8, Vec(8, UInt(4.W)))) // 8x8 chess board input to be printed
        val responseValid = Input(Bool())

        // Rx inputs
        val commandReady = Input(Bool())
        val responseStatus = Input(UInt(8.W))

        // Tx outputs
        val busy = Output(Bool()) // State of the UART transmitter
        
        // Rx outputs
        val commandValid = Output(Bool())
        val command = Output(UInt(8.W))
        val from = Output(UInt(8.W))
        val to = Output(UInt(8.W))
        val outputValid = Output(Bool())
        val responseReady = Output(Bool())
    })

    // Uart Tx
    val uartTx = Module(new UartTx(baudRate, clockFreq))
    io.tx := uartTx.io.tx
    uartTx.io.sendData := io.responseValid

    when(uartTx.io.busy) {
        uartTx.io.sendData := false.B // Prevent sending new data while busy
    }
    io.busy := uartTx.io.busy
    uartTx.io.board := io.board
    io.responseReady := !uartTx.io.busy // Ready to receive response when not sending data
    uartTx.io.responseStatus := io.responseStatus


    // Uart Rx
    val uartRx = Module(new UartRx(baudRate, clockFreq))
    uartRx.io.rx := io.rx

    val command = RegInit(0.U(8.W))
    val pending = RegInit(false.B)
    val receiveIndex = RegInit(0.U(3.W))
    val timeoutCycles = clockFreq / 10
    val timeout = RegInit(0.U(log2Ceil(timeoutCycles + 1).W))
    val from = RegInit(0.U(8.W))
    val to = RegInit(0.U(8.W))

    when(pending && io.commandReady) { pending := false.B }
    when(receiveIndex =/= 0.U) {
        timeout := timeout + 1.U
        when(timeout === (timeoutCycles - 1).U) { receiveIndex := 0.U; timeout := 0.U }
    }

    when(uartRx.io.dataValid && !pending) { // Format the received byte into two squares on the board
        timeout := 0.U
        switch(receiveIndex) {
            is(0.U) { 
                when(uartRx.io.dataOut === 0xFF.U) { 
                    receiveIndex := 1.U 
                } 
            }
            is(1.U) { 
                command := uartRx.io.dataOut
                receiveIndex := 2.U 
            }
            is(2.U) {
                from := uartRx.io.dataOut
                receiveIndex := 3.U
            }
            is(3.U) {
                to := uartRx.io.dataOut
                receiveIndex := 4.U
            }
            is(4.U) {
                when(uartRx.io.dataOut =/= (command ^ from ^ to)) { // Checksum validation
                    command := 255.U
                }
                pending := true.B
                receiveIndex := 0.U
            }
        }
    }
    io.commandValid := pending
    io.outputValid := uartRx.io.dataValid
    io.command := command
    io.from := from
    io.to := to
}