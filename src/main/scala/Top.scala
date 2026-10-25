import chisel3._
import chisel3.util._

class Top extends Module {
  val io = IO(new Bundle {
    // UART interface
    val tx = Output(Bool())
    val rx = Input(Bool())

    // other hardware peripherals
    val led = Output(UInt(16.W))
    val buttons = Input(UInt(5.W))
  })

  // Instantiate the UART transmitter and chess board modules
  val board = Module(new Board(depth = 2, color = true))

  val updBoard = WireDefault(false.B)

  board.io.updBoard := updBoard

  val uart = Module(new UartTop(baudRate = 115200, clockFreq = 100000000)) 

  // Hardware peripherals regs
  val ledReg = RegInit(0.U(16.W))

  // Connect the UART outputs to the top-level IO
  io.led := ledReg
  ledReg := uart.io.busy // Indicate UART busy state on LED 0
  io.tx := uart.io.tx
  uart.io.rx := io.rx

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

  val newBoard = RegInit(false.B)
  when (uart.io.boardValid || newBoard) { // When a new board is received via UART
    newBoard := true.B // Set the flag to indicate a new board is available 
    when(board.io.playerTurn) { // If it's player's turn, update the board
      board.io.newBoard := uart.io.boardOut
      newBoard := false.B // Reset the flag after updating the board
      updBoard := true.B // Indicate that the board has been updated
    }. otherwise {
      board.io.newBoard := board.io.board // Keep the current board if it's not player's turn
    }
  }.otherwise {
    board.io.newBoard := board.io.board 
  }
}

object Top extends App {
  emitVerilog(new Top)
}