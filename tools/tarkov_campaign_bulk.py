#!/usr/bin/env python3
"""Bulk campaign staging: preserve all reference conditions and produce safe adapter drafts.
No generated row is enabled automatically. Existing gameplay resources are not overwritten."""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

STOCK = {"findItem", "giveItem"}
CORE = STOCK | {"shoot", "extract", "sellItem"}
FIELD = CORE | {"findQuestItem", "giveQuestItem", "visit"}
CURRENCY = {"5449016a4bdc2d6f028b456f": 100, "5696686a4bdc2da3298b456a": 1, "569668774bdc2da2298b4568": 1}
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
# A reference task requirement is an AND of rows; statuses within a row are OR.
# Never infer a completion status from an unknown value.
OUTCOMES = {"active":"ACTIVE","started":"ACTIVE","complete":"COMPLETED","completed":"COMPLETED","success":"COMPLETED","fail":"FAILED","failed":"FAILED"}
def dependency_groups(q, known):
    groups, errors = [], []
    for req in q.get("taskRequirements") or []:
        if not isinstance(req,dict):
            errors.append({"reason":"invalid-requirement","raw":req});continue
        ref=req.get("task",req.get("taskId",req.get("id")))
        if isinstance(ref,dict):ref=ref.get("id")
        statuses=req.get("status",req.get("statuses"))
        if isinstance(statuses,str):statuses=[statuses]
        if not isinstance(ref,str) or ref not in known or ref==q.get("id"):
            errors.append({"reason":"invalid-reference","raw":req});continue
        if not isinstance(statuses,list) or not statuses or any(not isinstance(s,str) or s.lower() not in OUTCOMES for s in statuses):
            errors.append({"reason":"unknown-status","raw":req});continue
        groups.append([{"quest":ref,"outcomes":sorted({OUTCOMES[s.lower()] for s in statuses})}])
    return groups,errors

def dependency_audit(tasks,current):
    rows={}
    for qid,q in sorted(tasks.items()):
        groups,errors=dependency_groups(q,tasks)
        missing=sorted({r["quest"] for g in groups for r in g if r["quest"] not in current})
        rows[qid]={"slug":q["normalizedName"],"groups":groups,"errors":errors,"missingFromGameplay":missing}
    collector="5c51aac186f77432ea65c552"
    seeds={qid for qid,q in tasks.items() if q.get("kappaRequired") is True}|{collector}
    closure=set()
    pending=list(seeds)
    while pending:
        qid=pending.pop()
        if qid in closure or qid not in rows:continue
        closure.add(qid)
        pending.extend(r["quest"] for g in rows[qid]["groups"] for r in g)
    def canonical(groups):
        return sorted(tuple(sorted((r["quest"],tuple(sorted(r["outcomes"]))) for r in g)) for g in groups)
    mismatches=[]
    for qid,q in sorted(current.items()):
        row=rows[qid]
        if row["errors"] or canonical(q["prerequisites"])!=canonical(row["groups"]):
            mismatches.append({"id":qid,"slug":row["slug"],"gameplay":q["prerequisites"],"reference":row["groups"],"errors":row["errors"]})
    # Missing status rows can make the closure incomplete; report them explicitly.
    return {"schema":1,"quests":rows,"routedPrerequisiteMismatches":mismatches,
            "kappa":{"flaggedAndCollector":sorted(seeds),"dependencyClosure":sorted(closure),
                     "missingFromGameplay":sorted(closure-set(current)),
                     "unresolved":sorted(qid for qid in closure if rows[qid]["errors"])}}

def test_dependencies():
    a={"id":"a","normalizedName":"a","taskRequirements":[]}
    b={"id":"b","normalizedName":"b","taskRequirements":[{"task":"a","status":["complete","fail"]}]}
    tasks={"a":a,"b":b}
    assert dependency_groups(b,tasks)==([[{"quest":"a","outcomes":["COMPLETED","FAILED"]}]],[])
    bad=dict(b,taskRequirements=[{"task":"a","status":["unrecognized"]}])
    assert dependency_groups(bad,tasks)[0]==[] and dependency_groups(bad,tasks)[1]
    result=dependency_audit(tasks,{"b":{"prerequisites":[]}})
    assert result["quests"]["b"]["missingFromGameplay"]==["a"]
    assert len(result["routedPrerequisiteMismatches"])==1

def normalize_gates(q, current):
    blockers=set()
    prerequisites=[]
    statuses={"active":"ACTIVE","complete":"COMPLETED","failed":"FAILED"}
    for r in q.get("taskRequirements") or []:
        outcomes=[statuses[v] for v in r["status"] if v in statuses]
        if len(outcomes)!=len(r["status"]) or not outcomes:
            blockers.add("unknown-dependency-status")
        prerequisites.append([{"quest":r["task"],"outcomes":outcomes}])
        if r["task"] not in current: blockers.add("dependency-not-routed")
    trader_requirements=[]
    operations={">=":"GE","<=":"LE","<":"LT",">":"GT","==":"EQ"}
    for r in q.get("traderRequirements") or []:
        if r["requirementType"] not in ("level","reputation") or r["compareMethod"] not in operations:
            blockers.add("unknown-trader-requirement");continue
        value=r["value"]
        if r["requirementType"]=="reputation":value=round(value*1000)
        elif int(value)!=value or not 0<=value<=4:
            blockers.add("unsupported-loyalty");continue
        trader_requirements.append({"trader":TRADERS[r["trader"]],"kind":r["requirementType"].upper(),
                                    "operator":operations[r["compareMethod"]],"value":int(value)})
    dialogue=None
    for r in q.get("otherRequirements") or []:
        if r["type"]=="dialogue":
            # Intentional EFM adaptation: direct merchant interaction, no dialogue gate.
            continue
        else:
            blockers.add("unsupported-"+r["type"]+"-requirement")
    return {"prerequisites":prerequisites,"traderRequirements":trader_requirements,"dialogueTrader":dialogue},blockers

def test_dialogue_free_gates():
    trader="54cb50c76803fa8b248b4571"
    q={"trader":trader,"otherRequirements":[{"type":"dialogue","traders":[trader]}],
       "traderRequirements":[{"trader":trader,"requirementType":"level","compareMethod":">=","value":2}]}
    gates,blockers=normalize_gates(q,{})
    assert not blockers and gates["dialogueTrader"] is None
    assert gates["traderRequirements"]==[{"trader":"prapor","kind":"LEVEL","operator":"GE","value":2}]
    q["otherRequirements"].append({"type":"globalVariable"})
    assert "unsupported-globalVariable-requirement" in normalize_gates(q,{})[1]

def material_reward_plan(reward,mapped):
    items={}
    other={}
    blockers=set()
    for row in reward["unmappedItems"]:
        ref=row.get("item")
        count=row.get("count")
        if ref not in mapped or not isinstance(count,int) or count<=0 or row.get("attributes") or row.get("contains"):
            blockers.add("item-rewards")
            continue
        items[ref]=items.get(ref,0)+count
        if items[ref]>2147483647:blockers.add("item-rewards")
    for row in reward["otherStanding"]:
        trader=TRADERS.get(row.get("trader"))
        standing=row.get("standing")
        if not trader or not isinstance(standing,(int,float)):
            blockers.add("cross-trader-reputation");continue
        other[trader]=other.get(trader,0)+round(1000*standing)
        if abs(other[trader])>100000:blockers.add("cross-trader-reputation")
    return {"items":items,"otherStanding":other},blockers

def test_material_rewards():
    ref="a"*24
    reward={"unmappedItems":[{"item":ref,"count":3,"attributes":{}},{"item":ref,"count":2,"attributes":{}}],
            "otherStanding":[{"trader":"54cb57776803fa99248b456e","standing":-.02}]}
    plan,blockers=material_reward_plan(reward,{ref})
    assert not blockers and plan=={"items":{ref:5},"otherStanding":{"therapist":-20}}
    assert "item-rewards" in material_reward_plan(reward,set())[1]
    reward["unmappedItems"][0]["attributes"]={"durability":50}
    assert "item-rewards" in material_reward_plan(reward,{ref})[1]

def starter_plan(q,mapped):
    start=q.get("startRewards") or {}
    blockers=set()
    items={}
    if any(v for k,v in start.items() if k!="items"):blockers.add("unsupported-start-reward")
    for row in start.get("items") or []:
        ref,count=row.get("item"),row.get("count")
        if ref not in mapped or not isinstance(count,int) or not 1<=count<=2147483647 or row.get("attributes") or row.get("contains"):
            blockers.add("start-rewards");continue
        items[ref]=items.get(ref,0)+count
        if items[ref]>2147483647:blockers.add("start-rewards")
    return items,blockers

def test_starter_plan():
    ref="a"*24
    q={"startRewards":{"items":[{"item":ref,"count":3},{"item":ref,"count":2}]}}
    assert starter_plan(q,{ref})==({ref:5},set())
    assert "start-rewards" in starter_plan(q,set())[1]
    q["startRewards"]["items"][0]["attributes"]={"durability":50}
    assert "start-rewards" in starter_plan(q,{ref})[1]

