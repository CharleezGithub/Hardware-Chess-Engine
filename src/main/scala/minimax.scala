
import chisel3._
import chisel3.util._

// samling af info der skal bruges mellem hver branch
class SearchReg extends Bundle {
    val nextMove = UInt(13.W)
    val activeMove = UInt(12.W)
    val bestMove = UInt(12.W)
    val bestScore = SInt(32.W)
    val isMaximizing = Bool()

    // info til at gå tilbage i træet
    val playedMove = UInt(12.W)
    val movedPiece = UInt(16.W)
    val capturedPiece = UInt(16.W)

    val hasMove = Bool()
    val hasScored = Bool()
}

class minimax(maxDepth: Int) extends Module {
    if (maxDepth < 1) {
        throw new IllegalArgumentException("maxDepth must be at least 1")
    }

    val io = IO(new Bundle {
        // Inputs
        val board = Input(Vec(8, Vec(8, UInt(16.W))))
        val start = Input(Bool())
        val isWhiteTurn = Input(Bool())

        // Input fra MoveGenerator
        val moveValid = Input(Bool())
        val moveFinished = Input(Bool())
        val generatedMove = Input(UInt(12.W))
        val nextCursor = Input(UInt(13.W))
        val inCheck = Input(Bool())

        // Output til MoveGenerator
        val moveRequest = Output(Bool())
        val moveBoard = Output(Vec(8, Vec(8, UInt(16.W))))
        val moveCursor = Output(UInt(13.W))
        val moveWhiteTurn = Output(Bool())

        // Outputs
        val bestMove = Output(UInt(12.W))
        val bestScore = Output(SInt(32.W))
        val busy = Output(Bool())
        val done = Output(Bool())
        val hasLegalMove = Output(Bool())
        val rootInCheck = Output(Bool())
    })

    // Registers

    // reg for at gemme info mellem hver depth branch
    val searchReg = Reg(Vec(maxDepth + 1, new SearchReg))

    // nuværende depth reg
    val currentDepth = RegInit(0.U(log2Ceil(maxDepth + 1).W))

    // gemmer hvilken spiller der starter
    val whiteTurnReg = RegInit(true.B)

    // board der ændres under søgningen
    val workingBoard = Reg(Vec(8, Vec(8, UInt(16.W))))

    // gemmer det bedste resultat
    val bestMoveReg = RegInit(0.U(12.W))
    val bestScoreReg = RegInit(0.S(32.W))

    // score der returneres fra en branch
    val returnedScore = RegInit(0.S(32.W))
    val rootCheckReg = RegInit(false.B)

    val maxScore = 1000000.S(32.W)
    val minScore = (-1000000).S(32.W)
    val mateScore = 100000.S(32.W)

    // evaluator
    val evaluator = Module(new MatrixBuffer8x8())
    evaluator.io.boardState := workingBoard

    // hvem har tur ved nuværende depth
    val currentWhiteTurn = Mux(
        currentDepth(0),
        !whiteTurnReg,
        whiteTurnReg
    )

    // MoveGenerator
    io.moveBoard := workingBoard
    io.moveCursor := searchReg(currentDepth).nextMove
    io.moveWhiteTurn := currentWhiteTurn

    // fsm for at komme rundt i depths
    val sIdle :: sInit :: sGetMove :: sMakeMove :: sEvaluate :: sBacktrack :: sUpdate :: sDone :: Nil = Enum(8)

    val state = RegInit(sIdle)

    // The generator scans while we wait and holds its answer until we leave.
    io.moveRequest := state === sGetMove || state === sEvaluate

