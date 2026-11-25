package fr.taczoperations.operations;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.List;

/** Storage is owned by the physical, non-stackable equipment item. */
public final class FieldPack extends Item {
    public static final String[] BAGS={"small","patrol","tactical","military","expedition","elite"};
    public static final int[] CAPACITY={9,18,27,36,54,72};
    public static final String[] RIGS={"patrol","tactical","elite"};
    public static final int[] RIG_CAPACITY={4,8,12};
    private final int capacity;
    private final boolean rig;
    public FieldPack(Properties properties,int capacity,boolean rig){
        super(properties.stacksTo(1).component(DataComponents.CONTAINER,ItemContainerContents.EMPTY));this.capacity=capacity;this.rig=rig;
    }
    @Override public boolean canFitInsideContainerItems(){return false;}
    public int capacity(){return capacity;}
    public boolean rig(){return rig;}
    public static int capacity(ItemStack stack,boolean rig){
        return stack.getItem() instanceof FieldPack pack&&pack.rig==rig?pack.capacity:0;
    }
    public static boolean accepts(ItemStack stack,boolean rig){
        if(stack.isEmpty())return true;
        if(stack.getItem() instanceof FieldPack||stack.getItem() instanceof SecureCaseItem
            ||stack.has(DataComponents.CONTAINER)||stack.has(DataComponents.BUNDLE_CONTENTS))return false;
        return !rig||stack.getItem() instanceof FieldMedicine||stack.getItem() instanceof com.tacz.guns.api.item.IAmmo;
    }
    public static net.minecraft.core.NonNullList<ItemStack> contents(ItemStack carrier){
        int n=carrier.getItem() instanceof FieldPack p?p.capacity:0;
        var result=net.minecraft.core.NonNullList.withSize(n,ItemStack.EMPTY);
        if(n>0)carrier.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(result);
        return result;
    }
    public static void replaceContents(ItemStack carrier,List<ItemStack> items){
        if(carrier.getItem() instanceof FieldPack)carrier.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(items));
    }
    public static ItemStack create(boolean rig,int tier){
        String name=rig?"rig_"+RIGS[tier]:"backpack_"+BAGS[tier];
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations",name)));
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.literal("Capacité : "+capacity+" emplacements").withStyle(ChatFormatting.GRAY));
        int used=(int)contents(stack).stream().filter(item->!item.isEmpty()).count();
        tooltip.add(Component.literal("Occupé : "+used+" / "+capacity).withStyle(ChatFormatting.GRAY));
        if(rig)tooltip.add(Component.literal("Munitions et soins uniquement").withStyle(ChatFormatting.GRAY));
    }
}
