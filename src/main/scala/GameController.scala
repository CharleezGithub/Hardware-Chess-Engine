import chisel3._
import chisel3.util._

object ChessPosition {
    // a8 = 0, h1 = 63. Human is white; engine is black.
    val initial: Seq[Seq[Int]] = Seq.tabulate(8, 8) { (row, col) =>
        if (row == 1) 7 else if (row == 6) 1
        else if (row == 0 && col == 4) 12
        else if (row == 7 && col == 4) 6 else 0
    }
}

// Commands: 1 = new game, 2 = get board, 3 = move.
// Replies: 0 = white to move, 2 = white wins, 3 = black wins,
//          4 = draw, 5 = illegal move, 6 = bad command/frame.
class GameController(maxDepth: Int = 2) extends Module {
    val io = IO(new Bundle {
        val commandValid = Input(Bool())
        val commandReady = Output(Bool())
        val command = Input(UInt(8.W))
        val from = Input(UInt(8.W))
        val to = Input(UInt(8.W))
        val responseValid = Output(Bool())
        val responseReady = Input(Bool())
        val responseStatus = Output(UInt(8.W))
        val board = Output(Vec(8, Vec(8, UInt(4.W))))
        val status = Output(UInt(3.W))
        val busy = Output(Bool())
    })
    val initialBoard = VecInit(ChessPosition.initial.map(row => VecInit(row.map(_.U(16.W)))))
    val board = RegInit(initialBoard)
    val gameStatus = RegInit(0.U(3.W))
    val replyStatus = RegInit(0.U(8.W))
    val humanMove = RegInit(0.U(12.W))
    val sIdle :: sValidate :: sPlayHuman :: sStartEngine :: sThink :: sPlayEngine :: sCheckWhite :: sReply :: Nil = Enum(8)
    val state = RegInit(sIdle)
    val search = Module(new minimax(maxDepth))
    val generator = Module(new MoveGenerator)
    search.io.board := board
    search.io.isWhiteTurn := false.B
    search.io.start := state === sStartEngine

    // Share one generator between search, validation and game-over detection.
    val searching = state === sStartEngine || state === sThink
    generator.io.request := Mux(searching, search.io.moveRequest,
        state === sValidate || state === sCheckWhite)
    generator.io.board := Mux(searching, search.io.moveBoard, board)
    generator.io.whiteTurn := Mux(searching, search.io.moveWhiteTurn, true.B)
    generator.io.cursor := Mux(searching, search.io.moveCursor,
        Mux(state === sValidate, humanMove.pad(13), 0.U))
    search.io.generatedMove := generator.io.generatedMove
    search.io.nextCursor := generator.io.nextCursor
    search.io.moveValid := generator.io.moveValid
    search.io.moveFinished := generator.io.moveFinished
    search.io.inCheck := generator.io.inCheck

    def play(move: UInt): Unit = {
        val from = move(11, 6)
        val to = move(5, 0)
        val piece = board(from(5, 3))(from(2, 0))
        val promoted = Mux(piece === pieces.white_pawn && to(5, 3) === 0.U,
            pieces.white_queen, Mux(piece === pieces.black_pawn && to(5, 3) === 7.U,
                pieces.black_queen, piece))
        board(from(5, 3))(from(2, 0)) := 0.U
        board(to(5, 3))(to(2, 0)) := promoted
    }
    val bareKings = !(VecInit((0 until 64).map { square =>
        val piece = board(square / 8)(square % 8)
        piece =/= 0.U && piece =/= pieces.white_king && piece =/= pieces.black_king
    }).asUInt.orR)

    switch(state) {
        is(sIdle) {
            when(io.commandValid) {
                replyStatus := gameStatus
                state := sReply
                switch(io.command) {
                    is(1.U) { board := initialBoard; gameStatus := 0.U; replyStatus := 0.U }
                    is(2.U) { replyStatus := gameStatus }
                    is(3.U) {
                        when(gameStatus === 0.U) {
                            when(io.from < 64.U && io.to < 64.U && io.from =/= io.to) {
                                humanMove := Cat(io.from(5, 0), io.to(5, 0))
                                state := sValidate
                            }.otherwise { replyStatus := 5.U }
                        }
                    }
                }
                when(io.command =/= 1.U && io.command =/= 2.U && io.command =/= 3.U) {
                    replyStatus := 6.U
                }
            }
        }
        is(sValidate) {
            // Start at the submitted move; any different result means illegal.
            when(generator.io.moveValid || generator.io.moveFinished) {
                when(generator.io.moveValid && generator.io.generatedMove === humanMove) {
                    state := sPlayHuman
                }.otherwise { replyStatus := 5.U; state := sReply }
            }
        }
        is(sPlayHuman) { play(humanMove); state := sStartEngine }
        is(sStartEngine) { state := sThink }
        is(sThink) {
            when(search.io.done) {
                when(search.io.hasLegalMove) { state := sPlayEngine }
                    .otherwise {
                        val result = Mux(search.io.rootInCheck, 2.U, 4.U)
                        gameStatus := result; replyStatus := result; state := sReply
                    }
            }
        }
        is(sPlayEngine) { play(search.io.bestMove); state := sCheckWhite }
        is(sCheckWhite) {
            when(bareKings || generator.io.moveFinished) {
                val result = Mux(!bareKings && generator.io.inCheck, 3.U, 4.U)
                gameStatus := result; replyStatus := result; state := sReply
            }.elsewhen(generator.io.moveValid) { replyStatus := 0.U; state := sReply }
        }
        is(sReply) { when(io.responseReady) { state := sIdle } }
    }
    io.commandReady := state === sIdle
    io.responseValid := state === sReply
    io.responseStatus := replyStatus
    io.status := gameStatus
    io.busy := state =/= sIdle
    for (row <- 0 until 8; col <- 0 until 8) io.board(row)(col) := board(row)(col)(3, 0)
}
