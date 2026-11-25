package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;

final class HideoutServices {
    private static final String STASH="hideout_stash";
    enum Recipe {
        BANDAGE("bandage",Hideout.Station.INFIRMERIE,1,TradeLoot.RIPSTOP_FABRIC,1,"bandage",2,25),
        SALEWA("salewa",Hideout.Station.INFIRMERIE,2,TradeLoot.SALINE,1,"salewa",1,50),
        RATION("ration",Hideout.Station.CUISINE,1,TradeLoot.TEA_BOX,1,"field_ration",3,20),
        WATER("eau",Hideout.Station.EAU,1,TradeLoot.WATER_FILTER,1,"water",4,40);
        final String id,output;final Hideout.Station station;final TradeLoot material;
        final int tier,amount,count,price;
        Recipe(String id,Hideout.Station station,int tier,TradeLoot material,int amount,String output,int count,int price){
            this.id=id;this.station=station;this.tier=tier;this.material=material;this.amount=amount;this.output=output;this.count=count;this.price=price;
        }
        ItemStack result(){
            if(output.equals("water"))return PotionContents.createItemStack(Items.POTION,Potions.WATER);
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",output));
            return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item,count);
        }
    }
    static void reserve(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu)return;
        int tier=Hideout.tier(p,Hideout.Station.RESERVE);
        if(tier==0){Hideout.say(p,"Améliore la réserve au niveau 1.");return;}
        int rows=tier*2;var container=new Stash(p,rows*9);
        MenuType<?> type=rows==2?MenuType.GENERIC_9x2:rows==4?MenuType.GENERIC_9x4:MenuType.GENERIC_9x6;
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new ChestMenu(type,id,inv,container,rows){
            @Override public void clicked(int slot,int button,ClickType click,Player player){
                if(!container.stillValid(player)||click==ClickType.CLONE)return;
                super.clicked(slot,button,click,player);
            }
            @Override public ItemStack quickMoveStack(Player player,int slot){
                return container.stillValid(player)?super.quickMoveStack(player,slot):ItemStack.EMPTY;
            }
        },Component.literal("Réserve personnelle · "+(rows*9)+" cases")));
    }
    private static final class Stash extends SimpleContainer {
        final ServerPlayer owner;boolean ready;
        Stash(ServerPlayer p,int size){
            super(size);owner=p;
            for(var raw:SupplyContract.account(p).getList(STASH,10)){
                if(!(raw instanceof CompoundTag tag))continue;int i=tag.getInt("slot");
                if(i>=0&&i<size)super.setItem(i,ItemStack.parseOptional(p.registryAccess(),tag.getCompound("item")));
            }
            ready=true;
        }
        @Override public boolean stillValid(Player player){return player==owner&&Hideout.ownsPlot(owner);}
        @Override public void setChanged(){
            super.setChanged();if(!ready)return;
            var list=new ListTag();
            for(int i=0;i<getContainerSize();i++){
                var s=getItem(i);if(s.isEmpty())continue;
                var tag=new CompoundTag();tag.putInt("slot",i);tag.put("item",s.saveOptional(owner.registryAccess()));list.add(tag);
            }
            SupplyContract.account(owner).put(STASH,list);
        }
    }
    static void craft(ServerPlayer p,String id){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu){Hideout.say(p,"Fabrication dans ta planque, menu fermé.");return;}
        Recipe r=null;for(var x:Recipe.values())if(x.id.equals(id))r=x;
        if(r==null){Hideout.say(p,"Recettes : bandage, salewa, ration, eau.");return;}
        if(Hideout.tier(p,r.station)<r.tier){Hideout.say(p,r.station.label+" niveau "+r.tier+" requis.");return;}
        var output=r.result();if(output.isEmpty()){Hideout.say(p,"Objet de recette indisponible.");return;}
        int count=r.count,perStack=output.getMaxStackSize(),needed=(count+perStack-1)/perStack;
        var slots=new java.util.ArrayList<Integer>();
        for(int i=0;i<36&&slots.size()<needed;i++)if(p.getInventory().getItem(i).isEmpty())slots.add(i);
        if(slots.size()<needed){Hideout.say(p,"Libère "+needed+" cases dans ton inventaire.");return;}
        int balance=SupplyContract.balance(p);
        if(balance<r.price){Hideout.say(p,"Crédits insuffisants.");return;}
        var item=r.material.stack().getItem();
        if(!CarriedStorage.of(p).consume(s->s.is(item),r.amount)){Hideout.say(p,"Matériau requis : "+r.amount+" × "+r.material.title);return;}
        SupplyContract.account(p).putInt("credits",balance-r.price);
        for(int slot:slots){int n=Math.min(count,perStack);p.getInventory().setItem(slot,output.copyWithCount(n));count-=n;}
        p.getInventory().setChanged();Hideout.say(p,r.count+" × "+output.getHoverName().getString()+" fabriqué(s).");
    }
    private HideoutServices(){}
}
