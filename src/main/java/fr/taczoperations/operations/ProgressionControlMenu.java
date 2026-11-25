package fr.taczoperations.operations;
import fr.taczoperations.OperationsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ProgressionControlMenu extends AbstractContainerMenu {
    private final Player owner;
    private final boolean campaign;
    public ProgressionControlMenu(int id,Inventory inv,RegistryFriendlyByteBuf buf){this(id,inv,new SimpleContainer(54),buf.readBoolean());}
    ProgressionControlMenu(int id,Inventory inv,SimpleContainer icons,boolean campaign){
        super(OperationsMod.PROGRESSION_MENU.get(),id);owner=inv.player;this.campaign=campaign;
        for(int i=0;i<54;i++)addSlot(new Slot(icons,i,-10000,-10000){
            @Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public boolean mayPickup(Player p){return false;}
        });
    }
    public boolean campaign(){return campaign;}
    @Override public boolean stillValid(Player p){return p==owner&&p.isAlive()&&!p.isSpectator()&&(!(p instanceof ServerPlayer server)||ProgressionMenus.available(server));}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType click,Player p){}
    @Override public boolean clickMenuButton(Player player,int action){
        if(!(player instanceof ServerPlayer p)||p.containerMenu!=this||!stillValid(player)||!getCarried().isEmpty())return false;
        if(action==49){p.closeContainer();if(Hideout.ownsPlot(p))HideoutMenu.open(p);return true;}
        if(action==40&&campaign&&StoryMissions.current(p)==null&&StoryMissions.next(p)<StoryMissions.Mission.values().length){
            p.closeContainer();StoryMissions.accept(p);ProgressionMenus.missions(p);return true;
        }
        return false;
    }
}
