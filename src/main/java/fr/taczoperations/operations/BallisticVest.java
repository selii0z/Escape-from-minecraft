package fr.taczoperations.operations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

public final class BallisticVest {
    private static final String KEY = "taczoperations_ballistic_class";
    private static final int[] COLORS = {0x414A35, 0x70644C, 0x33383B, 0x555C4B};

    public static ItemStack create(int protectionClass, int color) {
        int tier = Math.clamp(protectionClass, 2, 5);
        ItemStack vest = new ItemStack(Items.LEATHER_CHESTPLATE);
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY, tier);
        vest.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        vest.set(DataComponents.DYED_COLOR, new DyedItemColor(color, false));
        vest.set(DataComponents.CUSTOM_NAME, Component.literal("Tunique PMC · Classe " + tier));
        vest.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
            Component.literal("Protection balistique : " + Math.round((1F - multiplier(tier)) * 100F) + " %"),
            Component.literal("Protège le corps, pas la tête."))));
        return vest;
    }

    public static ItemStack roll(java.util.Random random, boolean premium) {
        int n = random.nextInt(100);
        int tier = premium ? (n < 15 ? 2 : n < 60 ? 3 : n < 98 ? 4 : 5)
            : (n < 55 ? 2 : n < 87 ? 3 : n < 99 ? 4 : 5);
        return create(tier, COLORS[random.nextInt(COLORS.length)]);
    }

    private static float multiplier(int tier) {
        return switch (tier) { case 2 -> .85F; case 3 -> .70F; case 4 -> .55F; case 5 -> .35F; default -> 1F; };
    }

    static float protection(LivingEntity target, boolean headshot) {
        if (headshot) return 1F;
        ItemStack vest = target.getItemBySlot(EquipmentSlot.CHEST);
        if (!vest.is(Items.LEATHER_CHESTPLATE)) return 1F;
        CustomData data = vest.get(DataComponents.CUSTOM_DATA);
        return data == null ? 1F : multiplier(data.copyTag().getInt(KEY));
    }
    private BallisticVest() {}
}
