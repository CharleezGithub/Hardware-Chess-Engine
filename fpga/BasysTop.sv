// Basys 3: convert the 100 MHz oscillator to a relaxed 20 MHz engine clock.
module BasysTop(input clock, input reset, input io_rx,
                input [4:0] io_buttons, output io_tx, output [15:0] io_led);
  wire feedback, feedback_buffered, core_unbuffered, core_clock, locked;
  MMCME2_BASE #(.CLKIN1_PERIOD(10.0), .CLKFBOUT_MULT_F(10.0),
                .DIVCLK_DIVIDE(1), .CLKOUT0_DIVIDE_F(50.0)) pll (
    .CLKIN1(clock), .CLKFBIN(feedback_buffered), .CLKFBOUT(feedback),
    .CLKOUT0(core_unbuffered), .LOCKED(locked), .PWRDWN(1'b0), .RST(reset)
  );
  BUFG feedback_buf(.I(feedback), .O(feedback_buffered));
  BUFG core_buf(.I(core_unbuffered), .O(core_clock));
  wire reset_async = reset || !locked;
  (* ASYNC_REG = "TRUE" *) reg [1:0] reset_sync = 2'b11;
  always @(posedge core_clock or posedge reset_async)
    if (reset_async) reset_sync <= 2'b11;
    else reset_sync <= {reset_sync[0], 1'b0};
  Top game(.clock(core_clock), .reset(reset_sync[1]), .io_rx(io_rx),
           .io_tx(io_tx), .io_buttons(io_buttons), .io_led(io_led));
endmodule
