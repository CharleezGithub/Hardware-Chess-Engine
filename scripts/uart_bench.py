"""Generate native UART test and interactive benches for the exact Top RTL."""
from pathlib import Path

COMMON = r'''
`timescale 1ns/1ps
module uart_test;
  reg clock = 0;
  always #5 clock = ~clock;
  reg reset = 1, rx = 1;
  wire tx;
  wire [15:0] led;
  reg [4:0] buttons = 0;
  reg [7:0] response [0:34];
  reg [7:0] saved [0:31];
  integer n;
  Top dut(.clock(clock), .reset(reset), .io_rx(rx), .io_tx(tx), .io_led(led), .io_buttons(buttons));
  task tick; begin @(posedge clock); #1; end endtask
  task send_byte(input [7:0] value);
    integer bit_number;
    begin
      @(negedge clock); rx = 0;
      repeat(10) @(negedge clock);
      for(bit_number = 0; bit_number < 8; bit_number = bit_number + 1) begin
        rx = value[bit_number];
        repeat(10) @(negedge clock);
      end
      rx = 1;
      repeat(10) @(negedge clock);
    end
  endtask
  task receive_byte(output [7:0] value);
    integer bit_number;
    begin
      @(negedge tx);
      repeat(15) tick;
      for(bit_number = 0; bit_number < 8; bit_number = bit_number + 1) begin
        value[bit_number] = tx;
        repeat(10) tick;
      end
      if(tx !== 1) $fatal(1, "UART stop bit missing");
    end
  endtask
  task exchange_raw(input [7:0] header, command, from_square, to_square, check);
    integer index;
    reg [7:0] checksum;
    begin
      fork
        begin
          send_byte(header); send_byte(command); send_byte(from_square);
          send_byte(to_square); send_byte(check);
        end
        begin
          for(index = 0; index < 35; index = index + 1) receive_byte(response[index]);
        end
      join
      if(response[0] !== 8'h55) $fatal(1, "Reply header corrupt: %h", response[0]);
      checksum = 0;
      for(index = 1; index < 34; index = index + 1) checksum = checksum ^ response[index];
      if(checksum !== response[34]) $fatal(1, "Reply checksum corrupt");
    end
  endtask
  task exchange(input [7:0] command, from_square, to_square, corrupt);
    begin exchange_raw(8'hAA, command, from_square, to_square,
      command ^ from_square ^ to_square ^ corrupt); end
  endtask
  function [3:0] piece(input integer square);
    begin
      if(square % 2 == 0) piece = response[2 + square / 2] >> 4;
      else piece = response[2 + square / 2] & 15;
    end
  endfunction
  task expect_status(input [7:0] status);
    begin if(response[1] !== status) $fatal(1, "Status %0d, expected %0d", response[1], status); end
  endtask
  task save_board;
    begin for(n = 0; n < 32; n = n + 1) saved[n] = response[n + 2]; end
  endtask
  task unchanged;
    begin for(n = 0; n < 32; n = n + 1)
      if(saved[n] !== response[n + 2]) $fatal(1, "Rejected move changed board"); end
  endtask
'''


def fixture(pieces):
    # Simulation-only setup of endgames. Normal play is tested from hardware reset.
    return "\n@(negedge clock);\n" + "\n".join(
        f"dut.game.board_{n // 8}_{n % 8} = 16'd{pieces.get(n, 0)};" for n in range(64))


