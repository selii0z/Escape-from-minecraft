package fr.taczoperations.operations;

import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class WeaponCondition {
    private static final String CONDITION="efm_weapon_condition", JAM="efm_weapon_jammed";
    private static final int FULL=10000, WEAR=2;
    private static CompoundTag tag(ItemStack stack){
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return data==null?new CompoundTag():data.copyTag();
    }
    private static void write(ItemStack stack,CompoundTag data){
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    public static int condition(ItemStack stack){
        var data=tag(stack);
        return data.contains(CONDITION)?Math.clamp(data.getInt(CONDITION),0,FULL):FULL;
    }
    public static boolean jammed(ItemStack stack){return tag(stack).getBoolean(JAM);}
    static void usedLoot(ItemStack stack,int percent){
        if(!(stack.getItem() instanceof IGun))return;
        var data=tag(stack);data.putInt(CONDITION,Math.clamp(percent,0,100)*100);
        data.remove(JAM);write(stack,data);
    }
    private static boolean active(ServerPlayer p){
        return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&ForestRaid.isParticipant(p);
    }
    private static void notice(ServerPlayer p,String message){
        p.displayClientMessage(Component.literal(message),true);
    }
    public static void shoot(GunShootEvent event){
        if(event.getLogicalSide().isClient()||event.isCanceled()||!(event.getShooter() instanceof ServerPlayer p)||!active(p))return;
        ItemStack stack=event.getGunItemStack();
        if(!(stack.getItem() instanceof IGun))return;
        int value=condition(stack);
        if(value==0||jammed(stack)){
            event.setCanceled(true);
            if(p.tickCount%5==0)notice(p,value==0?"Arme hors service · réparation chez l'Armurier.":"Arme enrayée · recharge pour débloquer.");
            return;
        }
        // Below 70%, a low progressive chance; at very low condition max 6%.
        double risk=value>=7000?0:Math.min(.06,(7000-value)*.06/7000);
        if(risk>0&&p.getRandom().nextDouble()<risk){
            var data=tag(stack);data.putBoolean(JAM,true);write(stack,data);
            event.setCanceled(true);p.getInventory().setChanged();
            p.sendSystemMessage(Component.literal("Arme enrayée ! Recharge pour débloquer le mécanisme."));
        }
    }
    public static void fire(GunFireEvent event){
        if(event.getLogicalSide().isClient()||event.isCanceled()||!(event.getShooter() instanceof ServerPlayer p)||!active(p))return;
        var stack=event.getGunItemStack();
        if(!(stack.getItem() instanceof IGun))return;
        int value=condition(stack);
        // A burst must not bypass a jam or fire after reaching zero condition.
        if(value==0||jammed(stack)){event.setCanceled(true);return;}
        int wear=PassiveSkills.reduce(p,PassiveSkills.Skill.WEAPONS,WEAR);
        var data=tag(stack);data.putInt(CONDITION,Math.max(0,value-wear));write(stack,data);
        p.getInventory().setChanged();
        if(value>2000&&value-wear<=2000)p.sendSystemMessage(Component.literal("Attention : état de l'arme inférieur ou égal à 20 %."));
    }
    public static void reload(GunReloadEvent event){
        if(event.getLogicalSide().isClient()||event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p))return;
        var stack=event.getGunItemStack();
        if(!(stack.getItem() instanceof IGun)||!jammed(stack)||condition(stack)==0)return;
        var data=tag(stack);data.remove(JAM);write(stack,data);p.getInventory().setChanged();
        notice(p,"Enrayement résolu.");
    }
    static String repair(ServerPlayer p){return repair(p,0);}
    static String repair(ServerPlayer p,int discount){
        var stack=p.getMainHandItem();
        if(!(stack.getItem() instanceof IGun))return "Tiens l'arme TaCZ à réparer en main.";
        int value=condition(stack);
        if(value==FULL&&!jammed(stack))return "Arme déjà en parfait état.";
        int cost=Math.max(50,(int)Math.ceil((FULL-value)*8.0/100));
        cost=Math.max(1,(cost*(100-Math.clamp(discount,0,30))+99)/100);
        int balance=SupplyContract.balance(p);
        if(balance<cost)return "Crédits insuffisants · réparation : "+cost+" crédits.";
        var data=tag(stack);data.putInt(CONDITION,FULL);data.remove(JAM);write(stack,data);
        SupplyContract.account(p).putInt("credits",balance-cost);
        TradeJournal.record(p,"Réparation",discount>0?"Atelier":OperationsShop.ARMORER.title,stack.getHoverName().getString(),1,-cost);
        p.getInventory().setChanged();
        return "Arme réparée · 100 % · -"+cost+" crédits.";
    }
    public static void tooltip(ItemTooltipEvent event){
        var stack=event.getItemStack();
        if(!(stack.getItem() instanceof IGun))return;
        int value=condition(stack);
        event.getToolTip().add(Component.literal(String.format(java.util.Locale.FRANCE,"État : %.1f %%",value/100.0)));
        if(value==0)event.getToolTip().add(Component.literal("Hors service en raid"));
        else if(jammed(stack))event.getToolTip().add(Component.literal("Enrayée · recharger"));
    }
    private WeaponCondition(){}
}
