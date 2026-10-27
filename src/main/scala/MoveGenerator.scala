import chisel3._
import chisel3.util._

// Kings, pawns and queens (needed after automatic pawn promotion).
// Square 0 is a8; white pawns move towards row 0, black towards row 7.
class MoveGenerator extends Module {
    val io = IO(new Bundle {
        // Hold these inputs steady while request is high. Drop request after
        // accepting moveValid or moveFinished, before asking for another move.
        val request = Input(Bool())
        val board = Input(Vec(8, Vec(8, UInt(16.W))))
        val whiteTurn = Input(Bool())
        val cursor = Input(UInt(13.W))

        val generatedMove = Output(UInt(12.W))
        val nextCursor = Output(UInt(13.W))
        val moveValid = Output(Bool())
        val moveFinished = Output(Bool())
        val inCheck = Output(Bool())
    })

    val ownPawn = Mux(io.whiteTurn, pieces.white_pawn, pieces.black_pawn)
    val ownKing = Mux(io.whiteTurn, pieces.white_king, pieces.black_king)
    val ownQueen = Mux(io.whiteTurn, pieces.white_queen, pieces.black_queen)
    val enemyPawn = Mux(io.whiteTurn, pieces.black_pawn, pieces.white_pawn)
    val enemyKing = Mux(io.whiteTurn, pieces.black_king, pieces.white_king)
    val enemyQueen = Mux(io.whiteTurn, pieces.black_queen, pieces.white_queen)

    // One bit per square: bit n is true if square n contains this piece.
    // Looking up a single bit is cheaper than selecting a full board entry.
    def squaresContaining(board: Vec[Vec[UInt]], piece: UInt): UInt = {
        VecInit((0 until 64).map { square =>
            board(square / 8)(square % 8) === piece
        }).asUInt
    }
    val emptySquares = squaresContaining(io.board, 0.U)

    // The candidate number is also the move encoding: upper 6 bits = from,
    // lower 6 bits = to. 4096 means that all 64 * 64 pairs have been checked.
    val candidate = RegInit(0.U(13.W))
    val fromSquare = candidate(11, 6)
    val toSquare = candidate(5, 0)
    val fromRow = fromSquare(5, 3)
    val fromCol = fromSquare(2, 0)
    val toRow = toSquare(5, 3)
    val toCol = toSquare(2, 0)
    val movingPiece = io.board(fromRow)(fromCol)
    val targetPiece = io.board(toRow)(toCol)

    // Signed differences avoid wrapping around at the edges of the board.
    val rowDifference = toRow.zext - fromRow.zext
    val colDifference = toCol.zext - fromCol.zext
    val rowDistance = Mux(rowDifference < 0.S, -rowDifference, rowDifference).asUInt
    val colDistance = Mux(colDifference < 0.S, -colDifference, colDifference).asUInt

    val targetEmpty = targetPiece === 0.U
    val targetEnemy = Mux(io.whiteTurn,
        targetPiece >= pieces.black_pawn && targetPiece <= pieces.black_king,
        targetPiece >= pieces.white_pawn && targetPiece <= pieces.white_king)
    val canLand = targetEmpty || (targetEnemy && targetPiece =/= enemyKing)

    val kingMove = movingPiece === ownKing && rowDistance <= 1.U && colDistance <= 1.U

    val forward = Mux(io.whiteTurn, (-1).S(4.W), 1.S(4.W))
    val startingRow = Mux(io.whiteTurn, 6.U, 1.U)
    val middleRow = (fromRow.zext + forward).asUInt
    val pawnStep = rowDifference === forward && colDifference === 0.S && targetEmpty
    val pawnDoubleStep = fromRow === startingRow &&
        rowDifference === Mux(io.whiteTurn, (-2).S, 2.S) &&
        colDifference === 0.S && targetEmpty && io.board(middleRow(2, 0))(fromCol) === 0.U
    val pawnCapture = rowDifference === forward && colDistance === 1.U && targetEnemy
    val pawnMove = movingPiece === ownPawn && (pawnStep || pawnDoubleStep || pawnCapture)

