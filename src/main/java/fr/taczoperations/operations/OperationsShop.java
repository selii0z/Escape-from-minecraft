package fr.taczoperations.operations;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum OperationsShop {
    FOOD(Items.BREAD, 8, 10),
    LIGHT(Items.TORCH, 16, 12),
    ARROWS(Items.ARROW, 16, 18);

    public final Item item;
    public final int count;
    public final int price;

    OperationsShop(Item item, int count, int price) {
        this.item = item;
        this.count = count;
        this.price = price;
    }

    String buy(ServerPlayer player) {
        var account = SupplyContract.account(player);
        int credits = account.getInt("credits");
        if (credits < price) {
            return "Crédits insuffisants.";
        }
        ItemStack order = new ItemStack(item, count);
        var inventory = player.getInventory();
        int capacity = inventory.items.stream().mapToInt(stack -> {
            if (stack.isEmpty()) return order.getMaxStackSize();
            if (!ItemStack.isSameItemSameComponents(stack, order)) return 0;
            return Math.max(0, stack.getMaxStackSize() - stack.getCount());
        }).sum();
        if (capacity < count) {
            return "Inventaire plein.";
        }
        inventory.add(order);
        account.putInt("credits", credits - price);
        return "Achat ajouté à ton inventaire.";
    }
}
