package fr.taczoperations.operations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/** Per-raid survival state, owned by the server and preserved across reconnects. */
public final class RaidSurvival {
    private static final String WATER="efm_raid_water", ENERGY="efm_raid_energy";
    private static final String STARTED="efm_survival_started", SECONDS="efm_survival_seconds";
    private static final int FULL=1000;
    private static final String REST="efm_raid_rest_seconds";
    static void begin(ServerPlayer p){
        var d=p.getPersistentData();
        d.putBoolean(STARTED,true);d.putInt(WATER,FULL);d.putInt(ENERGY,FULL);d.putInt(SECONDS,0);d.putInt(REST,HideoutPower.claimRest(p));
    }
    static int water(ServerPlayer p){return percent(p,WATER);}
    static int energy(ServerPlayer p){return percent(p,ENERGY);}
    private static int percent(ServerPlayer p,String key){
        var d=p.getPersistentData();
        return d.getBoolean(STARTED)?Math.clamp((d.getInt(key)+9)/10,0,100):100;
    }
    static void second(ServerPlayer p){
        if(!RaidInjuries.active(p))return;
        var d=p.getPersistentData();
        if(!d.getBoolean(STARTED))begin(p);
        PassiveSkills.movement(p);
        int beforeWater=water(p),beforeEnergy=energy(p);
        int seconds=d.getInt(SECONDS)+1;
        d.putInt(SECONDS,seconds);
        // Normal activity: roughly 33 min of water and 50 min of food.
        // Sprinting drains both faster; no extra drain outside the raid.
        int w=d.getInt(WATER),e=d.getInt(ENERGY);
        if(seconds%2==0)w=Math.max(0,w-PassiveSkills.reduce(p,PassiveSkills.Skill.SURVIVAL,1));
        boolean rested=seconds<=d.getInt(REST);
        if(!rested&&seconds%3==0)e=Math.max(0,e-PassiveSkills.reduce(p,PassiveSkills.Skill.SURVIVAL,1));
        if(p.isSprinting()){
            if(!rested&&seconds%2==0){
                int sprintCost=PassiveSkills.loaded(p)?PassiveSkills.reduce(p,PassiveSkills.Skill.STRENGTH,1):1;
                e=Math.max(0,e-PassiveSkills.reduce(p,PassiveSkills.Skill.ENDURANCE,sprintCost));
            }
            if(seconds%3==0)w=Math.max(0,w-PassiveSkills.reduce(p,PassiveSkills.Skill.ENDURANCE,1));
        }
        d.putInt(WATER,w);d.putInt(ENERGY,e);
        if(beforeWater>20&&water(p)<=20)p.sendSystemMessage(Component.literal("Hydratation faible : bois de l'eau."));
        if(beforeEnergy>20&&energy(p)<=20)p.sendSystemMessage(Component.literal("Énergie faible : mange une ration."));
        // Do not overwrite stronger or longer effects from another mod.
        if(w==0&&p.getEffect(MobEffects.MOVEMENT_SLOWDOWN)==null)
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,25,0,false,false,true));
        if(e==0&&p.getEffect(MobEffects.WEAKNESS)==null)
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,25,0,false,false,true));
    }
    public static void consumed(LivingEntityUseItemEvent.Finish event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!RaidInjuries.active(p))return;
        var stack=event.getItem();int water=0,energy=0;
        var potion=stack.get(DataComponents.POTION_CONTENTS);
        if(stack.is(Items.POTION)&&potion!=null&&potion.is(Potions.WATER))water=40;
        else if(stack.is(Items.MILK_BUCKET))water=25;
        else if(stack.is(Items.HONEY_BOTTLE)){water=12;energy=10;}
        var food=stack.get(DataComponents.FOOD);
        if(food!=null&&!stack.is(Items.HONEY_BOTTLE)){
            energy=Math.clamp(food.nutrition()*5,5,40);
            if(stack.is(Items.MELON_SLICE)||stack.is(Items.APPLE)||stack.is(Items.BEETROOT_SOUP)||stack.is(Items.RABBIT_STEW)||stack.is(Items.MUSHROOM_STEW))water=8;
        }
        if(water==0&&energy==0)return;
        var d=p.getPersistentData();if(!d.getBoolean(STARTED))begin(p);
        d.putInt(WATER,Math.min(FULL,d.getInt(WATER)+water*10));
        d.putInt(ENERGY,Math.min(FULL,d.getInt(ENERGY)+energy*10));
        p.displayClientMessage(Component.literal("Hydratation : "+water(p)+" % · Énergie : "+energy(p)+" %"),true);
    }
    static void clear(ServerPlayer p){
        var d=p.getPersistentData();d.remove(WATER);d.remove(ENERGY);d.remove(STARTED);d.remove(SECONDS);d.remove(REST);
    }
    private RaidSurvival(){}
}
