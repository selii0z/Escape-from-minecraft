package fr.taczoperations.operations;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Origin marker applied only when the server generates raid loot. */
public final class RaidOrigin {
    private static final String FOUND="efm_found_in_raid";
    public static boolean found(ItemStack stack){
        var data=stack.get(DataComponents.CUSTOM_DATA);
        return !stack.isEmpty()&&data!=null&&data.copyTag().getBoolean(FOUND);
    }
    public static ItemStack mark(ItemStack stack){
        if(stack.isEmpty()||found(stack))return stack;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag=data==null?new CompoundTag():data.copyTag();
        tag.putBoolean(FOUND,true);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        return stack;
    }
    static void markContainer(Container inventory){
        for(int i=0;i<inventory.getContainerSize();i++){
            var stack=inventory.getItem(i);
            if(!stack.isEmpty())mark(stack);
        }
        inventory.setChanged();
    }
    public static void tooltip(ItemTooltipEvent event){
        if(found(event.getItemStack()))event.getToolTip().add(Component.literal("Trouvé en raid").withStyle(ChatFormatting.GREEN));
    }
    private RaidOrigin(){}
}