    // A queen follows a straight line or diagonal, with no pieces in between.
    val rowStep = Mux(rowDifference > 0.S, 1.S(5.W), Mux(rowDifference < 0.S, (-1).S(5.W), 0.S(5.W)))
    val colStep = Mux(colDifference > 0.S, 1.S(5.W), Mux(colDifference < 0.S, (-1).S(5.W), 0.S(5.W)))
    val moveDistance = Mux(rowDistance > colDistance, rowDistance, colDistance)
    val pathClear = (1 until 7).map { distance =>
        val row = (fromRow.zext + rowStep * distance.S).asUInt
        val col = (fromCol.zext + colStep * distance.S).asUInt
        distance.U >= moveDistance || emptySquares(Cat(row(2, 0), col(2, 0)))
    }.reduce(_ && _)
    val queenMove = movingPiece === ownQueen && pathClear &&
        (rowDifference === 0.S || colDifference === 0.S || rowDistance === colDistance)

    // Look outwards from the king in eight directions. The first piece on a
    // ray can attack; pieces behind it cannot. Pawns and kings attack nearby.
    def kingAttacked(board: Vec[Vec[UInt]], square: UInt): Bool = {
        val empty = squaresContaining(board, 0.U)
        val queens = squaresContaining(board, enemyQueen)
        val kings = squaresContaining(board, enemyKing)
        val pawns = squaresContaining(board, enemyPawn)
        val directions = Seq((-1, -1), (-1, 0), (-1, 1), (0, -1),
                             (0, 1), (1, -1), (1, 0), (1, 1))
        val attacks = directions.flatMap { case (dr, dc) =>
            var clear = true.B
            (1 to 7).map { distance =>
                val row = square(5, 3).zext + (dr * distance).S(5.W)
                val col = square(2, 0).zext + (dc * distance).S(5.W)
                val inside = row >= 0.S && row < 8.S && col >= 0.S && col < 8.S
                val target = Cat(row.asUInt(2, 0), col.asUInt(2, 0))
                val pawnDirection = if (dr == 0 || dc == 0) false.B
                    else if (dr == -1) io.whiteTurn else !io.whiteTurn
                val nearbyAttack = if (distance == 1)
                    kings(target) || (pawns(target) && pawnDirection)
                    else false.B
                val attack = inside && clear && (queens(target) || nearbyAttack)
                clear = clear && inside && empty(target)
                attack
            }
        }
        attacks.reduce(_ || _)
    }

    val kingSquares = squaresContaining(io.board, ownKing)
    val kingPresent = kingSquares.orR
    val kingSquare = PriorityEncoder(kingSquares)
    io.inCheck := kingPresent && kingAttacked(io.board, kingSquare)

    // Try the candidate on a temporary board to check our king's safety.
    // Promotion does not change occupancy, so it does not affect this check.
    val afterMove = Wire(Vec(8, Vec(8, UInt(16.W))))
    afterMove := io.board
    afterMove(fromRow)(fromCol) := 0.U
    afterMove(toRow)(toCol) := movingPiece
    val kingAfterMove = Mux(movingPiece === ownKing, toSquare, kingSquare)
    val legalMove = fromSquare =/= toSquare && canLand &&
        (kingMove || pawnMove || queenMove) && kingPresent &&
        !kingAttacked(afterMove, kingAfterMove)

    val sIdle :: sScan :: sResult :: Nil = Enum(3)
    val state = RegInit(sIdle)
    val finished = candidate >= 4096.U

    when(!io.request) {
        state := sIdle
    }.otherwise {
        switch(state) {
            is(sIdle) {
                candidate := io.cursor
                state := sScan
            }
            is(sScan) {
                when(finished || legalMove) {
                    state := sResult
                }.elsewhen(movingPiece =/= ownPawn && movingPiece =/= ownKing && movingPiece =/= ownQueen) {
                    // No move can start here; skip all destinations for this square.
                    candidate := (fromSquare +& 1.U) << 6
                }.otherwise {
                    candidate := candidate + 1.U
                }
            }
            // Hold the result until minimax lowers request.
        }
    }

    io.generatedMove := candidate(11, 0)
    io.nextCursor := Mux(finished, 4096.U, candidate + 1.U)
    io.moveValid := io.request && state === sResult && !finished
    io.moveFinished := io.request && state === sResult && finished
}
