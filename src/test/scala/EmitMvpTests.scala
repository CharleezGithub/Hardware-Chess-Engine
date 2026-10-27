import chisel3.stage.ChiselStage

// One elaboration batch; all execution afterward uses native Icarus simulation.
object EmitMvpTests extends App {
    (new ChiselStage).emitVerilog(new Top(), Array("--target-dir", "generated/fpga"))
    (new ChiselStage).emitVerilog(new Top(1000000, 100000), Array("--target-dir", "generated/sim"))
    (new ChiselStage).emitVerilog(new KingPawnSearchHarness(1), Array("--target-dir", "generated/search1"))
    (new ChiselStage).emitVerilog(new KingPawnSearchHarness(2), Array("--target-dir", "generated/search2"))
    (new ChiselStage).emitVerilog(new MoveGenerator, Array("--target-dir", "generated/moves"))
}
