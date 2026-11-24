package fr.taczoperations.operations;

import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class RaidSecurity {
    private static final String POUCH="secure_pouch_v1", SNAPSHOT="secure_pouch_before_raid",
        INSURED="operations_insured_owner", CLAIMS="insurance_claims_v1";
    private static final int FEE=250;
    private static final long DELAY=30L*60*1000;

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("efm")
            .then(Commands.literal("coffre").executes(ctx->{open(ctx.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("assurer").executes(ctx->{var p=ctx.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(insure(p)));return 1;}))
            .then(Commands.literal("recuperer").executes(ctx->{var p=ctx.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(claim(p)));return 1;})));
    }
    private static boolean available(ServerPlayer p) {
        return p.isAlive()&&!p.isSpectator()&&(!p.level().dimension().equals(ForestExpedition.FOREST)||ForestRaid.isParticipant(p));
    }
    static void open(ServerPlayer p) {
        if(!available(p)) return;
        p.closeContainer();
        var container=new Pouch(p);
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new HopperMenu(id,inv,container) {
            {
                for(int i=0;i<5;i++) {
                    Slot old=slots.get(i);
                    Slot filtered=new Slot(container,i,old.x,old.y) {
                        @Override public boolean mayPlace(ItemStack s) {
                            return !s.has(DataComponents.CONTAINER)&&!s.has(DataComponents.BUNDLE_CONTENTS);
                        }
                    };
                    filtered.index=i;
                    slots.set(i,filtered);
                }
            }
        },Component.literal("Coffre sécurisé · 5 emplacements")));
    }
    private static final class Pouch extends SimpleContainer {
        private final ServerPlayer owner;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private boolean ready;
        Pouch(ServerPlayer p) {
            super(5);
            owner=p;dimension=p.level().dimension();
            ListTag items=SupplyContract.account(p).getList(POUCH,10);
            for(int i=0;i<items.size();i++) {
                CompoundTag entry=items.getCompound(i);int slot=entry.getInt("slot");
                if(slot>=0&&slot<5) super.setItem(slot,ItemStack.parseOptional(p.registryAccess(),entry.getCompound("item")));
            }
            ready=true;
        }
        @Override public boolean stillValid(Player p) {
            return p==owner&&available(owner)&&owner.level().dimension().equals(dimension);
        }
        @Override public void setChanged() {
            super.setChanged();if(!ready)return;
            ListTag items=new ListTag();
            for(int i=0;i<getContainerSize();i++) {
                ItemStack s=getItem(i);if(s.isEmpty())continue;
                CompoundTag e=new CompoundTag();e.putInt("slot",i);e.put("item",s.saveOptional(owner.registryAccess()));items.add(e);
            }
            SupplyContract.account(owner).put(POUCH,items);
        }
    }
    static void snapshot(ServerPlayer p) {
        p.getPersistentData().put(SNAPSHOT,SupplyContract.account(p).getList(POUCH,10).copy());
    }
    static void rollback(ServerPlayer p) {
        if(p.getPersistentData().contains(SNAPSHOT,9)) {
            p.closeContainer();
            SupplyContract.account(p).put(POUCH,p.getPersistentData().getList(SNAPSHOT,10).copy());
        }
        clearSnapshot(p);
    }
    static void clearSnapshot(ServerPlayer p){p.getPersistentData().remove(SNAPSHOT);}
    private static CompoundTag tag(ItemStack s) {
        CustomData d=s.get(DataComponents.CUSTOM_DATA);return d==null?new CompoundTag():d.copyTag();
    }
    private static boolean eligible(ItemStack s) {
        return !s.isEmpty()&&s.getCount()==1&&(s.getItem() instanceof ArmorItem||s.getItem() instanceof com.tacz.guns.api.item.IGun);
    }
    private static void removeInsurance(ItemStack s) {
        CompoundTag d=tag(s);d.remove(INSURED);
        if(d.isEmpty())s.remove(DataComponents.CUSTOM_DATA);else s.set(DataComponents.CUSTOM_DATA,CustomData.of(d));
    }
    static String insure(ServerPlayer p) {
        if(!available(p)||p.level().dimension().equals(ForestExpedition.FOREST))return "Assurance disponible uniquement hors raid.";
        var items=new java.util.ArrayList<ItemStack>();
        items.add(p.getMainHandItem());
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})items.add(p.getItemBySlot(slot));
        items.removeIf(s->!eligible(s)||tag(s).hasUUID(INSURED)&&p.getUUID().equals(tag(s).getUUID(INSURED)));
        if(items.isEmpty())return "Aucune arme tenue ou armure équipée non assurée.";
        var account=SupplyContract.account(p);int cost=items.size()*FEE,balance=account.getInt("credits");
        if(balance<cost)return "Crédits insuffisants : "+cost+" requis.";
        for(ItemStack s:items){CompoundTag d=tag(s);d.putUUID(INSURED,p.getUUID());s.set(DataComponents.CUSTOM_DATA,CustomData.of(d));}
        account.putInt("credits",balance-cost);p.getInventory().setChanged();
        return items.size()+" équipement(s) assuré(s) · -"+cost+" crédits.";
    }
    public static void onDeathDrops(LivingDropsEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.level().dimension().equals(ForestExpedition.FOREST)||!ForestRaid.isParticipant(p)||event.isCanceled())return;
        var account=SupplyContract.account(p);ListTag claims=account.getList(CLAIMS,10);int recovered=0;
        var iterator=event.getDrops().iterator();
        while(iterator.hasNext()) {
            ItemEntity drop=iterator.next();ItemStack s=drop.getItem();CompoundTag d=tag(s);
            if(!d.hasUUID(INSURED)||!p.getUUID().equals(d.getUUID(INSURED)))continue;
            removeInsurance(s);
            if(claims.size()>=128||p.getRandom().nextInt(100)>=65)continue;
            CompoundTag e=new CompoundTag();e.putLong("ready",System.currentTimeMillis()+DELAY);
            e.put("item",s.saveOptional(p.registryAccess()));claims.add(e);iterator.remove();recovered++;
        }
        account.put(CLAIMS,claims);
        if(recovered>0)p.sendSystemMessage(Component.literal("Assurance : "+recovered+" équipement(s) en récupération (30 min)."));
    }
    static String claim(ServerPlayer p) {
        if(!available(p)||p.level().dimension().equals(ForestExpedition.FOREST))return "Retrait disponible uniquement hors raid.";
        var account=SupplyContract.account(p);ListTag claims=account.getList(CLAIMS,10),remaining=new ListTag();int count=0;
        long now=System.currentTimeMillis();
        for(int i=0;i<claims.size();i++) {
            CompoundTag e=claims.getCompound(i);
            if(e.getLong("ready")>now){remaining.add(e.copy());continue;}
            ItemStack s=ItemStack.parseOptional(p.registryAccess(),e.getCompound("item"));if(s.isEmpty())continue;
            // Insert only into a free main inventory slot; do not drop claims on the ground.
            int slot=p.getInventory().getFreeSlot();
            if(slot<0){remaining.add(e.copy());continue;}
            p.getInventory().setItem(slot,s);count++;
        }
        account.put(CLAIMS,remaining);p.getInventory().setChanged();
        return count+" équipement(s) récupéré(s) · "+remaining.size()+" en attente ou inventaire plein.";
    }
    private RaidSecurity(){}
}