def stock_draft(q, mapped, current):
    blockers=set()
    translated=[]
    objectives=q["objectives"]
    give=[o for o in objectives if o["type"]=="giveItem"]
    modes=set()
    for o in objectives:
        items=o.get("items",[])
        fir=o.get("foundInRaid")
        if len(items)==1 and items[0] in CURRENCY and o["type"]=="giveItem" and fir is False and not o.get("optional") and not o.get("maps") and not o.get("zones") and not o.get("dogTagLevel") and not o.get("minDurability") and o.get("maxDurability",100) in (100,None):
            amount=o.get("count")
            if isinstance(amount,int) and 1<=amount<=2147483647:
                cost=max(1,(amount+CURRENCY[items[0]]-1)//CURRENCY[items[0]])
                translated.append({"id":o["id"],"type":"pay-credits","amount":cost,"scope":"CAMPAIGN","optional":False,"label":"Verser "+str(cost)+" crédits"})
                continue
        if not items or not isinstance(fir,bool) or not isinstance(o.get("count"),int) or o["count"]<1:
            blockers.add("invalid-stock-objective");continue
        if o.get("maps") or o.get("zones"):
            blockers.add("location-restricted-stock")
        rank=o.get("dogTagLevel") or 0
        reviewed_rank=(q["id"]=="60e71ccb5688f6424c7bfec4" and rank==20 and o["type"]=="giveItem"
            and len(items)==1 and items[0] in {"59f32bb586f774757e1e8442","59f32c3b86f77472a31742f0"})
        if (rank!=0 and not reviewed_rank) or (o.get("minDurability") or 0)!=0 or o.get("maxDurability",100) not in (100,None):
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
        if reviewed_rank:translated[-1]["label"]="Remettre des plaques "+("BEAR" if items[0]=="59f32bb586f774757e1e8442" else "USEC")+" · niveau 20 minimum"
    # Mixed provenance is supported by the disjoint-stock allocator.
    if not give:
        blockers.add("no-handover")
    # Keep every original condition and reward alongside the adapter draft.
    reward=reward_plan(q)
    if reward["xp"] is None:blockers.add("unknown-experience")
    _,material_blockers=material_reward_plan(reward,mapped)
    blockers.update(material_blockers)
    if reward["additionalRewards"]:blockers.add("additional-rewards")
    starters,starter_blockers=starter_plan(q,mapped)
    blockers.update(starter_blockers)
    if any((q.get("failureOutcome") or {}).values()) or q.get("failConditions"):blockers.add("failure-handling")
    gates,gate_blockers=normalize_gates(q,current)
    blockers.update(gate_blockers)
    if q.get("availableDelaySecondsMin") or q.get("availableDelaySecondsMax"):blockers.add("quest-delay")
    if q.get("restartable"):blockers.add("repeatable")
    if q.get("factionName","Any")!="Any":blockers.add("faction")
    # Supported dependency and trader predicates are preserved in gates.
    return {"id":q["id"],"name":q["normalizedName"],"trader":TRADERS[q["trader"]],
            "level":max(1,q.get("minPlayerLevel") or 0),"objectives":translated,"reward":reward,
            "implemented":False,"adapter":"bulk-combat-v1" if any(o["type"]=="pay-credits" for o in translated) else "bulk-stock-v2","conflicts":[],"starterItems":starters,**gates,"blockers":sorted(blockers)}
MAP_ADAPTERS = {
    "55f2d3fd4bdc2d5f408b4567":"factory", "56f40101d2720b2a4d8b45d6":"customs",
    "5704e3c2d2720bac5b8b4567":"woods", "5714dbc024597771384a510d":"interchange",
    "5714dc692459777137212e12":"streets", "59fc81d786f774390775787e":"factory",
}
TARGET_ADAPTERS = {
    frozenset(["Any"]):"any", frozenset(["AnyPmc"]):"pmc",
    frozenset(["Savage","Marksman","assaultGroup"]):"scav",
    frozenset(["Savage"]):"scav", frozenset(["Marksman"]):"marksman",
    frozenset(["Marksman","followerKojaniy","followerGluharScout","bossBoarSniper","bossKojaniy"]):"longrange_targets",
    frozenset(["sectantPriest","sectantWarrior"]):"cultist",
    frozenset(["bossBoar","bossBully","bossGluhar","bossKilla","bossKojaniy","bossKolontay","bossSanitar","bossTagilla","bossZryachiy","bossKillaAgro","bossTagillaAgro","infectedTagilla","tagillaHelperAgro"]):"classic_boss",
    frozenset(["Bear"]):"bear", frozenset(["Usec"]):"usec",
    frozenset(["Marksman","bossBoarSniper"]):"marksman",
    frozenset(["bossBoar"]):"kaban", frozenset(["bossKolontay"]):"kollontay",
    frozenset(["bossKilla"]):"killa", frozenset(["bossTagilla"]):"foreman",
    frozenset(["bossBully"]):"reshala", frozenset(["followerBully"]):"reshala_escort", frozenset(["bossKojaniy"]):"tracker",
    frozenset(["followerBigPipe","followerBirdEye","ExUsec","bossKnight"]):"goons",
    frozenset(["bossGluhar"]):"glukhar", frozenset(["bossSanitar"]):"sanitar",
    frozenset(["bossKnight"]):"knight", frozenset(["followerBigPipe"]):"big_pipe",
    frozenset(["followerBirdEye"]):"birdeye", frozenset(["bossPartisan"]):"partisan",
}
def display_name(q):
    import re
    name=q.get("name")
    if name and not re.fullmatch(r"[0-9a-f]{24} name",name):
        return name
    # The mirror's name fields are untranslated localization keys.
    return q["normalizedName"].replace("-"," ").title()

# Known bolt-action reference set used by the Shooter chains; EFM uses its AWP class.
BOLT_REFERENCE={"627e14b21713922ded6f2c15","5bfd297f0db834001a669119","5ae08f0a5acfc408fb1398a1",
    "55801eed4bdc2d89578b4588","588892092459774ac91d4b11","5de652c31b7e3716273428be",
    "5df24cf80dee1b22f862e9bc","5bfea6e90db834001b7347f3","61f7c9e189e6fb1a5e3ea78d",
    "673cab3e03c6a20581028bc1","67c6de3ce39861860909e8e5"}
# Audited source alternative sets; unexpected sets stay blocked.
# EFM equivalents are base weapon families, without attachments or presets.
WEAPON_GROUPS={
    "d6607f5d84d294f1c73ec0be4803de0321ea5a8887440c81fad4caa2606369fb":"civilianlong",
    "c68d9f8998bd56a5910a94d1e2a2411512cac7e31304020569ed5d524996de5b":"pistol",
    "31138b3b113877fef665f246e685b9bb641134cec8ba7e92a4ac93f38256df5f":"rifle",
    "bbc5f2ca6ae8680227195cc1dff458757c116c88c8fba686f7aee18c09653b1b":"lmg",
    "82f6d52e4acd11319bb5814c922f2a17ad53f647deaaecdac94e9ff666e8edf8":"smg",
    "93d8779d1ce20c8b7062e3434da7c04861e4f1cc81f68c3663e411dae882215b":"ak",
    "55f6e3d2de618613b75f9d4adcd4b5143191c160d4444b4bf87f32a4536ae227":"shotgun",
    "2380c3b1d003e9cdd8c863d105f20bfa9bf94845e046a19eb709d4c41e6d0f74":"shotgun",
    "16880dc6d37b6bd36de325bdaf279faab8229bd0bede2c4920cdbc0546b35544":"pistol",
    "e07953c49fbab21dbf2e3b381487e79d911d50d8ae3e6f02683167abad8824e6":"pistol",
    "23426ad30af586b87349f02579a9cfc0f961096bc3047c2cabbfe81b6024b4ac":"ak",
    "f567b56ab07cfb247c02582d43e3cabd39600ba72575d067f5146874195f1bd6":"ak",
    "49c178a0f943942d6f89ac7844cf81c2736ae8fe94611932ab4a1130467bd43a":"western",
    "d6239b70320494fc567f16b32ccd769027a957279d7b2dea707de663311367f5":"western",
    "7613a9b6ec311855ed3f362cff3b25b794622d05ab2890a5c3f55bdeb948a1bf":"smg",
    "90c1486f9fef7a83704406c84d2a31664f3a666c324e0ea4275e0510a0b03fe7":"rifle",
    "b2ed8193677c863d5484f0c0ba663c072a1ef7b8142511b8a27c38a9f16baaab":"shotgun",
}
def weapon_group(refs):
    if not refs:return "any"
    if refs<=BOLT_REFERENCE:return "bolt"
    return WEAPON_GROUPS.get(hashlib.sha256(",".join(sorted(refs)).encode()).hexdigest())

# Exact reference sets with explicitly chosen equivalents in EFM.
GEAR_REQUIRED={
    "c8a228993a78b032d347888f0246f06bedbca74381cb7d1e3548fa4d017cf8d4":"elite_rig",
    "f29275ad30ac8ad850616910ae69898fe36eba733cc7adfa4f824e343caa5f5a":"scavuniform",
    "dcb1ada0a20d04ab3a0f1c6cdeb2cfec1e60dcd93e18cd43ed5e721992db7a4b":"heavyuniform",
    "d6e904eff6f3c50d8d49af80ced1320c6f093e5e5b0f20b0ef957a14304074ed":"uniform2",
    "4f5a98a4b84772300c3798c2cd847bd4c74528bd166981c04c4e46bddd0167d6":"armor2",
}
GEAR_FORBIDDEN={
    "4dc75f5e78afd46ef8589c2b1b4759cb864db7a2004effeb911e2c30e0cbf812":"novision",
    "325256dae9760b0fe3ebc824dc75335bfa694098b05cd29a2ca6adeabffb3df6":"noarmor",
    "18706061bb3caa06edf3b93e761e910dee5f6aca92f0fb343fda828d3e351d55":"unarmored",
}
def gear_requirement(o):
    required=o.get("wearing") or []
    forbidden=o.get("notWearing") or []
    if required and forbidden:
        key=hashlib.sha256(",".join(sorted("+".join(sorted(x["id"] for x in group)) for group in required)).encode()).hexdigest()
        denied=hashlib.sha256(",".join(sorted(x["id"] for x in forbidden)).encode()).hexdigest()
        pairs={
            ("912fe9e780735e34b8dd1bd7edf82c94b05e9428be6d710936a949f32c231ece","486dc1a03aab9c8badcdc361462c855b303da2abc6f21a9e0864db056e434e4b"):"headset_noarmor",
            ("8278d31243dbde802193292fab1cf66aa1395891e6071d7db38d89252d3e0815","e05253590017f06b41cbbf87b42cb8cc798c608bdb875a12029b1a23bf0b4271"):"helmet_noheadset",
        }
        return pairs.get((key,denied))
    if required:
        key=hashlib.sha256(",".join(sorted("+".join(sorted(x["id"] for x in group)) for group in required)).encode()).hexdigest()
        return GEAR_REQUIRED.get(key)
    if forbidden:
        key=hashlib.sha256(",".join(sorted(x["id"] for x in forbidden)).encode()).hexdigest()
        return GEAR_FORBIDDEN.get(key)
    return "any"

def combat_objective(o):
    blockers=set()
    maps=o.get("maps") or []
    if not set(maps)<=MAP_ADAPTERS.keys():
        blockers.add("map-not-implemented")
    if "59fc81d786f774390775787e" in maps and "55f2d3fd4bdc2d5f408b4567" not in maps:
        blockers.add("night-factory-only")
    map_value=",".join(sorted({MAP_ADAPTERS[m] for m in maps if m in MAP_ADAPTERS})) if maps else "*"
    if not isinstance(o.get("count"),int) or o["count"]<1 or o.get("optional") is not False:
        blockers.add("invalid-or-optional-combat-objective")
    basic={"id","description","type","count","optional","maps"}
    if o["type"]=="sellItem":
        refs=o.get("items") or []
        sales={
            "67519696567b9773f0811bae":("6eadb0b890cc2286d4401b915f3c1081445e0a312954c4f6e7605b2cf6edc023",50,"armorer","Armurier"),
            "675196dff77c0b8436ec1ef5":("2d6452509695bc6739952864384ccccf277664adb0f12ab9b248acd8fa00409c",50,"salvager","Récupérateur"),
            "673f637a1fbc23a60a72b743":("0df255050aed47cf1caf5df3f38fb462551ea89c8104845e17d32c7cb89a2775",50,"broker","Courtier"),
            "675197664e610fc2b88e0bf3":("a4dd71dbcdb33e78322c86f6ccf50d47aad20747f71c997d11b3b588e3ed807e",75,"broker","Courtier")
        }
        if o["id"] in sales:
            digest,amount,merchant,title=sales[o["id"]]
            if (hashlib.sha256(",".join(sorted(refs)).encode()).hexdigest()!=digest or o.get("count")!=amount
                or o.get("minDurability") not in (0,None) or o.get("maxDurability")!=100 or o.get("dogTagLevel")
                or o.get("foundInRaid") is not False or maps or o.get("zones")):
                blockers.add("unreviewed-sale-objective")
            return {"id":o["id"],"type":"sale:merchant:"+merchant,"amount":amount,"scope":"CAMPAIGN","optional":o.get("optional",False),
                    "label":"Vendre "+str(amount)+" objets au "+title},blockers
        if (o["id"]!="666973ee1d80fbbbfeaf46c9" or o.get("count")!=250
            or hashlib.sha256(",".join(sorted(refs)).encode()).hexdigest()!="7c3c600c5123e988214579a3f88553e279abc2906e9fc72c2ba25e49ec8cb6ae"
            or o.get("minDurability") not in (0,None) or o.get("maxDurability")!=100
            or o.get("dogTagLevel") or maps or o.get("zones")):
            blockers.add("unreviewed-sale-objective")
        return {"id":o["id"],"type":"sale:field-equipment","amount":o["count"],"scope":"CAMPAIGN","optional":o.get("optional",False),
                "label":"Vendre sacs, gilets et protections EFM au Récupérateur"},blockers
    if o["type"]=="shoot":
        known=basic|{"shotType","bodyParts","usingWeapon","usingWeaponMods","distance","timeFromHour","timeUntilHour",
                      "wearing","notWearing","enemyHealthEffect","targetNames","playerHealthEffect","zones","effects"}
        if set(o)-known:blockers.add("unknown-combat-predicate")
        mode=o.get("shotType")
        if mode not in {"kill","hit"}:blockers.add("hit-tracking-required")
        for key in ("usingWeaponMods","enemyHealthEffect","playerHealthEffect","zones","effects"):
            if o.get(key):blockers.add("combat-"+key)
        gear=gear_requirement(o)
        if gear is None:
            blockers.add("combat-gear-unmapped")
            gear="any"
        if ((o["id"]=="5d25c8c986f77443e47ad47a" and o.get("effects")==["Pain"] and (o.get("playerHealthEffect") or {}).get("effects")==["Pain"]) or (o["id"]=="5d25d4e786f77442734d335d" and o.get("effects")==["Tremor"] and (o.get("playerHealthEffect") or {}).get("effects")==["Tremor"])) and gear=="any":
            blockers.discard("combat-effects");blockers.discard("combat-playerHealthEffect");gear="injured"
        hours=(o.get("timeFromHour") or 0,o.get("timeUntilHour") or 0)
        night=hours in {(21,4),(21,5),(21,6)}
        if hours!=(0,0) and not night:blockers.add("unsupported-combat-time-window")
        distance=o.get("distance") or {"value":0,"compareMethod":">="}
        value=distance.get("value")
        compare={">=":"ge","<=":"le",">":"gt","<":"lt"}.get(distance.get("compareMethod"))
        if not isinstance(value,int) or not 0<=value<=1000 or not compare:
            blockers.add("distance-restriction")
            value=0;compare="ge"
        body={p.rsplit("/",1)[-1] for p in (o.get("bodyParts") or [])}
        part="any"
        if body=={"Head"}:part="head"
        elif body=={"LeftLeg","RightLeg"}:part="legs"
        elif body and body!={"Head","Chest","Stomach","LeftArm","RightArm","LeftLeg","RightLeg"}:blockers.add("combat-bodyParts")
        weapons=set(o.get("usingWeapon") or [])
        weapon="shotgun" if o["id"]=="66632deea5607d352f3aa844" and weapons=={"5580223e4bdc2d1c128b457f","64748cb8de82c85eaf0a273a"} else weapon_group(weapons)
        if weapon is None:
            blockers.add("combat-usingWeapon")
            weapon="any"
        role=TARGET_ADAPTERS.get(frozenset(o.get("targetNames") or []))
        if role is None and hashlib.sha256(",".join(sorted(o.get("targetNames") or [])).encode()).hexdigest()=="7b34312db838c04340030ca40de6b233fafc037b517a3e96f815fd0b9fe433c6":role="boss_escort"
        if role is None:blockers.add("unmapped-target")
        event="kill:"+map_value+":"+(role or "unsupported")
        scope="CAMPAIGN"
        label=("Toucher " if mode=="hit" else "Éliminer ")+({"any":"des ennemis","pmc":"des PMC","scav":"des Scavs"}.get(role,role or "cible à adapter"))
        if o["id"]=="5d25d09286f77444001e284c" and o["count"]==3 and map_value=="woods" and role=="scav":
            event="noheal:woods:scav";scope="CURRENT_RAID";label="Éliminer 3 Scavs et extraire dans le même raid sans utiliser de soin EFM"
        elif mode=="hit" or part!="any" or weapon!="any" or value or compare!="ge" or night or gear!="any":
            event="gunshot:"+map_value+":"+(role or "unsupported")+":"+str(mode)+":"+part+":"+weapon+":"+compare+":"+str(value)
            if night or gear!="any":event+=":"+("night" if night else "anytime")+":"+gear
            if night:label+=" · raid de nuit"
            if gear!="any":label+=" · "+{"headset_noarmor":"casque audio tactique, sans armure ni gilet","helmet_noheadset":"casque classe 2, sans casque audio","elite_rig":"gilet élite EFM équipé","scavuniform":"casque en cuir et gilet léger","novision":"sans lunettes nocturnes ni thermiques","injured":"avec une blessure EFM active","heavyuniform":"tunique classe 6 et casque classe 3","uniform2":"casque et tunique classe 2","armor2":"tunique classe 2","noarmor":"sans armure ni gilet","unarmored":"sans protection ni gilet"}[gear]
            if part!="any":label+=" · "+("tête" if part=="head" else "jambes")
            if weapon!="any":label+=" · "+{"civilianlong":"fusil à pompe, fusil à verrou ou AK EFM","bolt":"fusil à verrou EFM","pistol":"pistolet EFM","ak":"AK EFM","western":"M4 EFM","smg":"PM EFM","rifle":"fusil d’assaut EFM","shotgun":"fusil à pompe EFM","lmg":"mitrailleuse EFM"}[weapon]
            if value:label+=" · "+distance["compareMethod"]+" "+str(value)+" blocs"
    else:
        if set(o)-(basic|{"exitStatus","exitName","healthEffect","bodyParts","effects"}):
            blockers.add("unknown-extraction-predicate")
        if any(o.get(k) for k in ("exitName","healthEffect","bodyParts","effects")):
            blockers.add("specific-extraction-condition")
        # EFM only reports successful extraction, not run-through or map transitions.
        statuses=set(o.get("exitStatus") or [])
        if "ExpBonusSurvived" not in statuses:blockers.add("unsupported-extraction-status")
        event="extract:"+map_value
        scope="SURVIVING_RAID"
        label="Extractions réussies"
    if maps:label+=" · "+map_value
    return {"id":o["id"],"type":event,"amount":o.get("count"),"scope":scope,
            "optional":o.get("optional",False),"label":label},blockers

FIELD_CORE=CORE|{"visit","mark","plantItem","findQuestItem","giveQuestItem","traderLevel","traderStanding","skill","taskStatus"}

def field_objective(o,mapped):
    # A single reference zone (duplicated geometry is normal) on a supported map.
    # Preserve the zone identity and use a deterministic EFM encounter anchor.
    errors=set()
    maps=o.get("maps") or []
    zones=o.get("zones") or []
    zone_ids={z.get("id") for z in zones}
    if not maps or not set(maps)<=MAP_ADAPTERS.keys():errors.add("field-map-unavailable")
    translated_maps={MAP_ADAPTERS[m] for m in maps if m in MAP_ADAPTERS}
    if len(translated_maps)!=1 or len(zone_ids)!=1 or None in zone_ids or not zones:
        errors.add("field-multiple-sites")
    if set(maps)=={"59fc81d786f774390775787e"}:errors.add("night-factory-only")
    if o.get("optional") is not False:errors.add("optional-field")
    kind=o["type"]
    count=o.get("count",1)
    if not isinstance(count,int) or not 1<=count<=64:errors.add("field-count")
    basic={"id","description","type","count","optional","maps","zones"}
    zone=next(iter(zone_ids),None)
    key=hashlib.sha256(str(zone).encode()).hexdigest()[:16]
    map_name=next(iter(translated_maps),"unsupported")
    if kind=="visit":
        if set(o)-basic or count!=1:errors.add("visit-predicate")
        typ="visit-adapted:"+map_name+":"+key
        label="Reconnaître le secteur · "+map_name
    else:
        if kind=="mark":
            ref=o.get("markerItem")
            if set(o)-(basic|{"markerItem"}) or count!=1:errors.add("mark-predicate")
        else:
            refs=o.get("items") or []
            ref=refs[0] if len(refs)==1 else None
            if (set(o)-(basic|{"items","foundInRaid","dogTagLevel","maxDurability","minDurability"})
                or o.get("foundInRaid") is not False or o.get("dogTagLevel",0)!=0
                or o.get("minDurability",0)!=0 or o.get("maxDurability",100) not in (100,None)):
                errors.add("plant-predicate")
        if ref not in mapped:errors.add("field-item-mapping")
        typ="plant-adapted:"+map_name+":"+key+":"+str(ref)
        label=("Baliser" if kind=="mark" else "Déposer le matériel")+" · "+map_name
    return {"id":o["id"],"type":typ,"amount":count,"scope":"CAMPAIGN","optional":o.get("optional",False),
            "label":label,"sourceZone":zone},errors

def test_field_adapter():
    o={"id":"x","type":"visit","optional":False,"maps":["5704e3c2d2720bac5b8b4567"],"zones":[{"id":"camp"},{"id":"camp"}]}
    row,errors=field_objective(o,set())
    assert not errors and row["type"].startswith("visit-adapted:woods:") and row["amount"]==1
    assert "field-multiple-sites" in field_objective(dict(o,zones=[{"id":"camp"},{"id":"other"}]),set())[1]
    ref="a"*24
    row,errors=field_objective(dict(o,type="mark",markerItem=ref,count=1),{ref})
    assert not errors and row["type"].endswith(":"+ref)
    assert "field-item-mapping" in field_objective(dict(o,type="mark",markerItem=ref,count=1),set())[1]

# EFM consolidates Tarkov's individual skills into eight passive categories.
SKILL_ADAPTERS={"Intellect":"SEARCH","LightVests":"SURVIVAL","HeavyVests":"SURVIVAL","Endurance":"ENDURANCE","Strength":"STRENGTH","Attention":"SEARCH","Search":"SEARCH",
    "Perception":"SEARCH","Surgery":"MEDICINE","Vitality":"SURVIVAL","Health":"SURVIVAL",
    "StressResistance":"SURVIVAL","Immunity":"SURVIVAL","Metabolism":"SURVIVAL",
    "CovertMovement":"STEALTH","Charisma":"TRADE","AimDrills":"WEAPONS","TroubleShooting":"WEAPONS",
    "WeaponTreatment":"WEAPONS","MagDrills":"WEAPONS","SMG":"WEAPONS","Shotgun":"WEAPONS",
    "Sniper":"WEAPONS","Pistol":"WEAPONS","DMR":"WEAPONS","LMG":"WEAPONS","HMG":"WEAPONS",
    "Revolver":"WEAPONS","Assault":"WEAPONS"}

def milestone_objective(o,ready):
    errors=set()
    basic={"id","description","type","optional","count","zones","maps"}
    if o.get("optional") is not False or o.get("maps") or o.get("zones") or o.get("count") not in (None,1):
        errors.add("milestone-predicate")
    kind=o["type"]
    if kind=="traderLevel":
        trader=TRADERS.get(o.get("trader"))
        level=o.get("level")
        if not trader or not isinstance(level,int) or not 1<=level<=4 or set(o)-(basic|{"trader","level"}):
            errors.add("milestone-loyalty")
        typ=f"milestone:loyalty:{trader}:{level}"
        label=f"Atteindre la fidélité {level} · {trader}"
    elif kind=="traderStanding":
        trader=TRADERS.get(o.get("trader"))
        value=o.get("value")
        if not trader or not isinstance(value,(int,float)) or not -100<=value<=100 or o.get("compareMethod")!=">=" or set(o)-(basic|{"trader","value","compareMethod"}):
            errors.add("milestone-reputation")
        typ=f"milestone:reputation:{trader}:{round(value*1000) if isinstance(value,(int,float)) else 0}"
        label=f"Atteindre {value} de réputation · {trader}"
    elif kind=="skill":
        skill=SKILL_ADAPTERS.get(o.get("skill"))
        level=o.get("level")
        if not skill or not isinstance(level,int) or not 1<=level<=50 or set(o)-(basic|{"skill","level"}):
            errors.add("milestone-skill")
        typ=f"milestone:skill:{skill}:{level}"
        label=f"Compétence EFM {skill} · niveau {level}"
    else:
        task=o.get("task")
        if task not in ready or o.get("status")!=["complete"] or set(o)-(basic|{"task","status"}):
            errors.add("milestone-task-not-ready")
        typ=f"milestone:task:{task}"
        label="Terminer la mission associée"
    return {"id":o["id"],"type":typ,"amount":1,"scope":"CAMPAIGN","optional":o.get("optional",False),"label":label},errors

def skill_reward_plan(reward):
    result={}
    for entry in reward.get("skillLevelReward") or []:
        skill=SKILL_ADAPTERS.get(entry.get("skill"))
        level=entry.get("level")
        if not skill or not isinstance(level,int) or not 1<=level<=50:return None
        result[skill]=max(result.get(skill,0),level)
    return result

def test_milestones():
    o={"id":"x","type":"traderLevel","optional":False,"trader":"54cb50c76803fa8b248b4571","level":3}
    row,errors=milestone_objective(o,set())
    assert not errors and row["type"]=="milestone:loyalty:prapor:3"
    assert milestone_objective(dict(o,level=5),set())[1]
    assert skill_reward_plan({"skillLevelReward":[{"skill":"Pistol","level":2},{"skill":"SMG","level":3}]})=={"WEAPONS":3}
    assert skill_reward_plan({"skillLevelReward":[{"skill":"Unknown","level":1}]}) is None

def quest_item_objective(o,objectives):
    errors=set()
    ref=o.get("questItem")
    if not isinstance(ref,str) or len(ref)!=24 or any(ch not in "0123456789abcdef" for ch in ref):
        errors.add("quest-item-id")
    if o.get("count")!=1 or o.get("optional") is not False:errors.add("quest-item-count")
    finds=[v for v in objectives if v["type"]=="findQuestItem" and v.get("questItem")==ref]
    gives=[v for v in objectives if v["type"]=="giveQuestItem" and v.get("questItem")==ref]
    if len(finds)!=1 or len(gives)!=1:errors.add("quest-item-pair")
    basic={"id","description","type","count","optional","maps","questItem"}
    if o["type"]=="findQuestItem":
        maps=o.get("maps") or []
        translated={MAP_ADAPTERS[m] for m in maps if m in MAP_ADAPTERS}
        if not maps or not set(maps)<=MAP_ADAPTERS.keys() or len(translated)!=1:errors.add("quest-item-map")
        if set(maps)=={"59fc81d786f774390775787e"}:errors.add("night-factory-only")
        locations=o.get("possibleLocations") or []
        if not locations or any(v.get("map") not in maps or not v.get("positions") for v in locations):errors.add("quest-item-locations")
        if set(o)-(basic|{"possibleLocations"}):errors.add("quest-item-predicate")
        map_name=next(iter(translated),"unsupported")
        key=hashlib.sha256(str(ref).encode()).hexdigest()[:16]
        typ="quest-find-adapted:"+map_name+":"+key+":"+str(ref)
        scope="SURVIVING_RAID"
        label="Récupérer l'objet de mission · "+map_name
    else:
        if set(o)-basic or o.get("maps"):errors.add("quest-item-predicate")
        typ="quest-give-adapted:"+str(ref)
        scope="CAMPAIGN"
        label="Remettre l'objet de mission extrait"
    return {"id":o["id"],"type":typ,"amount":1,"scope":scope,"optional":o.get("optional",False),"label":label},errors

def test_quest_item_adapter():
    ref="a"*24
    map_id="5704e3c2d2720bac5b8b4567"
    f={"id":"f","type":"findQuestItem","count":1,"optional":False,"maps":[map_id],
       "questItem":ref,"possibleLocations":[{"map":map_id,"positions":[{"x":0,"y":0,"z":0}]}]}
    g={"id":"g","type":"giveQuestItem","count":1,"optional":False,"maps":[],"questItem":ref}
    row,errors=quest_item_objective(f,[f,g])
    assert not errors and row["scope"]=="SURVIVING_RAID" and row["type"].endswith(":"+ref)
    assert not quest_item_objective(g,[f,g])[1]
    assert "quest-item-pair" in quest_item_objective(f,[f])[1]

def combat_draft(q,mapped,current):
    objectives=q["objectives"]
    # Reuse the stock validator for rewards and other gates, even when there is no handover.
    stock=[o for o in objectives if o["type"] in STOCK]
    d=stock_draft(dict(q,objectives=stock),mapped,current)
    blockers=set(d["blockers"])
    if not stock:blockers.discard("no-handover")
    for o in objectives:
        if o["type"] not in STOCK:
            if o["type"] in {"findQuestItem","giveQuestItem"}:translated,errors=quest_item_objective(o,objectives)
            elif o["type"] in {"traderLevel","traderStanding","skill","taskStatus"}:translated,errors=milestone_objective(o,current)
            elif o["type"] in FIELD_CORE-CORE:translated,errors=field_objective(o,mapped)
            else:translated,errors=combat_objective(o)
            d["objectives"].append(translated);blockers.update(errors)
    if q.get("neededKeys"):blockers.add("required-key")
    d.update({"adapter":"bulk-combat-v1","blockers":sorted(blockers)})
    return d

def test_combat_adapter():
    o={"id":"x","type":"shoot","count":1,"optional":False,"shotType":"kill",
       "targetNames":["bossBoar"],"maps":["5714dc692459777137212e12"],
       "distance":{"value":0,"compareMethod":">="}}
    result,errors=combat_objective(o)
    assert not errors and result["type"]=="kill:streets:kaban"
    assert "combat-usingWeapon" in combat_objective(dict(o,usingWeapon=["test"]))[1]
    assert "map-not-implemented" in combat_objective(dict(o,maps=["missing"]))[1]
    e={"id":"e","type":"extract","count":1,"optional":False,"maps":[],"exitStatus":["ExpBonusSurvived"]}
    result,errors=combat_objective(e)
    assert not errors and result["type"]=="extract:*" and result["scope"]=="SURVIVING_RAID"
    assert "specific-extraction-condition" in combat_objective(dict(e,exitName="gate"))[1]
    assert "unsupported-extraction-status" in combat_objective(dict(e,exitStatus=["marathon Name"]))[1]

def offer_plan(tasks,items,mapped,out):
    """Preserve every reference unlock; sell only exact, simple mapped trade supplies.
    Prices use reference base price converted to EFM credits, not live flea prices."""
    rows=[]
    supported={}
    for qid,q in sorted(tasks.items()):
        entries=(q.get("finishRewards") or {}).get("offerUnlock") or []
        approved=True
        for offer in entries:
            ref=offer.get("item")
            item=items.get(ref,{})
            price=item.get("basePrice")
            reasons=[]
            if ref not in mapped:reasons.append("item-not-mapped")
            if offer.get("contains") or offer.get("attributes"):reasons.append("preset-or-attributes")
            if not isinstance(offer.get("count"),int) or not 1<=offer["count"]<=64:reasons.append("quantity")
            if not isinstance(offer.get("level"),int) or not 1<=offer["level"]<=4:reasons.append("loyalty")
            if offer.get("trader") not in TRADERS:reasons.append("trader")
            if not isinstance(price,int) or price<=0:reasons.append("price")
            if not isinstance(ref,str) or len(ref)!=24:reasons.append("item-id")
            # Only inert barter/provision/medical supplies already mapped in EFM.
            if not set(item.get("types",[])) & {"barter","provisions","meds","injectors","keys"}:
                reasons.append("functional-equipment-adapter-required")
            row={"id":offer["id"],"quest":qid,"questName":display_name(q),
                 "item":ref,"name":item.get("normalizedName",ref),"trader":TRADERS.get(offer.get("trader")),
                 "loyalty":offer.get("level"),"count":offer.get("count"),
                 "price":max(1,(price*offer["count"]+99)//100) if not reasons else None,
                 "supported":not reasons,"blockers":reasons}
            rows.append(row)
            if reasons:approved=False
        if entries and approved:supported[qid]=True
    write(out/"campaign-unlock-offers.json",{"schema":1,"offers":rows})
    return supported,len(rows),sum(r["supported"] for r in rows)

def test_offer_plan():
    from tempfile import TemporaryDirectory
    ref="a"*24
    qid="b"*24
    base={"id":"c"*24,"item":ref,"level":2,"count":2,"trader":"54cb50c76803fa8b248b4571","attributes":[],"contains":[]}
    tasks={qid:{"normalizedName":"test-quest","finishRewards":{"offerUnlock":[base]}}}
    with TemporaryDirectory() as tmp:
        approved,n,s=offer_plan(tasks,{ref:{"types":["barter"],"basePrice":151}}, {ref},Path(tmp))
        assert approved=={qid:True} and n==s==1
        assert read(Path(tmp)/"campaign-unlock-offers.json")["offers"][0]["price"]==4
        base["contains"]=[{"item":ref}]
        assert offer_plan(tasks,{ref:{"types":["barter"],"basePrice":151}}, {ref},Path(tmp))[2]==0

def activate_stock_batch(tasks,current,mapped,dependencies,out,item_names=None,approved_offers=None):
    item_names=item_names or {}
    approved_offers=approved_offers or {}
    """Build a separate development catalogue; never alter checked-in quests.
    Only exact, supported handovers with all gates and rewards represented qualify."""
    import re
    def slug(name):
        return re.sub(r"^-|-$","",re.sub(r"[^a-z0-9]+","-",name.lower()))
    result=dict(current)
    ready={qid for qid,q in current.items() if q.get("implemented") is True}
    names={slug(q["name"]) for q in current.values()}
    pending={qid:q for qid,q in tasks.items() if qid not in current and
             q["objectives"] and
             {o["type"] for o in q["objectives"]}<=FIELD_CORE}
    installed=[]
    while True:
        changed=False
        for qid,q in sorted(pending.items()):
            kinds={o["type"] for o in q["objectives"]}
            d=stock_draft(q,mapped,ready) if kinds<=STOCK else combat_draft(q,mapped,ready)
            additional=dict(d["reward"]["additionalRewards"])
            if approved_offers.get(qid):additional.pop("offerUnlock",None)
            skills=skill_reward_plan(additional)
            if skills is not None:additional.pop("skillLevelReward",None)
            if not additional:
                d["blockers"]=[b for b in d["blockers"] if b!="additional-rewards"]
            dep=dependencies["quests"][qid]
            if d["blockers"] or dep["errors"] or d["prerequisites"]!=dep["groups"]:
                continue
            if any(not any(r["quest"] in ready and r["outcomes"]==["COMPLETED"] for r in group)
                   for group in d["prerequisites"]):
                continue
            if not d["objectives"] or len({o["id"] for o in d["objectives"]})!=len(d["objectives"]):
                continue
            if not re.fullmatch(r"[0-9a-f]{24}",qid) or any(
                not re.fullmatch(r"[0-9a-f]{24}",ref)
                for o in d["objectives"] if o["type"].startswith(("turnin-","find-stock-"))
                for ref in o["type"].split(":",1)[1].split(",")):
                continue
            reward={k:d["reward"][k] for k in ("credits","xp","reputation")}
            if any(not isinstance(reward[k],int) or not 0<=reward[k]<=2147483647 for k in ("credits","xp")):
                continue
            if not isinstance(reward["reputation"],int) or abs(reward["reputation"])>100000:continue
            material,errors=material_reward_plan(d["reward"],mapped)
            if errors:continue
            reward.update(material)
            if skills:reward["skills"]=skills
            name=display_name(q)
            if not slug(name) or slug(name) in names:
                continue
            # Explicitly limit this batch to the existing stock adapter.
            row={**d,"name":name,"reward":reward,"implemented":True,
                 "review":"approved","approvalMethod":"strict-core-v1",
                 "reference":{"level":q.get("minPlayerLevel"),"experience":q.get("experience"),
                              "reward":d["reward"]}}
            for o in row["objectives"]:
                if "label" in o:continue
                mode="trouvé en raid" if "-fir:" in o["type"] else "toute provenance"
                refs=o["type"].split(":",1)[1].split(",")
                names_text=" / ".join(item_names.get(ref,ref) for ref in refs[:3])
                if len(refs)>3:names_text+=" ou "+str(len(refs)-3)+" alternative(s)"
                o["label"]=("Trouver" if o["type"].startswith("find-stock-") else "Remettre")+" : "+names_text+" ("+mode+")"
            if qid in {"5bc4893c86f774626f5ebf3e","5c0be13186f7746f016734aa"}:
                for o in row["objectives"]:o["label"]+=" · sans échec de raid (remise à zéro en cas d'échec)"
            if qid in {"66631489acf8442f8b05319f","6663149196a9349baa021baa"}:
                for o in row["objectives"]:o["label"]+=" · remise à zéro si "+("Scav ou boss interdit éliminé" if qid=="66631489acf8442f8b05319f" else "boss interdit éliminé")
            result[qid]=row
            ready.add(qid);names.add(slug(name));installed.append(qid);changed=True
        for qid in installed:pending.pop(qid,None)
        if not changed:break
    remaining=[]
    for qid,q in sorted(tasks.items()):
        if qid in ready:continue
        kinds={o["type"] for o in q["objectives"]}
        if not kinds or not kinds<=FIELD_CORE:
            blockers=["unsupported-objective:"+k for k in sorted(kinds-FIELD_CORE)] or ["empty-objectives"]
        else:
            d=stock_draft(q,mapped,ready) if kinds<=STOCK else combat_draft(q,mapped,ready)
            blockers=list(d["blockers"])
            additional=dict(d["reward"]["additionalRewards"])
            if approved_offers.get(qid):additional.pop("offerUnlock",None)
            if skill_reward_plan(additional) is not None:additional.pop("skillLevelReward",None)
            if not additional:blockers=[b for b in blockers if b!="additional-rewards"]
            elif "additional-rewards" in blockers:
                blockers.remove("additional-rewards")
                blockers.extend("reward:"+key for key in sorted(additional))
            if qid=="5c51aac186f77432ea65c552" and "5c093ca986f7740a1867ab12" not in mapped:blockers.append("collector-kappa-adapter")
            if not blockers:blockers.append("dependency-or-final-validation")
        remaining.append({"id":qid,"name":display_name(q),"trader":TRADERS[q["trader"]],
                          "kappa":qid in dependencies["kappa"]["dependencyClosure"],"blockers":sorted(set(blockers))})
    write(out/"activation-blockers.json",{"schema":1,"remaining":len(remaining),
        "counts":dict(Counter(b for row in remaining for b in row["blockers"])),
        "kappaRemaining":[row for row in remaining if row["kappa"]],"quests":remaining})
    print(json.dumps({"remainingCampaignBlockers":dict(Counter(b for row in remaining for b in row["blockers"])),
                      "remainingKappaQuests":sum(row["kappa"] for row in remaining)},indent=2))
    write(out/"quests-development.json",{"schema":1,"quests":list(result.values())})
    write(out/"stock-activation-report.json",{"schema":1,"base":len(current),
          "added":installed,"total":len(result),"remainingStock":len(pending)})
    return len(installed)

def test_stock_activation():
    from tempfile import TemporaryDirectory
    trader="54cb50c76803fa8b248b4571"
    item="a"*24
    first="f"*24
    second="1"*24
    unsupported="2"*24
    def task(qid,name,prereq=None):
        return {"id":qid,"name":name,"normalizedName":name.lower(),"trader":trader,
                "minPlayerLevel":1,"experience":500,"factionName":"Any",
                "taskRequirements":[{"task":prereq,"status":["complete"]}] if prereq else [],
                "objectives":[{"id":qid+":give","type":"giveItem","items":[item],
                               "count":2,"optional":False,"foundInRaid":True}],
                "finishRewards":{"items":[],"traderStanding":[]}}
    tasks={first:task(first,"First"),second:task(second,"Second",first),
           unsupported:task(unsupported,"Unsupported")}
    tasks[unsupported]["objectives"][0]["minDurability"]=50
    dependencies=dependency_audit(tasks,{})
    with TemporaryDirectory() as tmp:
        folder=Path(tmp)
        assert activate_stock_batch(tasks,{}, {item},dependencies,folder)==2
        rows=read(folder/"quests-development.json")["quests"]
        assert [q["id"] for q in rows]==[first,second]
        assert rows[1]["prerequisites"]==[[{"quest":first,"outcomes":["COMPLETED"]}]]
        assert all(q["implemented"] and q["review"]=="approved" for q in rows)
        assert rows[0]["objectives"][0]["type"]=="turnin-fir:"+item
        assert read(folder/"stock-activation-report.json")["remainingStock"]==1

def export_install_catalog(tasks,current,stock,dependencies,manifest,out):
    """Minimal runtime preview, separate from the playable graph and its save revision."""
    rows=[]
    for qid,q in sorted(tasks.items()):
        kinds=Counter(o.get("type","unknown") for o in q["objectives"])
        rows.append({"id":qid,"name":display_name(q),"trader":TRADERS[q["trader"]],
                     "level":max(1,q.get("minPlayerLevel") or 0),
                     "kappa":q.get("kappaRequired") is True or qid=="5c51aac186f77432ea65c552",
                     "objectiveKinds":dict(sorted(kinds.items())),
                     "prerequisiteCount":len(dependencies["quests"][qid]["groups"]),
                     "unresolvedPrerequisites":len(dependencies["quests"][qid]["errors"])})
    if len(rows)!=len(tasks) or len({r["id"] for r in rows})!=len(rows):
        raise ValueError("Incomplete or duplicate installation catalogue")
    write(out/"campaign-install-index.json",{"schema":1,"mode":manifest["mode"],
          "sourceHash":manifest["files"]["tasks"]["sha256"],"count":len(rows),"quests":rows})
    # Export an explicit review queue, not a falsely approved gameplay file.
    # Dependency requirements and reward limitations remain present on every candidate.
    candidates=[q for q in stock if not q["alreadyRouted"] and not q["blockers"] and not q["unresolvedPrerequisites"]]
    write(out/"bulk-install-candidates.json",{"schema":1,"sourceHash":manifest["files"]["tasks"]["sha256"],
          "approvalRequired":True,"quests":candidates})
    return len(candidates)

def supply_catalog(tasks,item_catalog,resources,out):
    """Expand only ordinary barter loot; never synthesize functional equipment.
    IDs are stable; reviewed hand-authored items retain their exact appearance."""
    import re
    existing=(resources/"supplies.txt").read_text(encoding="utf-8")
    known=ids(resources/"supplies.txt")|ids(resources/"collector-items.txt")
    wanted=set()
    for q in tasks.values():
        for o in q["objectives"]:
            if o["type"] in STOCK:wanted.update(o.get("items") or [])
        for stage in ("startRewards","finishRewards"):
            reward=q.get(stage) or {}
            for entry in reward.get("items",[]):wanted.add(entry.get("item"))
        for entry in (q.get("finishRewards") or {}).get("offerUnlock",[]):wanted.add(entry.get("item"))
    rows=[]
    for ref in sorted(wanted-known-{None}):
        if not isinstance(ref,str) or not re.fullmatch(r"[0-9a-f]{24}",ref):continue
        item=item_catalog.get(ref,{})
        types=set(item.get("types") or [])
        # Unknown, equipment, quest-only and currency IDs remain review-only.
        key="keys" in types
        # Consumable drinks/food are represented as sealed mission provisions,
        # not as medicines or simulated survival effects.
        provision="provisions" in types and not types&{"gun","weapon","ammo","armor","helmet","rig","backpack","mods","quest","currency","meds","injectors","grenade"}
        civilian=ref in {"5aa2b9ede5b5b000137b758b","59e7708286f7742cbd762753","6937f02dfd6488bb27024839","6937efd50543dcc2560a2c81","59e7715586f7742ee5789605","572b7f1624597762ae139822","5b432be65acfc433000ed01f"}
        sample="injectors" in types
        plate="armorPlate" in types
        wallet=ref in {"5783c43d2459774bbe137486","60b0f6c058e0b0481a09ad11"}
        salvage=ref in {"55d482194bdc2d1d4e8b456b","5c0126f40db834002a125382","601948682627df266209af05"}
        if not (key or provision or civilian or sample or plate or wallet or salvage) and ("barter" not in types or types & {"gun","weapon","ammo","armor","helmet","rig","backpack","container","keys","mods","quest","currency","meds","injectors","grenade","provisions"}):continue
        name=item.get("normalizedName")
        if not isinstance(name,str) or not name.strip():continue
        name=name.replace("-"," ").replace("_"," ").strip().title()
        if not name or len(name)>100 or any(c in name for c in "|\r\n"):continue
        normalized=name.lower()
        icon="paper"
        for words,candidate in (
            (("battery","metal","motor","steel","bolt","screw","tool","bearing","gear"),"iron_ingot"),
            (("cable","wire","cord","rope"),"string"),
            (("gold","silver","chain","ring","coin"),"gold_nugget"),
            (("electronic","circuit","processor","ram","graphics","capacitor","transistor"),"redstone"),
            (("fabric","cloth","fleece","textile","tape"),"white_dye"),
            (("bottle","liquid","solvent","cleaner","fluid"),"glass_bottle"),
            (("book","document","manual","paper","magazine"),"paper")):
            if any(word in normalized for word in words):icon=candidate;break
        if key:icon="tripwire_hook"
        elif civilian:icon="leather_helmet"
        elif sample:
            icon="glass_bottle"
            name="Échantillon médical scellé : "+name
        elif plate:
            icon="iron_ingot"
            name="Plaque de récupération : "+name
        elif wallet:icon="leather"
        elif salvage:
            icon="iron_ingot"
            name="Objet de récupération : "+name
        elif provision:
            icon="glass_bottle" if any(w in normalized for w in ("water","bottle","juice","drink","soda","milk","kvass","tea","coffee","whiskey","vodka","moonshine")) else "bread"
            name="Ration scellée : "+name
        rows.append((ref,name,icon))
    (out/"supplies-development.txt").write_text(existing.rstrip()+"\n"+"".join("|".join(row)+"\n" for row in rows),encoding="utf-8")
    write(out/"auto-supply-review.json",[{"id":r,"name":n,"icon":i,"adaptation":"mission-key-loot" if i=="tripwire_hook" else "sealed-provision-loot" if n.startswith("Ration scellée : ") else "sealed-medical-sample" if n.startswith("Échantillon médical scellé : ") else "salvage-armor-plate" if n.startswith("Plaque de récupération : ") else "inert-barter-loot"} for r,n,i in rows])
    return known|{r for r,n,i in rows},{r:n for r,n,i in rows},len(rows)

def test_supply_catalog():
    from tempfile import TemporaryDirectory
    with TemporaryDirectory() as tmp:
        folder=Path(tmp)
        (folder/"supplies.txt").write_text("a"*24+"|Existing|paper\n")
        (folder/"collector-items.txt").write_text("")
        barter="b"*24; gun="c"*24
        tasks={"q":{"objectives":[{"type":"giveItem","items":[barter,gun]}]}}
        catalog={barter:{"types":["barter"],"normalizedName":"test-cable"},gun:{"types":["gun"],"normalizedName":"test-gun"}}
        mapped,names,count=supply_catalog(tasks,catalog,folder,folder)
        assert count==1 and barter in mapped and gun not in mapped
        assert names[barter]=="Test Cable"
        assert "|Test Cable|string" in (folder/"supplies-development.txt").read_text()

REMOVED_OBJECTIVES={"buildWeapon","plantItem","plantQuestItem","mark"}
def select_efm_campaign(reference):
    """EFM scope: omit weapon builds, fixed deposits and quests requiring absent maps.
    Reconnect completion dependencies through excluded nodes, without granting their rewards."""
    from copy import deepcopy
    tasks=deepcopy(reference)
    # Both textile variants have identical handovers; keep one two-part chain.
    duplicates={"5e383a6386f77465910ce1f3":"5e381b0286f77420e3417a74",
                "5e4d515e86f77438b2195244":"5e4d4ac186f774264f758336"}
    duplicates={k:v for k,v in duplicates.items() if k in tasks and v in tasks}
    excluded={k:["duplicate-faction-chain"] for k in duplicates}
    for qid,q in tasks.items():
        reasons=set()
        if qid=="6663148ca9290f9e0806cca1" and any(o.get("effects")==["Intoxication"] for o in q.get("objectives",[])):reasons.add("unavailable-intoxication-extraction")
        objectives=q.get("objectives") or []
        # Explicit incompatible objectives: no stun status on NPCs, stimulant use,
        # flare interaction or dialogue-only missions in the current EFM build.
        retired_exact={
            "5d25e2d886f77442734d335e":"unavailable-enemy-stun",
            "5eaaaa7c93afa0558f3b5a1c":"unavailable-stimulant-effect",
            "666314b8312343839d032d24":"unavailable-location-specific-flare",
            "69c2a2d004de49c8f0055a3d":"dialogue-only"
        }
        if qid in retired_exact:reasons.add(retired_exact[qid])
        for o in objectives:
            if o["type"] in REMOVED_OBJECTIVES:reasons.add("excluded-objective:"+o["type"])
            if o["type"]=="shoot" and o.get("usingWeaponMods"):reasons.add("unavailable-weapon-attachment")
            if o["type"]=="shoot" and o.get("zones"):reasons.add("unavailable-exact-combat-location")
            if o["type"]=="extract" and o.get("exitName"):reasons.add("unavailable-exact-extraction")
            if o["type"]=="visit" and not o.get("maps") and not o.get("zones"):reasons.add("unavailable-location-definition")
            if o["type"]=="experience" and set(o.get("effects") or [])&{"ChronicStaminaFatigue","Dehydration"}:reasons.add("unavailable-health-effect")
            maps=o.get("maps") or []
            if maps and not set(maps)&MAP_ADAPTERS.keys():reasons.add("map-unavailable")
        if reasons:excluded[qid]=sorted(reasons)
    # A removed quest cannot supply an ACTIVE/FAILED state, or an explicit task objective.
    # Exclude these dependent missions rather than fabricating progress.
    changed=True
    while changed:
        changed=False
        for qid,q in tasks.items():
            if qid in excluded:continue
            impossible=any(r.get("task") in excluded and "complete" not in r.get("status",[])
                           for r in q.get("taskRequirements") or [])
            impossible|=any(o.get("type")=="taskStatus" and o.get("task") in excluded for o in q.get("objectives") or [])
            if impossible:
                excluded[qid]=["excluded-required-task-state"]
                changed=True
    def expand(req,trail):
        ref=req["task"]
        if ref in duplicates and duplicates[ref] not in excluded:
            return [dict(req,task=duplicates[ref])]
        if ref not in excluded:return [req]
        if ref in trail or "complete" not in req.get("status",[]):raise ValueError("Unresolvable retired quest dependency "+ref)
        result=[]
        for prior in tasks[ref].get("taskRequirements") or []:
            # An excluded branch cannot be required as active or failed; no artificial completion.
            if prior["task"] in excluded and "complete" not in prior.get("status",[]):continue
            result.extend(expand(prior,trail|{ref}))
        return result
    changes=[]
    for qid,q in tasks.items():
        if qid in excluded:continue
        original=q.get("taskRequirements") or []
        replacement=[]
        for req in original:
            for prior in expand(req,{qid}):
                if prior not in replacement:replacement.append(prior)
        if qid=="5bc4893c86f774626f5ebf3e" and original==[{"task":"5bc4856986f77454c317bea7","status":["complete"]}]:
            # Part 6 requires unavailable attachments. Part 7 must follow the
            # completed previous compatible challenge, not merely its acceptance.
            replacement=[dict(req,status=["complete"]) if req["task"]=="5bc4836986f7740c0152911c" and set(req["status"])=={"active","complete"} else req for req in replacement]
        if qid=="666314b2a9290f9e0806cca3" and original==[{"task":"666314b0acf8442f8b0531a1","status":["complete"]}]:
            # The removed weapon-build part depended on The Good Times 1 being
            # active or complete; a sequential EFM chain requires completion.
            replacement=[dict(req,status=["complete"]) if req["task"]=="666314b4d7f171c4c20226c3" and set(req["status"])=={"active","complete"} else req for req in replacement]
        # The alternate branches below were removed because their maps or
        # fixed deposits are unavailable. Keep the compatible chains sequential.
        sequential={"673f629c5b555b53460cf827","6745fae369a58fceba10343d","6744ab1def61d56e020b5c56"}
        if qid in sequential:
            replacement=[dict(req,status=["complete"]) if "complete" in req["status"] else req for req in replacement]
        # Audit failure conditions that reference retired tasks: those events
        # cannot happen, so remove only those exact task-status conditions.
        failures=q.get("failConditions") or []
        kept=[f for f in failures if not(f.get("type")=="taskStatus" and f.get("task") in excluded and f.get("status")==["complete"])]
        if failures!=kept:q["failConditions"]=kept
        q["taskRequirements"]=replacement
        if original!=replacement:changes.append({"id":qid,"before":original,"after":replacement})
        # A kill/extraction allowed on multiple maps can be satisfied on the available subset.
        # Leave other predicates intact; they remain subject to strict adapter validation.
        for o in q.get("objectives") or []:
            if o["type"] in {"shoot","extract"} and o.get("maps"):
                o["maps"]=[m for m in o["maps"] if m in MAP_ADAPTERS]
    return {qid:q for qid,q in tasks.items() if qid not in excluded},excluded,changes

def test_credit_objective():
    q={"id":"a"*24,"normalizedName":"test-payment","trader":"54cb50c76803fa8b248b4571","experience":100,
       "objectives":[{"id":"b"*24,"type":"giveItem","items":["5696686a4bdc2da3298b456a"],"count":5000,"foundInRaid":False,"optional":False,"maxDurability":100}]}
    row=stock_draft(q,set(),set())
    assert row["adapter"]=="bulk-combat-v1" and row["objectives"][0]["type"]=="pay-credits"
    assert row["objectives"][0]["amount"]==5000
    q["objectives"][0]["foundInRaid"]=True
    assert "item-mapping-required" in stock_draft(q,set(),set())["blockers"]

def test_scope_selection():
    a,b,c,d,e=("a"*24,"b"*24,"c"*24,"d"*24,"e"*24)
    def task(kind,req=None,maps=None):
        return {"objectives":[{"type":kind,"maps":maps or []}],"taskRequirements":[req] if req else []}
    complete=lambda x:{"task":x,"status":["complete"]}
    ref={a:task("shoot"),b:task("buildWeapon",complete(a)),c:task("shoot",complete(b)),
         d:task("visit",maps=["unavailable"]),e:task("shoot",{"task":b,"status":["active"]})}
    selected,excluded,changes=select_efm_campaign(ref)
    assert set(selected)=={a,c} and set(excluded)=={b,d,e}
    assert selected[c]["taskRequirements"]==[complete(a)] and ref[c]["taskRequirements"]==[complete(b)]
    assert len(changes)==1

WEAPON_HANDOVERS={
    "5644bd2b4bdc2d3b4c8b4572":"5644bd2b4bdc2d3b4c8b4572",
    "5ac4cd105acfc40016339859":"5644bd2b4bdc2d3b4c8b4572",
    "5ab8e9fcd8ce870019439434":"5644bd2b4bdc2d3b4c8b4572",
    "5bf3e03b0db834001d2c4a9c":"5644bd2b4bdc2d3b4c8b4572",
    "5bf3e0490db83400196199af":"5644bd2b4bdc2d3b4c8b4572",
    "5447a9cd4bdc2dbd208b4567":"5447a9cd4bdc2dbd208b4567",
    "5bb2475ed4351e00853264e3":"5447a9cd4bdc2dbd208b4567",
    "5448bd6b4bdc2dfc2f8b4569":"5448bd6b4bdc2dfc2f8b4569",
    "579204f224597773d619e051":"5448bd6b4bdc2dfc2f8b4569",
}
MEDICAL_EQUIVALENTS={
    "544fb45d4bdc2dee738b4568":"544fb45d4bdc2dee738b4568",
    "5755356824597772cb798962":"5755356824597772cb798962",
    "590c661e86f7741e566b646a":"5755356824597772cb798962",
    "590c678286f77426c9660122":"544fb45d4bdc2dee738b4568",
    "590c657e86f77412b013051d":"544fb45d4bdc2dee738b4568",
    "60098ad7c2240c0fe85c570a":"544fb45d4bdc2dee738b4568",
    "544fb25a4bdc2dfb738b4567":"544fb25a4bdc2dfb738b4567",
    "5751a25924597722c463c472":"544fb25a4bdc2dfb738b4567",
    "544fb3364bdc2d34748b456a":"544fb3364bdc2d34748b456a",
    "5af0454c86f7746bf20992e8":"544fb3364bdc2d34748b456a",
    "6937ecf8628ee476240c07cb":"544fb3364bdc2d34748b456a",
    "60098af40accd37ef2175f27":"60098af40accd37ef2175f27",
    "5e831507ea0a7c419c2f9bd9":"60098af40accd37ef2175f27",
    "5e8488fa988a8701445df1e4":"60098af40accd37ef2175f27",
}
def adapt_efm_rewards_and_gates(tasks,mapped,item_catalog,out):
    """Explicit gameplay adaptations for mechanics that do not exist in EFM.
    Never change objective predicates or replace the Collector/Kappa reward."""
    rubles="5449016a4bdc2d6f028b456f"
    audit=[]
    # Postman Pat shares a virtual letter across two merchants in the source.
    # EFM records extraction in part 1, then validates the transfer at Therapist.
    part1="59675ea386f77414b32bded2";part2="596760e186f7741e11214d58"
    if part1 in tasks and part2 in tasks:
        first,second=tasks[part1],tasks[part2]
        find=next((o for o in first["objectives"] if o["type"]=="findQuestItem"),None)
        gate=next((o for o in first["objectives"] if o["type"]=="taskStatus" and o.get("task")==part2),None)
        give=next((o for o in second["objectives"] if o["type"]=="giveQuestItem"),None)
        if find and gate and give and find["questItem"]==give["questItem"]:
            first["objectives"]=[o for o in first["objectives"] if o is not gate]+[{"id":gate["id"],
                "type":"giveQuestItem","count":1,"optional":False,"maps":[],"questItem":find["questItem"],
                "description":"Transmission du courrier extrait"}]
            second["objectives"]=[{"id":give["id"],"type":"taskStatus","task":part1,"status":["complete"],
                "optional":False,"maps":[],"zones":[],"description":"Confirmer le courrier transmis"}]
            second["taskRequirements"]=[{"task":part1,"status":["complete"]}]
            audit.append({"id":part1,"name":display_name(first),"changes":[{"type":"cross-merchant-letter-sequential","reference":gate}]})
            audit.append({"id":part2,"name":display_name(second),"changes":[{"type":"cross-merchant-letter-sequential","reference":give}]})
    # This source chain uses an active parent and a cross-trader document.
    # EFM uses three sequential missions; the first really requires extraction.
    helping="6752f6d83038f7df520c83e8"; shipping1="673f348dd3346c21670217e7"; shipping2="673f2cd5d3346c2167020484"
    if all(qid in tasks for qid in (helping,shipping1,shipping2)):
        first=tasks[helping]; second=tasks[shipping1]; third=tasks[shipping2]
        find=next((o for o in first["objectives"] if o["type"]=="findQuestItem"),None)
        gate=next((o for o in first["objectives"] if o["type"]=="taskStatus" and o.get("task")==shipping1),None)
        give=next((o for o in second["objectives"] if o["type"]=="giveQuestItem"),None)
        if find and gate and give and find["questItem"]==give["questItem"] and len(second["objectives"])==1:
            first["objectives"]=[o for o in first["objectives"] if o is not gate]+[dict(give,id=gate["id"])]
            second["objectives"]=[{"id":give["id"],"type":"taskStatus","task":helping,"status":["complete"],"optional":False,"maps":[],"zones":[],"description":"Confirmer la transmission du document extrait"}]
            second["taskRequirements"]=[{"task":helping,"status":["complete"]}]
            third["taskRequirements"]=[dict(req,status=["complete"]) if req["task"]==helping else req for req in third.get("taskRequirements",[])]
            audit.extend({"id":qid,"name":display_name(tasks[qid]),"changes":[{"type":"shipping-document-sequential"}]} for qid in (helping,shipping1,shipping2))
    # Sensory Analysis: visit an eligible map and extract, then hand the
    # actual stock item to the second merchant. Avoid the circular ACTIVE gate.
    sensory1="67a0967c003a9986cb0f5ac1"; sensory2="67a096ed77dd677f600804ba"
    if sensory1 in tasks and sensory2 in tasks:
        first,second=tasks[sensory1],tasks[sensory2]
        find=next((o for o in first["objectives"] if o["id"]=="67dc3861d5c0070b960c1bba"),None)
        visit=next((o for o in first["objectives"] if o["id"]=="67dc393c6089aa48050efa02"),None)
        status=next((o for o in first["objectives"] if o["type"]=="taskStatus" and o.get("task")==sensory2),None)
        give=next((o for o in second["objectives"] if o["type"]=="giveItem"),None)
        if find and visit and status and give and find["items"]==give["items"] and find["count"]==give["count"]==1 and len(first["objectives"])==3:
            before=first["objectives"]
            first["objectives"]=[{"id":visit["id"],"description":"Reconnaissance avec extraction","type":"extract","count":1,"optional":False,"maps":visit["maps"],"exitStatus":["ExpBonusSurvived"]}]
            second["taskRequirements"]=[{"task":sensory1,"status":["complete"]}]
            first["availableDelaySecondsMin"]=first["availableDelaySecondsMax"]=0
            audit.append({"id":sensory1,"name":display_name(first),"changes":[{"type":"sequential-reconnaissance-before-item-handover","reference":before}]})
            audit.append({"id":sensory2,"name":display_name(second),"changes":[{"type":"complete-reconnaissance-before-handover"}]})
    for qid,q in sorted(tasks.items()):
        changes=[]
        if qid=="6396701b9113f06a7c3b2379" and q.get("availableDelaySecondsMin")==q.get("availableDelaySecondsMax")==36000:
            for o in q["objectives"]:
                if o["id"]=="6397ac912e519e69d2139b26" and o.get("distance")=={"value":200,"compareMethod":">="}:
                    o["distance"]={"value":100,"compareMethod":">="}
                    q["availableDelaySecondsMin"]=q["availableDelaySecondsMax"]=0
                    changes.append({"type":"efm-render-range-and-no-wait","originalDistance":200,"distance":100,"originalDelaySeconds":36000})
        if qid=="675c1cf4a757ddd00404f0a3" and {o["id"] for o in q["objectives"]}=={"675c1cf4a757ddd00404f0a6","676ab31c058363b09072c78e"}:
            visit=next(o for o in q["objectives"] if o["type"]=="visit")
            optional=next(o for o in q["objectives"] if o["type"]=="findItem")
            if optional.get("optional") and {z.get("id") for z in visit.get("zones",[])}=={"exit777"}:
                before=q["objectives"]
                q["objectives"]=[{"id":visit["id"],"description":"Reconnaissance d’une extraction industrielle","type":"extract","count":1,"optional":False,"maps":["56f40101d2720b2a4d8b45d6"],"exitStatus":["ExpBonusSurvived"]}]
                changes.append({"type":"industrial-extraction-equivalent","reference":before})
        # Both battery deliveries remain independently completable. Their
        # original branch conflict refers to a quest that was rewritten in EFM.
        if qid=="6744a728352b4da8e003eda9":
            fail=q.get("failConditions") or []
            if len(fail)==1 and fail[0].get("type")=="taskStatus" and fail[0].get("task")=="6744a9dfef61d56e020b5c4a" and fail[0].get("status")==["complete"] and not any((q.get("failureOutcome") or {}).values()):
                q["failConditions"]=[]
                changes.append({"type":"independent-battery-deliveries","reference":fail})
        # These two reviewed challenges reset on any unsuccessful EFM raid.
        # A fresh attempt starts automatically, with no reward or repeat-completion.
        if qid in {"5bc4893c86f774626f5ebf3e","5c0be13186f7746f016734aa"}:
            fail=q.get("failConditions") or []
            if q.get("restartable") and len(fail)==1 and fail[0].get("type")=="extract" and fail[0].get("count")==1 and not fail[0].get("maps") and set(fail[0].get("exitStatus",[]))=={"ExpBonusKilled","ExpBonusMissingInAction","ExpBonusLeft"} and not any((q.get("failureOutcome") or {}).values()):
                q["failConditions"]=[]
                q["restartable"]=False
                changes.append({"type":"reset-progress-on-failed-raid","reference":fail})
        if qid=="5d25cf2686f77443e75488d4":
            fail=q.get("failConditions") or []
            if q.get("restartable") and len(fail)==1 and fail[0].get("type")=="useItem" and fail[0].get("count")==1 and fail[0].get("maps")==["5704e3c2d2720bac5b8b4567"] and not any((q.get("failureOutcome") or {}).values()):
                q["failConditions"]=[];q["restartable"]=False
                changes.append({"type":"same-raid-without-efm-treatment","reference":fail})
        restricted_kills={"66631489acf8442f8b05319f":"06e9eb1f9e04148ea4752b918d6d0a89f455ee4728eafc75620c2927d5369c6f",
                          "6663149196a9349baa021baa":"0b2156b029eb0dcecb3964218c7c2cf5f44c14ad3f4f3dff6361ed6678ae0a0b"}
        if qid in restricted_kills:
            fail=q.get("failConditions") or []
            if q.get("restartable") and len(fail)==1 and fail[0].get("type")=="shoot" and fail[0].get("shotType")=="kill" and fail[0].get("count")==1 and hashlib.sha256(",".join(sorted(fail[0].get("targetNames") or [])).encode()).hexdigest()==restricted_kills[qid] and not any((q.get("failureOutcome") or {}).values()):
                q["failConditions"]=[]
                q["restartable"]=False
                changes.append({"type":"reset-on-forbidden-kill","reference":fail})
        if q.get("factionName") in {"USEC","BEAR"}:
            changes.append({"type":"faction-neutral-efm","reference":q["factionName"]})
            q["factionName"]="Any"
        old_level=q.get("minPlayerLevel") or 0
        level=old_level
        gates=[]
        for req in q.get("otherRequirements") or []:
            if req.get("type")=="globalVariable" and req.get("compareMethod")==">=" and isinstance(req.get("value"),int) and 1<=req["value"]<=5:
                target=req["value"]*2
                level=max(level,target)
                changes.append({"type":"global-counter-to-player-level","reference":req,"minimumLevel":target})
            else:gates.append(req)
        q["otherRequirements"]=gates
        if level!=old_level:q["minPlayerLevel"]=level
        if qid!="5c51aac186f77432ea65c552":
            # The imported documents are at accessible EFM sites, not behind Tarkov's doors.
            # Explicit collection/handover objectives for keys remain intact.
            if q.get("neededKeys"):
                changes.append({"type":"unavailable-door-gate-removed","reference":q["neededKeys"]})
                q["neededKeys"]=[]
            kept=[]
            for o in q.get("objectives") or []:
                if qid in {"60e71c11d54b755a3b53eb65","666314b2a9290f9e0806cca3"} and o["type"]=="shoot" and "59fc81d786f774390775787e" in (o.get("maps") or []) and len(o["maps"])>1:
                    old=o["maps"]
                    o["maps"]=[m for m in old if m!="59fc81d786f774390775787e"]
                    changes.append({"type":"omit-night-only-factory-alternative","before":old,"after":o["maps"]})
                if o.get("optional") and o["type"]=="visit":
                    changes.append({"type":"optional-reconnaissance-omitted","reference":o})
                    continue
                if o.get("optional") and o["type"]=="findItem" and o.get("items") and all("keys" in item_catalog.get(ref,{}).get("types",[]) for ref in o["items"]):
                    changes.append({"type":"optional-key-search-omitted","reference":o})
                    continue
                if o["type"]=="findQuestItem" and o.get("maps"):
                    supported=[m for m in o["maps"] if m in MAP_ADAPTERS]
                    if supported and len({MAP_ADAPTERS[m] for m in supported})==1 and supported!=o["maps"]:
                        locations=[v for v in o.get("possibleLocations",[]) if v.get("map") in supported]
                        if locations:
                            changes.append({"type":"document-map-alternative","before":o["maps"],"after":supported})
                            o["maps"]=supported
                            o["possibleLocations"]=locations
                if o["type"] in {"findQuestItem","visit"} and o.get("requiredKeys"):
                    changes.append({"type":"unavailable-door-gate-removed","reference":o["requiredKeys"],"objective":o["id"]})
                    o.pop("requiredKeys")
                if o["type"] in STOCK and o.get("items"):
                    original=o["items"]
                    if qid=="67af4c169d95ad16e004fd86":
                        safety={"5c0e625a86f7742d77340f62":"5ab8e79e86f7742d8b372e78",
                            "5c0e842486f77443a74d2976":"5c0e874186f7745dc7616606"}
                        adapted=[safety.get(ref,ref) for ref in original]
                        if adapted!=original:
                            o["items"]=adapted
                            changes.append({"type":"heavy-protection-equivalent","objective":o["id"],"before":original,"after":adapted})
                            original=adapted
                    if qid=="5d25e43786f7740a212217fa" and set(original)=={"5ca2151486f774244a3b8d30","5e9dacf986f774054d6b89f4","545cdb794bdc2d3a198b456a","5b44d22286f774172b0c9de8","5ab8e79e86f7742d8b372e78","5c0e625a86f7742d77340f62","5f5f41476bdad616ad46d631"}:
                        o["items"]=["5c0e655586f774045612eeb2","5ab8e79e86f7742d8b372e78","545cdb794bdc2d3a198b456a"]
                        changes.append({"type":"heavy-armor-category-equivalent","objective":o["id"],"before":original,"after":o["items"]})
                        original=o["items"]
                    if qid=="5c112d7e86f7740d6f647486":
                        # Preserve five distinct categories and all 75 FIR handovers.
                        scavenger={
                          "6a57553be11c6adb225f69b3":("b3d57bcdce5ab4406cd6679f040c060775fc76da139c9ecbb68cd2f6cadf0f79",["572b7f1624597762ae139822","59e7715586f7742ee5789605"]),
                          "6a575543bb648e3e317d5bbf":("908f1c4f4ca5e36370cfc741ee802d15f5e8b1181e117334fe22779778f90f3f",["5aa7d03ae5b5b00016327db5"]),
                          "6a57554a8715a99f675a8191":("035719ef0783b681d2ffd9437382702ddf3f7b7fe8a982864bfb0866ba12c6ad",["5645bcc04bdc2d363b8b4572"]),
                          "6a5755579da173f9a707240f":("abb45150ae2967d7aeeaba587a9032eb660ad5a6a3e846152bfcb3835afe8a7f",["5b432be65acfc433000ed01f"]),
                          "6a575575a7263e4961397d13":("6e23c750fac6d1fcf977a299c21920465efc94da494baf9a461861ab04b9d6f0",["59e7635f86f7742cbf2c1095","5c0e655586f774045612eeb2","5ab8e79e86f7742d8b372e78","545cdb794bdc2d3a198b456a"]),
                        }
                        expected,alternatives=scavenger.get(o["id"],("",[]))
                        if expected==hashlib.sha256(",".join(sorted(original)).encode()).hexdigest():
                            o["items"]=alternatives
                            changes.append({"type":"scavenger-equipment-category","objective":o["id"],"before":original,"after":alternatives})
                            original=o["items"]
                    if all(item_catalog.get(ref,{}).get("normalizedName","").startswith(("dogtag-bear","double-dogtag-bear","marked-dogtag-bear")) for ref in original):
                        o["items"]=["59f32bb586f774757e1e8442"]
                        changes.append({"type":"real-pmc-dogtag","objective":o["id"],"before":original,"after":o["items"]})
                        original=o["items"]
                    elif all(item_catalog.get(ref,{}).get("normalizedName","").startswith(("dogtag-usec","double-dogtag-usec","marked-dogtag-usec")) for ref in original):
                        o["items"]=["59f32c3b86f77472a31742f0"]
                        changes.append({"type":"real-pmc-dogtag","objective":o["id"],"before":original,"after":o["items"]})
                        original=o["items"]
                    if qid=="69ce21e990144e437802b1e0" and set(original)=={"68947a4be4bf255d1b0ca746","689479eb30cc5ba7be00f5ff","689479cb47e5acd1e10be986","689479a4a733b1602007e2eb"}:
                        o["items"]=["5648a69d4bdc2ded0b8b457b"]
                        changes.append({"type":"elite-rig-equivalent","objective":o["id"],"before":original,"after":o["items"]})
                        original=o["items"]
                    if set(original)<=WEAPON_HANDOVERS.keys():
                        adapted=sorted({WEAPON_HANDOVERS[ref] for ref in original})
                        if adapted!=sorted(set(original)):
                            o["items"]=adapted
                            changes.append({"type":"base-weapon-handover-equivalent","objective":o["id"],"before":original,"after":adapted})
                        original=o["items"]
                    medical=[MEDICAL_EQUIVALENTS.get(ref,ref) for ref in original]
                    # If the source allows a choice of medical items, keep the usable
                    # equivalents; do not pretend surgical kits are bandages.
                    if any(ref in MEDICAL_EQUIVALENTS for ref in original):
                        usable=sorted(set(ref for ref in medical if ref in mapped))
                        if usable and usable!=sorted(set(original)):
                            o["items"]=usable
                            changes.append({"type":"medical-alternatives-to-efm","objective":o["id"],"before":original,"after":usable})
                kept.append(o)
            if kept:q["objectives"]=kept
            reward=q.get("finishRewards") or {}
            skills=reward.get("skillLevelReward") or []
            unsupported=[s for s in skills if s.get("skill") in {"Crafting","HideoutManagement"}]
            if unsupported:
                reward["skillLevelReward"]=[s for s in skills if s not in unsupported]
                changes.append({"type":"unavailable-hideout-skill-bonus-omitted","reference":unsupported})
            items=[]
            converted=0
            for row in reward.get("items") or []:
                ref,count=row.get("item"),row.get("count")
                if ref in CURRENCY or ref in mapped and not row.get("attributes") and not row.get("contains"):
                    items.append(row);continue
                price=item_catalog.get(ref,{}).get("basePrice")
                if isinstance(price,int) and price>0 and isinstance(count,int) and count>0:
                    value=price*count
                    if converted+value<=2147483647:
                        converted+=value
                        changes.append({"type":"unavailable-reward-to-credits","reference":row,"referenceRubles":value})
                        continue
                items.append(row)
            if converted:items.append({"item":rubles,"count":converted,"attributes":{}})
            reward["items"]=items
            # Cosmetics, recipes and unsupported shop unlocks do not have an equivalent.
            # Keep each compatible shop unlock and preserve every removed reward in the audit.
            for key in ("craftUnlock","customization","achievement"):
                if reward.get(key):
                    changes.append({"type":"unsupported-finish-bonus-omitted","key":key,"reference":reward[key]})
                    reward[key]=[]
            offers=[]
            for offer in reward.get("offerUnlock") or []:
                ref=offer.get("item");item=item_catalog.get(ref,{})
                if (ref in mapped and not offer.get("contains") and not offer.get("attributes")
                    and isinstance(offer.get("count"),int) and 1<=offer["count"]<=64
                    and isinstance(offer.get("level"),int) and 1<=offer["level"]<=4
                    and offer.get("trader") in TRADERS and isinstance(item.get("basePrice"),int) and item["basePrice"]>0
                    and set(item.get("types",[])) & {"barter","provisions","meds","injectors","keys"}):
                    offers.append(offer)
                else:changes.append({"type":"unsupported-offer-omitted","reference":offer})
            reward["offerUnlock"]=offers
            q["finishRewards"]=reward
            start=q.get("startRewards") or {}
            for key in ("craftUnlock","offerUnlock"):
                if start.get(key):
                    changes.append({"type":"unsupported-start-bonus-omitted","key":key,"reference":start[key]})
                    start[key]=[]
            if qid=="5bc4776586f774512d07cf05":
                old=start.get("items") or []
                if {row["item"] for row in old}=={"5b8683a486f77467f2423114","629792f0fd6eda47d6466fe8"}:
                    start["items"]=[{"item":"5b8683a486f77467f2423114","count":1,"attributes":{}}]
                    changes.append({"type":"starter-rifles-to-one-efm-bolt-action","reference":old})
            # Preserve the usable starter weapon; unsupported separate magazines/packs
            # from these two exact starter kits are not required by the objective.
            starter_adapters={
                "666314a31cd52e3d040a2e76":({"628baf0b967de16aab5a4f36"},[{"item":"5648a69d4bdc2ded0b8b457b","count":1,"attributes":{}}]),
                "6740a2c17e3818d5bb0648b6":({"66d9f1abb16d9aacf5068468"},[]),
                "666314b2a9290f9e0806cca3":({"707265736574000000000047"},[{"item":"5a38e6bac4a2826c6e06d79b","count":1,"attributes":{}}]),
                "666314bafd5ca9577902e03a":({"5b44abe986f774283e2e3512"},[{"item":"5448bd6b4bdc2dfc2f8b4569","count":1,"attributes":{}}]),
                "666314b4d7f171c4c20226c3":({"65766adc234b9f6e050a431a","5645bc214bdc2d363b8b4571"},[{"item":"545cdb794bdc2d3a198b456a","count":1,"attributes":{}},{"item":"5645bc214bdc2d363b8b4571","count":1,"attributes":{}}]),
                "5c0d4c12d09282029f539173":({"5aa7d03ae5b5b00016327db5","5ab8e4ed86f7742d8e50c7fa","707265736574000000000009","5aaa5dfee5b5b000140293d3"},[{"item":"5aa7d03ae5b5b00016327db5","count":1,"attributes":{}},{"item":"59e7635f86f7742cbf2c1095","count":1,"attributes":{}},{"item":"5447a9cd4bdc2dbd208b4567","count":1,"attributes":{}}]),
                "68341a0b2f0e2a7eb90b62d4":({"5648a7494bdc2d9d488b4583"},[{"item":"59e7635f86f7742cbf2c1095","count":2,"attributes":{}}]),
                "671a59e43d73dac1360765cc":({"707265736574000000000051"},[{"item":"5a38e6bac4a2826c6e06d79b","count":1,"attributes":{}}]),
                "68341846186efa3c5b07f989":({"6398636bb483a550805be5e5","62e7c98b550c8218d602cbb4","65702656c5d7d4cb4d078591"},[{"item":"5447a9cd4bdc2dbd208b4567","count":2,"attributes":{}}])
            }
            if qid in starter_adapters:
                expected,replacement=starter_adapters[qid]
                old=start.get("items") or []
                if {row["item"] for row in old}==expected:
                    start["items"]=replacement
                    changes.append({"type":"starter-kit-base-weapon","reference":old,"replacement":replacement})
            q["startRewards"]=start
        else:
            # Collector is an explicitly reviewed exception: preserve all 44 FIR
            # handovers and the actual secure container. Only the cosmetic armband
            # (not supported in EFM) is converted to credits.
            refs={ref for o in q["objectives"] for ref in o.get("items",[])}
            if len(q["objectives"])!=44 or len(refs)!=44 or not refs<=mapped or any(
                o["type"]!="giveItem" or o["count"]!=1 or not o.get("foundInRaid") or o.get("optional")
                for o in q["objectives"]):
                raise ValueError("Collector requirements changed: manual review required")
            reward=q["finishRewards"]
            kappa="5c093ca986f7740a1867ab12"
            armband="619bddc6c9546643a67df6ee"
            if {r["item"] for r in reward["items"]}!={kappa,armband} or any(r["count"]!=1 for r in reward["items"]):
                raise ValueError("Collector rewards changed: manual review required")
            price=item_catalog[armband]["basePrice"]
            if not isinstance(price,int) or price<=0:raise ValueError("Invalid Collector armband value")
            reward["items"]=[r for r in reward["items"] if r["item"]==kappa]+[{"item":rubles,"count":price,"attributes":{}}]
            changes.append({"type":"collector-kappa-real-reward","reference":kappa,"capacity":12,"cosmeticCredits":price//100})
        if changes:audit.append({"id":qid,"name":display_name(q),"changes":changes})
    write(out/"campaign-adaptation-review.json",{"schema":1,"quests":audit,
        "counts":dict(Counter(c["type"] for row in audit for c in row["changes"]))})
    return len(audit)

def test_efm_adaptations():
    from tempfile import TemporaryDirectory
    ref="a"*24
    qid="b"*24
    q={"id":qid,"normalizedName":"test","minPlayerLevel":1,
       "otherRequirements":[{"type":"globalVariable","compareMethod":">=","value":3,"variableId":"test"}],
       "finishRewards":{"items":[{"item":ref,"count":2,"attributes":{}}],"customization":[{"id":"test"}]}}
    with TemporaryDirectory() as tmp:
        folder=Path(tmp)
        assert adapt_efm_rewards_and_gates({qid:q},set(),{ref:{"basePrice":5000}},folder)==1
        assert q["minPlayerLevel"]==6 and not q["otherRequirements"]
        assert q["finishRewards"]["items"]==[{"item":"5449016a4bdc2d6f028b456f","count":10000,"attributes":{}}]
        assert not q["finishRewards"]["customization"]
        assert len(read(folder/"campaign-adaptation-review.json")["quests"][0]["changes"])==3

def build(snapshot,out,resources):
    manifest=read(snapshot/"manifest.json")
    for n,info in manifest["files"].items():
        if hashlib.sha256((snapshot/(n+".json")).read_bytes()).hexdigest()!=info["sha256"]:
            raise ValueError("Snapshot hash mismatch: "+n)
    tasks=read(snapshot/"tasks.json")["data"]["tasks"]
    if len(tasks)!=manifest["audit"]["tasks"]:raise ValueError("Task count mismatch")
    original_tasks=tasks
    tasks,excluded,rewired=select_efm_campaign(original_tasks)
    current={q["id"]:q for q in read(resources/"quests.json")["quests"] if q["id"] not in excluded}
    out.mkdir(parents=True,exist_ok=True)
    write(out/"campaign-retired-quests.json",{"schema":1,"quests":sorted(excluded)})
    write(out/"campaign-scope-review.json",{"schema":1,"originalCount":len(original_tasks),"retainedCount":len(tasks),
          "excluded":[{"id":qid,"name":display_name(original_tasks[qid]),"reasons":why} for qid,why in sorted(excluded.items())],
          "rewired":rewired})
    print(json.dumps({"scopeOriginal":len(original_tasks),"scopeRetained":len(tasks),"scopeExcluded":len(excluded),"rewiredChains":len(rewired)},indent=2))
    item_catalog=read(snapshot/"items.json")["data"]["items"]
    mapped,auto_names,auto_count=supply_catalog(original_tasks,item_catalog,resources,out)
    mapped.update({"5c093ca986f7740a1867ab12","59e763f286f7742ee57895da","59e7643b86f7742cbf2c109a","5648a69d4bdc2ded0b8b457b","5b8683a486f77467f2423114"})
    auto_names.update({"59e763f286f7742ee57895da":"Sac expédition EFM (Pilgrim)","59e7643b86f7742cbf2c109a":"Gilet tactique EFM (Wartech)","5648a69d4bdc2ded0b8b457b":"Gilet élite EFM (Blackrock)"})
    mapped.update({"59e7635f86f7742cbf2c1095","5c0e655586f774045612eeb2","5ab8e79e86f7742d8b372e78","5a38e6bac4a2826c6e06d79b"})
    auto_names.update({"59e7635f86f7742cbf2c1095":"Tunique PMC classe 2","5c0e655586f774045612eeb2":"Tunique PMC classe 4","5ab8e79e86f7742d8b372e78":"Tunique PMC classe 5","5a38e6bac4a2826c6e06d79b":"Fusil M870 EFM"})
    mapped.update({"59f32bb586f774757e1e8442","59f32c3b86f77472a31742f0"})
    mapped.update({"5aa7d03ae5b5b00016327db5","5645bcc04bdc2d363b8b4572"})
    auto_names.update({"5aa7d03ae5b5b00016327db5":"Casque PMC classe 2","5645bcc04bdc2d363b8b4572":"Casque audio tactique"})
    mapped.update({"5a1eaa87fcdbcb001865f75e","5ca20ee186f774799474abc2","60194943740c5d77f6705eea"})
    auto_names.update({"5a1eaa87fcdbcb001865f75e":"Lunettes thermiques EFM","5ca20ee186f774799474abc2":"Casque PMC classe 5","60194943740c5d77f6705eea":"Munitions TaCZ 5,56"})
    mapped.update({"59db794186f77448bc595262","5645bc214bdc2d363b8b4571"})
    auto_names["59db794186f77448bc595262"]="Conteneur sécurisé Epsilon (vide)"
    mapped.update(WEAPON_HANDOVERS.values())
    mapped.add("545cdb794bdc2d3a198b456a")
    auto_names.update({"5644bd2b4bdc2d3b4c8b4572":"AK EFM","5447a9cd4bdc2dbd208b4567":"M4 EFM","5448bd6b4bdc2dfc2f8b4569":"Pistolet EFM","545cdb794bdc2d3a198b456a":"Tunique PMC classe 6"})
    mapped.update(MEDICAL_EQUIVALENTS.values())
    auto_names.update({"544fb45d4bdc2dee738b4568":"Salewa","5755356824597772cb798962":"Trousse de soins",
        "544fb25a4bdc2dfb738b4567":"Bandage","544fb3364bdc2d34748b456a":"Attelle","60098af40accd37ef2175f27":"Hémostatique"})
    adaptation_count=adapt_efm_rewards_and_gates(tasks,mapped,item_catalog,out)
    if not set(current)<=set(tasks):raise ValueError("Unknown quest in gameplay catalogue")
    dependencies=dependency_audit(tasks,current)
    staged=[]
    stock=[]
    missing_items={}
    discrepancies=[]
    for qid,q in sorted(tasks.items()):
        kinds={o["type"] for o in q["objectives"]}
        bucket="stock" if kinds<=STOCK else "combat-extract" if kinds<=CORE else "field" if kinds<=FIELD else "special"
        gates,gate_blockers=normalize_gates(q,current)
        staged.append({"id":qid,"slug":q["normalizedName"],"trader":TRADERS[q["trader"]],"batch":bucket,
                       "routed":qid in current,"reference":original_tasks[qid],"adaptedReference":q,"normalizedGates":gates,"gateBlockers":sorted(gate_blockers)})
        if kinds<=STOCK:
            draft=stock_draft(q,mapped,current)
            for o in q["objectives"]:
                for item in o.get("items",[]):
                    if item not in mapped:missing_items.setdefault(item,set()).add(qid)
            draft["alreadyRouted"]=qid in current
            draft["prerequisites"]=dependencies["quests"][qid]["groups"]
            draft["unresolvedPrerequisites"]=dependencies["quests"][qid]["errors"]
            draft["missingPrerequisiteQuests"]=dependencies["quests"][qid]["missingFromGameplay"]
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
    item_catalog=read(snapshot/"items.json")["data"]["items"]
    write(out/"bulk-stock-item-worklist.json",[{"id":id,"name":item_catalog.get(id,{}).get("normalizedName"),
        "types":item_catalog.get(id,{}).get("types",[]),"quests":sorted(qids),"mapping":None}
        for id,qids in sorted(missing_items.items())])
    write(out/"routed-quest-review.json",discrepancies)
    summary={"generatedBarterSupplies":auto_count,"referenceQuests":len(original_tasks),"retainedQuests":len(tasks),"rewardOrGateAdaptations":adaptation_count,"excludedQuests":len(excluded),"rewiredChains":len(rewired),"routedQuests":len(current),"batches":dict(Counter(q["batch"] for q in staged)),
             "stockDrafts":len(stock),"unmappedStockItemIDs":len(missing_items),"stockDraftsWithoutBlockers":sum(not q["blockers"] and not q["alreadyRouted"] for q in stock),
             "stockBlockers":dict(Counter(b for q in stock for b in q["blockers"])),
             "routedQuestsWithReviewNotes":len(discrepancies)}
    write(out/"dependency-audit.json",dependencies)
    summary.update({"routedPrerequisiteMismatches":len(dependencies["routedPrerequisiteMismatches"]),
                    "questsWithUnresolvedPrerequisites":sum(bool(q["errors"]) for q in dependencies["quests"].values()),
                    "kappaDependencyClosure":len(dependencies["kappa"]["dependencyClosure"]),
                    "kappaQuestsMissingFromGameplay":len(dependencies["kappa"]["missingFromGameplay"])})
    summary["bulkInstallCandidates"]=export_install_catalog(tasks,current,stock,dependencies,manifest,out)
    item_names=dict(auto_names)
    for filename in ("supplies.txt","collector-items.txt"):
        for line in (resources/filename).read_text(encoding="utf-8").splitlines():
            if line.strip():
                ref,name,_=line.split("|")
                item_names[ref]=name
    # Collector and boss trophies remain raid-only, not merchant supplies.
    supply_ids=ids(out/"supplies-development.txt")-{"5c0e874186f7745dc7616606","6759af0f9c8a538dd70bfae6","60a7acf20c5cb24b01346648","675aae1c26dc64e17800fee6","6540d2162ae6d96b540afcaf","5b3b713c5acfc4330140bd8d","5e997f0b86f7741ac73993e2","609e860ebd219504d8507525","628baf0b967de16aab5a4f36","628bc7fb408e2b2e9c0801b1","628b9c7d45122232a872358f","5fc64ea372b0dd78d51159dc"}
    approved_offers,total_offers,supported_offers=offer_plan(tasks,item_catalog,supply_ids|set(MEDICAL_EQUIVALENTS.values()),out)
    summary.update({"questOfferUnlocks":total_offers,"supportedQuestOfferUnlocks":supported_offers})
    summary["automaticallyActivatedCore"]=activate_stock_batch(tasks,current,mapped,dependencies,out,item_names,approved_offers)
    write(out/"bulk-summary.json",summary)
    print(json.dumps(summary,indent=2))
if __name__=="__main__":
    test_dependencies()
    test_stock_activation()
    test_dialogue_free_gates()
    test_combat_adapter()
    test_material_rewards()
    test_offer_plan()
    test_supply_catalog()
    test_starter_plan()
    test_field_adapter()
    test_quest_item_adapter()
    test_milestones()
    test_scope_selection()
    test_credit_objective()
    test_efm_adaptations()
    p=argparse.ArgumentParser()
    p.add_argument("--snapshot",type=Path,default=Path("build/tarkov-reference"))
    p.add_argument("--out",type=Path,default=Path("build/tarkov-review"))
    p.add_argument("--resources",type=Path,default=Path("src/main/resources/data/taczoperations/campaign"))
    a=p.parse_args()
    build(a.snapshot,a.out,a.resources)
