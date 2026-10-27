open_hw_manager
connect_hw_server
open_hw_target
set devices [get_hw_devices xc7a35t*]
if {[llength $devices] != 1} { error "Connect exactly one Basys 3 device" }
set device [lindex $devices 0]
current_hw_device $device
set_property PROGRAM.FILE [file normalize build/fpga/chess.bit] $device
program_hw_devices $device
