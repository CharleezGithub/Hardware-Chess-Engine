import chisel3._
import chisel3.util._

// The Basys wrapper supplies 20 MHz. Simulation uses a shorter UART bit period.
class Top(clockFreq: Int = 20000000, baudRate: Int = 115200, maxDepth: Int = 2) extends Module {
    val io = IO(new Bundle {
        val tx = Output(Bool())
        val rx = Input(Bool())
        val led = Output(UInt(16.W))
        val buttons = Input(UInt(5.W))
    })
    val game = Module(new GameController(maxDepth))
    val uart = Module(new GameUart(clockFreq, baudRate))
    uart.io.rx := io.rx
    io.tx := uart.io.tx

    val button = RegNext(RegNext(io.buttons(0), false.B), false.B)
    val stable = RegInit(false.B)
    val debounceCycles = clockFreq / 100
    val count = RegInit(0.U(log2Ceil(debounceCycles + 1).W))
    when(button =/= stable) {
        when(count === (debounceCycles - 1).U) { stable := button; count := 0.U }
            .otherwise { count := count + 1.U }
    }.otherwise { count := 0.U }
    val previous = RegNext(stable, false.B)
    val resetPending = RegInit(false.B)
    when(stable && !previous) { resetPending := true.B }
    when(resetPending && game.io.commandReady) { resetPending := false.B }

    game.io.commandValid := uart.io.commandValid || resetPending
    game.io.command := Mux(resetPending, 1.U, uart.io.command)
    game.io.from := Mux(resetPending, 0.U, uart.io.from)
    game.io.to := Mux(resetPending, 0.U, uart.io.to)
    uart.io.commandReady := game.io.commandReady && !resetPending
    uart.io.responseValid := game.io.responseValid
    game.io.responseReady := uart.io.responseReady
    uart.io.responseStatus := game.io.responseStatus
    uart.io.board := game.io.board
    io.led := Cat(0.U(6.W), uart.io.busy, game.io.busy, 0.U(5.W), game.io.status)
}

object Top extends App {
    emitVerilog(new Top, args)
}
