import chisel3._
import chisel3.util._

// Legal moves and king safety for all six piece types.
// Square 0 is a8; white pawns move towards row 0, black towards row 7.
class MoveGenerator extends Module {
    val io = IO(new Bundle {
        // Hold these inputs steady while request is high. Drop request after
        // accepting moveValid or moveFinished, before asking for another move.
        val request = Input(Bool())
        val board = Input(Vec(8, Vec(8, UInt(4.W))))
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
    val ownBishop = Mux(io.whiteTurn, pieces.white_bishop, pieces.black_bishop)
    val ownKnight = Mux(io.whiteTurn, pieces.white_knight, pieces.black_knight)
    val ownRook = Mux(io.whiteTurn, pieces.white_rook, pieces.black_rook)
    val ownQueen = Mux(io.whiteTurn, pieces.white_queen, pieces.black_queen)
    val enemyPawn = Mux(io.whiteTurn, pieces.black_pawn, pieces.white_pawn)
    val enemyKing = Mux(io.whiteTurn, pieces.black_king, pieces.white_king)
    val enemyBishop = Mux(io.whiteTurn, pieces.black_bishop, pieces.white_bishop)
    val enemyKnight = Mux(io.whiteTurn, pieces.black_knight, pieces.white_knight)
    val enemyRook = Mux(io.whiteTurn, pieces.black_rook, pieces.white_rook)
    val enemyQueen = Mux(io.whiteTurn, pieces.black_queen, pieces.white_queen)

    // One bit per square: bit n is true if square n contains this piece.
    // Looking up a single bit is cheaper than selecting a full board entry.
    def squaresContaining(board: Vec[Vec[UInt]], piece: UInt): UInt = {
        VecInit((0 until 64).map { square =>
            board(square / 8)(square % 8) === piece
        }).asUInt
    }
    val emptySquares = squaresContaining(io.board, 0.U)

    val ownSquares = VecInit(io.board.flatten.map { p =>
        Mux(io.whiteTurn, p >= pieces.white_pawn && p <= pieces.white_king,
            p >= pieces.black_pawn && p <= pieces.black_king)
    }).asUInt
    val capturable = VecInit(io.board.flatten.map { p =>
        Mux(io.whiteTurn, p >= pieces.black_pawn && p < pieces.black_king,
            p >= pieces.white_pawn && p < pieces.white_king)
    }).asUInt

    // Return a bitboard shifted by a fixed chess displacement. Mask the
    // outer files before shifting so a step cannot wrap into another rank.
    def shift(squares: UInt, dr: Int, dc: Int): UInt = {
        val files = (0 until 64).filter { n =>
            val col = n % 8 + dc
            col >= 0 && col < 8
        }.foldLeft(BigInt(0))((mask, n) => mask.setBit(n))
        val clipped = squares & files.U(64.W)
        val distance = 8 * dr + dc
        if (distance > 0) (clipped << distance)(63, 0)
        else (clipped >> -distance).pad(64)
    }

    val fromSquare = RegInit(0.U(6.W))
    val fromRow = fromSquare(5, 3)
    val fromCol = fromSquare(2, 0)
    val movingPiece = io.board(fromRow)(fromCol)
    val source = UIntToOH(fromSquare, 64)

    val directions = Seq((-1, -1), (-1, 0), (-1, 1), (0, -1),
                         (0, 1), (1, -1), (1, 0), (1, 1))
    val knightOffsets = Seq((-2, -1), (-2, 1), (-1, -2), (-1, 2),
                            (1, -2), (1, 2), (2, -1), (2, 1))
    val kingTargets = directions.map { case (dr, dc) => shift(source, dr, dc) }.reduce(_ | _)
    val knightTargets = knightOffsets.map { case (dr, dc) => shift(source, dr, dc) }.reduce(_ | _)

    // Include the first occupied square on each ray, but propagate only
    // through empty squares. Friendly pieces and enemy kings are masked below.
    val sliderTargets = directions.map { case (dr, dc) =>
        val slider = if (dr == 0 || dc == 0) ownRook else ownBishop
        var frontier = Mux(movingPiece === ownQueen || movingPiece === slider, source, 0.U)
        (1 to 7).map { _ =>
            val next = shift(frontier, dr, dc)
            frontier = next & emptySquares
            next
        }.reduce(_ | _)
    }.reduce(_ | _)

    val pawnStep = Mux(io.whiteTurn, shift(source, -1, 0), shift(source, 1, 0))
    val singlePush = pawnStep & emptySquares
    val doublePush = Mux(io.whiteTurn, shift(singlePush, -1, 0), shift(singlePush, 1, 0)) & emptySquares
    val pawnCaptures = (shift(pawnStep, 0, -1) | shift(pawnStep, 0, 1)) & capturable
    val pawnTargets = singlePush | pawnCaptures |
        Mux(fromRow === Mux(io.whiteTurn, 6.U, 1.U), doublePush, 0.U)

    val targets = (emptySquares | capturable) & (sliderTargets |
        Mux(movingPiece === ownKing, kingTargets, 0.U) |
        Mux(movingPiece === ownKnight, knightTargets, 0.U) |
        Mux(movingPiece === ownPawn, pawnTargets, 0.U))

    val remainingTargets = RegInit(0.U(64.W))
    val toSquare = PriorityEncoder(remainingTargets)
    val toRow = toSquare(5, 3)
    val toCol = toSquare(2, 0)

    // Look outwards from the king in eight directions. The first piece on a
    // ray can attack; pieces behind it cannot. Pawns and kings attack nearby.
    def kingAttacked(board: Vec[Vec[UInt]], square: UInt): Bool = {
        val empty = squaresContaining(board, 0.U)
        val queens = squaresContaining(board, enemyQueen)
        val rooks = squaresContaining(board, enemyRook)
        val bishops = squaresContaining(board, enemyBishop)
        val knights = squaresContaining(board, enemyKnight)
        val kings = squaresContaining(board, enemyKing)
        val pawns = squaresContaining(board, enemyPawn)
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
                val slider = if (dr == 0 || dc == 0) rooks(target) else bishops(target)
                val attack = inside && clear && (queens(target) || slider || nearbyAttack)
                clear = clear && inside && empty(target)
                attack
            }
        }
        val knightAttacks = knightOffsets.map { case (dr, dc) =>
            val row = square(5, 3).zext + dr.S(5.W)
            val col = square(2, 0).zext + dc.S(5.W)
            val inside = row >= 0.S && row < 8.S && col >= 0.S && col < 8.S
            inside && knights(Cat(row.asUInt(2, 0), col.asUInt(2, 0)))
        }
        (attacks ++ knightAttacks).reduce(_ || _)
    }

