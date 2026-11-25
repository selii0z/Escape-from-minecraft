package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Dedicated server-backed inventory view; does not resize vanilla Inventory.items. */
public final class FieldInventoryMenu extends AbstractContainerMenu {
    public static final String GOGGLES="efm_goggles", BAG="efm_backpack", RIG="efm_rig";
    public static final int WIDTH=368, HEIGHT=242;
    private static final java.util.Map<ServerPlayer,FieldInventoryMenu> REFRESH=new java.util.WeakHashMap<>();
    private final Player owner;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private final boolean server;
    private final IItemHandlerModifiable bagHandler,rigHandler;
    private final ItemStack bagCarrier,rigCarrier;
    private final int bagSize,rigSize;
    private final SimpleContainer bagStorage,rigStorage;
    private static boolean allowed(Player p){
        return p.isAlive()&&!p.isSpectator()&&(!p.level().dimension().equals(ForestExpedition.FOREST)||p instanceof ServerPlayer s&&ForestRaid.isParticipant(s));
    }
    static IItemHandlerModifiable handler(Player p,String id){
        if(!ModList.get().isLoaded("curios"))return null;
        return fr.taczoperations.compat.FieldCurios.handler(p,id);
    }
    static ItemStack current(IItemHandlerModifiable handler){
        return handler==null||handler.getSlots()==0?ItemStack.EMPTY:handler.getStackInSlot(0);
    }
    public static void open(ServerPlayer p){
        if(!allowed(p)||p.isCreative()||!ModList.get().isLoaded("curios"))return;
        if(p.containerMenu!=p.inventoryMenu)return;
        var g=handler(p,GOGGLES);var b=handler(p,BAG);var r=handler(p,RIG);
        if(g==null||b==null||r==null){
            p.sendSystemMessage(Component.literal("Emplacements EFM indisponibles : vérifie Curios et les datapacks."));
            return;
        }
        int bs=FieldPack.capacity(current(b),false),rs=FieldPack.capacity(current(r),true);
        p.openMenu(new SimpleMenuProvider((id,inv,viewer)->new FieldInventoryMenu(id,inv,g,b,r,bs,rs,true),
            Component.literal("Inventaire tactique")),buf->{buf.writeVarInt(bs);buf.writeVarInt(rs);});
    }
    public FieldInventoryMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){
        this(id,inv,new ItemStackHandler(1),new ItemStackHandler(1),new ItemStackHandler(1),buf.readVarInt(),buf.readVarInt(),false);
    }
    private FieldInventoryMenu(int id,Inventory inv,IItemHandlerModifiable goggles,IItemHandlerModifiable bag,IItemHandlerModifiable rig,int bs,int rs,boolean server){
        super(OperationsMod.FIELD_INVENTORY.get(),id);
        this.owner=inv.player;this.dimension=owner.level().dimension();this.server=server;bagHandler=bag;rigHandler=rig;
        bagCarrier=current(bag);rigCarrier=current(rig);
        bagSize=Math.clamp(bs,0,72);rigSize=Math.clamp(rs,0,12);
        // Original four armor slots, left of the player.
        EquipmentSlot[] armor={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++){
            final EquipmentSlot type=armor[i];
            addSlot(new Slot(inv,39-i,10,18+i*19){
                @Override public int getMaxStackSize(){return 1;}
                @Override public boolean mayPlace(ItemStack s){return !s.isEmpty()&&owner.getEquipmentSlotForItem(s)==type;}
                @Override public boolean mayPickup(Player p){return super.mayPickup(p)&&(p.isCreative()||!net.minecraft.world.item.enchantment.EnchantmentHelper.has(getItem(),net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE));}
            });
        }
        addSlot(new Slot(inv,40,10,96));
        // Actual Curios equipment slots, right of the character.
        addGear(goggles,0,91,18);addGear(bag,1,91,40);addGear(rig,2,91,62);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,9+row*9+col,10+col*18,132+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,10+col*18,195));
        var bags=server?new PackStorage(bagCarrier,bagSize):new SimpleContainer(bagSize);
        var rigs=server?new PackStorage(rigCarrier,rigSize):new SimpleContainer(rigSize);
        bagStorage=bags;rigStorage=rigs;
        for(int i=0;i<bagSize;i++)addStorage(bags,i,false,194+(i%9)*18,28+(i/9)*18);
        for(int i=0;i<rigSize;i++)addStorage(rigs,i,true,194+(i%9)*18,195+(i/9)*18);
    }
    private void addGear(IItemHandlerModifiable handler,int type,int x,int y){
        addSlot(new SlotItemHandler(handler,0,x,y){
            @Override public int getMaxStackSize(){return 1;}
            @Override public boolean mayPlace(ItemStack s){
                boolean matches=type==0?isGoggle(s):FieldPack.capacity(s,type==2)>0;
                return matches&&super.mayPlace(s);
            }
        });
    }
    private static boolean isGoggle(ItemStack s){
        var id=BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        return id.equals("taczoperations:night_vision_goggles")||id.equals("taczoperations:thermal_goggles");
    }
    private void addStorage(SimpleContainer storage,int index,boolean rig,int x,int y){
        addSlot(new Slot(storage,index,x,y){
            @Override public boolean mayPlace(ItemStack s){return FieldPack.accepts(s,rig);}
            @Override public boolean mayPickup(Player p){return stillValid(p);}
        });
    }
    public int bagSize(){return bagSize;}
    public int rigSize(){return rigSize;}
    @Override public boolean stillValid(Player p){
        return p==owner&&(!server||allowed(p)&&p.level().dimension().equals(dimension)&&!p.isCreative()&&current(bagHandler)==bagCarrier&&current(rigHandler)==rigCarrier&&((PackStorage)bagStorage).unchanged()&&((PackStorage)rigStorage).unchanged());
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p){
        if(!stillValid(p)||type==ClickType.CLONE)return;
        // Avoid multi-slot gestures across a carrier that can change the menu shape.
        if(slot>=5&&slot<8&&type!=ClickType.PICKUP)return;
        if(type==ClickType.PICKUP_ALL||type==ClickType.QUICK_CRAFT)return;
        super.clicked(slot,button,type,p);
        if(server&&p instanceof ServerPlayer sp&&slot>=6&&slot<8&&!stillValid(p))REFRESH.put(sp,this);
    }
    public static void refresh(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var old=REFRESH.remove(p);
        if(old==null||!allowed(p)||!p.level().dimension().equals(old.dimension))return;
        if(p.containerMenu==old)p.closeContainer();
        if(p.containerMenu==p.inventoryMenu)open(p);
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(!stillValid(p)||index<0||index>=slots.size()||index>=5&&index<8)return ItemStack.EMPTY;
        Slot slot=slots.get(index);
        if(!slot.hasItem()||!slot.mayPickup(p))return ItemStack.EMPTY;
        ItemStack source=slot.getItem(),original=source.copy();
        if(index>=44||index<5){
            if(!moveItemStackTo(source,8,44,false))return ItemStack.EMPTY;
        }else{
            boolean moved=false;
            if(index>=8&&index<44&&FieldPack.accepts(source,false)&&bagSize>0)
                moved=moveItemStackTo(source,44,44+bagSize,false);
            if(!moved&&FieldPack.accepts(source,true)&&rigSize>0)
                moved=moveItemStackTo(source,44+bagSize,slots.size(),false);
            if(!moved){
                if(index<35)moved=moveItemStackTo(source,35,44,false);
                else moved=moveItemStackTo(source,8,35,false);
            }
            if(!moved)return ItemStack.EMPTY;
        }
        if(source.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        if(source.getCount()==original.getCount())return ItemStack.EMPTY;
        slot.onTake(p,source);return original;
    }
    private static final class PackStorage extends SimpleContainer{
        final ItemStack carrier;
        private boolean ready;
        private ItemContainerContents expected;
        boolean unchanged(){return java.util.Objects.equals(expected,carrier.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY));}
        PackStorage(ItemStack carrier,int size){
            super(size);this.carrier=carrier;
            var contents=NonNullList.withSize(size,ItemStack.EMPTY);
            carrier.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(contents);
            for(int i=0;i<size;i++)super.setItem(i,contents.get(i));
            expected=carrier.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);ready=true;
        }
        @Override public void setChanged(){
            super.setChanged();if(!ready||carrier.isEmpty())return;
            var items=NonNullList.withSize(getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<getContainerSize();i++)items.set(i,getItem(i).copy());
            if(!unchanged())return;
            expected=ItemContainerContents.fromItems(items);
            carrier.set(DataComponents.CONTAINER,expected);
        }
    }
}
