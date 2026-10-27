.PHONY: doit test fpga program clean

# Emit the FPGA design and native simulation designs in one batch.
doit:
	sbt "Test / runMain EmitMvpTests"

# Run the test
test:
	python scripts/test_mvp.py

fpga: doit
	vivado -mode batch -source fpga/build.tcl

program:
	vivado -mode batch -source fpga/program.tcl

clean:
	sbt clean

