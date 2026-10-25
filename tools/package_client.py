#!/usr/bin/env python3
"""Create a public Prism import ZIP from the two prepared mods only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def pins(root):
    gradle = (root / "client-mod/build.gradle.kts").read_text()
    def version(coordinate):
        match = re.search(re.escape(coordinate) + r':([^"\s]+)', gradle)
        if not match:
            raise RuntimeError("Missing pinned dependency: " + coordinate)
        return match.group(1)
    release = next(line.split("=", 1)[1] for line in (root / "gradle.properties").read_text().splitlines()
                   if line.startswith("ancientrpgRelease="))
    return {"release": release, "minecraft": version("com.mojang:minecraft"),
            "fabric_loader": version("net.fabricmc:fabric-loader"),
            "fabric_api": version("net.fabricmc.fabric-api:fabric-api"), "java_feature": 25}


def package(root, output):
    versions = pins(root)
    mods = sorted((root / "client-mod/build/demo-mods").glob("*.jar"))
    if len(mods) != 2 or {p.name for p in mods} != {
            "client-mod-" + versions["release"] + ".jar", "fabric-api-" + versions["fabric_api"] + ".jar"}:
        raise RuntimeError("Prepare exactly the matching AncientRPG and Fabric API mods first.")
    with zipfile.ZipFile(next(p for p in mods if p.name.startswith("client-mod-"))) as jar:
        if json.loads(jar.read("fabric.mod.json"))["version"] != versions["release"]:
            raise RuntimeError("Prepared mod version does not match source.")
    files = {".minecraft/mods/" + p.name: p.read_bytes() for p in mods}
    manifest = {**versions, "client_commit": subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip(),
        "files": {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    files["mmc-pack.json"] = (json.dumps({"formatVersion": 1, "components": [
        {"uid": "net.minecraft", "version": versions["minecraft"]},
        {"uid": "net.fabricmc.fabric-loader", "version": versions["fabric_loader"]}]}, indent=2) + "\n").encode()
    files["instance.cfg"] = ("[General]\nInstanceType=OneSix\nConfigVersion=1.2\nname=AncientRPG "
                             + versions["release"] + " playtest\nOverrideJavaLocation=false\n").encode()
    files["environment.json"] = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    files["README.md"] = (root / "docs/tester-quickstart.md").read_bytes()
    files["LICENSE"] = (root / "LICENSE").read_bytes()
    files["THIRD_PARTY_NOTICES.md"] = (root / "THIRD_PARTY_NOTICES.md").read_bytes()
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data)
    output.with_suffix(output.suffix + ".sha256").write_text(
        hashlib.sha256(output.read_bytes()).hexdigest() + "  " + output.name + "\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = package(ROOT, args.output)
    print(f"Prepared public Prism pack: {args.output} ({result['release']})")
