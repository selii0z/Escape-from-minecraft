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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;

public final class HideoutServices {
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
            return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);
        }
    }
    static void reserve(ServerPlayer p){
        if(!Hideout.ownsPlot(p)||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return;
        int tier=Hideout.tier(p,Hideout.Station.RESERVE);
        if(tier==0){Hideout.say(p,"Améliore la réserve au niveau 1.");return;}
        int rows=tier*2;var container=new Stash(p,rows*9);
        MenuType<?> type=rows==2?MenuType.GENERIC_9x2:rows==4?MenuType.GENERIC_9x4:MenuType.GENERIC_9x6;
        p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new ChestMenu(type,id,inv,container,rows){
            {
                for(int i=0;i<rows*9;i++){
                    Slot old=slots.get(i);
                    Slot filtered=new Slot(old.container,old.getContainerSlot(),old.x,old.y){
                        @Override public boolean mayPlace(ItemStack s){return !(s.getItem() instanceof SecureCaseItem);}
                    };
                    filtered.index=i;slots.set(i,filtered);
                }
            }
            @Override public boolean stillValid(Player player){return container.stillValid(player);}
            @Override public void clicked(int slot,int button,ClickType click,Player player){
                if(player!=p||p.containerMenu!=this||!container.stillValid(player)||click==ClickType.CLONE)return;
                super.clicked(slot,button,click,player);
                container.setChanged();
            }
            @Override public ItemStack quickMoveStack(Player player,int slot){
                if(player!=p||p.containerMenu!=this||!container.stillValid(player)||slot<0||slot>=slots.size())return ItemStack.EMPTY;
                ItemStack moved=super.quickMoveStack(player,slot);
                if(!moved.isEmpty())container.setChanged();
                return moved;
            }
            @Override public void removed(Player player){
                super.removed(player);
                if(player==p)container.setChanged();
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
        if(!HideoutPower.available(p)){Hideout.say(p,"Alimente le générateur avant de fabriquer.");return;}
        var output=r.result();if(output.isEmpty()){Hideout.say(p,"Objet de recette indisponible.");return;}
        // Plan the complete output before charging anything. Fill compatible
        // existing stacks first, then use empty native inventory slots.
        int remaining=r.count;
        var allocation=new java.util.LinkedHashMap<Integer,Integer>();
        for(int i=0;i<36&&remaining>0;i++){
            var stack=p.getInventory().getItem(i);
            if(stack.isEmpty()||!ItemStack.isSameItemSameComponents(stack,output))continue;
            int n=Math.min(remaining,Math.max(0,stack.getMaxStackSize()-stack.getCount()));
            if(n>0){allocation.put(i,n);remaining-=n;}
        }
        for(int i=0;i<36&&remaining>0;i++){
            if(!p.getInventory().getItem(i).isEmpty())continue;
            int n=Math.min(remaining,output.getMaxStackSize());
            allocation.put(i,n);remaining-=n;
        }
        if(remaining>0){Hideout.say(p,"Inventaire plein : libère de la place pour "+r.count+" objets.");return;}
        int balance=SupplyContract.balance(p);
        if(balance<r.price){Hideout.say(p,"Crédits insuffisants.");return;}
        var item=r.material.stack().getItem();
        if(!CarriedStorage.of(p).consume(s->s.is(item),r.amount)){Hideout.say(p,"Matériau requis : "+r.amount+" × "+r.material.title);return;}
        SupplyContract.account(p).putInt("credits",balance-r.price);
        for(var entry:allocation.entrySet()){
            int slot=entry.getKey(),n=entry.getValue();var stack=p.getInventory().getItem(slot);
            if(stack.isEmpty())p.getInventory().setItem(slot,output.copyWithCount(n));
            else stack.grow(n);
        }
        HideoutPower.use(p);p.getInventory().setChanged();Hideout.say(p,r.count+" × "+output.getHoverName().getString()+" fabriqué(s).");
    }
    public static void recipeCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("efm")
            .then(net.minecraft.commands.Commands.literal("recettes")
                .executes(ctx->{
                    ServerPlayer p=ctx.getSource().getPlayerOrException();
                    if(!Hideout.ownsPlot(p)){Hideout.say(p,"Consulte les recettes dans ta planque.");return 0;}
                    var storage=CarriedStorage.of(p);
                    for(var r:Recipe.values()){
                        var material=r.material.stack().getItem();
                        int owned=storage.count(s->s.is(material));
                        boolean ready=Hideout.tier(p,r.station)>=r.tier&&HideoutPower.available(p)
                            &&SupplyContract.balance(p)>=r.price&&owned>=r.amount;
                        var line=Component.literal(r.id+" ×"+r.count+" · "+r.price+" Cr. · "+r.material.title+" "+owned+"/"+r.amount
                            +" · "+r.station.label+" "+Hideout.tier(p,r.station)+"/"+r.tier+" · énergie "+HideoutPower.energy(p)+"/1");
                        p.sendSystemMessage(line.withStyle(ready?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.RED));
                    }
                    return 1;
                })));
    }
    /** Withdraw-only migration. Preserve anything that cannot fit or be decoded. */
    static int recoverLegacyStash(ServerPlayer p){
        if(!PlayerMarket.available(p)||ForestExpedition.hasReturnPoint(p)||ScavRun.active(p)||!p.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
            ||p.containerMenu!=p.inventoryMenu||!p.containerMenu.getCarried().isEmpty())return 0;
        var account=SupplyContract.account(p);
        var saved=account.getList(STASH,10);
        var remainder=new ListTag();int recovered=0;
        for(var raw:saved){
            if(!(raw instanceof CompoundTag tag)){remainder.add(raw.copy());continue;}
            try{
                var stack=ItemStack.parseOptional(p.registryAccess(),tag.getCompound("item"));
                if(stack.isEmpty()){remainder.add(tag.copy());continue;}
                int before=stack.getCount();p.getInventory().add(stack);
                recovered+=before-stack.getCount();
                if(!stack.isEmpty()){
                    var left=tag.copy();left.put("item",stack.saveOptional(p.registryAccess()));remainder.add(left);
                }
            }catch(RuntimeException ex){remainder.add(tag.copy());}
        }
        account.put(STASH,remainder);p.getInventory().setChanged();
        Hideout.say(p,recovered+" objet(s) récupéré(s). "+remainder.size()+" pile(s) restent dans l’ancienne réserve.");
        return 1;
    }
    private HideoutServices(){}
}
