"""Run the same pure-Java checks as JUnit without Gradle/dependency downloads.
Requires Python 3 and JDK 25+ on PATH, or --jdk /path/to/jdk.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--jdk", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]

def executable(name):
    return str(args.jdk / "bin" / name) if args.jdk else name

sources = sorted((root / "protocol/src/main/java").rglob("*.java"))
sources.append(root / "protocol/src/test/java/dev/ancientrpg/protocol/ProtocolChecks.java")
with tempfile.TemporaryDirectory(prefix="ancientrpg-protocol-") as output:
    subprocess.run([executable("javac"), "--release", "25", "-d", output,
                    *map(str, sources)], check=True)
    resources = root / "protocol/src/main/resources"
    for source in resources.rglob("*"):
        if source.is_file():
            target = Path(output) / source.relative_to(resources)
            target.parent.mkdir(parents=True, exist_ok=True)
            # Match Gradle resource expansion for the selected checkout's release.
            release = next(line.split("=", 1)[1] for line in (root / "gradle.properties").read_text().splitlines() if line.startswith("ancientrpgRelease="))
            target.write_bytes(source.read_bytes().replace(b"${version}", release.encode()))
    subprocess.run([executable("java"), "-cp", output,
                    "dev.ancientrpg.protocol.ProtocolChecks"], check=True)
