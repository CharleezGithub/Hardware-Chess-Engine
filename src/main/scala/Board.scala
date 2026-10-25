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
        //val newBoard = Input(Vec(8, Vec(8, UInt(16.W))))

        // Outputs for Eval 
        val isLate = Output(Bool())
        val board = Output(Vec(8, Vec(8, UInt(16.W)))) // To share with UART module later
    })

    // Registers
    val Board = RegInit(VecInit(Seq.fill(8)(VecInit(Seq.fill(8)(0.U(16.W))))))
    val whiteMaterial = RegInit(0.U(16.W))
    val blackMaterial = RegInit(0.U(16.W))
    val isLateReg = RegInit(false.B)

    
    // Determines if it is lategame
    when(whiteMaterial + blackMaterial <= 2400.U) {
        io.isLate := true.B
    }

    // Calculates the material value of a piece
    def materialValue(piece: UInt): UInt = {
        MuxLookup(piece, 0.U(16.W))(Seq(
            pieces.white_pawn   -> 100.U(16.W),
            pieces.white_bishop -> 300.U(16.W),
            pieces.white_knight -> 300.U(16.W),
            pieces.white_rook   -> 500.U(16.W),
            pieces.white_queen  -> 900.U(16.W),
            pieces.black_pawn   -> 100.U(16.W),
            pieces.black_bishop -> 300.U(16.W),
            pieces.black_knight -> 300.U(16.W),
            pieces.black_rook   -> 500.U(16.W),
            pieces.black_queen  -> 900.U(16.W)
        ))
    }

    // Generate material values for all white pieces
    val whiteValues = for {
        row <- 0 until 8
        col <- 0 until 8
    } yield {
        val piece = Board(row)(col)(3, 0)
        Mux(
            piece >= pieces.white_pawn &&
            piece <= pieces.white_king,
            materialValue(piece),
            0.U(16.W)
        )
    }

    // Generate material values for all black pieces
    val blackValues = for {
        row <- 0 until 8
        col <- 0 until 8
    } yield {
        val piece = Board(row)(col)(3, 0)
        Mux(
            piece >= pieces.black_pawn &&
            piece <= pieces.black_king,
            materialValue(piece),
            0.U(16.W)
        )
    }

    // Sum the material values
    whiteMaterial := whiteValues.reduce(_ +& _)(15, 0)
    blackMaterial := blackValues.reduce(_ +& _)(15, 0)


    // IO-REG CONNECTIONS
    io.isLate := isLateReg
    io.board := Board
}