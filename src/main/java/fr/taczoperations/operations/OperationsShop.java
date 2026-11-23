package fr.taczoperations.operations;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public enum OperationsShop {
    ARMORER("Armurier", "Armes, munitions et accessoires"),
    MEDIC("Médecin", "Soins et survie"),
    SALVAGER("Récupérateur", "Matériel de terrain"),
    BROKER("Courtier", "Renseignements et accès permanents");

    public final String title;
    public final String specialty;

    OperationsShop(String title, String specialty) {
        this.title = title;
        this.specialty = specialty;
    }

    public enum Action {
        BUY("Acheter"), SELL("Vendre"), UNLOCK("Débloquer");
        public final String label;
        Action(String label) { this.label = label; }
    }

    public record Offer(String title, int price, Action action, Supplier<ItemStack> stack,
                        RaidRelic key, int rankXp) {
        public String requirement() {
            return key != null && action == Action.BUY ? key.title
                : rankXp > 0 ? rankXp + " XP de contrat" : "";
        }
    }

    public List<Offer> offers() {
        var offers = new ArrayList<Offer>();
        switch (this) {
            case ARMORER -> {
                offers.add(buy("Glock 17", 380, () -> weapon("glock_17", 12), null, 0));
                offers.add(buy("MP5", 950, () -> weapon("hk_mp5a5", 20), null, 35));
                offers.add(buy("AK-47", 1600, () -> weapon("ak47", 20), null, 80));
                offers.add(buy("M870", 1150, () -> weapon("m870", 5), null, 45));
                offers.add(buy("AWP", 2900, () -> weapon("ai_awp", 5), RaidRelic.CHIP, 0));
                offers.add(buy("M4A1", 2450, () -> weapon("m4a1", 20), RaidRelic.OMEGA, 0));
                // Enumerate every weapon actually loaded by TaCZ, including gun packs.
                // Avoid hard-coded lists that silently omit most of the arsenal.
                for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().toString())).toList()) {
                    ResourceLocation id = entry.getKey();
                    var data = entry.getValue().getGunData();
                    if (data == null || data.getFireModeSet().isEmpty()) continue;
                    int rounds = Math.max(1, data.getAmmoAmount());
                    int price = 600 + Math.min(3000, rounds * 28);
                    offers.add(buy("TaCZ · " + id.getPath().replace('_', ' '), price,
                        () -> weapon(id, rounds), null, 0));
                }
                // Sell ammunition for every loaded gun family, including addon packs.
                var ammoTypes = new java.util.TreeSet<ResourceLocation>(
                    java.util.Comparator.comparing(ResourceLocation::toString));
                for (var entry : TimelessAPI.getAllCommonGunIndex()) {
                    var gunData = entry.getValue().getGunData();
                    if (gunData != null && gunData.getAmmoId() != null) ammoTypes.add(gunData.getAmmoId());
                }
                for (ResourceLocation ammoId : ammoTypes) {
                    offers.add(buy("Munitions · " + ammoId.getPath().replace('_', ' '), 110,
                        () -> AmmoItemBuilder.create().setId(ammoId).setCount(30).build(), null, 0));
                }
                offers.add(buy("24 × 9 mm", 65, () -> ammo("9mm", 24), null, 0));
                offers.add(buy("30 × 7,62 × 39", 120, () -> ammo("762x39", 30), null, 0));
                offers.add(buy("12 × calibre 12", 95, () -> ammo("12g", 12), null, 0));
                offers.add(buy("10 × .338", 160, () -> ammo("338", 10), null, 0));
                offers.add(buy("30 × 5,56 × 45", 125, () -> ammo("556x45", 30), null, 0));
                offers.add(buy("Chargeur étendu", 460, () -> attachment("extended_mag_1"), RaidRelic.CIRCUIT, 0));
                offers.add(buy("Viseur 552", 900, () -> attachment("sight_552"), RaidRelic.OPTIC, 0));
            }
            case MEDIC -> {
                offers.add(buy("Soupe de survie", 55, () -> new ItemStack(Items.RABBIT_STEW), null, 0));
                offers.add(buy("Pomme dorée", 190, () -> new ItemStack(Items.GOLDEN_APPLE), RaidRelic.WATCH, 0));
                offers.add(buy("Potion de soin", 140, () -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                    Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING), null, 0));
                offers.add(new Offer("Reprise · pomme dorée", 45, Action.SELL, () -> new ItemStack(Items.GOLDEN_APPLE), null, 0));
            }
            case SALVAGER -> {
                offers.add(buy("Lot de torches", 45, () -> new ItemStack(Items.TORCH, 24), RaidRelic.PLAQUE, 0));
                offers.add(buy("Lot de flèches", 65, () -> new ItemStack(Items.ARROW, 24), RaidRelic.RADIO, 0));
                offers.add(buy("Veste en cuir", 160, () -> new ItemStack(Items.LEATHER_CHESTPLATE), null, 0));
                offers.add(buy("Casque en cuir", 95, () -> new ItemStack(Items.LEATHER_HELMET), null, 0));
                offers.add(buy("Bottes en cuir", 85, () -> new ItemStack(Items.LEATHER_BOOTS), null, 0));
                offers.add(buy("Casque en mailles", 250, () -> new ItemStack(Items.CHAINMAIL_HELMET), null, 20));
                offers.add(buy("Bottes en mailles", 230, () -> new ItemStack(Items.CHAINMAIL_BOOTS), null, 20));
                offers.add(buy("Casque en fer", 460, () -> new ItemStack(Items.IRON_HELMET), null, 65));
                offers.add(buy("Plastron en fer", 850, () -> new ItemStack(Items.IRON_CHESTPLATE), null, 90));
                offers.add(buy("Jambières en fer", 680, () -> new ItemStack(Items.IRON_LEGGINGS), null, 75));
                offers.add(buy("Bottes en fer", 370, () -> new ItemStack(Items.IRON_BOOTS), null, 50));
                offers.add(buy("Casque en diamant", 1450, () -> new ItemStack(Items.DIAMOND_HELMET), null, 190));
                offers.add(buy("Plastron en diamant", 2600, () -> new ItemStack(Items.DIAMOND_CHESTPLATE), null, 280));
                offers.add(buy("Jambières en mailles", 350, () -> new ItemStack(Items.CHAINMAIL_LEGGINGS), RaidRelic.DRIVE, 0));
                offers.add(buy("Veste en mailles", 400, () -> new ItemStack(Items.CHAINMAIL_CHESTPLATE), RaidRelic.CASE, 0));
                for (RaidRelic relic : List.of(RaidRelic.PLAQUE, RaidRelic.RADIO, RaidRelic.WATCH, RaidRelic.CIRCUIT, RaidRelic.DRIVE)) {
                    offers.add(sale(relic));
                }
            }
            case BROKER -> {
                offers.add(buy("Ravitaillement confidentiel", 220, () -> new ItemStack(Items.GOLDEN_CARROT, 8),
                    RaidRelic.DOCUMENTS, 0));
                for (RaidRelic relic : RaidRelic.values()) {
                    offers.add(sale(relic));
                    offers.add(new Offer("Accès · " + relic.title, 0, Action.UNLOCK, relic::stack, relic, 0));
                }
            }
        }
        return offers;
    }

    private static Offer sale(RaidRelic relic) {
        return new Offer(relic.title, relic.price, Action.SELL, relic::stack, null, 0);
    }

    private static Offer buy(String title, int credits, Supplier<ItemStack> stack, RaidRelic key, int xp) {
        return new Offer(title, credits, Action.BUY, stack, key, xp);
    }

    static ItemStack weapon(String id, int rounds) {
        return weapon(tacz(id), rounds);
    }

    static ItemStack weapon(ResourceLocation id, int rounds) {
        var index = TimelessAPI.getCommonGunIndex(id);
        if (index.isEmpty() || index.get().getGunData().getFireModeSet().isEmpty())
            return ItemStack.EMPTY;
        var data = index.get().getGunData();
        return GunItemBuilder.create().setId(id).setFireMode(data.getFireModeSet().getFirst())
            .setAmmoCount(Math.min(rounds, Math.max(1, data.getAmmoAmount())))
            .setAmmoInBarrel(rounds > 0).build();
    }

    public static ItemStack ammo(String id, int count) {
        return AmmoItemBuilder.create().setId(tacz(id)).setCount(count).build();
    }

    static ItemStack attachment(String id) {
        return AttachmentItemBuilder.create().setId(tacz(id)).build();
    }

    private static ResourceLocation tacz(String id) {
        return ResourceLocation.fromNamespaceAndPath("tacz", id);
    }

    static int unlocks(ServerPlayer player) {
        int bits = 0;
        for (RaidRelic relic : RaidRelic.values()) {
            if (SupplyContract.account(player).getBoolean("unlock_" + relic.id)) bits |= 1 << relic.ordinal();
        }
        return bits;
    }

    String trade(ServerPlayer player, int index) {
        var offers = offers();
        if (index < 0 || index >= offers.size()) return "Offre indisponible.";
        Offer offer = offers.get(index);
        var account = SupplyContract.account(player);
        if (offer.action == Action.UNLOCK) {
            String key = "unlock_" + offer.key.id;
            if (account.getBoolean(key)) return "Accès déjà débloqué.";
            if (!SupplyContract.consume(player, offer.key.item(), 1)) return "Rapporte l’objet requis.";
            account.putBoolean(key, true);
            return "Accès permanent débloqué.";
        }
        ItemStack stack = offer.stack.get();
        if (stack.isEmpty()) return "Article indisponible dans cette installation TaCZ.";
        int balance = account.getInt("credits");
        if (offer.action == Action.SELL) {
            if (balance > Integer.MAX_VALUE - offer.price) return "Solde maximal atteint.";
            if (!SupplyContract.consume(player, stack.getItem(), 1)) return "Tu ne possèdes pas cet objet.";
            account.putInt("credits", balance + offer.price);
            return "Vente effectuée · +" + offer.price + " crédits.";
        }
        if (offer.key != null && !account.getBoolean("unlock_" + offer.key.id)
            || SupplyContract.experienceEarned(player) < offer.rankXp) return "Accès ou rang requis.";
        if (balance < offer.price) return "Crédits insuffisants.";
        var inventory = player.getInventory();
        int capacity = inventory.items.stream().mapToInt(slot -> slot.isEmpty() ? stack.getMaxStackSize()
            : ItemStack.isSameItemSameComponents(slot, stack) ? slot.getMaxStackSize() - slot.getCount() : 0).sum();
        if (capacity < stack.getCount()) return "Inventaire plein.";
        inventory.add(stack);
        account.putInt("credits", balance - offer.price);
        return "Achat ajouté à ton inventaire.";
    }
}
