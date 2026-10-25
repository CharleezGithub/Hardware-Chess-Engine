import chisel3._
import chisel3.util._

class Top extends Module {
  val io = IO(new Bundle {
    // UART interface
    val tx = Output(Bool())
    //val rx = Input(Bool())

    // other hardware peripherals
    val led = Output(UInt(16.W))
    val buttons = Input(UInt(5.W))
  })

  // Instantiate the UART transmitter and chess board modules
  val board = Module(new Board(depth = 2, color = true))

  val uart = Module(new UartTop(baudRate = 115200, clockFreq = 100000000)) 

  // Hardware peripherals regs
  val ledReg = RegInit(0.U(16.W))

  // Connect the UART outputs to the top-level IO
  io.led := ledReg
  ledReg := uart.io.busy // Indicate UART busy state on LED 0
  io.tx := uart.io.tx
  // Synchronize asynchronous button input to avoid metastability
  val btnSync = RegNext(RegNext(io.buttons(0).asBool, false.B), false.B)

  // Debounce counter and register
  val btnDebounce = RegInit(false.B )
  val debounceCounter = RegInit(0.U(20.W)) // 20-bit counter for debouncing
  when (btnSync =/= btnDebounce) { // Check if the button state has changed
    debounceCounter := debounceCounter + 1.U
    when (debounceCounter === 1000000.U) { // Adjust the debounce time as needed
      btnDebounce := btnSync
      debounceCounter := 0.U
    }
  }.otherwise {
    debounceCounter := 0.U
  }
  
  val btnPrev = RegNext(btnDebounce, false.B)

  uart.io.sendData := btnDebounce && !btnPrev // Use the debounced button signal to trigger data send
  uart.io.board := board.io.board // Connect the chess board to UART for transmission
}

object Top extends App {
  emitVerilog(new Top)
}