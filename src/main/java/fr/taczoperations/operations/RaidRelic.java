package fr.taczoperations.operations;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public enum RaidRelic {
    PLAQUE("engraved_tag", "Plaque militaire gravée", 80, 0, 25),
    RADIO("tactical_radio", "Radio tactique endommagée", 120, 0, 22),
    DOCUMENTS("classified_documents", "Documents confidentiels", 250, 1, 15),
    WATCH("military_watch", "Montre militaire ancienne", 350, 1, 12),
    CIRCUIT("secure_circuit", "Circuit électronique sécurisé", 600, 2, 8),
    DRIVE("encrypted_drive", "Disque dur crypté", 850, 2, 6),
    CASE("intel_case", "Mallette de renseignements", 1200, 2, 5),
    OPTIC("prototype_optic", "Prototype de viseur militaire", 2000, 3, 3),
    CHIP("command_chip", "Puce de commandement", 3500, 3, 2),
    OMEGA("omega_file", "Dossier classifié Omega", 6000, 4, 1);

    public final String id;
    public final String title;
    public final int price;
    public final int tier;
    private final int weight;
    private Supplier<? extends Item> item;

    RaidRelic(String id, String title, int price, int tier, int weight) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.tier = tier;
        this.weight = weight;
    }

    public static void register(DeferredRegister.Items registry) {
        for (var relic : values()) {
            relic.item = registry.registerItem(relic.id, props -> new RelicItem(props, relic),
                new Item.Properties().stacksTo(16).rarity(switch (relic.tier) {
                    case 0 -> net.minecraft.world.item.Rarity.COMMON;
                    case 1 -> net.minecraft.world.item.Rarity.UNCOMMON;
                    case 2 -> net.minecraft.world.item.Rarity.RARE;
                    default -> net.minecraft.world.item.Rarity.EPIC;
                }));
        }
    }

    private static final class RelicItem extends Item {
        private final RaidRelic relic;
        RelicItem(Properties properties, RaidRelic relic) {
            super(properties);
            this.relic = relic;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> text, net.minecraft.world.item.TooltipFlag flag) {
            text.add(net.minecraft.network.chat.Component.literal(relic.rarity() + " · " + relic.price + " crédits"));
            text.add(net.minecraft.network.chat.Component.literal("Vente, contrat ou accès au catalogue."));
        }
    }

    public String rarity() {
        return switch (tier) {
            case 0 -> "Commun";
            case 1 -> "Peu commun";
            case 2 -> "Rare";
            case 3 -> "Épique";
            default -> "Légendaire";
        };
    }

    public Item item() { return item.get(); }
    public ItemStack stack() { return new ItemStack(item()); }

    static RaidRelic roll(java.util.Random random) {
        int total = 0;
        for (var relic : values()) total += relic.weight;
        int draw = random.nextInt(total);
        for (var relic : values()) {
            draw -= relic.weight;
            if (draw < 0) return relic;
        }
        throw new IllegalStateException("Invalid relic table");
    }
}
