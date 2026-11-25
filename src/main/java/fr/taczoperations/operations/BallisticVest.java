package fr.taczoperations.operations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;

public final class BallisticVest {
    private static final String KEY = "taczoperations_ballistic_class";
    private static final String CONDITION = "taczoperations_ballistic_condition";
    public static int classColor(int tier) {
        return switch (tier) {
            case 2 -> 0x9A8058; // desert sand
            case 3 -> 0x4F633A; // olive green
            case 4 -> 0x465661; // slate blue-grey
            case 5 -> 0x101114;
            case 6 -> 0x632A24; // exclusive boss armor // tactical black
            default -> 0x4F633A;
        };
    }
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static Item item(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.LEATHER_HELMET;
            case LEGS -> Items.LEATHER_LEGGINGS;
            case FEET -> Items.LEATHER_BOOTS;
            default -> Items.LEATHER_CHESTPLATE;
        };
    }

    public static String name(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Casque PMC";
            case LEGS -> "Pantalon PMC";
            case FEET -> "Bottes PMC";
            default -> "Tunique PMC";
        };
    }

    public static ItemStack create(int protectionClass, int color) {
        return create(EquipmentSlot.CHEST, protectionClass, color);
    }

    public static ItemStack create(EquipmentSlot slot, int protectionClass, int color) {
        int tier = Math.clamp(protectionClass, 2, 6);
        ItemStack piece = new ItemStack(item(slot));
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY, tier);
        tag.putInt(CONDITION, 100);
        piece.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        piece.set(DataComponents.DYED_COLOR, new DyedItemColor(classColor(tier), false));
        piece.set(DataComponents.CUSTOM_NAME, Component.literal(name(slot) + " · Classe " + tier));
        updateLore(piece, slot, tier, 100);
        return piece;
    }

    public static ItemStack roll(java.util.Random random, boolean premium) {
        return roll(EquipmentSlot.CHEST, random, premium);
    }

    public static ItemStack roll(EquipmentSlot slot, java.util.Random random, boolean premium) {
        int n = random.nextInt(100);
        int tier = premium ? (n < 20 ? 2 : n < 65 ? 3 : n < 99 ? 4 : 5)
            : (n < 65 ? 2 : n < 91 ? 3 : n < 99 ? 4 : 5);
        return create(slot, tier, classColor(tier));
    }

    private static float multiplier(int tier) {
        return switch (tier) { case 2 -> .85F; case 3 -> .70F; case 4 -> .55F; case 5 -> .35F; case 6 -> .20F; default -> 1F; };
    }

    public static int tier(ItemStack stack, EquipmentSlot slot) {
        if (!stack.is(item(slot))) return 0;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        int value = data.copyTag().getInt(KEY);
        return value >= 2 && value <= 6 ? value : 0;
    }

    public static int condition(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 100;
        CompoundTag tag = data.copyTag();
        return tag.contains(CONDITION) ? Math.clamp(tag.getInt(CONDITION), 0, 100) : 100;
    }

    // Restore the canonical appearance of older pieces and prevent vanilla dyes
    // from disguising the ballistic class. This never changes the tier or wear.
    private static void normalizeColor(ItemStack stack) {
        for (EquipmentSlot slot : SLOTS) {
            int tier = tier(stack,slot);
            if (tier == 0) continue;
            var expected = new DyedItemColor(classColor(tier), false);
            if (!expected.equals(stack.get(DataComponents.DYED_COLOR))) {
                stack.set(DataComponents.DYED_COLOR, expected);
                updateLore(stack,slot,tier,condition(stack));
            }
            return;
        }
    }

    public static void normalizePlayerEquipment(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            || player.tickCount % 20 != 0) return;
        for (var stack : player.getInventory().items) normalizeColor(stack);
        for (var stack : player.getInventory().armor) normalizeColor(stack);
        normalizeColor(player.getOffhandItem());
    }

    private static void updateLore(ItemStack stack, EquipmentSlot slot, int tier, int condition) {
        String zone = switch (slot) {
            case HEAD -> "Tête";
            case LEGS -> "Jambes";
            case FEET -> "Pieds";
            default -> "Torse et bras";
        };
        int effective = Math.round((1F-multiplier(tier))*condition);
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
            Component.literal("Protection balistique : " + effective + " %"),
            Component.literal("État balistique : " + condition + " / 100"),
            Component.literal("Zone protégée : " + zone))));
    }

    private static void setCondition(ItemStack stack, EquipmentSlot slot, int tier, int value) {
        CustomData old = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = old == null ? new CompoundTag() : old.copyTag();
        tag.putInt(CONDITION, value);
        stack.set(DataComponents.DYED_COLOR,new DyedItemColor(classColor(tier),false));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        updateLore(stack,slot,tier,value);
    }

    public static boolean repair(ItemStack stack, EquipmentSlot slot) {
        int tier = tier(stack,slot);
        if (tier == 0 || condition(stack) == 100 && stack.getDamageValue() == 0) return false;
        setCondition(stack,slot,tier,100);
        stack.setDamageValue(0);
        return true;
    }

    static EquipmentSlot hitSlot(LivingEntity target, Entity bullet, boolean headshot) {
        if (headshot) return EquipmentSlot.HEAD;
        if (bullet == null) return EquipmentSlot.CHEST;
        Vec3 start = bullet.position(), movement = bullet.getDeltaMovement();
        var box = target.getBoundingBox();
        var hit = box.clip(start, start.add(movement));
        if (hit.isEmpty()) {
            if (box.contains(start)) hit = java.util.Optional.of(start);
            else return EquipmentSlot.CHEST;
        }
        double height = (hit.get().y-box.minY)/Math.max(.01,box.getYsize());
        return height < .14 ? EquipmentSlot.FEET : height < .46 ? EquipmentSlot.LEGS : EquipmentSlot.CHEST;
    }

    // Exactly one tagged piece contributes to the custom bullet multiplier.
    static float protection(LivingEntity target, Entity bullet, boolean headshot, float damage) {
        EquipmentSlot slot = hitSlot(target,bullet,headshot);
        ItemStack piece = target.getItemBySlot(slot);
        int tier = tier(piece,slot);
        if (tier == 0 || !Float.isFinite(damage) || damage <= 0) return 1F;
        int condition = condition(piece);
        float resistance = (1F-multiplier(tier))*condition/100F;
        if (condition > 0) {
            int wear = Math.clamp((int)Math.ceil(damage*resistance/3F),1,12);
            setCondition(piece,slot,tier,Math.max(0,condition-wear));
        }
        return 1F-resistance;
    }
    private BallisticVest() {}
}
