#!/usr/bin/env python3
"""Resolve actual reference item and trader IDs; no invented item substitutions."""
import argparse
import json
import hashlib
from collections import defaultdict
from pathlib import Path

COLLECTOR = "5c51aac186f77432ea65c552"
KAPPA = "5c093ca986f7740a1867ab12"

def read(path):
    return json.loads(path.read_text(encoding="utf-8"))

def write(path, obj):
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False, sort_keys=True), encoding="utf-8")

def ref(v):
    return v if isinstance(v,str) else v.get("id") if isinstance(v,dict) else None

def build(snapshot,out):
    manifest=read(snapshot/"manifest.json")
    for name,info in manifest["files"].items():
        if hashlib.sha256((snapshot/(name+".json")).read_bytes()).hexdigest()!=info["sha256"]:
            raise ValueError("Changed reference file: "+name)
    tasks=read(snapshot/"tasks.json")["data"]
    quests=tasks["tasks"]
    quest_items=tasks["questItems"]
    items=read(snapshot/"items.json")["data"]["items"]
    traders=read(snapshot/"traders.json")["data"]
    if len(quests)!=manifest["audit"]["tasks"]:
        raise ValueError("Inconsistent reference task count")
    needed=defaultdict(set)
    missing=[]
    def add(value,quest,role):
        id=ref(value)
        if id is None:
            missing.append({"quest":quest,"role":role,"reference":value})
            return
        needed[id].add((quest,role))
        if id not in items and id not in quest_items:
            missing.append({"quest":quest,"role":role,"reference":id})
    for qid,q in quests.items():
        for o in q["objectives"]:
            for key in ("items","usingWeapon","wearing","notWearing","usingWeaponMods","containsAll","requiredKeys"):
                # Nested arrays describe OR/AND restrictions; keep all IDs for review, not flattened gameplay rules.
                def visit(value):
                    if isinstance(value,list):
                        for v in value:visit(v)
                    elif value is not None:add(value,qid,o["type"]+":"+key)
                visit(o.get(key,[]))
            for key in ("questItem","markerItem","item"):
                if o.get(key):add(o[key],qid,o["type"]+":"+key)
        for stage in ("startRewards","finishRewards","failureOutcome"):
            reward=q.get(stage) or {}
            if isinstance(reward,dict):
                for entry in reward.get("items",[]):
                    add(entry.get("item"),qid,stage)
    catalog=[]
    for id,roles in sorted(needed.items()):
        data=quest_items.get(id) or items.get(id)
        if not data:continue
        catalog.append({"id":id,"name":data.get("normalizedName"),
                        "questOnly":id in quest_items,"types":data.get("types",[]),
                        "stackMaxSize":data.get("stackMaxSize"),"basePrice":data.get("basePrice"),
                        "roles":[{"quest":qid,"role":role} for qid,role in sorted(roles)],
                        "mapping":None,"review":"pending"})
    collector=quests[COLLECTOR]
    objectives=[]
    for o in collector["objectives"]:
        if o["type"]!="giveItem" or not o.get("items"):
            raise ValueError("Collector objective type changed")
        objectives.append({"id":o["id"],"count":o["count"],"foundInRaid":o.get("foundInRaid",False),
                           "optional":o.get("optional",False),"items":[{"id":ref(x),"name":items[ref(x)]["normalizedName"]} for x in o["items"]]})
    if not any(ref(r.get("item"))==KAPPA for r in collector["finishRewards"]["items"]):
        raise ValueError("Collector no longer rewards Kappa")
    closure=set()
    def visit(qid):
        if qid in closure:return
        if qid not in quests:raise ValueError("Unresolved Kappa dependency "+qid)
        closure.add(qid)
        for r in quests[qid].get("taskRequirements",[]):
            visit(ref(r["task"]))
    visit(COLLECTOR)
    # This is a candidate dependency closure; alternative completion/failure states
    # are preserved verbatim rather than interpreted as a mandatory AND.
    kappa={"id":COLLECTOR,"level":collector["minPlayerLevel"],
           "directPrerequisites":collector["taskRequirements"],
           "candidateDependencyClosure":sorted(closure),
           "traderRequirements":collector["traderRequirements"],
           "otherRequirements":collector["otherRequirements"],
           "objectives":objectives,"rewards":collector["finishRewards"],
           "referenceFlags":[id for id,q in quests.items() if q.get("kappaRequired") is True],
           "implemented":False}
    used_traders=defaultdict(int)
    for q in quests.values():used_traders[ref(q["trader"])]+=1
    trader_index=[{"id":id,"name":traders[id]["normalizedName"],"questCount":count,"levels":traders[id].get("levels",[])}
                  for id,count in sorted(used_traders.items())]
    out.mkdir(parents=True,exist_ok=True)
    write(out/"item-mapping-review.json",catalog)
    write(out/"collector-review.json",kappa)
    write(out/"resolved-traders.json",trader_index)
    summary={"questItemsInSource":len(quest_items),"referencedResolvedItems":len(catalog),
             "unresolvedItemReferences":len(missing),"collectorObjectives":len(objectives),
             "candidateKappaDependencyClosure":len(closure),"traders":trader_index}
    write(out/"item-review-summary.json",{"summary":summary,"unresolved":missing})
    print(json.dumps({k:v for k,v in summary.items() if k!="traders"},indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,default=Path("build/tarkov-reference"))
    p.add_argument("--out",type=Path,default=Path("build/tarkov-review"))
    a=p.parse_args()
    build(a.snapshot,a.out)
