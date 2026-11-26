#!/usr/bin/env python3
"""Development-only Tarkov reference snapshot. No network requests from Minecraft."""
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import urllib.request
from collections import Counter

BASE = "https://api.tarkov-stammtisch.de/api/v2"
FILES = ("tasks", "traders", "items", "maps")
MAX_BYTES = 80 * 1024 * 1024
COLLECTOR_ID = "5c51aac186f77432ea65c552"

def load(mode, name, directory, offline):
    path = directory / (name + ".json")
    if offline:
        raw = path.read_bytes()
        source = "local snapshot"
    else:
        source = f"{BASE}/{mode}/{name}"
        req = urllib.request.Request(source, headers={"User-Agent": "EFM-campaign-development/1.0", "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=60) as response:
            raw = response.read(MAX_BYTES + 1)
    if len(raw) > MAX_BYTES:
        raise ValueError(f"{name}: unexpected response size")
    data = json.loads(raw)
    if not isinstance(data, (dict, list)):
        raise ValueError(f"{name}: invalid JSON document")
    if not offline:
        path.write_bytes(raw)
    return data, {"source": source, "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}

def rows(value):
    if isinstance(value, list):
        for entry in value:
            if isinstance(entry, dict):
                yield entry
                yield from rows(entry)
    elif isinstance(value, dict):
        for key, entry in value.items():
            if isinstance(entry, dict):
                if "id" not in entry and len(key) == 24 and all(c in "0123456789abcdef" for c in key):
                    yield dict(entry, id=key)
                else:
                    yield entry
                yield from rows(entry)
            elif isinstance(entry, list):
                yield from rows(entry)

def task_rows(data):
    tasks = {}
    for entry in rows(data):
        identifier = entry.get("id")
        if isinstance(identifier, str) and isinstance(entry.get("objectives"), list) and (
            "trader" in entry or "traderId" in entry or "experience" in entry or "taskRequirements" in entry
        ):
            tasks[identifier] = entry
    return tasks

def trader_key(task):
    value = task.get("trader", task.get("traderId", "unknown"))
    if isinstance(value, dict):
        return str(value.get("id", value.get("name", "unknown")))
    return str(value)

def audit(tasks):
    if len(tasks) < 200:
        raise ValueError(f"Only {len(tasks)} tasks detected; inspect the source schema before importing")
    collector = tasks.get(COLLECTOR_ID)
    if collector is None:
        collector = next((t for t in tasks.values() if str(t.get("name", "")).lower() == "collector"), None)
    if collector is None or not collector["objectives"]:
        raise ValueError("Collector missing; do not publish incomplete catalog")
    objective_types = Counter(str(o.get("type", o.get("__typename", "unknown"))) for t in tasks.values()
                              for o in t["objectives"] if isinstance(o, dict))
    return {"tasks": len(tasks), "traders": dict(Counter(trader_key(t) for t in tasks.values())),
            "objective_types": dict(objective_types), "collector_id": collector["id"],
            "collector_objectives": len(collector["objectives"]),
            "collector_fields": sorted(collector.keys()),
            "sample_task_fields": sorted(next(iter(tasks.values())).keys())}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", choices=("pve", "regular"), default="pve")
    parser.add_argument("--out", type=pathlib.Path, default=pathlib.Path("build/tarkov-reference"))
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    meta = {"mode": args.mode, "utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "attribution": "tarkov.dev via Tarkovstammtisch v2 mirror", "files": {}}
    for name in FILES:
        data, info = load(args.mode, name, args.out, args.offline)
        meta["files"][name] = info
        if name == "tasks":
            meta["audit"] = audit(task_rows(data))
    (args.out / "manifest.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(meta["audit"], indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