    val kingSquares = squaresContaining(io.board, ownKing)
    val kingPresent = kingSquares.orR
    val kingSquare = PriorityEncoder(kingSquares)
    io.inCheck := kingPresent && kingAttacked(io.board, kingSquare)

    // Try the candidate on a temporary board to check our king's safety.
    // Promotion does not change occupancy, so it does not affect this check.
    val afterMove = Wire(Vec(8, Vec(8, UInt(4.W))))
    afterMove := io.board
    afterMove(fromRow)(fromCol) := 0.U
    afterMove(toRow)(toCol) := movingPiece
    val kingAfterMove = Mux(movingPiece === ownKing, toSquare, kingSquare)
    val safe = kingPresent && !kingAttacked(afterMove, kingAfterMove)

    // The public cursor is still an inclusive lower bound on (from << 6 | to).
    // Selecting the lowest remaining bit retains the order used by minimax and
    // lets the controller validate a human move starting at that exact cursor.
    def atOrAbove(square: UInt): UInt = (~0.U(64.W) << square)(63, 0)

    val remainingSources = RegInit(0.U(64.W))
    val firstTarget = RegInit(0.U(6.W))
    val move = Cat(fromSquare, toSquare)
    val sIdle :: sSource :: sGenerate :: sTest :: sValid :: sFinished :: Nil = Enum(6)
    val state = RegInit(sIdle)
    when(!io.request) {
        state := sIdle
    }.otherwise {
        switch(state) {
            is(sIdle) {
                remainingSources := ownSquares & atOrAbove(io.cursor(11, 6))
                state := Mux(io.cursor(12), sFinished, sSource)
            }
            is(sSource) {
                val nextSource = PriorityEncoder(remainingSources)
                fromSquare := nextSource
                firstTarget := Mux(nextSource === io.cursor(11, 6), io.cursor(5, 0), 0.U)
                remainingSources := remainingSources & (remainingSources - 1.U)
                state := Mux(remainingSources.orR, sGenerate, sFinished)
            }
            is(sGenerate) {
                val candidates = targets & atOrAbove(firstTarget)
                remainingTargets := candidates
                state := Mux(candidates.orR, sTest, sSource)
            }
            is(sTest) {
                when(safe) {
                    state := sValid
                }.otherwise {
                    val rest = remainingTargets & (remainingTargets - 1.U)
                    remainingTargets := rest
                    state := Mux(rest.orR, sTest, sSource)
                }
            }
            // sValid and sFinished hold their result until request falls.
        }
    }
    io.generatedMove := move
    io.nextCursor := Mux(state === sFinished, 4096.U, move +& 1.U)
    io.moveValid := io.request && state === sValid
    io.moveFinished := io.request && state === sFinished
}
