#!/usr/bin/env python3
"""Offline, backup-only reset of the generated Black Forest area."""
import argparse
import datetime
from pathlib import Path
import shutil
import sys

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path, help="World save containing level.dat")
    parser.add_argument("--apply", action="store_true", help="Move selected files into a backup")
    parser.add_argument("--server-stopped", action="store_true", help="Confirm every Minecraft process using this world is stopped")
    args = parser.parse_args()
    world = args.world.resolve()
    if not (world / "level.dat").is_file():
        parser.error("Select the world folder containing level.dat")
    dimension = world / "dimensions" / "taczoperations" / "black_forest"
    if not dimension.is_dir():
        parser.error("Black Forest dimension not found. No files changed.")
    # Forest occupies block coordinates 12288..13187 on both axes.
    # Region files cover 512 x 512 blocks. Industrial zone starts at 16384.
    candidates = []
    for folder in ("region", "entities", "poi"):
        for x in (24, 25):
            for z in (24, 25):
                candidates.append(dimension / folder / f"r.{x}.{z}.mca")
        # Oversized external chunk data belongs to the same region rectangle.
        directory = dimension / folder
        if directory.is_dir():
            for p in directory.glob("c.*.*.mcc"):
                parts = p.name.split(".")
                if len(parts) == 4:
                    try:
                        x, z = int(parts[1]), int(parts[2])
                    except ValueError:
                        continue
                    if 24 <= x // 32 <= 25 and 24 <= z // 32 <= 25:
                        candidates.append(p)
    prep = world / "data" / "taczoperations_forest_preparation_v2.dat"
    candidates.append(prep)
    files = [p for p in candidates if p.is_file()]
    if any(p.is_symlink() or p.resolve() != p for p in files):
        parser.error("Unexpected symlink in selected files. No files changed.")
    print("World:", world)
    print("Files to move into backup:")
    for p in files:
        print(" ", p.relative_to(world))
    if not args.apply:
        print("Preview only. Nothing has changed.")
        return
    if not args.server_stopped:
        parser.error("Stop the server/game and confirm --server-stopped")
    if not files:
        print("No matching files. Nothing to do.")
        return
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    backup = world.parent / (world.name + "-black-forest-backup-" + stamp)
    backup.mkdir()
    moved = []
    try:
        for p in files:
            target = backup / p.relative_to(world)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(p), str(target))
            moved.append((p, target))
    except BaseException:
        print("Reset interrupted; attempting rollback.", file=sys.stderr)
        for original, target in reversed(moved):
            if target.exists() and not original.exists():
                shutil.move(str(target), str(original))
        raise
    print("Backup:", backup)
    print("Done. Restart Minecraft with the updated mod to regenerate the forest.")
    print("To restore, stop Minecraft, remove the newly generated matching files,")
    print("and copy the backed-up relative paths back into the world folder.")

if __name__ == "__main__":
    main()
