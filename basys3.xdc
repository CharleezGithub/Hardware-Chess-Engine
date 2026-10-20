## Basys 3 constraints for WildcatTop (with Verilog Wrapper)
## FPGA: Artix-7 XC7A35T-1CPG236C

set_property IOSTANDARD LVCMOS33 [get_ports *]

## Configuration
set_property CONFIG_VOLTAGE 3.3 [current_design]
set_property CFGBVS VCCO [current_design]
set_property BITSTREAM.GENERAL.COMPRESS TRUE [current_design]

# System Clock Pin (100 MHz)
set_property -dict { PACKAGE_PIN W5    IOSTANDARD LVCMOS33 } [get_ports { clock }];
create_clock -add -name sys_clk_pin -period 10.00 -waveform {0 5} [get_ports { clock }];

## Reset - left most switch (COMMENTED OUT: fpga_top.v does not expose a reset port)
set_property -dict { PACKAGE_PIN R2   IOSTANDARD LVCMOS33 } [get_ports { reset }]

## Buttons
set_property -dict { PACKAGE_PIN T18  IOSTANDARD LVCMOS33 } [get_ports { button[0] }]
set_property -dict { PACKAGE_PIN U17  IOSTANDARD LVCMOS33 } [get_ports { button[1] }]
set_property -dict { PACKAGE_PIN W19  IOSTANDARD LVCMOS33 } [get_ports { button[2] }]
set_property -dict { PACKAGE_PIN T17  IOSTANDARD LVCMOS33 } [get_ports { button[3] }]
set_property -dict { PACKAGE_PIN U18  IOSTANDARD LVCMOS33 } [get_ports { button[4] }]
## Extra button module
#set_property -dict { PACKAGE_PIN A14  IOSTANDARD LVCMOS33 PULLDOWN TRUE } [get_ports { button[5] }]
#set_property -dict { PACKAGE_PIN A16  IOSTANDARD LVCMOS33 PULLDOWN TRUE } [get_ports { button[6] }]
#set_property -dict { PACKAGE_PIN B15  IOSTANDARD LVCMOS33 PULLDOWN TRUE } [get_ports { button[7] }]
#set_property -dict { PACKAGE_PIN B16  IOSTANDARD LVCMOS33 PULLDOWN TRUE } [get_ports { button[8] }]

## LEDs
set_property -dict { PACKAGE_PIN U16  IOSTANDARD LVCMOS33 } [get_ports { led[0] }]
set_property -dict { PACKAGE_PIN E19  IOSTANDARD LVCMOS33 } [get_ports { led[1] }]
set_property -dict { PACKAGE_PIN U19  IOSTANDARD LVCMOS33 } [get_ports { led[2] }]
set_property -dict { PACKAGE_PIN V19  IOSTANDARD LVCMOS33 } [get_ports { led[3] }]
set_property -dict { PACKAGE_PIN W18  IOSTANDARD LVCMOS33 } [get_ports { led[4] }]
set_property -dict { PACKAGE_PIN U15  IOSTANDARD LVCMOS33 } [get_ports { led[5] }]
set_property -dict { PACKAGE_PIN U14  IOSTANDARD LVCMOS33 } [get_ports { led[6] }]
set_property -dict { PACKAGE_PIN V14  IOSTANDARD LVCMOS33 } [get_ports { led[7] }]
set_property -dict { PACKAGE_PIN V13  IOSTANDARD LVCMOS33 } [get_ports { led[8] }]
set_property -dict { PACKAGE_PIN V3   IOSTANDARD LVCMOS33 } [get_ports { led[9] }]
set_property -dict { PACKAGE_PIN W3   IOSTANDARD LVCMOS33 } [get_ports { led[10] }]
set_property -dict { PACKAGE_PIN U3   IOSTANDARD LVCMOS33 } [get_ports { led[11] }]
set_property -dict { PACKAGE_PIN P3   IOSTANDARD LVCMOS33 } [get_ports { led[12] }]
set_property -dict { PACKAGE_PIN N3   IOSTANDARD LVCMOS33 } [get_ports { led[13] }]
set_property -dict { PACKAGE_PIN P1   IOSTANDARD LVCMOS33 } [get_ports { led[14] }]
set_property -dict { PACKAGE_PIN L1   IOSTANDARD LVCMOS33 } [get_ports { led[15] }]

## USB-RS232 Interface
## B18 = RX into FPGA (UART_TXD_IN on Basys 3)
## A18 = TX out of FPGA (UART_RXD_OUT on Basys 3)
set_property -dict { PACKAGE_PIN B18  IOSTANDARD LVCMOS33 } [get_ports { rx }]
set_property -dict { PACKAGE_PIN A18  IOSTANDARD LVCMOS33 } [get_ports { tx }]

## VGA
#set_property -dict { PACKAGE_PIN G19  IOSTANDARD LVCMOS33 } [get_ports { vgaRed[0] }]
#set_property -dict { PACKAGE_PIN H19  IOSTANDARD LVCMOS33 } [get_ports { vgaRed[1] }]
#set_property -dict { PACKAGE_PIN J19  IOSTANDARD LVCMOS33 } [get_ports { vgaRed[2] }]
#set_property -dict { PACKAGE_PIN N19  IOSTANDARD LVCMOS33 } [get_ports { vgaRed[3] }]
#set_property -dict { PACKAGE_PIN N18  IOSTANDARD LVCMOS33 } [get_ports { vgaBlue[0] }]
#set_property -dict { PACKAGE_PIN L18  IOSTANDARD LVCMOS33 } [get_ports { vgaBlue[1] }]
#set_property -dict { PACKAGE_PIN K18  IOSTANDARD LVCMOS33 } [get_ports { vgaBlue[2] }]
#set_property -dict { PACKAGE_PIN J18  IOSTANDARD LVCMOS33 } [get_ports { vgaBlue[3] }]
#set_property -dict { PACKAGE_PIN J17  IOSTANDARD LVCMOS33 } [get_ports { vgaGreen[0] }]
#set_property -dict { PACKAGE_PIN H17  IOSTANDARD LVCMOS33 } [get_ports { vgaGreen[1] }]
#set_property -dict { PACKAGE_PIN G17  IOSTANDARD LVCMOS33 } [get_ports { vgaGreen[2] }]
#set_property -dict { PACKAGE_PIN D17  IOSTANDARD LVCMOS33 } [get_ports { vgaGreen[3] }]
#set_property -dict { PACKAGE_PIN P19  IOSTANDARD LVCMOS33 } [get_ports { Hsync }]
#set_property -dict { PACKAGE_PIN R19  IOSTANDARD LVCMOS33 } [get_ports { Vsync }]


## SPI Flash (Macronix MX25L3233F)
#set_property -dict { PACKAGE_PIN K19  IOSTANDARD LVCMOS33 } [get_ports { qspiCs }]
#set_property -dict { PACKAGE_PIN D18  IOSTANDARD LVCMOS33 } [get_ports { qspiMosi }]
#set_property -dict { PACKAGE_PIN D19  IOSTANDARD LVCMOS33 } [get_ports { qspiMiso }]
#set_property -dict { PACKAGE_PIN G18  IOSTANDARD LVCMOS33 } [get_ports { qspiWp }]
#set_property -dict { PACKAGE_PIN F18  IOSTANDARD LVCMOS33 } [get_ports { qspiHold }]