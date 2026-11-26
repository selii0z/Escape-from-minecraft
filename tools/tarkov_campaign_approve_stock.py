#!/usr/bin/env python3
"""Merge reviewed, blocker-free stock drafts into a separate candidate gameplay catalog.
Never mutates checked-in resources. All approval decisions must be explicit."""
import argparse
import json
from pathlib import Path
from tarkov_campaign_bulk import build, read, write

def merge(snapshot,resources,review,approved,out):
    # Rebuild from the exact snapshot and current mappings rather than trusting a stale draft.
    build(snapshot,review,resources)
    data=read(resources/"quests.json")
    existing={q["id"]:q for q in data["quests"]}
    drafts={q["id"]:q for q in read(review/"bulk-stock-drafts.json")["quests"]}
    wanted=read(approved)
    if not isinstance(wanted,list) or not wanted or len(set(wanted))!=len(wanted):
        raise ValueError("Approval file must list distinct quest IDs")
    pending=set(wanted)
    for id in pending:
        if id in existing:raise ValueError("Already routed: "+id)
        if id not in drafts:raise ValueError("Not a stock quest: "+id)
    staged=[]
    while pending:
        progressed=False
        for id in sorted(pending):
            q=drafts[id]
            blockers=set(q["blockers"])
            if all(req["quest"] in existing for group in q["prerequisites"] for req in group):
                blockers.discard("dependency-not-routed")
            if blockers:continue
            r=q["reward"]
            if r["unmappedItems"] or r["additionalRewards"] or r["otherStanding"] or r["xp"] is None:
                raise ValueError("Incomplete reward mapping: "+id)
            if min(r["credits"],r["xp"],r["reputation"])<0:
                raise ValueError("Signed rewards require a reviewed adapter: "+id)
            row={k:v for k,v in q.items() if k not in ("alreadyRouted","reward","blockers")}
            row.update(implemented=True,review="approved",blockers=[],loyalty=0,
                       reward={k:r[k] for k in ("credits","xp","reputation")})
            existing[id]=row
            staged.append(row)
            pending.remove(id)
            progressed=True
            break
        if not progressed:
            raise ValueError("Unresolved blockers: "+json.dumps({id:drafts[id]["blockers"] for id in sorted(pending)}))
    data["quests"].extend(staged)
    if out.resolve()==(resources/"quests.json").resolve():
        raise ValueError("Candidate output must be separate from the gameplay resource")
    out.parent.mkdir(parents=True,exist_ok=True)
    write(out,data)
    print(f"Prepared {len(staged)} approved quests; total {len(data['quests'])}. Review and commit explicitly.")

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,default=Path("build/tarkov-reference"))
    p.add_argument("--resources",type=Path,default=Path("src/main/resources/data/taczoperations/campaign"))
    p.add_argument("--review",type=Path,default=Path("build/tarkov-review"))
    p.add_argument("--approved",type=Path,required=True)
    p.add_argument("--output",type=Path,default=Path("build/tarkov-review/candidate-quests.json"))
    a=p.parse_args()
    merge(a.snapshot,a.resources,a.review,a.approved,a.output)
