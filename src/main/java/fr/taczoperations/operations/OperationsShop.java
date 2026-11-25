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

    public static int requiredPlayerLevel(int loyalty) {
        return switch (loyalty) { case 2 -> 20; case 3 -> 50; case 4 -> 100; default -> 1; };
    }

    public static int requiredReputation(int loyalty) {
        return switch (loyalty) { case 2 -> 200; case 3 -> 800; case 4 -> 2000; default -> 0; };
    }

    public static int reputationLevel(int points, int experience) {
        int level = MilitaryProgression.level(experience);
        for (int loyalty = 4; loyalty >= 2; loyalty--) {
            if (points >= requiredReputation(loyalty) && level >= requiredPlayerLevel(loyalty)) return loyalty;
        }
        return 1;
    }

    public static int nextReputation(int points, int experience) {
        return requiredReputation(Math.min(4,reputationLevel(points,experience)+1));
    }

    int reputation(ServerPlayer player) {
        var account = SupplyContract.account(player);
        long points = Math.max(0,account.getInt("timed_reputation_"+ordinal()));
        for (SupplyContract contract : SupplyContract.values()) {
            if (contract.merchant() != this) continue;
            String key = "completed_" + contract.name();
            int count = account.contains(key) ? account.getInt(key)
                : contract.status(player) == SupplyContract.COMPLETED ? 1 : 0;
            points += (long)Math.max(0, count) * contract.reputationReward();
        }
        return (int)Math.min(Integer.MAX_VALUE, points);
    }

    public enum Action {
        BUY("Acheter"), SELL("Vendre"), UNLOCK("Débloquer"), REPAIR("Réparer");
        public final String label;
        Action(String label) { this.label = label; }
    }

    public record Offer(String title, int price, Action action, Supplier<ItemStack> stack,
                        RaidRelic key, int rankXp) {
        public int loyaltyLevel() {
            if (action != Action.BUY) return 1;
            int level = rankXp >= 180 ? 4 : rankXp >= 65 ? 3 : rankXp > 0 ? 2 : 1;
            return key == null ? level : Math.max(level, key == RaidRelic.OMEGA ? 4 : key == RaidRelic.CHIP ? 3 : 2);
        }
        public String requirement() {
            return (loyaltyLevel() > 1 ? "Fidélité " + loyaltyLevel() : "")
                + (key != null && action == Action.BUY ? (loyaltyLevel() > 1 ? " · " : "") + key.title : "");
        }
    }

    public List<Offer> offers() {
        var offers = new ArrayList<Offer>();
        switch (this) {
            case ARMORER -> {
                offers.add(new Offer("Entretien · arme tenue en main",0,Action.REPAIR,()->new ItemStack(Items.IRON_INGOT),null,0));
                // Build a single authoritative catalogue from the installed TaCZ packs.
                // Each calibre inherits the lowest tier of a weapon using it, with
                // a minimum tier for the calibre itself. No duplicate cheap ammo offers.
                var ammoLevels = new java.util.TreeMap<ResourceLocation,Integer>(
                    java.util.Comparator.comparing(ResourceLocation::toString));
                for (var entry : TimelessAPI.getAllCommonGunIndex().stream()
                    .sorted(java.util.Comparator.comparing(e -> e.getKey().toString())).toList()) {
                    ResourceLocation id = entry.getKey();
                    var data = entry.getValue().getGunData();
                    if (data == null || data.getFireModeSet().isEmpty()) continue;
                    int rounds = Math.max(1, data.getAmmoAmount());
                    int tier = weaponTier(id,data.getAmmoId(),rounds);
                    int price = switch (tier) {
                        case 1 -> 380 + Math.min(400,rounds*12);
                        case 2 -> 950 + Math.min(950,rounds*20);
                        case 3 -> 1600 + Math.min(1600,rounds*30);
                        default -> 2900 + Math.min(4500,rounds*40);
                    };
                    offers.add(buy(id.getPath().replace('_',' ').toUpperCase(java.util.Locale.ROOT),price,
                        () -> weapon(id,rounds),null,tierRequirement(tier)));
                    if (data.getAmmoId()!=null) ammoLevels.merge(data.getAmmoId(),tier,Math::min);
                }
                for (var entry : ammoLevels.entrySet()) {
                    ResourceLocation ammoId=entry.getKey();
                    int tier=Math.max(calibreTier(ammoId),entry.getValue());
                    int count=tier==4?10:30;
                    int price=switch(tier){case 1->65;case 2->95;case 3->125;default->160;};
                    offers.add(buy("Munitions · "+ammoId.getPath().replace('_',' '),price,
                        () -> AmmoItemBuilder.create().setId(ammoId).setCount(count).build(),null,tierRequirement(tier)));
                }
                offers.add(buy("Chargeur étendu", 460, () -> attachment("extended_mag_1"), RaidRelic.CIRCUIT, 0));
                offers.add(buy("Viseur 552", 900, () -> attachment("sight_552"), RaidRelic.OPTIC, 0));
            }
            case MEDIC -> {
                offers.add(buy("Bandage",45,()->medical("bandage"),null,0));
                offers.add(buy("Attelle",95,()->medical("splint"),null,0));
                offers.add(buy("Kit hémostatique",120,()->medical("hemostat"),null,0));
                offers.add(buy("Kit de premiers secours",180,()->medical("medkit"),null,0));
                offers.add(buy("Salewa",150,()->medical("salewa"),null,0));
                offers.add(buy("Rations de terrain", 80, () -> new ItemStack(Items.COOKED_BEEF, 4), null, 0));
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
                for (var slot : BallisticVest.SLOTS) {
                    int percent = switch (slot) { case HEAD -> 80; case LEGS -> 75; case FEET -> 50; default -> 100; };
                    for (int tier=2;tier<=4;tier++) {
                        final int t = tier;
                        int base = tier==2?900:tier==3?2400:6500;
                        offers.add(buy(BallisticVest.name(slot)+" · Classe "+tier,base*percent/100,
                            () -> BallisticVest.create(slot,t,BallisticVest.classColor(t)),null,tier==2?20:tier==3?100:190));
                    }
                    offers.add(new Offer("Réparation · "+BallisticVest.name(slot)+" portée", 300*percent/100,
                        Action.REPAIR, () -> new ItemStack(BallisticVest.item(slot)),null,0));
                }
                // Class 5 is raid loot only.
                offers.add(buy("Lunettes de vision nocturne", 15000, () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("taczoperations:night_vision_goggles"))), null, 180));
                offers.add(buy("Lunettes thermiques", 60000, () -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("taczoperations:thermal_goggles"))), null, 180));
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
        if (this == ARMORER) {
            offers.sort(java.util.Comparator.comparingInt(Offer::loyaltyLevel));
            for (Offer offer : List.copyOf(offers)) {
                if (offer.action != Action.BUY) continue;
                ItemStack sample = offer.stack.get();
                if (!(sample.getItem() instanceof com.tacz.guns.api.item.IGun)) continue;
                offers.add(new Offer("Reprise · " + offer.title, Math.max(1, offer.price * 30 / 100),
                    Action.SELL, offer.stack, null, 0));
            }
        }
        return offers;
    }

    private static int tierRequirement(int tier) {
        return switch(tier) { case 1 -> 0; case 2 -> 20; case 3 -> 80; default -> 180; };
    }

    private static int calibreTier(ResourceLocation id) {
        if (id==null) return 4;
        String s=id.getPath().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]","");
        if (java.util.Set.of("9mm","9x19","9x19mm","45acp","45auto","380acp","22lr","22longrifle","9x18","9x18mm").contains(s)) return 1;
        if (java.util.Set.of("12g","12ga","12gauge","20g","20ga","357magnum","357","44magnum","44mag","57x28","57x28mm","46x30","46x30mm").contains(s)) return 2;
        if (java.util.Set.of("762x39","762x39mm","556x45","556x45mm","545x39","545x39mm","300blk","300blackout","762x35","223","223rem").contains(s)) return 3;
        return 4; // high-power and unclassified addon ammunition unlock last
    }

    private static int weaponTier(ResourceLocation id, ResourceLocation ammo, int rounds) {
        String s=id.getPath().toLowerCase(java.util.Locale.ROOT);
        int base=calibreTier(ammo);
        if (s.matches(".*(sniper|awp|barrett|m82|m107|m249|m240|pkm|minigun|launcher|rpg|scar_h|sr25|m110|svd|m200|intervention|mg42|mg3).*")) return 4;
        if (s.matches(".*(glock|m1911|p226|p320|m9|beretta|makarov|usp|cz75|pistol|revolver).*")) return base;
        if (s.matches(".*(mp5|mp7|mp9|ump|uzi|vector|p90|pp19|bizon|smg|m870|shotgun|spas|aa12|ksg).*")) return Math.max(2,base);
        if (s.matches(".*(m4a1|hk416|scar_l|ak12|aug|famas|g36|ar15|m16|mk18|sopmod|as_val|vss).*")) return Math.max(3,base);
        return base==1 && rounds>18 ? 2 : base;
    }

    private static ItemStack medical(String id) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("taczoperations",id)));
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
        if (offer.action == Action.REPAIR) {
            if (this == ARMORER) return WeaponCondition.repair(player);
            if (this != SALVAGER) return "Service indisponible.";
            for (var slot : BallisticVest.SLOTS) {
                if (!stack.is(BallisticVest.item(slot))) continue;
                ItemStack worn = player.getItemBySlot(slot);
                if (BallisticVest.tier(worn,slot) == 0) return "Équipe d’abord la protection balistique à réparer.";
                if (BallisticVest.condition(worn) == 100 && worn.getDamageValue() == 0) return "Protection déjà en parfait état.";
                if (balance < offer.price) return "Crédits insuffisants.";
                if (!BallisticVest.repair(worn,slot)) return "Réparation impossible.";
                account.putInt("credits", balance-offer.price);
                player.getInventory().setChanged();
                return "Protection réparée · état 100/100.";
            }
            return "Protection indisponible.";
        }
        if (offer.action == Action.SELL) {
            if (balance > Integer.MAX_VALUE - offer.price) return "Solde maximal atteint.";
            if (stack.getItem() instanceof com.tacz.guns.api.item.IGun gun) {
                var expected = gun.getGunId(stack);
                var found = player.getInventory().items.stream().filter(candidate ->
                    candidate.getItem() instanceof com.tacz.guns.api.item.IGun actual
                    && expected.equals(actual.getGunId(candidate))).findFirst();
                if (found.isEmpty()) return "Tu ne possèdes pas cette arme.";
                found.get().shrink(1);
                player.getInventory().setChanged();
            } else if (!SupplyContract.consume(player, stack.getItem(), 1)) return "Tu ne possèdes pas cet objet.";
            account.putInt("credits", balance + offer.price);
            return "Vente effectuée · +" + offer.price + " crédits.";
        }
        int experience = SupplyContract.experienceEarned(player);
        int loyalty = offer.loyaltyLevel();
        if (MilitaryProgression.level(experience) < requiredPlayerLevel(loyalty))
            return "Niveau militaire " + requiredPlayerLevel(loyalty) + " requis.";
        if (reputationLevel(reputation(player),experience) < loyalty)
            return "Fidélité " + loyalty + " requise · " + requiredReputation(loyalty) + " rép.";
        if (offer.key != null && !account.getBoolean("unlock_" + offer.key.id))
            return "Accès permanent requis.";
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