    switch(state) {

        is(sIdle) {
            when(io.start) {
                // gem start position og spiller
                whiteTurnReg := io.isWhiteTurn
                workingBoard := io.board
                state := sInit
            }
        }

        is(sInit) {
            // start søgningen fra depth 0
            currentDepth := 0.U

            bestMoveReg := 0.U
            bestScoreReg := 0.S
            returnedScore := 0.S
            rootCheckReg := false.B

            // initialiser root
            searchReg(0).nextMove := 0.U
            searchReg(0).activeMove := 0.U
            searchReg(0).bestMove := 0.U
            searchReg(0).isMaximizing := whiteTurnReg

            searchReg(0).hasMove := false.B
            searchReg(0).hasScored := false.B

            // white maximerer og black minimerer
            when(whiteTurnReg) {
                searchReg(0).bestScore := minScore
            }.otherwise {
                searchReg(0).bestScore := maxScore
            }

            state := sGetMove
        }

        is(sGetMove) {
            // hent næste lovlige move fra MoveGenerator
            when(io.moveValid) {
                // gem move vi skal undersøge
                searchReg(currentDepth).activeMove :=
                    io.generatedMove

                // gem hvor MoveGenerator skal fortsætte
                searchReg(currentDepth).nextMove :=
                    io.nextCursor

                searchReg(currentDepth).hasMove := true.B

                state := sMakeMove

            }.elsewhen(io.moveFinished) {
                // ingen flere moves ved denne depth

                when(currentDepth === 0.U) {
                    rootCheckReg := io.inCheck
                    // hele søgningen er færdig
                    when(searchReg(0).hasScored) {
                        bestMoveReg := searchReg(0).bestMove
                        bestScoreReg := searchReg(0).bestScore
                    }.otherwise {
                        // ingen lovlige moves fra start position
                        bestMoveReg := 0.U

                        when(io.inCheck) {
                            bestScoreReg := Mux(
                                whiteTurnReg,
                                -mateScore,
                                mateScore
                            )
                        }.otherwise {
                            bestScoreReg := 0.S
                        }
                    }

                    state := sDone

                }.otherwise {
                    // returner bedste score fra denne depth
                    when(searchReg(currentDepth).hasScored) {
                        returnedScore :=
                            searchReg(currentDepth).bestScore

                    }.otherwise {
                        // ingen lovlige moves = mat eller pat
                        when(io.inCheck) {
                            returnedScore := Mux(
                                currentWhiteTurn,
                                -mateScore + currentDepth.zext,
                                mateScore - currentDepth.zext
                            )
                        }.otherwise {
                            returnedScore := 0.S
                        }
                    }

                    state := sBacktrack
                }
            }
        }

        is(sMakeMove) {
            // find start og slut position
            val move = searchReg(currentDepth).activeMove

            val fromSquare = move(11, 6)
            val toSquare = move(5, 0)

            val fromRow = fromSquare(5, 3)
            val fromCol = fromSquare(2, 0)

            val toRow = toSquare(5, 3)
            val toCol = toSquare(2, 0)

            // næste depth
            val nextDepth = currentDepth + 1.U

            // gem info til at undo move
            searchReg(nextDepth).playedMove := move

            searchReg(nextDepth).movedPiece :=
                workingBoard(fromRow)(fromCol)

            searchReg(nextDepth).capturedPiece :=
                workingBoard(toRow)(toCol)

            // udfør move på workingBoard
            // Automatically promote to a queen. movedPiece above keeps the
            // original pawn, so backtracking restores it correctly.
            val piece = workingBoard(fromRow)(fromCol)
            val promotesWhite = piece === pieces.white_pawn && toRow === 0.U
            val promotesBlack = piece === pieces.black_pawn && toRow === 7.U
            workingBoard(toRow)(toCol) := Mux(promotesWhite, pieces.white_queen,
                Mux(promotesBlack, pieces.black_queen, piece))

            workingBoard(fromRow)(fromCol) := 0.U

            // initialiser næste depth
            searchReg(nextDepth).nextMove := 0.U
            searchReg(nextDepth).activeMove := 0.U
            searchReg(nextDepth).bestMove := 0.U

            searchReg(nextDepth).isMaximizing :=
                !searchReg(currentDepth).isMaximizing

            searchReg(nextDepth).hasMove := false.B
            searchReg(nextDepth).hasScored := false.B

            when(searchReg(currentDepth).isMaximizing) {
                searchReg(nextDepth).bestScore := maxScore
            }.otherwise {
                searchReg(nextDepth).bestScore := minScore
            }

            currentDepth := nextDepth

            // evaluer hvis maxDepth er nået
            when(nextDepth === maxDepth.U) {
                state := sEvaluate
            }.otherwise {
                state := sGetMove
            }
        }

        is(sEvaluate) {
            // Even at the depth limit, check for mate/stalemate before using
            // the static score. Finding one legal move is enough here.
            when(io.moveValid) {
                returnedScore := evaluator.io.score
                state := sBacktrack
            }.elsewhen(io.moveFinished) {
                returnedScore := Mux(io.inCheck,
                    Mux(currentWhiteTurn, -mateScore + currentDepth.zext,
                        mateScore - currentDepth.zext), 0.S)
                state := sBacktrack
            }
        }

        is(sBacktrack) {
            // find move som førte til denne depth
            val playedMove = searchReg(currentDepth).playedMove

            val fromSquare = playedMove(11, 6)
            val toSquare = playedMove(5, 0)

            val fromRow = fromSquare(5, 3)
            val fromCol = fromSquare(2, 0)

            val toRow = toSquare(5, 3)
            val toCol = toSquare(2, 0)

            // restore board til parent position
            workingBoard(fromRow)(fromCol) :=
                searchReg(currentDepth).movedPiece

            workingBoard(toRow)(toCol) :=
                searchReg(currentDepth).capturedPiece

            // gå tilbage til parent depth
            currentDepth := currentDepth - 1.U

            state := sUpdate
        }

        is(sUpdate) {
            // sammenlign score fra child med parent bestScore
            val betterScore = Mux(
                searchReg(currentDepth).isMaximizing,
                returnedScore > searchReg(currentDepth).bestScore,
                returnedScore < searchReg(currentDepth).bestScore
            )

            // gem resultat hvis det er første eller bedste move
            when(!searchReg(currentDepth).hasScored || betterScore) {
                searchReg(currentDepth).bestScore := returnedScore

                searchReg(currentDepth).bestMove :=
                    searchReg(currentDepth).activeMove
            }

            searchReg(currentDepth).hasScored := true.B

            // fortsæt med næste move ved samme depth
            state := sGetMove
        }

        is(sDone) {
            // søgningen er færdig
            when(!io.start) {
                state := sIdle
            }
        }
    }

    // IO connections
    io.bestMove := bestMoveReg
    io.bestScore := bestScoreReg

    io.busy := state =/= sIdle && state =/= sDone
    io.done := state === sDone
    io.hasLegalMove := state === sDone && searchReg(0).hasMove
    io.rootInCheck := rootCheckReg
}
