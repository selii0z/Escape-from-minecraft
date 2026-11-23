package fr.taczoperations.operations;

import com.tacz.guns.api.item.IGun;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

final class GuardPouch {
    private static final String STORAGE = "taczoperations_guard_pouch";

    static NonNullList<ItemStack> load(Pillager guard) {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        var data = guard.getPersistentData();
        if (data.contains(STORAGE)) {
            ContainerHelper.loadAllItems(data.getCompound(STORAGE), items, guard.registryAccess());
            return items;
        }
        var random = new Random(guard.getUUID().getLeastSignificantBits());
        int quantity = 9 + random.nextInt(16);
        items.set(0, guard.getMainHandItem().getItem() instanceof IGun
            ? OperationsShop.ammo(GuardRole.get(guard).ammo, quantity)
            : new ItemStack(Items.ARROW, quantity));
        // Each guard carries a different mix of field supplies, scrap and valuables.
        var food = new net.minecraft.world.item.Item[] {
            Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO,
            Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP, Items.DRIED_KELP
        };
        var scrap = new net.minecraft.world.item.Item[] {
            Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE,
            Items.TORCH, Items.STRING, Items.FLINT, Items.PAPER, Items.COAL
        };
        items.set(1, new ItemStack(food[random.nextInt(food.length)], 1 + random.nextInt(4)));
        items.set(2, new ItemStack(scrap[random.nextInt(scrap.length)], 1 + random.nextInt(5)));
        if (random.nextInt(3) == 0) {
            items.set(3, new ItemStack(random.nextBoolean() ? Items.GOLDEN_CARROT : Items.HONEY_BOTTLE,
                1 + random.nextInt(2)));
        }
        if (random.nextInt(3) == 0) {
            items.set(5, new ItemStack(scrap[random.nextInt(scrap.length)], 1 + random.nextInt(3)));
        }
        if (random.nextInt(7) == 0) {
            items.set(6, new ItemStack(random.nextBoolean() ? Items.EMERALD : Items.GOLD_INGOT,
                1 + random.nextInt(2)));
        }
        if (random.nextInt(12) == 0) items.set(4, RaidRelic.roll(random).stack());
        // Rare equipment drops make veteran guards worth searching.
        // The attachment remains a real TaCZ item, usable by the player.
        if (GuardRole.get(guard).elite() && random.nextInt(10) == 0) {
            items.set(7, OperationsShop.attachment(
                random.nextBoolean() ? "sight_552" : "extended_mag_1"));
        }
        save(guard, items);
        return items;
    }

    static int consume(Pillager guard, int maximum, boolean arrows) {
        var items = load(guard);
        ItemStack ammo = items.getFirst();
        if (ammo.isEmpty() || ammo.is(Items.ARROW) != arrows) {
            return 0;
        }
        int used = Math.min(maximum, ammo.getCount());
        ammo.shrink(used);
        save(guard, items);
        return used;
    }

    static void switchToArrows(Pillager guard) {
        if (!guard.getPersistentData().contains(STORAGE)) {
            return;
        }
        var items = load(guard);
        int reserve = items.getFirst().getCount();
        if (reserve > 0 && !items.getFirst().is(Items.ARROW)) {
            items.set(0, new ItemStack(Items.ARROW, reserve));
            save(guard, items);
        }
    }

    static void clear(Pillager guard) {
        save(guard, NonNullList.withSize(9, ItemStack.EMPTY));
    }

    private static void save(Pillager guard, NonNullList<ItemStack> items) {
        guard.getPersistentData().put(STORAGE,
            ContainerHelper.saveAllItems(new CompoundTag(), items, guard.registryAccess()));
    }

    private GuardPouch() {}
}
