package fr.taczoperations.operations;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

final class GuardPouch {
    private static final String STORAGE = "taczoperations_guard_pouch";
    private static final ResourceLocation AMMO_ID = ResourceLocation.fromNamespaceAndPath("tacz", "9mm");

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
            ? AmmoItemBuilder.create().setId(AMMO_ID).setCount(quantity).build()
            : new ItemStack(Items.ARROW, quantity));
        items.set(1, new ItemStack(random.nextBoolean() ? Items.BREAD : Items.COOKED_BEEF, 1 + random.nextInt(3)));
        items.set(2, new ItemStack(random.nextBoolean() ? Items.IRON_INGOT : Items.TORCH, 1 + random.nextInt(4)));
        if (random.nextInt(4) == 0) {
            items.set(3, new ItemStack(Items.GOLDEN_CARROT));
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
