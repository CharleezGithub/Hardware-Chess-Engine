import chisel3._
import chisel3.util._

// Piece definition 
object pieces {
    val  white_pawn   = 1.U(4.W)
    val white_bishop = 2.U(4.W)
    val white_knight = 3.U(4.W)
    val white_rook   = 4.U(4.W)
    val white_queen  = 5.U(4.W)
    val white_king   = 6.U(4.W)

    val black_pawn   = 7.U(4.W)
    val black_bishop = 8.U(4.W)
    val black_knight = 9.U(4.W)
    val black_rook   = 10.U(4.W) 
    val black_queen  = 11.U(4.W)
    val black_king   = 12.U(4.W)
}


class Board(depth: Int, color: Boolean) extends Module {
    val io = IO(new Bundle {
        // U-ART Input, TODO: when we have UART rx signal
        val newBoard = Input(Vec(8, Vec(8, UInt(4.W))))
        val updBoard = Input(Bool())
        // Outputs for Eval 
        val board = Output(Vec(8, Vec(8, UInt(4.W)))) // To transmit the board state via UART
        val playerTurn = Output(Bool()) // True for white, false for black
    })

    // Registers
    val initSeq = Seq.tabulate(8, 8) { (r, c) => // Just have a king piece to move around for now
    if (r == 4 && c == 4) pieces.white_king else 0.U(4.W)
    }
    val Board = RegInit(VecInit(initSeq.map(row => VecInit(row))))
    val playerTurnReg = RegInit(color.B) // True for white, false for black
    when(io.updBoard) {
        Board := io.newBoard
    }


    // IO-REG CONNECTIONS
    io.board := Board
    io.playerTurn := playerTurnReg
}