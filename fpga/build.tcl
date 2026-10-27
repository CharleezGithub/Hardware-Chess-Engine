set root [file normalize [file join [file dirname [info script]] ..]]
cd $root
file mkdir build/fpga
set_param general.maxThreads 4
read_verilog generated/fpga/Top.v
read_verilog -sv fpga/BasysTop.sv
read_xdc basys3.xdc
synth_design -top BasysTop -part xc7a35tcpg236-1
opt_design
place_design
phys_opt_design
route_design
report_utilization -file build/fpga/utilization.rpt
report_timing_summary -file build/fpga/timing.rpt
report_drc -file build/fpga/drc.rpt
if {[get_property SLACK [get_timing_paths -delay_type max -max_paths 1]] < 0} {
    error "Setup timing failed; see build/fpga/timing.rpt"
}
if {[get_property SLACK [get_timing_paths -delay_type min -max_paths 1]] < 0} {
    error "Hold timing failed; see build/fpga/timing.rpt"
}
write_checkpoint -force build/fpga/chess.dcp
write_bitstream -force build/fpga/chess.bit
