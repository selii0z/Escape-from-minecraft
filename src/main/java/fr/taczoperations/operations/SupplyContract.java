package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum SupplyContract {
    CLEAR("Nettoyage", 5, 250, 15, Objective.KILL, null),
    PATROL("Patrouille hostile", 12, 720, 34, Objective.KILL, null),
    WARZONE("Zone de guerre", 25, 1650, 65, Objective.KILL, null),
    ELITE_HUNT("Chasse aux vétérans", 7, 1350, 56, Objective.ELITE, null),
    CLEAN_SWEEP("Nettoyage total", 15, 1250, 48, Objective.COMBAT_EXIT, null),
    HARD_EXIT("Extraction risquée", 4, 640, 31, Objective.COMBAT_EXIT, null),
    INDUSTRIAL_RUN("Retour au dépôt", 3, 1050, 48, Objective.INDUSTRIAL_EXIT, null),
    LONG_HAUL("Convoi de ravitaillement", 6, 1850, 70, Objective.INDUSTRIAL_EXIT, null),
    ASSAULT("Fusil d’assaut", 10, 680, 36, Objective.ASSAULT, null),
    ELITES("Éliminer les élites", 3, 500, 30, Objective.ELITE, null),
    TRACKER("Le Traqueur", 1, 750, 40, Objective.BOSS, null),
    GET_OUT("Première extraction", 1, 180, 12, Objective.EXIT, null),
    VETERAN("Rentrer trois fois", 3, 650, 35, Objective.EXIT, null),
    COMBAT_EXIT("Sortie sous pression", 8, 850, 45, Objective.COMBAT_EXIT, null),
    INDUSTRY("Fret abandonné", 1, 350, 22, Objective.INDUSTRIAL_EXIT, null),
    DECAPITATION("Code du Traqueur", 1, 5200, 80, Objective.BOSS_INTEL, RaidRelic.CHIP),
    TAGS(RaidRelic.PLAQUE, 3), RADIOS(RaidRelic.RADIO, 2), PAPERS(RaidRelic.DOCUMENTS, 1),
    WATCH(RaidRelic.WATCH, 1), BOARD(RaidRelic.CIRCUIT, 1), ENCRYPTED(RaidRelic.DRIVE, 1),
    SUITCASE(RaidRelic.CASE, 1), OPTIC(RaidRelic.OPTIC, 1), COMMAND(RaidRelic.CHIP, 1), OMEGA(RaidRelic.OMEGA, 1);

    private enum Objective { KILL, ASSAULT, ELITE, BOSS, EXIT, COMBAT_EXIT, INDUSTRIAL_EXIT, BOSS_INTEL, RELIC }

    public static final int AVAILABLE = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETED = 2;
    public final String title;
    public final int amount;
    public final int credits;
    public final int experience;
    private final Objective objective;
    private final RaidRelic relic;

    SupplyContract(String title, int amount, int credits, int experience, Objective objective, RaidRelic relic) {
        this.title = title;
        this.amount = amount;
        this.credits = credits;
        this.experience = experience;
        this.objective = objective;
        this.relic = relic;
    }

    SupplyContract(RaidRelic relic, int amount) {
        this(relic.title, amount, relic.price * amount + relic.price / 3, 12 + 8 * relic.tier, Objective.RELIC, relic);
    }

    public Item icon() {
        return relic == null ? (objective == Objective.EXIT ? Items.COMPASS : Items.IRON_SWORD) : relic.item();
    }

    public String description() {
        return switch (objective) {
            case KILL -> "Éliminer " + amount + " ennemis en raid";
            case ASSAULT -> "Éliminer " + amount + " gardes avec un AK-47 ou un M4A1 TaCZ";
            case ELITE -> "Éliminer " + amount + " ennemis d’élite";
            case BOSS -> "Éliminer Le Traqueur";
            case EXIT -> "Réussir " + amount + (amount == 1 ? " extraction" : " extractions");
            case COMBAT_EXIT -> "S’extraire après " + amount + " éliminations dans le même raid";
            case INDUSTRIAL_EXIT -> "Réussir " + amount + (amount == 1 ? " extraction" : " extractions") + " de la Zone industrielle";
            case BOSS_INTEL -> "Abattre Le Traqueur et extraire une puce de commandement";
            case RELIC -> "Extraire avec " + amount + " × " + relic.title;
        };
    }

    static CompoundTag account(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag account = persisted.getCompound("taczoperations");
        persisted.put("taczoperations", account);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        return account;
    }

    static int balance(ServerPlayer player) { return account(player).getInt("credits"); }

    static int experienceEarned(ServerPlayer player) {
        CompoundTag account = account(player);
        int earned = account.getInt("raid_activity_xp")
            + (account.getInt("OUTPOST") == 2 ? 8 : 0) + (account.getInt("BARRICADE") == 2 ? 12 : 0)
            + (account.getInt("WORKSHOP") == 2 ? 18 : 0);
        for (var contract : values()) if (contract.status(player) == COMPLETED) earned += contract.experience;
        return earned;
    }

    // Escape from Minecraft progression XP: distinct from vanilla experience.
    // Used by the existing progression and shop-rank display.
    static void activityXp(ServerPlayer player, int amount, String source) {
        if (amount <= 0 || !ForestRaid.isParticipant(player)) return;
        CompoundTag data = account(player);
        data.putInt("raid_activity_xp",
            (int) Math.min(Integer.MAX_VALUE, (long) data.getInt("raid_activity_xp") + amount));
        String counter = amount == 5 ? "raid_kill_xp" : "raid_search_xp";
        data.putInt(counter,
            (int) Math.min(Integer.MAX_VALUE, (long) data.getInt(counter) + amount));
        String runCounter = amount == 5 ? "taczoperations_run_kill_xp" : "taczoperations_run_search_xp";
        CompoundTag run = player.getPersistentData();
        run.putInt(runCounter, (int) Math.min(Integer.MAX_VALUE,
            (long) run.getInt(runCounter) + amount));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "+" + amount + " XP Escape from Minecraft · " + source), true);
    }

    static boolean showExtraction(ServerPlayer player) { return !account(player).getBoolean("hide_extraction"); }
    static void toggleExtraction(ServerPlayer player) { account(player).putBoolean("hide_extraction", showExtraction(player)); }

    int status(ServerPlayer player) { return account(player).getInt("raid_" + name()); }
    int progress(ServerPlayer player) { return account(player).getInt("progress_" + name()); }

    String act(ServerPlayer player) {
        if (status(player) == COMPLETED) return "Ce contrat est déjà terminé.";
        if (status(player) == ACTIVE) {
            account(player).remove("raid_" + name());
            account(player).remove("progress_" + name());
            return "Contrat abandonné. Tu peux le reprendre plus tard.";
        }
        int active = 0;
        for (var contract : values()) if (contract.status(player) == ACTIVE) active++;
        if (active >= 3) return "Tu as déjà trois contrats actifs.";
        account(player).putInt("raid_" + name(), ACTIVE);
        return "Contrat accepté. Les récompenses sont versées automatiquement.";
    }

    static void kill(ServerPlayer player, boolean elite, boolean boss) {
        if (!ForestRaid.isParticipant(player)) return;
        CompoundTag data = player.getPersistentData();
        data.putInt("taczoperations_raid_kills", data.getInt("taczoperations_raid_kills") + 1);
        if (boss) data.putBoolean("taczoperations_raid_boss", true);
        for (var contract : values()) {
            if (contract.status(player) == ACTIVE && (contract.objective == Objective.KILL
                || (elite && contract.objective == Objective.ELITE) || (boss && contract.objective == Objective.BOSS))) {
                contract.advance(player);
            }
        }
    }

    public static void assaultKill(com.tacz.guns.api.event.common.EntityKillByGunEvent event) {
        if (event.getLogicalSide().isClient() || !(event.getAttacker() instanceof ServerPlayer player)
            || !(event.getKilledEntity() instanceof net.minecraft.world.entity.monster.Pillager guard)
            || !(guard.level() instanceof net.minecraft.server.level.ServerLevel level)
            || !isAssaultRifle(event.getGunId()) || !ForestRaid.isParticipant(player)
            || !ForestRaid.currentGuard(level, guard) || ASSAULT.status(player) != ACTIVE
            || guard.getPersistentData().getBoolean("taczoperations_assault_counted")) {
            return;
        }
        guard.getPersistentData().putBoolean("taczoperations_assault_counted", true);
        ASSAULT.advance(player);
    }

    private static boolean isAssaultRifle(net.minecraft.resources.ResourceLocation gun) {
        return gun != null && gun.getNamespace().equals("tacz")
            && (gun.getPath().equals("ak47") || gun.getPath().equals("m4a1"));
    }

    static void departure(ServerPlayer player) {
        player.getPersistentData().remove("taczoperations_raid_kills");
        player.getPersistentData().remove("taczoperations_raid_boss");
        player.getPersistentData().putInt("taczoperations_run_kill_xp", 0);
        player.getPersistentData().putInt("taczoperations_run_search_xp", 0);
        CompoundTag baseline = new CompoundTag();
        for (RaidRelic relic : RaidRelic.values()) {
            baseline.putInt(relic.id, count(player, relic.item()));
        }
        player.getPersistentData().put("taczoperations_relic_baseline", baseline);
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    static void extracted(ServerPlayer player, boolean industrial) {
        int kills = player.getPersistentData().getInt("taczoperations_raid_kills");
        CompoundTag baseline = player.getPersistentData().getCompound("taczoperations_relic_baseline");
        for (var contract : values()) {
            if (contract.status(player) != ACTIVE) continue;
            if (contract.objective == Objective.EXIT || industrial && contract.objective == Objective.INDUSTRIAL_EXIT) {
                contract.advance(player);
            }
            if (contract.objective == Objective.COMBAT_EXIT && kills >= contract.amount) contract.finish(player);
            if ((contract.objective == Objective.RELIC
                || contract.objective == Objective.BOSS_INTEL && player.getPersistentData().getBoolean("taczoperations_raid_boss"))
                && baseline.contains(contract.relic.id)
                && count(player, contract.relic.item()) - baseline.getInt(contract.relic.id) >= contract.amount
                && consume(player, contract.relic.item(), contract.amount)) {
                contract.finish(player);
            }
        }
        int killXp = player.getPersistentData().getInt("taczoperations_run_kill_xp");
        int searchXp = player.getPersistentData().getInt("taczoperations_run_search_xp");
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Bilan Escape · " + (killXp + searchXp) + " XP (combats : "
                + killXp + ", fouilles : " + searchXp + ")"));
        player.getPersistentData().remove("taczoperations_run_kill_xp");
        player.getPersistentData().remove("taczoperations_run_search_xp");
        player.getPersistentData().remove("taczoperations_raid_kills");
        player.getPersistentData().remove("taczoperations_raid_boss");
        player.getPersistentData().remove("taczoperations_relic_baseline");
    }

    static boolean consume(ServerPlayer player, Item item, int amount) {
        var inventory = player.getInventory();
        int count = inventory.items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
        if (count < amount) return false;
        int remaining = amount;
        for (ItemStack stack : inventory.items) {
            if (!stack.is(item)) continue;
            int used = Math.min(remaining, stack.getCount());
            stack.shrink(used);
            remaining -= used;
            if (remaining == 0) break;
        }
        inventory.setChanged();
        return true;
    }

    private void advance(ServerPlayer player) {
        int next = progress(player) + 1;
        account(player).putInt("progress_" + name(), next);
        if (next >= amount) finish(player);
    }

    private void finish(ServerPlayer player) {
        CompoundTag account = account(player);
        account.putInt("raid_" + name(), COMPLETED);
        account.putInt("progress_" + name(), amount);
        account.putInt("credits", Math.min(Integer.MAX_VALUE - credits, balance(player)) + credits);
        player.giveExperiencePoints(experience);
        if (objective == Objective.BOSS || this == OMEGA) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.GOLDEN_APPLE, 2));
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Contrat terminé · " + title + " · +" + credits + " crédits / +" + experience + " XP"));
    }
}
