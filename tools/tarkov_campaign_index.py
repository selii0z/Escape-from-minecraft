#!/usr/bin/env python3
"""Build a reviewable quest index from one frozen reference snapshot, never enable unadapted objectives."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from tarkov_campaign_snapshot import task_rows, trader_key, COLLECTOR_ID

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def write(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")

def object_id(value):
    if isinstance(value, str):
        return value
    if isinstance(value, dict):
        return value.get("id")
    return None

def index(directory, out):
    manifest=read(directory/"manifest.json")
    for name, info in manifest["files"].items():
        path=directory/(name+".json")
        if hashlib.sha256(path.read_bytes()).hexdigest()!=info["sha256"]:
            raise ValueError(f"Snapshot hash mismatch: {name}")
    tasks=task_rows(read(directory/"tasks.json"))
    if len(tasks)!=manifest["audit"]["tasks"] or COLLECTOR_ID not in tasks:
        raise ValueError("Task count or Collector changed since audit")
    entries=[]
    traders={}
    unknown_dependencies=[]
    objective_fields={}
    kappa=[]
    for id, task in sorted(tasks.items()):
        trader=trader_key(task)
        raw_trader=task.get("trader")
        name=raw_trader.get("name",trader) if isinstance(raw_trader,dict) else trader
        info=traders.setdefault(trader,{"id":trader,"name":name,"count":0})
        info["count"]+=1
        if task.get("kappaRequired") is True:
            kappa.append(id)
        prerequisites=[]
        for req in task.get("taskRequirements") or []:
            if not isinstance(req,dict):
                unknown_dependencies.append({"quest":id,"reference":req})
                continue
            ref=object_id(req.get("task",req.get("taskId",req.get("id"))))
            if not ref or ref not in tasks:
                unknown_dependencies.append({"quest":id,"reference":req})
            prerequisites.append({"task":ref,"statuses":req.get("status",req.get("statuses"))})
        objectives=[]
        for i, obj in enumerate(task["objectives"]):
            if not isinstance(obj,dict):
                raise ValueError(f"Malformed objective {id}/{i}")
            kind=str(obj.get("type",obj.get("__typename","unknown")))
            objective_fields.setdefault(kind,set()).update(obj)
            objectives.append({
                "id":obj.get("id") or f"{id}:{i}",
                "type":kind,
                "count":obj.get("count"),
                "optional":obj.get("optional"),
                "review":"pending",
            })
        entries.append({
            "id":id,"name":task.get("name"),"trader":trader,
            "level":task.get("minPlayerLevel"),
            "experience":task.get("experience"),
            "kappaRequired":task.get("kappaRequired"),
            "lightkeeperRequired":task.get("lightkeeperRequired"),
            "map":task.get("map"),"prerequisites":prerequisites,
            "objectives":objectives,"review":"pending","implemented":False,
        })
    collector=next(q for q in entries if q["id"]==COLLECTOR_ID)
    out.mkdir(parents=True,exist_ok=True)
    write(out/"quest-index.json",{"schema":1,"reference":manifest,"quests":entries})
    write(out/"trader-index.json",list(traders.values()))
    write(out/"kappa-index.json",{"requiredFlagged":kappa,"collector":collector})
    summary={"quests":len(entries),"traders":list(traders.values()),
             "kappaFlaggedQuests":len(kappa),"collectorObjectives":len(collector["objectives"]),
             "unresolvedDependencies":len(unknown_dependencies),
             "objectiveFields":{k:sorted(v) for k,v in objective_fields.items()}}
    write(out/"review.json",{"summary":summary,"unresolvedDependencies":unknown_dependencies})
    print(json.dumps(summary,ensure_ascii=False,indent=2))

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,default=Path("build/tarkov-reference"))
    p.add_argument("--out",type=Path,default=Path("build/tarkov-review"))
    a=p.parse_args()
    index(a.snapshot,a.out)
