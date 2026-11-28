package fr.taczoperations.operations;

import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Separate from military XP. Server-authoritative; no training-range progress. */
public final class PassiveSkills {
    private static final String ACCOUNT="efm_skills_v1",LIMITS="efm_skills_run_v1",MOVE="efm_skills_motion_v1",DAY="efm_skills_trade_day";
    enum Skill {
        ENDURANCE("Endurance",35,20), STRENGTH("Force",30,15), MEDICINE("Médecine",40,20),
        SEARCH("Fouille",40,20), STEALTH("Discrétion",35,15), SURVIVAL("Survie",30,15),
        WEAPONS("Maîtrise des armes",45,15), TRADE("Commerce",60,10);
        final String title;final int cap,maxBonus;
        Skill(String title,int cap,int maxBonus){this.title=title;this.cap=cap;this.maxBonus=maxBonus;}
    }
    private static CompoundTag data(ServerPlayer p){
        var a=SupplyContract.account(p);if(!a.contains(ACCOUNT))a.put(ACCOUNT,new CompoundTag());return a.getCompound(ACCOUNT);
    }
    static int threshold(int level){int n=Math.clamp(level,1,50);return 6*n*(n-1);}
    static int xp(ServerPlayer p,Skill s){return Math.clamp(data(p).getInt(s.name()),0,threshold(50));}
    static int level(ServerPlayer p,Skill s){
        int x=xp(p,s),n=1;while(n<50&&x>=threshold(n+1))n++;return n;
    }
    static int bonus(ServerPlayer p,Skill s){return (level(p,s)-1)*s.maxBonus/49;}
    static void begin(ServerPlayer p){
        p.getPersistentData().remove(LIMITS);p.getPersistentData().remove(MOVE);p.getPersistentData().remove("efm_skill_extraction_awarded");
    }
    private static void gain(ServerPlayer p,Skill s,int amount,boolean extraction){
        if(amount<=0||ScavRun.active(p)||p.isCreative()||p.isSpectator()||(!extraction&&!RaidInjuries.active(p)))return;
        var root=p.getPersistentData();var limits=root.getCompound(LIMITS);int used=Math.clamp(limits.getInt(s.name()),0,s.cap);
        int grant=Math.min(amount,s.cap-used);if(grant<=0)return;
        limits.putInt(s.name(),used+grant);root.put(LIMITS,limits);award(p,s,grant);
    }
    private static void award(ServerPlayer p,Skill s,int amount){
        int before=level(p,s);var d=data(p);d.putInt(s.name(),Math.min(threshold(50),xp(p,s)+amount));
        int after=level(p,s);if(after>before)p.sendSystemMessage(Component.literal(s.title+" · niveau "+after+"/50"));
    }
    static void questReward(ServerPlayer p,Skill skill,int levels){
        int target=Math.min(50,level(p,skill)+levels);
        award(p,skill,Math.max(0,threshold(target)-xp(p,skill)));
    }
    static boolean loaded(ServerPlayer p){return p.getInventory().items.stream().filter(s->!s.isEmpty()).count()>=24;}
    // Sample movement once a second; reject stationary and teleport-scale displacements.
    static void movement(ServerPlayer p){
        if(!RaidInjuries.active(p))return;
        var d=p.getPersistentData();var m=d.getCompound(MOVE);
        double dx=p.getX()-m.getDouble("x"),dz=p.getZ()-m.getDouble("z");double dist=dx*dx+dz*dz;
        boolean moved=m.contains("x")&&dist>=.25&&dist<=144&&p.onGround()&&!p.isPassenger();
        m.putDouble("x",p.getX());m.putDouble("z",p.getZ());d.put(MOVE,m);
        if(!moved)return;
        if(p.isSprinting()&&p.tickCount%100==0)gain(p,Skill.ENDURANCE,1,false);
        if(p.isCrouching()&&p.tickCount%100==0)gain(p,Skill.STEALTH,1,false);
        if(p.tickCount%100==0&&loaded(p))
            gain(p,Skill.STRENGTH,1,false);
    }
    static void medicine(ServerPlayer p){gain(p,Skill.MEDICINE,3,false);}
    static void search(ServerPlayer p){gain(p,Skill.SEARCH,1,false);}
    static void kill(ServerPlayer p){gain(p,Skill.WEAPONS,3,false);}
    static void extracted(ServerPlayer p){
        var run=p.getPersistentData();
        if(!p.isAlive()||!run.contains("taczoperations_run_started")||run.getBoolean("taczoperations_run_xp_finalized")||run.getBoolean("efm_skill_extraction_awarded"))return;
        run.putBoolean("efm_skill_extraction_awarded",true);
        gain(p,Skill.SURVIVAL,10,true);
    }
    static void trade(ServerPlayer p,long delta){
        if(delta==0||!PlayerMarket.available(p))return;
        var a=SupplyContract.account(p);var day=a.getCompound(DAY);
        long now=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
        if(day.getLong("day")!=now){day=new CompoundTag();day.putLong("day",now);}
        int used=Math.clamp(day.getInt("xp"),0,Skill.TRADE.cap);
        if(used>=Skill.TRADE.cap)return;
        long volume=Math.min((long)Skill.TRADE.cap*250,Math.abs(delta))+Math.clamp(day.getInt("remainder"),0,249);
        int grant=Math.min(Skill.TRADE.cap-used,(int)(volume/250));
        day.putInt("xp",used+grant);
        day.putInt("remainder",used+grant>=Skill.TRADE.cap?0:(int)(volume%250));
        a.put(DAY,day);
        if(grant>0)award(p,Skill.TRADE,grant);
    }
    static int reduce(ServerPlayer p,Skill skill,int units){
        if(units<=0)return 0;int percent=bonus(p,skill);
        int removed=0;for(int i=0;i<units;i++)if(p.getRandom().nextInt(100)<percent)removed++;
        return units-removed;
    }
    static void status(ServerPlayer p){
        p.sendSystemMessage(Component.literal("Compétences passives · niveaux 1 à 50"));
        for(var s:Skill.values()){
            int n=level(p,s),x=xp(p,s);
            p.sendSystemMessage(Component.literal(s.title+" · "+n+"/50 · "+(n==50?"MAX":(x-threshold(n))+"/"+(threshold(n+1)-threshold(n))+" XP")+(s==Skill.TRADE?" · réduction achats "+bonus(p,s)+" %":" · bonus "+bonus(p,s)+" %")));
        }
    }
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("efm").then(Commands.literal("competences").executes(ctx->{status(ctx.getSource().getPlayerOrException());return 1;})
            .then(Commands.literal("menu").executes(ctx->{ProgressionMenus.skills(ctx.getSource().getPlayerOrException());return 1;}))));
    }
    private PassiveSkills(){}
}
