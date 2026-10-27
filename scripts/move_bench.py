"""Compare native RTL move enumeration with a small independent chess oracle."""
from pathlib import Path
import random


def color(piece):
    return piece in (1, 5, 6)


def reaches(board, source, target, attack=False):
    piece = board[source]
    row, col = divmod(source, 8)
    tr, tc = divmod(target, 8)
    dr, dc = tr - row, tc - col
    if source == target or piece == 0:
        return False
    kind = piece if color(piece) else piece - 6
    if kind == 6:
        return max(abs(dr), abs(dc)) == 1
    if kind == 1:
        direction = -1 if color(piece) else 1
        if attack:
            return dr == direction and abs(dc) == 1
        if dc == 0 and board[target] == 0:
            return dr == direction or (row == (6 if color(piece) else 1) and
                dr == 2 * direction and board[source + direction * 8] == 0)
        return dr == direction and abs(dc) == 1 and board[target] != 0
    if kind == 5 and (dr == 0 or dc == 0 or abs(dr) == abs(dc)):
        rs, cs = (dr > 0) - (dr < 0), (dc > 0) - (dc < 0)
        return all(board[(row + step * rs) * 8 + col + step * cs] == 0
                   for step in range(1, max(abs(dr), abs(dc))))
    return False


def checked(board, white):
    king = board.index(6 if white else 12)
    return any(piece and color(piece) != white and reaches(board, source, king, True)
               for source, piece in enumerate(board))


def legal_moves(board, white):
    result = []
    for source, piece in enumerate(board):
        if not piece or color(piece) != white:
            continue
        for target, victim in enumerate(board):
            if victim and (color(victim) == white or victim in (6, 12)):
                continue
            if not reaches(board, source, target):
                continue
            trial = board.copy()
            trial[source], trial[target] = 0, piece
            if not checked(trial, white):
                result.append((source << 6) | target)
    return result


def generate():
    connections = [".clock(clock)", ".reset(reset)", ".io_request(request)",
        ".io_whiteTurn(white)", ".io_cursor(cursor)", ".io_moveValid(valid)",
        ".io_moveFinished(finished)", ".io_generatedMove(move)",
        ".io_nextCursor(next_cursor)", ".io_inCheck(in_check)"]
    connections += [f".io_board_{n // 8}_{n % 8}(board[{n}])" for n in range(64)]
    text = r'''
`timescale 1ns/1ps
module move_test;
reg clock=0, reset=1, request=0, white=1;
always #5 clock=~clock;
reg [15:0] board[0:63];
reg [12:0] cursor=0;
wire valid, finished, in_check;
wire [11:0] move;
wire [12:0] next_cursor;
integer n, cycles;
MoveGenerator dut(CONNECTIONS);
task tick; begin @(posedge clock); #1; end endtask
task ask;
begin
  @(negedge clock); request=1;
  cycles=0; tick;
  while (!valid && !finished && cycles<4200) begin tick; cycles=cycles+1; end
  if(cycles>=4200) $fatal(1,"Move generator timed out");
end
endtask
task release_request;
begin @(negedge clock); request=0; tick; end
endtask
initial begin
for(n=0;n<64;n=n+1) board[n]=0;
repeat(3) tick;
@(negedge clock); reset=0;
'''.replace("CONNECTIONS", ",\n".join(connections))
    fixtures = [{56: 6, 51: 1, 7: 12, 42: 7, 44: 7},
                {60: 6, 0: 12, 4: 11, 52: 1, 43: 7},
                {0: 12, 9: 5, 18: 6}, {0: 12, 17: 5, 18: 6},
                {63: 6, 7: 12, 8: 1, 1: 11}, {63: 6, 7: 12, 48: 7, 57: 5}]
    rng = random.Random(7301)
    for _ in range(10):
        cells = rng.sample(range(64), 6)
        # Nonadjacent kings; arbitrary supported pieces exercise blockers/pins.
        if max(abs(cells[0] // 8 - cells[1] // 8), abs(cells[0] % 8 - cells[1] % 8)) > 1:
            fixtures.append(dict(zip(cells, [6, 12, 1, 7, 5, 11])))
    for case, occupied in enumerate(fixtures):
        board = [occupied.get(n, 0) for n in range(64)]
        for white in (True, False):
            text += "release_request;\n@(negedge clock);\n"
            text += "\n".join(f"board[{n}]=16'd{piece};" for n, piece in enumerate(board))
            text += f"\nwhite={int(white)}; cursor=0;\n"
            for expected in legal_moves(board, white):
                text += f'''ask;
if(!valid || finished || move!==12'd{expected} || next_cursor!==13'd{expected + 1})
  $fatal(1,"Enumeration mismatch case {case} side {white}: move=%0d expected={expected}",move);
if(in_check!==1'b{int(checked(board, white))}) $fatal(1,"Wrong check status");
tick;
if(move!==12'd{expected} || !valid) $fatal(1,"Result was not held");
release_request; cursor=13'd{expected + 1};
'''
            text += f'''ask;
if(!finished || valid || next_cursor!==4096) $fatal(1,"Enumeration did not finish");
if(in_check!==1'b{int(checked(board, white))}) $fatal(1,"Wrong terminal check status");
'''
    text += '$display("PASS complete legal-move enumeration against independent oracle"); $finish; end\nendmodule\n'
    Path("generated/moves/move_test.sv").write_text(text)


if __name__ == "__main__":
    generate()
