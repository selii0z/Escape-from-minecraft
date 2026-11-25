package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;

public final class RaidFurnitureEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items=NonNullList.withSize(27,ItemStack.EMPTY);
    public RaidFurnitureEntity(BlockPos pos,BlockState state){super(OperationsMod.FURNITURE_ENTITY.get(),pos,state);}
    @Override public int getContainerSize(){return 27;}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> items){this.items=items;}
    @Override protected Component getDefaultName(){return getBlockState().getBlock().getName();}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        items=NonNullList.withSize(27,ItemStack.EMPTY);
        if(!tryLoadLootTable(tag))ContainerHelper.loadAllItems(tag,items,registries);
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        if(!trySaveLootTable(tag))ContainerHelper.saveAllItems(tag,items,registries);
    }
    @Override public boolean stillValid(Player player){
        return player instanceof ServerPlayer p&&ForestRaid.canSearchFurniture(p,this)&&super.stillValid(player);
    }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){
        return new ChestMenu(MenuType.GENERIC_9x3,id,inventory,this,3){
            @Override protected Slot addSlot(Slot slot){
                if(slot.container instanceof RaidFurnitureEntity)return super.addSlot(new Slot(slot.container,slot.getContainerSlot(),slot.x,slot.y){
                    @Override public boolean mayPlace(ItemStack s){return false;}
                });
                return super.addSlot(slot);
            }
            @Override public ItemStack quickMoveStack(Player p,int index){
                return index<27?super.quickMoveStack(p,index):ItemStack.EMPTY;
            }
        };
    }
}
