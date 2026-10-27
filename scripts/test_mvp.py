"""One compile, bundled native RTL checks, then the real client against RTL."""
import argparse
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def native_tool(name):
    found = shutil.which(name) or shutil.which(name + ".exe")
    fallback = Path(f"C:/Tools/oss-cad-suite/bin/{name}.exe")
    if not found and fallback.exists():
        found = str(fallback)
    if not found:
        raise SystemExit(f"Install Icarus Verilog: missing {name}")
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-compile", action="store_true", help="Use freshly emitted RTL from this source revision")
    args = parser.parse_args()
    os.chdir(ROOT)
    compiler, simulator = native_tool("iverilog"), native_tool("vvp")
    env = os.environ.copy()
    env["PATH"] = str(Path(compiler).resolve().parent.parent / "lib") + os.pathsep + env["PATH"]

    def run(command, **kwargs):
        print("+ " + " ".join(map(str, command)), flush=True)
        subprocess.run(command, check=True, cwd=ROOT, env=env, timeout=180, **kwargs)

    if not args.skip_compile:
        subprocess.run([shutil.which("sbt") or "sbt", "Test / runMain EmitMvpTests"],
            check=True, cwd=ROOT, timeout=600, shell=os.name == "nt")
    Path("target").mkdir(exist_ok=True)
    runpy.run_path(str(ROOT / "scripts/search_bench.py"), run_name="__main__")
    runpy.run_path(str(ROOT / "scripts/move_bench.py"), run_name="__main__")
    runpy.run_path(str(ROOT / "scripts/uart_bench.py"), run_name="__main__")

    def build(name, top, rtl, bench):
        output = f"generated/sim/{name}.vvp"
        run([compiler, "-g2012", "-s", top, "-o", output, rtl, bench])
        return output

    moves = build("moves", "move_test", "generated/moves/MoveGenerator.v", "generated/moves/move_test.sv")
    search1 = build("search1", "minimax_native_test", "generated/search1/KingPawnSearchHarness.v", "target/minimax_native_test.sv")
    search2 = build("search2", "minimax_native_test", "generated/search2/KingPawnSearchHarness.v", "target/minimax_native_test.sv")
    uart = build("uart", "uart_test", "generated/sim/Top.v", "generated/sim/uart_test.sv")
    build("interactive", "uart_test", "generated/sim/Top.v", "generated/sim/interactive.sv")
    for image, extra in [(moves, []), (search1, ["+leaf"]), (search2, []), (uart, [])]:
        run([simulator, image] + extra)
    run([sys.executable, "-m", "unittest", "discover", "-s", "tests", "-v"])
    print("MVP CHECKS PASSED. Play with: python UART.py --simulate", flush=True)


if __name__ == "__main__":
    main()
