#!/usr/bin/env python3
"""Bulk campaign staging: preserve all reference conditions and produce safe adapter drafts.
No generated row is enabled automatically. Existing gameplay resources are not overwritten."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

STOCK = {"findItem", "giveItem"}
CORE = STOCK | {"shoot", "extract"}
FIELD = CORE | {"findQuestItem", "giveQuestItem", "visit"}
CURRENCY = {"5449016a4bdc2d6f028b456f": 100, "5696686a4bdc2da3298b456a": 1}
TRADERS = {
    "54cb50c76803fa8b248b4571":"prapor", "54cb57776803fa99248b456e":"therapist",
    "58330581ace78e27b8b10cee":"skier", "5935c25fb3acc3127c3d8cd9":"peacekeeper",
    "5a7c2eca46aef81a7ca2145d":"mechanic", "5ac3b934156ae10c4430e83c":"ragman",
    "5c0647fdd443bc2504c2d371":"jaeger", "579dc571d53a0658a154fbec":"fence",
    "6617beeaa9cfa777ca915b7c":"ref", "638f541a29ffd1183d187f57":"lightkeeper",
    "656f0f98d80a697f855d34b1":"btr-driver",
}
def read(p):
    return json.loads(p.read_text(encoding="utf-8"))
def write(p,v):
    p.write_text(json.dumps(v,ensure_ascii=False,indent=2,sort_keys=True)+"\n",encoding="utf-8")
def ids(p):
    return {line.split("|")[0] for line in p.read_text(encoding="utf-8").splitlines() if line.strip()}
def reward_plan(q):
    reward=q.get("finishRewards") or {}
    credits=0
    unresolved=[]
    for r in reward.get("items",[]):
        if r["item"] in CURRENCY and not r.get("attributes") and not r.get("contains"):
            credits+=r["count"]//CURRENCY[r["item"]]
        else:
            unresolved.append(r)
    standing=reward.get("traderStanding",[])
    return {"credits":credits,"xp":q["experience"]//100 if isinstance(q.get("experience"),int) else None,
            "reputation":round(1000*sum(r["standing"] for r in standing if r["trader"]==q["trader"])),
            "unmappedItems":unresolved,
            "otherStanding":[r for r in standing if r["trader"]!=q["trader"]],
            "additionalRewards":{k:v for k,v in reward.items() if k not in ("items","traderStanding") and v}}
def stock_draft(q, mapped):
    blockers=set()
    translated=[]
    objectives=q["objectives"]
    give=[o for o in objectives if o["type"]=="giveItem"]
    modes=set()
    for o in objectives:
        items=o.get("items",[])
        fir=o.get("foundInRaid")
        if not items or not isinstance(fir,bool) or not isinstance(o.get("count"),int) or o["count"]<1:
            blockers.add("invalid-stock-objective");continue
        if o.get("maps") or o.get("zones"):
            blockers.add("location-restricted-stock")
        if (o.get("dogTagLevel") or 0)!=0 or (o.get("minDurability") or 0)!=0 or o.get("maxDurability",100) not in (100,None):
            blockers.add("stock-condition-or-dogtag-level")
        if set(o)-{"id","description","type","count","optional","items","dogTagLevel","maxDurability","minDurability","foundInRaid","maps","zones"}:
            blockers.add("unknown-stock-predicate")
        if o["type"]=="findItem":
            if not any(set(g.get("items",[]))==set(items) and g.get("count")==o["count"]
                       and g.get("foundInRaid")==fir and not g.get("optional") for g in give):
                blockers.add("standalone-find-or-mismatched-handover")
            prefix="find-stock-"
        else:
            prefix="turnin-"
            modes.add(fir)
        if o.get("optional"):
            blockers.add("optional-stock")
        if not set(items)<=mapped:
            blockers.add("item-mapping-required")
        translated.append({"id":o["id"],"type":prefix+("fir:" if fir else "any:")+",".join(sorted(items)),
                           "amount":o["count"],"scope":"CAMPAIGN","optional":o.get("optional",False)})
    if len(modes)>1:
        blockers.add("mixed-provenance-handover")
    if not give:
        blockers.add("no-handover")
    # Keep every original condition and reward alongside the adapter draft.
    reward=reward_plan(q)
    if reward["xp"] is None:blockers.add("unknown-experience")
    if reward["unmappedItems"]:blockers.add("item-rewards")
    if reward["additionalRewards"]:blockers.add("additional-rewards")
    if reward["otherStanding"]:blockers.add("cross-trader-reputation")
    if any((q.get("startRewards") or {}).values()):blockers.add("start-rewards")
    if any((q.get("failureOutcome") or {}).values()) or q.get("failConditions"):blockers.add("failure-handling")
    if q.get("otherRequirements"):blockers.add("other-requirements")
    if q.get("availableDelaySecondsMin") or q.get("availableDelaySecondsMax"):blockers.add("quest-delay")
    if q.get("restartable"):blockers.add("repeatable")
    if q.get("factionName","Any")!="Any":blockers.add("faction")
    if q.get("taskRequirements"):blockers.add("dependency-graph")
    if q.get("traderRequirements"):blockers.add("trader-requirements")
    return {"id":q["id"],"name":q["normalizedName"],"trader":TRADERS[q["trader"]],
            "level":max(1,q.get("minPlayerLevel") or 0),"objectives":translated,"reward":reward,
            "implemented":False,"blockers":sorted(blockers)}
def build(snapshot,out,resources):
    manifest=read(snapshot/"manifest.json")
    for n,info in manifest["files"].items():
        if hashlib.sha256((snapshot/(n+".json")).read_bytes()).hexdigest()!=info["sha256"]:
            raise ValueError("Snapshot hash mismatch: "+n)
    tasks=read(snapshot/"tasks.json")["data"]["tasks"]
    if len(tasks)!=manifest["audit"]["tasks"]:raise ValueError("Task count mismatch")
    current={q["id"]:q for q in read(resources/"quests.json")["quests"]}
    mapped=ids(resources/"supplies.txt")|ids(resources/"collector-items.txt")
    if not set(current)<=set(tasks):raise ValueError("Unknown quest in gameplay catalogue")
    staged=[]
    stock=[]
    discrepancies=[]
    for qid,q in sorted(tasks.items()):
        kinds={o["type"] for o in q["objectives"]}
        bucket="stock" if kinds<=STOCK else "combat-extract" if kinds<=CORE else "field" if kinds<=FIELD else "special"
        staged.append({"id":qid,"slug":q["normalizedName"],"trader":TRADERS[q["trader"]],"batch":bucket,
                       "routed":qid in current,"reference":q})
        if kinds<=STOCK:
            draft=stock_draft(q,mapped)
            draft["alreadyRouted"]=qid in current
            stock.append(draft)
        if qid in current:
            adapted=current[qid]
            source={o["id"]:o for o in q["objectives"]}
            differences=[]
            for o in adapted["objectives"]:
                raw=source.get(o["id"])
                if raw is None:
                    differences.append({"objective":o["id"],"issue":"unknown-id"});continue
                if raw.get("count",1)!=o["amount"]:
                    differences.append({"objective":o["id"],"issue":"quantity","reference":raw.get("count",1),"adapted":o["amount"]})
                if o["type"].startswith(("turnin-","find-stock-")):
                    typ,refs=o["type"].split(":",1)
                    if set(refs.split(","))!=set(raw.get("items",[])):
                        differences.append({"objective":o["id"],"issue":"item-set"})
                    if typ.endswith("-fir")!=raw.get("foundInRaid",False):
                        differences.append({"objective":o["id"],"issue":"found-in-raid"})
            for missing in set(source)-{o["id"] for o in adapted["objectives"]}:
                differences.append({"objective":missing,"issue":"missing-reference-objective"})
            rewards=reward_plan(q)
            if rewards["additionalRewards"] or rewards["unmappedItems"] or rewards["otherStanding"]:
                differences.append({"issue":"review-additional-rewards","details":rewards})
            if differences:discrepancies.append({"id":qid,"slug":q["normalizedName"],"review":differences})
    out.mkdir(parents=True,exist_ok=True)
    write(out/"bulk-campaign-reference.json",{"schema":1,"snapshot":manifest,"quests":staged})
    write(out/"bulk-stock-drafts.json",{"schema":1,"quests":stock})
    write(out/"routed-quest-review.json",discrepancies)
    summary={"referenceQuests":len(tasks),"routedQuests":len(current),"batches":dict(Counter(q["batch"] for q in staged)),
             "stockDrafts":len(stock),"stockDraftsWithoutBlockers":sum(not q["blockers"] and not q["alreadyRouted"] for q in stock),
             "stockBlockers":dict(Counter(b for q in stock for b in q["blockers"])),
             "routedQuestsWithReviewNotes":len(discrepancies)}
    write(out/"bulk-summary.json",summary)
    print(json.dumps(summary,indent=2))
if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,default=Path("build/tarkov-reference"))
    p.add_argument("--out",type=Path,default=Path("build/tarkov-review"))
    p.add_argument("--resources",type=Path,default=Path("src/main/resources/data/taczoperations/campaign"))
    a=p.parse_args()
    build(a.snapshot,a.out,a.resources)
