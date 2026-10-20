import chisel3._
import chisel3.util._

class Main(depth: Int, color: Boolean) extends Module {
    val io = IO(new Bundle {
        // U-ART Input
        val newBoard = Input(Vec(8, Vec(8, UInt(16.W))))

        // Outputs for Eval 
        val isLate = Output(Bool())

    })

    // Registers
    val Board = RegInit(VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(16.W))))))
    val whiteMaterial = RegInit(0.U(16.W))
    val blackMaterial = RegInit(0.U(16.W))
    val isLateReg = RegInit(false.B)

    // Piece definition 
    object pieces {
        white_pawn   = 0.U(4.W)
        white_bishop = 1.U(4.W)
        white_knight = 2.U(4.W)
        white_rook   = 3.U(4.W)
        white_queen  = 4.U(4.W)
        white_king   = 5.U(4.W)

        black_pawn   = 6.U(4.W)
        black_bishop = 7.U(4.W)
        black_knight = 8.U(4.W)
        black_rook   = 9.U(4.W) 
        black_queen  = 10.U(4.W)
        black_king   = 11.U(4.W)
    }

    // Determines if it is lategame
    when(whiteMaterial + blackMaterial <= 2400.U) {
        io.isLate := true.B
    }

    // Counts the actual material for each side
    whiteMaterial = 











    // IO-REG CONNECTIONS
    io.isLate := isLateReg
}

object Main extends App {
    emitVerilog(new Main(depth = 16, color = true))
}