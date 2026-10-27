from pathlib import Path

connections = [".clock(clock)", ".reset(reset)", ".io_whiteTurn(whiteTurn)",
               ".io_start(start)", ".io_done(done)", ".io_bestMove(bestMove)",
               ".io_bestScore(bestScore)", ".io_watchSquare(watchSquare)",
               ".io_watchPiece(watchPiece)", ".io_sawPiece(sawPiece)"]
for n in range(64):
    connections += [f".io_board_{n // 8}_{n % 8}(board[{n}])",
                    f".io_workingBoard_{n // 8}_{n % 8}(workingBoard[{n}])"]

bench = r'''
`timescale 1ns/1ps
module minimax_native_test;
  reg clock = 0;
  always #5 clock = ~clock;
  reg reset = 1;
  reg start = 0;
  reg whiteTurn = 1;
  reg [15:0] board [0:63];
  wire [15:0] workingBoard [0:63];
  reg [5:0] watchSquare = 0;
  reg [15:0] watchPiece = 0;
  wire sawPiece, done;
  wire [11:0] bestMove;
  wire signed [31:0] bestScore;
  integer n, cycles;
  KingPawnSearchHarness dut (CONNECTIONS);

  task tick;
    begin @(posedge clock); #1; end
  endtask

  task prepare;
    begin
      @(negedge clock); start = 0;
      tick;
      @(negedge clock);
      for (n = 0; n < 64; n = n + 1) board[n] = 0;
    end
  endtask

  task run_search;
    begin
      start = 1;
      cycles = 0;
      tick;
      while (!done && cycles < 20000) begin
        tick;
        cycles = cycles + 1;
      end
      if (done !== 1) $fatal(1, "Search timed out");
      for (n = 0; n < 64; n = n + 1)
        if (workingBoard[n] !== board[n])
          $fatal(1, "Backtracking failed at square %0d: got %0d expected %0d", n, workingBoard[n], board[n]);
    end
  endtask

  task promotion_case(input bit white_side, input bit capture);
    integer from_square, to_square;
    begin
      prepare;
      whiteTurn = white_side;
      board[63] = 6; // White king h1.
      board[7] = 12; // Black king h8.
      from_square = white_side ? 8 : 48;
      to_square = (white_side ? 0 : 56) + capture;
      board[from_square] = white_side ? 1 : 7;
      if (capture) board[to_square] = white_side ? 11 : 5;
      watchSquare = to_square;
      watchPiece = white_side ? 5 : 11;
      run_search;
      if (!sawPiece) $fatal(1, "Promotion never applied");
      if (bestMove !== ((from_square << 6) | to_square))
        $fatal(1, "Wrong best move %0d", bestMove);
      if (white_side ? bestScore <= 500 : bestScore >= -500)
        $fatal(1, "Wrong promotion score %0d", bestScore);
      $display("PASS promotion white=%0d capture=%0d cycles=%0d score=%0d", white_side, capture, cycles, bestScore);
    end
  endtask

  initial begin
    for (n = 0; n < 64; n = n + 1) board[n] = 0;
    repeat (3) tick;
    @(negedge clock); reset = 0;
    if ($test$plusargs("leaf")) begin
      prepare;
      whiteTurn = 1;
      board[0] = 12; board[17] = 5; board[18] = 6;
      run_search;
      if (bestMove !== ((17 << 6) | 9) || bestScore !== 99999)
        $fatal(1, "Mate at the search horizon was missed: move=%0d score=%0d", bestMove, bestScore);
      $display("PASS checkmate at depth-1 horizon");
      $finish;
    end
    promotion_case(1, 0);
    promotion_case(0, 0);
    promotion_case(1, 1);
    promotion_case(0, 1);
    prepare;
    whiteTurn = 0;
    board[0] = 12; board[9] = 5; board[18] = 6;
    run_search;
    if (bestMove !== 0 || bestScore !== 100000) $fatal(1, "Checkmate result incorrect");
    $display("PASS checkmate cycles=%0d", cycles);
    prepare;
    board[0] = 12; board[17] = 5; board[18] = 6;
    run_search;
    if (bestMove !== 0 || bestScore !== 0) $fatal(1, "Stalemate result incorrect");
    $display("PASS stalemate cycles=%0d", cycles);
    $display("ALL NATIVE INTEGRATION CHECKS PASSED");
    $finish;
  end
endmodule
'''
Path("target/minimax_native_test.sv").write_text(bench.replace("CONNECTIONS", ",\n    ".join(connections)))
