import chisel3._
import chisel3.util._

// Stop-and-wait protocol. Request: AA command from to XOR(command,from,to).
// Reply: 55 status 32 packed board bytes XOR(status,board bytes).
class GameUart(clockFreq: Int, baudRate: Int) extends Module {
    val io = IO(new Bundle {
        val rx = Input(Bool())
        val tx = Output(Bool())
        val commandValid = Output(Bool())
        val commandReady = Input(Bool())
        val command = Output(UInt(8.W))
        val from = Output(UInt(8.W))
        val to = Output(UInt(8.W))
        val responseValid = Input(Bool())
        val responseReady = Output(Bool())
        val responseStatus = Input(UInt(8.W))
        val board = Input(Vec(8, Vec(8, UInt(4.W))))
        val busy = Output(Bool())
    })
    val rx = Module(new UartRx(baudRate, clockFreq))
    rx.io.rx := io.rx
    val command = RegInit(0.U(8.W))
    val from = RegInit(0.U(8.W))
    val to = RegInit(0.U(8.W))
    val pending = RegInit(false.B)
    val receiveIndex = RegInit(0.U(3.W))
    val timeoutCycles = clockFreq / 10
    val timeout = RegInit(0.U(log2Ceil(timeoutCycles + 1).W))
    when(pending && io.commandReady) { pending := false.B }
    when(receiveIndex =/= 0.U) {
        timeout := timeout + 1.U
        when(timeout === (timeoutCycles - 1).U) { receiveIndex := 0.U; timeout := 0.U }
    }
    when(rx.io.dataValid && !pending) {
        timeout := 0.U
        switch(receiveIndex) {
            is(0.U) { when(rx.io.dataOut === "hAA".U) { receiveIndex := 1.U } }
            is(1.U) { command := rx.io.dataOut; receiveIndex := 2.U }
            is(2.U) { from := rx.io.dataOut; receiveIndex := 3.U }
            is(3.U) { to := rx.io.dataOut; receiveIndex := 4.U }
            is(4.U) {
                when(rx.io.dataOut =/= (command ^ from ^ to)) { command := 255.U }
                pending := true.B
                receiveIndex := 0.U
            }
        }
    }
    io.commandValid := pending
    io.command := command
    io.from := from
    io.to := to

    val tx = Module(new UartByteTx(clockFreq, baudRate))
    val sending = RegInit(false.B)
    val sendIndex = RegInit(0.U(6.W))
    val payload = Reg(Vec(33, UInt(8.W)))
    val checksum = RegInit(0.U(8.W))
    val packedBoard = VecInit((0 until 32).map { n =>
        Cat(io.board(n / 4)((n % 4) * 2), io.board(n / 4)((n % 4) * 2 + 1))
    })
    io.responseReady := !sending && tx.io.ready
    when(io.responseValid && io.responseReady) {
        payload(0) := io.responseStatus
        for (n <- 0 until 32) payload(n + 1) := packedBoard(n)
        checksum := io.responseStatus ^ packedBoard.reduce(_ ^ _)
        sending := true.B; sendIndex := 0.U
    }
    tx.io.valid := sending
    tx.io.data := Mux(sendIndex === 0.U, "h55".U,
        Mux(sendIndex === 34.U, checksum, payload(sendIndex - 1.U)))
    when(sending && tx.io.ready) {
        when(sendIndex === 34.U) { sending := false.B }
            .otherwise { sendIndex := sendIndex + 1.U }
    }
    io.tx := tx.io.tx
    io.busy := sending || !tx.io.ready
}