def generate():
    checks = r'''
  initial begin #500000000; $fatal(1, "UART/game test timed out"); end
  initial begin
    repeat(4) tick;
    @(negedge clock); reset = 0;
    exchange(1, 0, 0, 0); expect_status(0);
    if(piece(4) != 12 || piece(60) != 6) $fatal(1, "Initial kings missing");
    for(n = 8; n < 16; n = n + 1) if(piece(n) != 7) $fatal(1, "Initial black pawns missing");
    for(n = 48; n < 56; n = n + 1) if(piece(n) != 1) $fatal(1, "Initial white pawns missing");
    save_board;
    exchange(3, 60, 44, 0); expect_status(5); unchanged; // King two squares.
    exchange(3, 12, 20, 0); expect_status(5); unchanged; // Wrong color.
    exchange(3, 60, 51, 0); expect_status(5); unchanged; // Friendly capture.
    exchange(3, 255, 0, 0); expect_status(5); unchanged;
    exchange(3, 52, 36, 1); expect_status(6); unchanged; // Bad checksum.
    exchange(99, 0, 0, 0); expect_status(6); unchanged;
    // A truncated packet times out and the next header resynchronizes.
    send_byte(8'hAA); send_byte(3); repeat(100020) tick;
    exchange(2, 0, 0, 0); expect_status(0); unchanged;
    exchange(3, 52, 36, 0); expect_status(0); // e2e4 + engine reply.
    if(piece(52) != 0 || piece(36) != 1) $fatal(1, "Human move not applied");
    save_board;
    exchange(3, 52, 36, 0); expect_status(5); unchanged;
    exchange(3, 54, 38, 0); expect_status(0); // g2g4 + another reply.
    if(piece(54) != 0 || piece(38) != 1) $fatal(1, "Second turn failed");
    $display("PASS UART frames, recovery, illegal moves and consecutive engine turns");
    exchange(1, 0, 0, 0);
'''
    checks += fixture({63: 6, 7: 12, 8: 1}) + r'''
    exchange(3, 8, 0, 0); expect_status(0);
    if(piece(0) != 5 || piece(8) != 0) $fatal(1, "Human promotion failed");
    exchange(1, 0, 0, 0);
'''
    checks += fixture({63: 6, 7: 12, 48: 7}) + r'''
    exchange(3, 63, 62, 0); expect_status(0);
    if(piece(56) != 11 || piece(48) != 0) $fatal(1, "Engine promotion failed");
    $display("PASS human and engine promotion over UART");
    exchange(1, 0, 0, 0);
'''
    checks += fixture({0: 12, 17: 5, 18: 6}) + r'''
    exchange(3, 17, 9, 0); expect_status(2);
    save_board;
    exchange(3, 9, 17, 0); expect_status(2); unchanged;
    exchange(1, 0, 0, 0);
'''
    checks += fixture({56: 6, 42: 11, 50: 12}) + r'''
    exchange(3, 56, 48, 0); expect_status(3);
    exchange(1, 0, 0, 0);
'''
    checks += fixture({0: 12, 25: 5, 18: 6}) + r'''
    exchange(3, 25, 17, 0); expect_status(4);
    exchange(1, 0, 0, 0);
'''
    checks += fixture({63: 6, 7: 12}) + r'''
    exchange(3, 63, 62, 0); expect_status(4);
    exchange(1, 0, 0, 0); expect_status(0);
    if(piece(52) != 1 || piece(12) != 7) $fatal(1, "New game failed");
    $display("PASS both winners, stalemate, bare kings and new game");
    $display("ALL UART GAME CHECKS PASSED");
    $finish;
  end
endmodule
'''
    interactive = r'''
  integer count, header, command, from_square, to_square, checksum;
  initial begin
    repeat(4) tick;
    @(negedge clock); reset = 0;
    forever begin
      count = $fscanf(32'h80000000, "%h %h %h %h %h", header, command, from_square, to_square, checksum);
      if(count != 5) $finish;
      exchange_raw(header, command, from_square, to_square, checksum);
      $write("FRAME ");
      for(n = 0; n < 35; n = n + 1) $write("%02x", response[n]);
      $write("\n");
      $fflush(32'h80000001);
    end
  end
endmodule
'''
    Path("generated/sim/uart_test.sv").write_text(COMMON + checks)
    Path("generated/sim/interactive.sv").write_text(COMMON + interactive)


if __name__ == "__main__":
    generate()
