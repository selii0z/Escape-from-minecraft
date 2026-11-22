package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum SupplyContract {
    OUTPOST("Avant-poste", Items.SPRUCE_LOG, 8, Items.TORCH, 16, 25, 8),
    BARRICADE("Barricades", Items.COBBLESTONE, 16, Items.ARROW, 12, 35, 12),
    WORKSHOP("Atelier", Items.IRON_INGOT, 4, Items.GOLDEN_CARROT, 4, 50, 18);

    public static final int AVAILABLE = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETED = 2;

    public final String title;
    public final Item requested;
    public final int amount;
    public final Item reward;
    public final int rewardAmount;
    public final int credits;
    public final int experience;

    SupplyContract(String title, Item requested, int amount, Item reward, int rewardAmount,
                   int credits, int experience) {
        this.title = title;
        this.requested = requested;
        this.amount = amount;
        this.reward = reward;
        this.rewardAmount = rewardAmount;
        this.credits = credits;
        this.experience = experience;
    }

    private static CompoundTag account(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag account = persisted.getCompound("taczoperations");
        persisted.put("taczoperations", account);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        return account;
    }

    static int balance(ServerPlayer player) {
        return account(player).getInt("credits");
    }

    int status(ServerPlayer player) {
        return account(player).getInt(name());
    }

    String act(ServerPlayer player) {
        CompoundTag account = account(player);
        int status = account.getInt(name());
        if (status == COMPLETED) {
            return "Récompense déjà remise.";
        }
        if (status == AVAILABLE) {
            account.putInt(name(), ACTIVE);
            return "Contrat accepté.";
        }

        Inventory inventory = player.getInventory();
        int count = inventory.items.stream().filter(stack -> stack.is(requested))
            .mapToInt(ItemStack::getCount).sum();
        if (count < amount) {
            return "Ravitaillement incomplet (" + count + "/" + amount + ").";
        }
        int remaining = amount;
        for (ItemStack stack : inventory.items) {
            if (stack.is(requested)) {
                int used = Math.min(remaining, stack.getCount());
                stack.shrink(used);
                remaining -= used;
                if (remaining == 0) {
                    break;
                }
            }
        }
        account.putInt(name(), COMPLETED);
        account.putInt("credits", account.getInt("credits") + credits);
        inventory.placeItemBackInInventory(new ItemStack(reward, rewardAmount));
        inventory.setChanged();
        player.giveExperiencePoints(experience);
        return "Ravitaillement livré. Récompense remise.";
    }
}
