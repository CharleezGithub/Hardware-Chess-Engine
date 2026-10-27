import chisel3._
import chisel3.util._

// One byte, 8 data bits, no parity, one stop bit (8N1).
class UartByteTx(clockFreq: Int, baudRate: Int) extends Module {
    val io = IO(new Bundle {
        val valid = Input(Bool())
        val ready = Output(Bool())
        val data = Input(UInt(8.W))
        val tx = Output(Bool())
    })
    val bitCycles = clockFreq / baudRate
    require(bitCycles >= 4)
    val busy = RegInit(false.B)
    val frame = RegInit(1023.U(10.W))
    val count = RegInit(0.U(log2Ceil(bitCycles).W))
    val bit = RegInit(0.U(4.W))
    io.ready := !busy
    io.tx := Mux(busy, frame(0), true.B)
    when(!busy) {
        when(io.valid) {
            frame := Cat(1.U(1.W), io.data, 0.U(1.W))
            count := 0.U; bit := 0.U; busy := true.B
        }
    }.otherwise {
        when(count === (bitCycles - 1).U) {
            count := 0.U
            frame := Cat(1.U(1.W), frame(9, 1))
            bit := bit + 1.U
            when(bit === 9.U) { busy := false.B }
        }.otherwise { count := count + 1.U }
    }
}
