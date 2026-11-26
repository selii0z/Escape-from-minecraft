#!/usr/bin/env python3
"""Merge user-owned private Zombiepolis data into a compiled mod, without uploading the map to GitHub."""
import argparse
import json
import struct
import zipfile
from pathlib import Path

ROOT = "data/taczoperations/zombiepolis/"
SIZE = 1152

def validate(pack):
    names = set(pack.namelist())
    for name in ("palette.txt", "height.bin", "source.json"):
        if ROOT + name not in names:
            raise ValueError("Missing terrain file: " + name)
    palette = pack.read(ROOT + "palette.txt").decode("utf-8").splitlines()
    if not 1 <= len(palette) <= 65535:
        raise ValueError("Invalid palette")
    if len(pack.read(ROOT + "height.bin")) != SIZE * SIZE * 2:
        raise ValueError("Invalid height map")
    anchors = json.loads(pack.read(ROOT + "source.json"))
    for name in ("guards", "loot"):
        if not anchors[name] or any(len(p) != 3 or not all(isinstance(v, int) for v in p) for p in anchors[name]):
            raise ValueError("Invalid " + name)
    expected = {ROOT + f"chunks/{x}_{z}.bin" for x in range(72) for z in range(72)}
    chunks = {n for n in names if n.startswith(ROOT + "chunks/") and n.endswith(".bin")}
    if chunks != expected:
        raise ValueError(f"Incomplete terrain: {len(chunks)} of {len(expected)} chunks")
    for name in chunks:
        data = pack.read(name)
        magic, sections = struct.unpack_from(">HB", data)
        if magic != 0x5a50 or sections > 24:
            raise ValueError("Invalid chunk: " + name)
        pos = 3
        for _ in range(sections):
            _, runs = struct.unpack_from(">bH", data, pos)
            pos += 3
            total = 0
            for _ in range(runs):
                length, state = struct.unpack_from(">HH", data, pos)
                pos += 4
                if not length or state >= len(palette):
                    raise ValueError("Invalid RLE: " + name)
                total += length
            if total != 4096:
                raise ValueError("Invalid section size: " + name)
        if pos != len(data):
            raise ValueError("Trailing chunk data: " + name)
    return len(chunks), len(anchors["guards"]), len(anchors["loot"])

def package(base, archive, output):
    if output.resolve() in (base.resolve(), archive.resolve()):
        raise ValueError("Output must be a separate file")
    with zipfile.ZipFile(base) as jar, zipfile.ZipFile(archive) as pack:
        counts = validate(pack)
        if "fr/taczoperations/worldgen/ZombiepolisMap.class" not in jar.namelist():
            raise ValueError("The selected mod has no Zombiepolis terrain loader")
        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as result:
            for info in jar.infolist():
                if not info.filename.startswith(ROOT):
                    result.writestr(info, jar.read(info))
            for info in pack.infolist():
                if info.filename.startswith(ROOT) and not info.is_dir():
                    result.writestr(info, pack.read(info))
    with zipfile.ZipFile(output) as result:
        if result.testzip():
            raise ValueError("Invalid packaged JAR")
    print(f"Packaged Zombiepolis: {counts[0]} chunks, {counts[1]} enemy anchors, {counts[2]} loot anchors")

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("base", type=Path)
    p.add_argument("archive", type=Path)
    p.add_argument("output", type=Path)
    a = p.parse_args()
    package(a.base, a.archive, a.output)
