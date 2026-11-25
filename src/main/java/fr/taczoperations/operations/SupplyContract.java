package fr.taczoperations.operations;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum SupplyContract {
    CLEAR("Nettoyage", 5, 250, 15, Objective.KILL, null),
    PATROL("Patrouille hostile", 12, 720, 34, Objective.KILL, null),
    WARZONE("Zone de guerre", 25, 1650, 65, Objective.KILL, null),
    SKIRMISH("Escarmouche", 3, 170, 8, Objective.KILL, null),
    SCAVENGER("Récupération rapide", 3, 150, 8, Objective.CHEST, null),
    SEARCH_PARTY("Inspection de secteur", 8, 420, 22, Objective.CHEST, null),
    DEEP_SEARCH("Fouille méthodique", 20, 1150, 52, Objective.CHEST, null),
    INTERCEPTION("Interception", 8, 470, 24, Objective.KILL, null),
    SIEGE("Siège prolongé", 40, 2600, 110, Objective.KILL, null),
    VETERAN_PATROL("Patrouille vétéran", 2, 420, 20, Objective.ELITE, null),
    ELITE_SWEEP("Escouade d'élite", 12, 2500, 105, Objective.ELITE, null),
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
    SAFE_RETURN("Retour sécurisé", 2, 330, 18, Objective.EXIT, null),
    EXTRACTOR("Extraction professionnelle", 8, 2100, 95, Objective.EXIT, null),
    FACTORY_SHIFT("Quart de nuit industriel", 2, 600, 32, Objective.INDUSTRIAL_EXIT, null),
    COMBAT_EXIT("Sortie sous pression", 8, 850, 45, Objective.COMBAT_EXIT, null),
    INDUSTRY("Fret abandonné", 1, 350, 22, Objective.INDUSTRIAL_EXIT, null),
    DECAPITATION("Code du Traqueur", 1, 5200, 80, Objective.BOSS_INTEL, RaidRelic.CHIP),
    QUICK_ESCAPE("Opération éclair", 2, 320, 16, Objective.COMBAT_EXIT, null),
    RECON("Reconnaissance approfondie", 5, 290, 15, Objective.CHEST, null),
    ELITE_OFFICERS("Officiers hostiles", 5, 980, 42, Objective.ELITE, null),
    INDUSTRIAL_VETERAN("Spécialiste industriel", 4, 1350, 55, Objective.INDUSTRIAL_EXIT, null),
    RADIO_CACHE("Réseau de communication", 4, 680, 30, Objective.RELIC, RaidRelic.RADIO),
    INTEL_CACHE("Archives sensibles", 3, 1100, 45, Objective.RELIC, RaidRelic.DOCUMENTS),
    CIRCUIT_CACHE("Composants stratégiques", 2, 1650, 55, Objective.RELIC, RaidRelic.CIRCUIT),
    BOUNTY("Prime du Traqueur", 3, 2700, 110, Objective.BOSS, null),
    EXTRA_KILL_1("Premier contact", 1, 60, 3, Objective.KILL, null),
    EXTRA_KILL_2("Double menace", 2, 120, 6, Objective.KILL, null),
    EXTRA_KILL_6("Route dégagée", 6, 360, 18, Objective.KILL, null),
    EXTRA_KILL_10("Dix cibles", 10, 600, 30, Objective.KILL, null),
    EXTRA_KILL_18("Secteur contesté", 18, 1080, 54, Objective.KILL, null),
    EXTRA_KILL_30("Offensive", 30, 1800, 90, Objective.KILL, null),
    EXTRA_KILL_50("Campagne punitive", 50, 3000, 150, Objective.KILL, null),
    EXTRA_KILL_75("Front hostile", 75, 4500, 225, Objective.KILL, null),
    EXTRA_KILL_100("Centurion", 100, 6000, 300, Objective.KILL, null),
    EXTRA_KILL_150("Guerre d’usure", 150, 9000, 450, Objective.KILL, null),
    EXTRA_CHEST_1("Premier ravitaillement", 1, 58, 3, Objective.CHEST, null),
    EXTRA_CHEST_2("Deux caches", 2, 116, 6, Objective.CHEST, null),
    EXTRA_CHEST_4("Inventaire de terrain", 4, 232, 12, Objective.CHEST, null),
    EXTRA_CHEST_6("Recherche locale", 6, 348, 18, Objective.CHEST, null),
    EXTRA_CHEST_10("Prospection", 10, 580, 30, Objective.CHEST, null),
    EXTRA_CHEST_12("Collecte avancée", 12, 696, 36, Objective.CHEST, null),
    EXTRA_CHEST_15("Quadrillage", 15, 870, 45, Objective.CHEST, null),
    EXTRA_CHEST_25("Expédition logistique", 25, 1450, 75, Objective.CHEST, null),
    EXTRA_CHEST_35("Grand inventaire", 35, 2030, 105, Objective.CHEST, null),
    EXTRA_CHEST_50("Maître récupérateur", 50, 2900, 150, Objective.CHEST, null),
    EXTRA_ELITE_1("Cible prioritaire", 1, 205, 9, Objective.ELITE, null),
    EXTRA_ELITE_4("Escorte neutralisée", 4, 820, 36, Objective.ELITE, null),
    EXTRA_ELITE_6("Chasse spécialisée", 6, 1230, 54, Objective.ELITE, null),
    EXTRA_ELITE_8("Force spéciale", 8, 1640, 72, Objective.ELITE, null),
    EXTRA_ELITE_10("Dix vétérans", 10, 2050, 90, Objective.ELITE, null),
    EXTRA_ELITE_15("Commandement hostile", 15, 3075, 135, Objective.ELITE, null),
    EXTRA_ELITE_20("Élite brisée", 20, 4100, 180, Objective.ELITE, null),
    EXTRA_ELITE_30("Chasseur de légendes", 30, 6150, 270, Objective.ELITE, null),
    EXTRA_EXIT_4("Retour régulier", 4, 940, 48, Objective.EXIT, null),
    EXTRA_EXIT_5("Cinq retours", 5, 1175, 60, Objective.EXIT, null),
    EXTRA_EXIT_6("Survivant confirmé", 6, 1410, 72, Objective.EXIT, null),
    EXTRA_EXIT_10("Dix extractions", 10, 2350, 120, Objective.EXIT, null),
    EXTRA_EXIT_15("Route sûre", 15, 3525, 180, Objective.EXIT, null),
    EXTRA_EXIT_20("Vétéran du terrain", 20, 4700, 240, Objective.EXIT, null),
    EXTRA_EXIT_30("Survivant légendaire", 30, 7050, 360, Objective.EXIT, null),
    EXTRA_INDUSTRIAL_EXIT_5("Rotation industrielle", 5, 1650, 75, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_INDUSTRIAL_EXIT_8("Logistique lourde", 8, 2640, 120, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_INDUSTRIAL_EXIT_10("Dix rotations", 10, 3300, 150, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_INDUSTRIAL_EXIT_15("Circuit des usines", 15, 4950, 225, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_INDUSTRIAL_EXIT_20("Maître du dépôt", 20, 6600, 300, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_COMBAT_EXIT_1("Sortie mouvementée", 1, 95, 5, Objective.COMBAT_EXIT, null),
    EXTRA_COMBAT_EXIT_3("Dernière patrouille", 3, 285, 15, Objective.COMBAT_EXIT, null),
    EXTRA_COMBAT_EXIT_5("Percée", 5, 475, 25, Objective.COMBAT_EXIT, null),
    EXTRA_COMBAT_EXIT_6("Évacuation hostile", 6, 570, 30, Objective.COMBAT_EXIT, null),
    EXTRA_COMBAT_EXIT_10("Extraction offensive", 10, 950, 50, Objective.COMBAT_EXIT, null),
    EXTRA_COMBAT_EXIT_12("Mission à haut risque", 12, 1140, 60, Objective.COMBAT_EXIT, null),
    EXTRA_ASSAULT_3("Tir de couverture", 3, 210, 12, Objective.ASSAULT, null),
    EXTRA_ASSAULT_5("Fusilier", 5, 350, 20, Objective.ASSAULT, null),
    EXTRA_ASSAULT_15("Assaut coordonné", 15, 1050, 60, Objective.ASSAULT, null),
    EXTRA_ASSAULT_25("Force de frappe", 25, 1750, 100, Objective.ASSAULT, null),
    EXTRA_ASSAULT_40("Infanterie lourde", 40, 2800, 160, Objective.ASSAULT, null),
    EXTRA_ASSAULT_60("Spécialiste du fusil", 60, 4200, 240, Objective.ASSAULT, null),
    EXTRA_BOSS_2("Double traque", 2, 1800, 76, Objective.BOSS, null),
    EXTRA_BOSS_5("Chasseur de primes", 5, 4500, 190, Objective.BOSS, null),
    EXTRA_BOSS_10("Némésis du Traqueur", 10, 9000, 380, Objective.BOSS, null),
    EXTRA_KILL_7("Neutralisation · 7", 7, 455, 21, Objective.KILL, null),
    EXTRA_ELITE_9("Chasse spécialisée · 9", 9, 1845, 81, Objective.ELITE, null),
    EXTRA_INDUSTRIAL_EXIT_7("Mission industrielle · 7", 7, 2310, 105, Objective.INDUSTRIAL_EXIT, null),
    EXTRA_COMBAT_EXIT_20("Extraction offensive · 20", 20, 2200, 100, Objective.COMBAT_EXIT, null),
    EXTRA_ASSAULT_20("Maîtrise du fusil · 20", 20, 1400, 80, Objective.ASSAULT, null),
    EXTRA_ASSAULT_30("Maîtrise du fusil · 30", 30, 2100, 120, Objective.ASSAULT, null),
    EXTRA_ASSAULT_50("Maîtrise du fusil · 50", 50, 3500, 200, Objective.ASSAULT, null),
    EXTRA_ASSAULT_75("Maîtrise du fusil · 75", 75, 5250, 300, Objective.ASSAULT, null),
    EXTRA_BOSS_4("Traque prioritaire · 4", 4, 3600, 152, Objective.BOSS, null),
    EXTRA_BOSS_8("Traque prioritaire · 8", 8, 7200, 304, Objective.BOSS, null),
    TAGS(RaidRelic.PLAQUE, 3), RADIOS(RaidRelic.RADIO, 2), PAPERS(RaidRelic.DOCUMENTS, 1),
    WATCH(RaidRelic.WATCH, 1), BOARD(RaidRelic.CIRCUIT, 1), ENCRYPTED(RaidRelic.DRIVE, 1),
    SUITCASE(RaidRelic.CASE, 1), OPTIC(RaidRelic.OPTIC, 1), COMMAND(RaidRelic.CHIP, 1), OMEGA(RaidRelic.OMEGA, 1);

    private enum Objective { KILL, CHEST, ASSAULT, ELITE, BOSS, EXIT, COMBAT_EXIT, INDUSTRIAL_EXIT, BOSS_INTEL, RELIC }

    public static final int AVAILABLE = 0;
    public static final int ACTIVE = 1;
    public static final int COMPLETED = 2;
    public final String title;
    public final int amount;
    public final int credits;
    public final int experience;
    private final int legacyExperience;
    private final Objective objective;
    private final RaidRelic relic;

    SupplyContract(String title, int amount, int credits, int experience, Objective objective, RaidRelic relic) {
        this.title = title;
        this.amount = amount;
        this.credits = credits;
        this.legacyExperience = experience;
        this.experience = experience * 3;
        this.objective = objective;
        this.relic = relic;
    }

    SupplyContract(RaidRelic relic, int amount) {
        this(relic.title, amount, relic.price * amount + relic.price / 3, 12 + 8 * relic.tier, Objective.RELIC, relic);
    }

    public OperationsShop merchant() {
        return switch (objective) {
            case KILL, ELITE, ASSAULT, BOSS -> OperationsShop.ARMORER;
            case EXIT, COMBAT_EXIT -> OperationsShop.MEDIC;
            case CHEST, INDUSTRIAL_EXIT -> OperationsShop.SALVAGER;
            case RELIC, BOSS_INTEL -> OperationsShop.BROKER;
        };
    }

    public int reputationReward() { return difficulty() * 2; }

    public int category() {
        return switch (objective) {
            case KILL, ELITE, ASSAULT -> 1;
            case CHEST -> 2;
            case EXIT, COMBAT_EXIT, INDUSTRIAL_EXIT -> 3;
            case RELIC -> 4;
            case BOSS, BOSS_INTEL -> 5;
        };
    }

    public int difficulty() {
        return credits < 400 ? 1 : credits < 900 ? 2 : credits < 1800 ? 3 : credits < 3500 ? 4 : 5;
    }

    public Item icon() {
        return relic != null ? relic.item() : switch (objective) { case CHEST -> Items.CHEST; case EXIT, COMBAT_EXIT, INDUSTRIAL_EXIT -> Items.COMPASS; default -> Items.IRON_SWORD; };
    }

    public String description() {
        return switch (objective) {
            case KILL -> "Éliminer " + amount + " ennemis en raid";
            case CHEST -> "Fouiller " + amount + " coffres de raid différents";
            case ASSAULT -> "Éliminer " + amount + " gardes avec un AK-47 ou un M4A1 TaCZ";
            case ELITE -> "Éliminer " + amount + " ennemis d’élite";
            case BOSS -> "Éliminer Le Traqueur" + (amount > 1 ? " " + amount + " fois" : "");
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
        long earned = (long) Math.max(0, account.getInt("raid_activity_xp")) + Math.max(0, account.getInt("contract_cycle_xp"))
            + Math.max(0, account.getInt("contract_xp_bonus_v2"))
            + Math.max(0, account.getInt("timed_contract_xp"))
            + (account.getInt("OUTPOST") == 2 ? 8 : 0)
            + (account.getInt("BARRICADE") == 2 ? 12 : 0)
            + (account.getInt("WORKSHOP") == 2 ? 18 : 0);
        for (var contract : values()) {
            int times = account.contains("completed_" + contract.name())
                ? account.getInt("completed_" + contract.name())
                : (contract.status(player) == COMPLETED ? 1 : 0);
            earned = Math.min(Integer.MAX_VALUE,
                earned + (long) contract.legacyExperience * Math.max(0, times));
        }
        return (int) Math.min(Integer.MAX_VALUE, earned);
    }

    // Escape from Minecraft progression XP: distinct from vanilla experience.
    // Used by the existing progression and shop-rank display.
    static void activityXp(ServerPlayer player, int amount, boolean combat, String source) {
        if (amount <= 0 || !ForestRaid.isParticipant(player)) return;
        CompoundTag data = account(player);
        // Base XP is provisional until extraction or death applies its multiplier.
        data.putInt("raid_activity_xp",
            (int) Math.min(Integer.MAX_VALUE, (long) data.getInt("raid_activity_xp") + amount));
        String counter = combat ? "raid_kill_xp" : "raid_search_xp";
        data.putInt(counter,
            (int) Math.min(Integer.MAX_VALUE, (long) data.getInt(counter) + amount));
        String runCounter = combat ? "taczoperations_run_kill_xp" : "taczoperations_run_search_xp";
        CompoundTag run = player.getPersistentData();
        run.putInt(runCounter, (int) Math.min(Integer.MAX_VALUE,
            (long) run.getInt(runCounter) + amount));
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
            "+" + amount + " XP Escape from Minecraft · " + source), true);
    }

    static int finalizeRaidXp(ServerPlayer player, CompoundTag run, boolean success) {
        long base = (long) Math.max(0, run.getInt("taczoperations_run_kill_xp"))
            + Math.max(0, run.getInt("taczoperations_run_search_xp"));
        if (run.getBoolean("taczoperations_run_xp_finalized")) return 0;
        run.putBoolean("taczoperations_run_xp_finalized", true);
        long adjusted = Math.min(Integer.MAX_VALUE, (base * (success ? 120 : 90) + 50) / 100);
        int delta = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, adjusted - base));
        CompoundTag data = account(player);
        data.putInt("raid_activity_xp", (int) Math.max(0,
            Math.min(Integer.MAX_VALUE, (long) data.getInt("raid_activity_xp") + delta)));
        rewardLevels(player);
        return delta;
    }

    // Saved after each raid so a future result screen can reopen the last report.
    static void saveRaidReport(ServerPlayer player, boolean success, long seconds,
                               int kills, int crates, int contracts, int killXp, int searchXp, int contractXp, int adjustment, int xpBefore) {
        int[] timed=TimedContracts.record(player,success,kills,crates);
        contracts=(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,contracts)+timed[0]);
        contractXp=(int)Math.min(Integer.MAX_VALUE,(long)Math.max(0,contractXp)+timed[1]);
        rewardLevels(player);
        CompoundTag report = new CompoundTag();
        report.putBoolean("success", success);
        report.putLong("duration_seconds", Math.max(0, seconds));
        report.putInt("kills", Math.max(0, kills));
        report.putInt("crates", Math.max(0, crates));
        report.putInt("contracts", Math.max(0, contracts));
        report.putInt("combat_xp", Math.max(0, killXp));
        report.putInt("search_xp", Math.max(0, searchXp));
        report.putInt("contract_xp", Math.max(0, contractXp));
        report.putInt("adjustment_xp", adjustment);
        report.putInt("final_xp", (int) Math.min(Integer.MAX_VALUE,
            Math.max(0L, (long) killXp + searchXp + contractXp + adjustment)));
        int xpAfter = experienceEarned(player);
        report.putInt("xp_before", xpBefore < 0 ? -1 : Math.min(xpBefore,xpAfter));
        report.putInt("xp_after", xpAfter);
        if (xpBefore >= 0) report.putInt("final_xp", Math.max(0,xpAfter-xpBefore));
        CompoundTag data = account(player);
        var totals = raidTotals(player);
        addRaidTotals(totals, report);
        data.put("raid_totals_v1",totals);
        data.put("last_raid_report", report);
        data.putBoolean("pending_raid_report", true);
        // Retain the ten most recent runs, newest first, for a future history tab.
        ListTag previous = data.getList("raid_history", 10);
        ListTag history = new ListTag();
        history.add(report.copy());
        for (int i = 0; i < previous.size() && i < 9; i++) {
            history.add(previous.getCompound(i).copy());
        }
        data.put("raid_history", history);
    }

    // Migrate the available ten-raid history once. Earlier records are not reconstructible.
    static CompoundTag raidTotals(ServerPlayer player) {
        var data=account(player);
        if(data.contains("raid_totals_v1"))return data.getCompound("raid_totals_v1");
        var totals=new CompoundTag();
        var history=data.getList("raid_history",10);
        for(int i=0;i<history.size();i++)addRaidTotals(totals,history.getCompound(i));
        data.put("raid_totals_v1",totals);
        return totals;
    }
    private static void addRaidTotals(CompoundTag totals,CompoundTag report) {
        incrementTotal(totals,"raids",1);
        incrementTotal(totals,report.getBoolean("success")?"wins":"losses",1);
        incrementTotal(totals,"kills",report.getInt("kills"));
        incrementTotal(totals,"crates",report.getInt("crates"));
        incrementTotal(totals,"contracts",report.getInt("contracts"));
        incrementTotal(totals,"seconds",report.getLong("duration_seconds"));
        incrementTotal(totals,"xp",report.getInt("final_xp"));
        totals.putLong("best_kills",Math.max(totals.getLong("best_kills"),report.getInt("kills")));
    }
    private static void incrementTotal(CompoundTag totals,String key,long value) {
        long current=Math.max(0L,totals.getLong(key));
        totals.putLong(key,current+Math.min(Long.MAX_VALUE-current,Math.max(0L,value)));
    }

    static boolean showExtraction(ServerPlayer player) { return !account(player).getBoolean("hide_extraction"); }
    static void toggleExtraction(ServerPlayer player) { account(player).putBoolean("hide_extraction", showExtraction(player)); }

    int status(ServerPlayer player) { return account(player).getInt("raid_" + name()); }
    int progress(ServerPlayer player) { return account(player).getInt("progress_" + name()); }

    String act(ServerPlayer player) {
        if (status(player) == COMPLETED) return "Contrat déjà terminé pour ce cycle.";
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

    static void chestOpened(ServerPlayer player) {
        if (!ForestRaid.isParticipant(player)) return;
        for (var contract : values()) {
            if (contract.objective == Objective.CHEST && contract.status(player) == ACTIVE) {
                contract.advance(player);
            }
        }
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
            || !ForestRaid.currentGuard(level, guard) 
            || guard.getPersistentData().getBoolean("taczoperations_assault_counted")) {
            return;
        }
        guard.getPersistentData().putBoolean("taczoperations_assault_counted", true);
        for (var contract : values()) {
            if (contract.objective == Objective.ASSAULT && contract.status(player) == ACTIVE) contract.advance(player);
        }
    }

    private static boolean isAssaultRifle(net.minecraft.resources.ResourceLocation gun) {
        return gun != null && gun.getNamespace().equals("tacz")
            && (gun.getPath().equals("ak47") || gun.getPath().equals("m4a1"));
    }

    static void departure(ServerPlayer player) {
        player.getPersistentData().remove("taczoperations_raid_kills");
        player.getPersistentData().remove("taczoperations_raid_boss");
        player.getPersistentData().remove("taczoperations_run_xp_finalized");
        player.getPersistentData().putInt("taczoperations_run_kill_xp", 0);
        player.getPersistentData().putInt("taczoperations_run_search_xp", 0);
        player.getPersistentData().putInt("taczoperations_run_crates", 0);
        player.getPersistentData().putInt("taczoperations_run_contracts", 0);
        player.getPersistentData().putInt("taczoperations_run_contract_xp", 0);
        player.getPersistentData().putInt("taczoperations_run_xp_before", experienceEarned(player));
        player.getPersistentData().putLong("taczoperations_run_started", player.server.overworld().getGameTime());
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
        int bonus = finalizeRaidXp(player, player.getPersistentData(), true);
        int crates = player.getPersistentData().getInt("taczoperations_run_crates");
        long started = player.getPersistentData().getLong("taczoperations_run_started");
        long seconds = started > 0 ? Math.max(0, (player.server.overworld().getGameTime() - started) / 20) : 0;
        int completed = player.getPersistentData().getInt("taczoperations_run_contracts");
        int contractXp = player.getPersistentData().getInt("taczoperations_run_contract_xp");
        saveRaidReport(player, true, seconds, kills, crates, completed, killXp, searchXp, contractXp, bonus,
            player.getPersistentData().contains("taczoperations_run_xp_before") ? player.getPersistentData().getInt("taczoperations_run_xp_before") : -1);
        // The persistent report is delivered by the player tick after the dimension transition.
        // The graphical report is authoritative; keep chat concise.
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Extraction réussie · +" + ((long) killXp + searchXp + contractXp + bonus)
                + " XP Escape (bonus +" + bonus + "). Bilan enregistré."));
        player.getPersistentData().remove("taczoperations_run_kill_xp");
        player.getPersistentData().remove("taczoperations_run_search_xp");
        player.getPersistentData().remove("taczoperations_run_crates");
        player.getPersistentData().remove("taczoperations_run_contracts");
        player.getPersistentData().remove("taczoperations_run_contract_xp");
        player.getPersistentData().remove("taczoperations_run_started");
        player.getPersistentData().remove("taczoperations_run_xp_before");
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
        if (status(player) != ACTIVE) return;
        CompoundTag account = account(player);
        account.putInt("raid_" + name(), COMPLETED);
        account.putInt("completed_" + name(), (int) Math.min(Integer.MAX_VALUE, (long) account.getInt("completed_" + name()) + 1));
        // Preserve past XP: only completions after this update earn the new bonus.
        account.putInt("contract_xp_bonus_v2", (int) Math.min(Integer.MAX_VALUE,
            (long) Math.max(0, account.getInt("contract_xp_bonus_v2")) + experience - legacyExperience));
        // Extraction objectives finish after teleport, when the raid token is gone.
        // The run marker keeps these completions in the final raid report.
        if (player.getPersistentData().contains("taczoperations_run_started")) {
            CompoundTag run = player.getPersistentData();
            run.putInt("taczoperations_run_contracts", run.getInt("taczoperations_run_contracts") + 1);
            trackContractXp(player, experience);
        }
        account.putInt("progress_" + name(), amount);
        account.putInt("credits", Math.min(Integer.MAX_VALUE - credits, balance(player)) + credits);
        player.giveExperiencePoints(legacyExperience);
        if (objective == Objective.BOSS || this == OMEGA) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.GOLDEN_APPLE, 2));
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
            "Contrat terminé · " + title + " · +" + credits + " crédits / +" + experience + " XP"));
        renewCycle(player);
        rewardLevels(player);
    }

    private static void trackContractXp(ServerPlayer player, int amount) {
        CompoundTag run = player.getPersistentData();
        if (!run.contains("taczoperations_run_started")) return;
        run.putInt("taczoperations_run_contract_xp", (int)Math.min(Integer.MAX_VALUE,
            (long)Math.max(0, run.getInt("taczoperations_run_contract_xp")) + amount));
    }

    static void rewardLevels(ServerPlayer player) {
        CompoundTag run = player.getPersistentData();
        // Never award a level based on provisional raid XP before the death/extraction adjustment.
        if (run.contains("taczoperations_run_started") && !run.getBoolean("taczoperations_run_xp_finalized")) return;
        CompoundTag data = account(player);
        int reached = MilitaryProgression.level(experienceEarned(player));
        int previous = Math.max(1, data.getInt("military_rewarded_level"));
        if (reached <= previous) return;
        long credits = 0;
        for (int l = previous + 1; l <= reached; l++) credits += MilitaryProgression.reward(l);
        data.putInt("military_rewarded_level", reached);
        for (int l = previous + 1; l <= reached; l++) {
            if (l == 200) player.getInventory().placeItemBackInInventory(RaidRelic.OMEGA.stack());
            else if (l % 25 == 0) player.getInventory().placeItemBackInInventory(RaidRelic.OPTIC.stack());
            else if (l % 10 == 0) player.getInventory().placeItemBackInInventory(new ItemStack(Items.GOLDEN_APPLE, 2));
            else if (l % 5 == 0) player.getInventory().placeItemBackInInventory(new ItemStack(Items.IRON_INGOT, 4));
        }
        data.putInt("credits", (int)Math.min(Integer.MAX_VALUE, balance(player) + credits));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Progression militaire : " + MilitaryProgression.label(experienceEarned(player)) + " · +" + credits + " crédits"));
    }

    static int cyclesCompleted(ServerPlayer player) { return account(player).getInt("contract_cycles"); }

    static int cycleProgress(ServerPlayer player) {
        int count = 0;
        for (var contract : values()) if (contract.status(player) == COMPLETED) count++;
        return count;
    }

    static void renewCycle(ServerPlayer player) {
        if (cycleProgress(player) != values().length) return;
        CompoundTag data = account(player);
        for (var contract : values()) {
            String key = "completed_" + contract.name();
            if (!data.contains(key)) data.putInt(key, 1);
            data.remove("raid_" + contract.name());
            data.remove("progress_" + contract.name());
        }
        data.putInt("contract_cycles", (int) Math.min(Integer.MAX_VALUE, (long) cyclesCompleted(player) + 1));
        data.putInt("contract_cycle_xp", (int) Math.min(Integer.MAX_VALUE, (long) data.getInt("contract_cycle_xp") + 2500));
        trackContractXp(player, 2500);
        data.putInt("credits", (int) Math.min(Integer.MAX_VALUE, (long) balance(player) + 25000));
        player.getInventory().placeItemBackInInventory(RaidRelic.OPTIC.stack());
        player.getInventory().placeItemBackInInventory(new ItemStack(Items.GOLDEN_APPLE, 4));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Cycle terminé ! +25 000 crédits / +2 500 XP. Nouveau cycle disponible."));
    }
}
