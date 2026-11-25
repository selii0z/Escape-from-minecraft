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
    private static final String POUCH="secure_pouch_v1",
        INSURED="operations_insured_owner", CLAIMS="insurance_claims_v1";
    private static final int FEE=100;
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
    public static void open(ServerPlayer p) {
        if(!available(p)) return;
        int slot=p.getInventory().selected;
        ItemStack carrier=p.getInventory().getItem(slot);
        if(!(carrier.getItem() instanceof SecureCaseItem)) {
            p.sendSystemMessage(Component.literal("Tiens le coffre sécurisé en main pour l'ouvrir."));
            return;
        }
        p.closeContainer();
        var container=new Pouch(p,carrier,slot);
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new HopperMenu(id,inv,container) {
            {
                for(int i=0;i<slots.size();i++) {
                    Slot old=slots.get(i);
                    final boolean storage=i<5;
                    final boolean locked=old.container==inv && old.getContainerSlot()==slot;
                    if(!storage&&!locked)continue;
                    Slot filtered=new Slot(old.container,old.getContainerSlot(),old.x,old.y) {
                        @Override public boolean mayPlace(ItemStack s) {
                            return !locked && !(s.getItem() instanceof SecureCaseItem)
                                && !s.has(DataComponents.CONTAINER) && !s.has(DataComponents.BUNDLE_CONTENTS);
                        }
                        @Override public boolean mayPickup(Player player) {return !locked;}
                    };
                    filtered.index=i;slots.set(i,filtered);
                }
            }
            @Override public void clicked(int index,int button,net.minecraft.world.inventory.ClickType type,Player player) {
                if(!container.stillValid(player))return;
                if(type==net.minecraft.world.inventory.ClickType.SWAP && button==slot)return;
                if(index>=0&&index<slots.size()&&slots.get(index).container==inv&&slots.get(index).getContainerSlot()==slot)return;
                super.clicked(index,button,type,player);
            }
            @Override public ItemStack quickMoveStack(Player player,int index) {
                if(!container.stillValid(player))return ItemStack.EMPTY;
                if(index>=0&&index<slots.size()&&slots.get(index).container==inv&&slots.get(index).getContainerSlot()==slot)return ItemStack.EMPTY;
                return super.quickMoveStack(player,index);
            }
        },Component.literal("Coffre sécurisé · 5 emplacements")));
    }
    private static final class Pouch extends SimpleContainer {
        private final ServerPlayer owner;
        private final ItemStack carrier;
        private final int slot;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private boolean ready;
        Pouch(ServerPlayer p,ItemStack carrier,int slot) {
            super(5);owner=p;this.carrier=carrier;this.slot=slot;dimension=p.level().dimension();
            ListTag items=tag(carrier).getList(POUCH,10);
            for(int i=0;i<items.size();i++) {
                CompoundTag e=items.getCompound(i);int n=e.getInt("slot");
                if(n>=0&&n<5) super.setItem(n,ItemStack.parseOptional(p.registryAccess(),e.getCompound("item")));
            }
            ready=true;
        }
        @Override public boolean stillValid(Player p) {
            return p==owner&&available(owner)&&owner.level().dimension().equals(dimension)
                &&owner.getInventory().getItem(slot)==carrier;
        }
        @Override public void setChanged() {
            super.setChanged();if(!ready)return;
            ListTag items=new ListTag();
            for(int i=0;i<getContainerSize();i++) {
                ItemStack s=getItem(i);if(s.isEmpty())continue;
                CompoundTag e=new CompoundTag();e.putInt("slot",i);e.put("item",s.saveOptional(owner.registryAccess()));items.add(e);
            }
            CompoundTag d=tag(carrier);d.put(POUCH,items);
            carrier.set(DataComponents.CUSTOM_DATA,CustomData.of(d));
            owner.getInventory().setChanged();
        }
    }
    // Store only real drops that have been removed from the death event.
    // This also protects the physical case from death outside a raid.
    private static final String PROTECTED="secure_cases_pending";
    private static final String DEATH_CASES="secure_cases_death";
    public static void cloneCases(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        if(!event.isWasDeath()||!(event.getOriginal() instanceof ServerPlayer old)||!(event.getEntity() instanceof ServerPlayer p))return;
        ListTag saved=old.getPersistentData().getList(DEATH_CASES,10);
        if(saved.isEmpty())return;
        var account=SupplyContract.account(p);ListTag queue=account.getList(PROTECTED,10);
        for(int i=0;i<saved.size();i++)queue.add(saved.getCompound(i).copy());
        account.put(PROTECTED,queue);old.getPersistentData().remove(DEATH_CASES);
    }
    public static void returnCases(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive()||p.tickCount%20!=0)return;
        var account=SupplyContract.account(p);ListTag queue=account.getList(PROTECTED,10);
        boolean changed=false;
        while(!queue.isEmpty()) {
            int slot=p.getInventory().getFreeSlot();if(slot<0)break;
            ItemStack s=ItemStack.parseOptional(p.registryAccess(),queue.getCompound(0));queue.remove(0);changed=true;
            if(s.getItem() instanceof SecureCaseItem)p.getInventory().setItem(slot,s);
        }
        if(changed){account.put(PROTECTED,queue);p.getInventory().setChanged();}
    }
    static java.util.List<ItemStack> caseContents(ServerPlayer p,ItemStack carrier){
        var result=new java.util.ArrayList<ItemStack>();
        if(!(carrier.getItem() instanceof SecureCaseItem))return result;
        var items=tag(carrier).getList(POUCH,10);
        for(int i=0;i<items.size()&&i<5;i++){
            var entry=items.getCompound(i);
            int slot=entry.getInt("slot");
            if(slot>=0&&slot<5)result.add(ItemStack.parseOptional(p.registryAccess(),entry.getCompound("item")));
        }
        return result;
    }
    private static CompoundTag tag(ItemStack s) {
        CustomData d=s.get(DataComponents.CUSTOM_DATA);return d==null?new CompoundTag():d.copyTag();
    }
    private static boolean eligible(ItemStack s) {
        return !s.isEmpty()&&s.getCount()==1&&(s.getItem() instanceof ArmorItem||s.getItem() instanceof com.tacz.guns.api.item.IGun);
    }
    static void removeInsuranceForTrade(ItemStack s){if(tag(s).contains(INSURED))removeInsurance(s);}
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
        if(!(event.getEntity() instanceof ServerPlayer p)||event.isCanceled())return;
        ListTag protectedCases=p.getPersistentData().getList(DEATH_CASES,10);
        var protectedIterator=event.getDrops().iterator();
        while(protectedIterator.hasNext()) {
            ItemStack s=protectedIterator.next().getItem();
            if(s.getItem() instanceof SecureCaseItem) {
                protectedCases.add(s.saveOptional(p.registryAccess()));protectedIterator.remove();
            }
        }
        if(!protectedCases.isEmpty())p.getPersistentData().put(DEATH_CASES,protectedCases);
        if(!p.level().dimension().equals(ForestExpedition.FOREST)||!ForestRaid.isParticipant(p))return;
        var account=SupplyContract.account(p);ListTag claims=account.getList(CLAIMS,10);int recovered=0;
        var iterator=event.getDrops().iterator();
        while(iterator.hasNext()) {
            ItemEntity drop=iterator.next();ItemStack s=drop.getItem();CompoundTag d=tag(s);
            if(!d.hasUUID(INSURED)||!p.getUUID().equals(d.getUUID(INSURED)))continue;
            removeInsurance(s);
            if(claims.size()>=128||p.getRandom().nextInt(100)>=95)continue;
            CompoundTag e=new CompoundTag();e.putLong("ready",System.currentTimeMillis()+DELAY);
            e.put("item",s.saveOptional(p.registryAccess()));claims.add(e);iterator.remove();recovered++;
        }
        account.put(CLAIMS,claims);
        if(recovered>0)p.sendSystemMessage(Component.literal("Assurance : "+recovered+" équipement(s) en récupération (30 min)."));
    }
    static String claim(ServerPlayer p) {
        if(!available(p)||p.level().dimension().equals(ForestExpedition.FOREST))return "Retrait disponible uniquement hors raid.";
        p.closeContainer();
        var container=new Claims(p);
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new net.minecraft.world.inventory.ChestMenu(
            net.minecraft.world.inventory.MenuType.GENERIC_9x6,id,inv,container,6) {
            {
                for(int i=0;i<54;i++) {
                    Slot old=slots.get(i);
                    Slot filtered=new Slot(container,i,old.x,old.y) {
                        @Override public boolean mayPlace(ItemStack s){return false;}
                    };
                    filtered.index=i;slots.set(i,filtered);
                }
            }
            @Override public void clicked(int index,int button,net.minecraft.world.inventory.ClickType type,Player player) {
                if(!container.stillValid(player))return;
                if(index>=0&&index<54 && (type==net.minecraft.world.inventory.ClickType.SWAP||type==net.minecraft.world.inventory.ClickType.CLONE))return;
                super.clicked(index,button,type,player);
            }
        },Component.literal("Assurance · matériel récupérable")));
        return container.waiting+" en attente (30 min) · retire les objets disponibles.";
    }
    private static final class Claims extends SimpleContainer {
        private final ServerPlayer owner;
        private final ListTag other=new ListTag();
        private boolean ready;
        private int waiting;
        Claims(ServerPlayer p){
            super(54);owner=p;
            ListTag claims=SupplyContract.account(p).getList(CLAIMS,10);
            long now=System.currentTimeMillis();int slot=0;
            for(int i=0;i<claims.size();i++){
                CompoundTag e=claims.getCompound(i);
                if(e.getLong("ready")>now){other.add(e.copy());waiting++;continue;}
                if(slot>=54){other.add(e.copy());continue;}
                ItemStack s=ItemStack.parseOptional(p.registryAccess(),e.getCompound("item"));
                if(!s.isEmpty())super.setItem(slot++,s);
            }
            ready=true;
        }
        @Override public boolean stillValid(Player p){return p==owner&&available(owner)&&!owner.level().dimension().equals(ForestExpedition.FOREST);}
        @Override public void setChanged(){
            super.setChanged();if(!ready)return;
            ListTag remaining=other.copy();
            for(int i=0;i<54;i++){
                ItemStack s=getItem(i);if(s.isEmpty())continue;
                CompoundTag e=new CompoundTag();e.putLong("ready",0L);e.put("item",s.saveOptional(owner.registryAccess()));remaining.add(e);
            }
            SupplyContract.account(owner).put(CLAIMS,remaining);
        }
    }
    private RaidSecurity(){}
}
