////////////////////////////////////////////////////////////////////////////////////////////
//                                        CONVENTIONS
//  Chess-piece encryptions: 0=empty, 1=pawn, 2=knight, 3=bishop, 4=rook, 5=wueen, 6=king
//
//
//
////////////////////////////////////////////////////////////////////////////////////////////

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

    // Determines if it is lategame
    when(whiteMaterial + blackMaterial <= 2400.U ) {
        io.isLate := true.B
    }

    // Counts the actual material for each side
    











    // IO-REG CONNECTIONS
    io.isLate := isLateReg
}

object Main extends App {
    emitVerilog(new Main(depth = 16, color = true))
}