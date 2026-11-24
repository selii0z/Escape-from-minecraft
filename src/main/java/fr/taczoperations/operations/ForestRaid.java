package fr.taczoperations.operations;

import fr.taczoperations.OperationsMod;
import fr.taczoperations.entity.GuardCorpse;
import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.ForestRaidAnchors;
import fr.taczoperations.worldgen.IndustrialZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ForestRaid extends SavedData {
    static final String ENEMY = "taczoperations_enemy";
    private static final String PLAYER = "taczoperations_raid_player";
    private static final String CORPSE = "taczoperations_raid_corpse";
    private static final String DROP = "taczoperations_raid_drop";
    private static final String CRATE = "taczoperations_raid_crate";
    private static final String CRATE_INDEX = "taczoperations_raid_crate_index";
    private static final String AIRDROP_VISUAL = "taczoperations_airdrop_visual";
    private static final Factory<ForestRaid> FACTORY = new Factory<>(ForestRaid::new, ForestRaid::read);

    private static final long RECONNECT_GRACE_MS = 10 * 60 * 1000L;
    private final Set<UUID> participants = new HashSet<>();
    private long offlineSince;
    private UUID id;
    private long seed;
    private boolean industrial;
    private long dropDue, dropLanding;
    private int dropAttempts;
    private BlockPos dropPos;
    private UUID dropVisual;
    private boolean dropLanded;
    private BitSet crates = new BitSet();
    private BitSet enemies = new BitSet();
    private BitSet pmcGroups = new BitSet();
    private List<ForestRaidAnchors.Mark> locations = List.of();

    private static ForestRaid get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "taczoperations_raid");
    }

    static void begin(ServerLevel level, ServerPlayer player, ServerPlayer partner, boolean industrial) {
        ForestRaid raid = get(level);
        // A previous raid may have left corpses in loaded chunks. Remove them
        // before assigning a new session ID, so their inventories cannot be looted.
        java.util.List<net.minecraft.world.entity.Entity> expired = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GuardCorpse || entity instanceof ItemEntity
                || entity.getPersistentData().hasUUID(AIRDROP_VISUAL)
                || entity instanceof Pillager guard && guard.getPersistentData().hasUUID(ENEMY)) {
                expired.add(entity);
            }
        }
        expired.forEach(net.minecraft.world.entity.Entity::discard);
        raid.id = UUID.randomUUID();
        raid.participants.clear();
        raid.participants.add(player.getUUID());
        if (partner != null) raid.participants.add(partner.getUUID());
        raid.offlineSince = 0;
        raid.seed = level.getRandom().nextLong();
        raid.industrial = industrial;
        raid.crates.clear();
        raid.enemies.clear();
        raid.pmcGroups = assignFactions(industrial ? IndustrialZone.GUARDS : ForestRaidAnchors.ENEMIES, raid.seed);
        raid.dropDue = level.getGameTime() + 20L * (45 + new Random(raid.seed).nextInt(46));
        raid.dropLanding = 0;
        raid.dropAttempts = 0;
        raid.dropPos = null;
        raid.dropVisual = null;
        raid.dropLanded = false;
        raid.locations = raid.industrial ? IndustrialZone.loot(raid.seed) : ForestRaidAnchors.pickLoot(raid.seed);
        player.getPersistentData().putUUID(PLAYER, raid.id);
        SupplyContract.departure(player);
        if (partner != null) {
            partner.getPersistentData().putUUID(PLAYER, raid.id);
            SupplyContract.departure(partner);
        }
        // Clear stale containers in the chunks around the spawn immediately,
        // rather than waiting for the first scheduled player tick.
        raid.removeOldCrates(level, player);
        if (partner != null) {
            raid.removeOldCrates(level, partner);
        }
        raid.setDirty();
    }

    // Offline players retain their raid slot for a limited reconnection window.
    static void finishParticipant(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(ForestExpedition.FOREST);
        if (level == null) return;
        ForestRaid raid = get(level);
        raid.participants.remove(player.getUUID());
        raid.setDirty();
    }

    static void cleanupWhenEmpty(ServerLevel level) {
        ForestRaid raid = get(level);
        if (raid.id == null || !raid.participants.isEmpty()) return;
        raid.closeSession(level);
    }

    private void closeSession(ServerLevel level) {
        java.util.List<net.minecraft.world.entity.Entity> expired = new java.util.ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GuardCorpse || entity instanceof ItemEntity
                || entity.getPersistentData().hasUUID(AIRDROP_VISUAL)
                || entity instanceof Pillager guard && guard.getPersistentData().hasUUID(ENEMY)) {
                expired.add(entity);
            }
        }
        expired.forEach(net.minecraft.world.entity.Entity::discard);
        id = null;
        participants.clear();
        offlineSince = 0;
        setDirty();
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.serverLevel().dimension().equals(ForestExpedition.FOREST)) return;
        if (isParticipant(player)) {
            player.sendSystemMessage(Component.literal(
                "Expédition reprise. Ton raid est toujours actif : rejoins l'extraction pour conserver ton butin."));
        } else {
            player.sendSystemMessage(Component.literal(
                "Ton raid a expiré. Retour au point de départ et restauration de ton inventaire initial."));
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.serverLevel().dimension().equals(ForestExpedition.FOREST)) return;
        ForestRaid raid = get(player.serverLevel());
        if (raid.id == null || !raid.participants.contains(player.getUUID())) return;
        // Exclude the disconnecting player, which can still appear in level.players().
        boolean othersOnline = player.serverLevel().players().stream().anyMatch(other ->
            other != player && raid.participants.contains(other.getUUID()));
        if (!othersOnline) {
            raid.offlineSince = System.currentTimeMillis();
            raid.setDirty();
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        // One maintenance pass per second is sufficient for a ten-minute
        // grace period; avoid scanning the raid dimension 20 times a second.
        if (event.getServer().getTickCount() % 20 != 0) return;
        ServerLevel level = event.getServer().getLevel(ForestExpedition.FOREST);
        if (level == null) return;
        ForestRaid raid = get(level);
        if (raid.id == null || raid.participants.isEmpty()) return;
        boolean online = level.players().stream().anyMatch(p ->
            raid.participants.contains(p.getUUID()) && isParticipant(p));
        if (online) {
            raid.tickAirdrop(level);
            if (raid.offlineSince != 0) {
                raid.offlineSince = 0;
                raid.setDirty();
            }
        } else if (raid.offlineSince == 0) {
            raid.offlineSince = System.currentTimeMillis();
            raid.setDirty();
        } else if (System.currentTimeMillis() - raid.offlineSince >= RECONNECT_GRACE_MS) {
            raid.closeSession(level);
        }
    }

    private void tickAirdrop(ServerLevel level) {
        long now = level.getGameTime();
        if (dropPos == null && now >= dropDue) {
            var points = industrial ? IndustrialZone.GUARDS : ForestRaidAnchors.dropPoints();
            Random rng = new Random(seed ^ 0x5f9238acL ^ dropAttempts++);
            var point = points.get(rng.nextInt(points.size()));
            int origin = industrial ? IndustrialZone.ORIGIN : ForestPlan.ORIGIN;
            int cx = origin + point.x(), cz = origin + point.z();
            // Generate at most one destination chunk per maintenance pass.
            level.getChunk(cx >> 4, cz >> 4);
            for (int attempt = 0; attempt < 24 && dropPos == null; attempt++) {
                int x = cx + rng.nextInt(17)-8, z = cz + rng.nextInt(17)-8;
                if (!level.hasChunk(x >> 4, z >> 4) ||
                    !(industrial ? IndustrialZone.inside(x,z) : ForestPlan.inside(x,z))) continue;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x,z);
                BlockPos p = new BlockPos(x,y,z);
                if (y <= ForestPlan.WATER || !level.getBlockState(p).isAir()
                    || !level.getBlockState(p.above()).isAir() || !level.canSeeSky(p)
                    || !level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                    || level.getBlockState(p.below()).is(Blocks.GOLD_BLOCK)
                    || level.getBlockState(p.below()).is(Blocks.MOSSY_STONE_BRICKS)) continue;
                dropPos = p;
            }
            if (dropPos != null) {
                dropLanding = now + 240;
                var visual = EntityType.BLOCK_DISPLAY.create(level);
                if (visual != null) {
                    CompoundTag nbt = new CompoundTag();
                    nbt.put("block_state", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.BARREL.defaultBlockState()));
                    nbt.putInt("teleport_duration", 20);
                    visual.load(nbt);
                    visual.setPos(dropPos.getX(), dropPos.getY()+24, dropPos.getZ());
                    visual.getPersistentData().putUUID(AIRDROP_VISUAL,id);
                    if (level.addFreshEntity(visual)) dropVisual = visual.getUUID();
                }
                for (ServerPlayer p : level.players()) if (isParticipant(p))
                    p.sendSystemMessage(Component.literal("ALERTE · Largage de ravitaillement détecté ! X "
                        + dropPos.getX()+" Y "+dropPos.getY()+" Z "+dropPos.getZ()));
            }
        }
        if (dropPos != null && !dropLanded) {
            if (now >= dropLanding) {
                if (!level.hasChunkAt(dropPos)) level.getChunk(dropPos.getX() >> 4, dropPos.getZ() >> 4);
                if (createAirdrop(level)) {
                    dropLanded = true;
                    if (dropVisual != null && level.getEntity(dropVisual) != null) level.getEntity(dropVisual).discard();
                }
            } else {
                double y = dropPos.getY()+Math.max(0, (dropLanding-now)/10.0);
                if (dropVisual != null && level.getEntity(dropVisual) != null)
                    level.getEntity(dropVisual).setPos(dropPos.getX(),y,dropPos.getZ());
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    dropPos.getX()+.5,y+1,dropPos.getZ()+.5,4,.3,.3,.3,.01);
            }
        }
        if (dropLanded && dropPos != null && level.hasChunkAt(dropPos))
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                dropPos.getX()+.5,dropPos.getY()+1,dropPos.getZ()+.5,2,.25,.2,.25,.01);
        RaidDropPayload packet = dropPos == null ? new RaidDropPayload(false,false,0,0,0)
            : new RaidDropPayload(true,dropLanded,dropPos.getX(),dropPos.getY(),dropPos.getZ());
        for (ServerPlayer p : level.players()) if (isParticipant(p))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,packet);
    }

    private boolean createAirdrop(ServerLevel level) {
        if (level.getBlockEntity(dropPos) instanceof RandomizableContainerBlockEntity existing
            && matches(existing.getPersistentData(),CRATE) && existing.getPersistentData().getInt(CRATE_INDEX) == -1)
            return true;
        if (!level.getBlockState(dropPos).isAir()
            || !ForestProtection.setRaidContainer(level,dropPos,Blocks.BARREL.defaultBlockState())
            || !(level.getBlockEntity(dropPos) instanceof RandomizableContainerBlockEntity barrel)) return false;
        Random rng = new Random(seed ^ 0x62B64A31L);
        barrel.getPersistentData().putUUID(CRATE,id);
        barrel.getPersistentData().putInt(CRATE_INDEX,-1);
        ItemStack label = new ItemStack(Items.BARREL);
        label.set(DataComponents.CUSTOM_NAME,Component.literal("Largage militaire · Butin rare"));
        barrel.applyComponentsFromItemStack(label);
        var guns = new ArrayList<>(com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex());
        guns.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
        guns.removeIf(e -> e.getValue().getGunData() == null || e.getValue().getGunData().getFireModeSet().isEmpty());
        for (int i=0;i<2 && !guns.isEmpty();i++) {
            var gun = guns.remove(rng.nextInt(guns.size()));
            var data = gun.getValue().getGunData();
            barrel.setItem(i,OperationsShop.weapon(gun.getKey(),Math.max(1,data.getAmmoAmount())));
            if (data.getAmmoId()!=null) barrel.setItem(3+i,com.tacz.guns.api.item.builder.AmmoItemBuilder.create()
                .setId(data.getAmmoId()).setCount(48).build());
        }
        var attachments = new ArrayList<>(com.tacz.guns.api.TimelessAPI.getAllCommonAttachmentIndex());
        attachments.sort(java.util.Comparator.comparing(e -> e.getKey().toString()));
        for (int i=0;i<3 && !attachments.isEmpty();i++) {
            var a = attachments.remove(rng.nextInt(attachments.size()));
            barrel.setItem(6+i,com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(a.getKey()).build());
        }
        barrel.setItem(9,BallisticVest.roll(rng,true));
        barrel.setItem(10,new ItemStack(Items.IRON_HELMET));
        barrel.setItem(11,new ItemStack(Items.GOLDEN_APPLE,2));
        barrel.setItem(12,RaidRelic.CIRCUIT.stack());
        barrel.setItem(13,RaidRelic.values()[5+rng.nextInt(4)].stack());
        barrel.setItem(14,RaidRelic.roll(rng).stack());
        if (rng.nextInt(100)<5) barrel.setItem(15,RaidRelic.OMEGA.stack());
        if (rng.nextInt(100)<8) barrel.setItem(16,new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.parse("taczoperations:night_vision_goggles"))));
        barrel.setChanged();
        return true;
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)
            || !(event.getEntity() instanceof ServerPlayer respawned)
            || !original.serverLevel().dimension().equals(ForestExpedition.FOREST)) {
            return;
        }
        // Raid deaths must not affect the vanilla Minecraft death statistic.
        // The vanilla death has already been counted when the clone event fires.
        boolean raidDeath = original.getPersistentData().hasUUID(PLAYER)
            && get(original.serverLevel()).matches(original.getPersistentData(), PLAYER);
        if (raidDeath) {
            // Explicitly transfer Escape progression before applying the penalty:
            // a respawned player may not yet have the original persistent account.
            CompoundTag oldRoot = original.getPersistentData();
            CompoundTag oldPersisted = oldRoot.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            CompoundTag oldAccount = oldPersisted.getCompound("taczoperations");
            CompoundTag newRoot = respawned.getPersistentData();
            CompoundTag newPersisted = newRoot.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            newPersisted.put("taczoperations", oldAccount.copy());
            newRoot.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, newPersisted);

            var deaths = net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS);
            var statistics = respawned.getStats();
            statistics.setValue(respawned, deaths,
                Math.max(0, statistics.getValue(deaths) - 1));

            int adjustment = SupplyContract.finalizeRaidXp(respawned, oldRoot, false);
            long base = (long) Math.max(0, oldRoot.getInt("taczoperations_run_kill_xp"))
                + Math.max(0, oldRoot.getInt("taczoperations_run_search_xp"));
            long started = oldRoot.getLong("taczoperations_run_started");
            long duration = started > 0 ? Math.max(0, (original.server.overworld().getGameTime() - started) / 20) : 0;
            SupplyContract.saveRaidReport(respawned, false, duration,
                oldRoot.getInt("taczoperations_raid_kills"),
                oldRoot.getInt("taczoperations_run_crates"),
                oldRoot.getInt("taczoperations_run_contracts"),
                oldRoot.getInt("taczoperations_run_kill_xp"),
                oldRoot.getInt("taczoperations_run_search_xp"),
                oldRoot.getInt("taczoperations_run_contract_xp"), adjustment);
            // Deliver the UI only after the new player has fully respawned.
            respawned.getPersistentData().putBoolean("taczoperations_pending_failed_report", true);
            respawned.sendSystemMessage(Component.literal(
                "Raid échoué · +" + (base + oldRoot.getInt("taczoperations_run_contract_xp") + adjustment)
                    + " XP Escape (malus " + adjustment + "). Bilan affiché à l'écran."));
        }
        // The normal death-drop process has already run. Do not duplicate
        // equipment into a custom corpse or copy the raid token on respawn.
        clearPlayer(respawned);
        ForestExpedition.clearReturnPoint(respawned);
        clearPlayer(original);
        // A surviving duo member can still collect vanilla death drops.
        finishParticipant(original);
        cleanupWhenEmpty(original.serverLevel());
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !player.getPersistentData().getBoolean("taczoperations_pending_failed_report")) return;
        player.getPersistentData().remove("taczoperations_pending_failed_report");
        // Remove stale run state only after its report has been persisted.
        for (String key : new String[] {
            "taczoperations_run_kill_xp", "taczoperations_run_search_xp",
            "taczoperations_run_crates", "taczoperations_run_contracts", "taczoperations_run_contract_xp",
            "taczoperations_run_started", "taczoperations_run_xp_finalized",
            "taczoperations_raid_kills", "taczoperations_raid_boss",
            "taczoperations_relic_baseline"
        }) player.getPersistentData().remove(key);

        // The same delayed delivery path handles both success and failure.
    }

    private static final java.util.Map<ServerPlayer, Integer> REPORT_READY = new java.util.WeakHashMap<>();

    public static void deliverReport(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var account = SupplyContract.account(player);
        if (!account.getBoolean("pending_raid_report") || !player.isAlive()
            || player.serverLevel().dimension().equals(ForestExpedition.FOREST)) {
            REPORT_READY.remove(player);
            return;
        }
        int ready = REPORT_READY.computeIfAbsent(player, p -> p.tickCount + 40);
        if (player.tickCount < ready) return;
        CompoundTag report = account.getCompound("last_raid_report");
        if (!report.contains("final_xp")) {
            account.remove("pending_raid_report");
            REPORT_READY.remove(player);
            return;
        }
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new RaidResultPayload(report.getBoolean("success"), report.getLong("duration_seconds"),
                    report.getInt("kills"), report.getInt("crates"), report.getInt("contracts"),
                    report.getInt("combat_xp"), report.getInt("search_xp"),
                    report.getInt("contract_xp"), report.getInt("adjustment_xp")));
            account.remove("pending_raid_report");
            REPORT_READY.remove(player);
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Raid report delivery failed; retained for retry", exception);
            REPORT_READY.put(player, player.tickCount + 100);
        }
    }

    static boolean hasActiveSession(ServerLevel level) {
        ForestRaid raid = get(level);
        if (raid.id == null) return false;
        if (raid.participants.removeIf(uuid -> {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(uuid);
            return online != null && !isParticipant(online);
        })) raid.setDirty();
        if (raid.participants.isEmpty()) raid.closeSession(level);
        return raid.id != null;
    }

    static boolean isParticipant(ServerPlayer player) {
        return player.level().dimension().equals(ForestExpedition.FOREST)
            && ForestExpedition.hasReturnPoint(player)
            && get(player.serverLevel()).participants.contains(player.getUUID())
            && get(player.serverLevel()).matches(player.getPersistentData(), PLAYER);
    }

    static boolean currentGuard(ServerLevel level, Pillager guard) {
        return get(level).matches(guard.getPersistentData(), ENEMY);
    }

    public static boolean canKeepCorpse(ServerLevel level, GuardCorpse corpse) {
        return level.dimension().equals(ForestExpedition.FOREST)
            && get(level).matches(corpse.getPersistentData(), CORPSE);
    }

    public static boolean canSearchCorpse(ServerPlayer player, GuardCorpse corpse) {
        return isParticipant(player) && canKeepCorpse(player.serverLevel(), corpse);
    }

    static boolean hasRaidToken(ServerPlayer player) {
        return player.getPersistentData().hasUUID(PLAYER);
    }

    static void clearPlayer(ServerPlayer player) {
        player.getPersistentData().remove(PLAYER);
    }

    private boolean matches(CompoundTag data, String key) {
        return id != null && data.hasUUID(key) && id.equals(data.getUUID(key));
    }

    static void populateNear(ServerPlayer player) {
        if (!isParticipant(player) || player.isSpectator() || player.tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ForestRaid raid = get(level);
        raid.removeOldCrates(level, player);
        int budget = 5;
        for (int i = 0; i < raid.locations.size() && budget > 0; i++) {
            if (raid.crates.get(i)) {
                continue;
            }
            var mark = raid.locations.get(i);
            int origin = raid.industrial ? IndustrialZone.ORIGIN : ForestPlan.ORIGIN;
            BlockPos center = new BlockPos(origin + mark.x(), mark.y(), origin + mark.z());
            if (!near(player, center) || !level.hasChunkAt(center)) {
                continue;
            }
            if (raid.existingCrate(level, center, i)) {
                raid.crates.set(i);
                raid.setDirty();
                continue;
            }
            BlockPos place = findFloor(level, center, 2, false);
            if (place != null && raid.putCrate(level, place, "Caisse de ravitaillement", new Random(raid.seed ^ i), i)) {
                raid.crates.set(i);
                raid.setDirty();
                budget--;
            }
        }

        var guards = raid.industrial ? IndustrialZone.GUARDS : ForestRaidAnchors.ENEMIES;
        // Prefer the nearest encounter anchors rather than always the first list entries.
        var ordered = new java.util.ArrayList<Integer>();
        for (int i = 0; i < guards.size(); i++) ordered.add(i);
        ordered.sort(java.util.Comparator.comparingDouble(i -> {
            var p = guards.get(i);
            BlockPos pos = raid.industrial ? ForestExpedition.industrialPosition(p) : ForestLandmarks.position(p);
            return pos.distToCenterSqr(player.position());
        }));
        for (int group : ordered) {
            if (budget <= 0) break;
            var point = guards.get(group);
            BlockPos center = raid.industrial ? ForestExpedition.industrialPosition(point) : ForestLandmarks.position(point);
            if (!near(player, center) || !level.hasChunkAt(center)) continue;
            boolean pmc = raid.pmcGroups.get(group);
            Random squadRandom = new Random(raid.seed ^ (0x632BE59BD9B4E019L * (group + 1)));
            int members = 2 + squadRandom.nextInt(2);
            for (int member = 0; member < members && budget > 0; member++) {
                int slot = group * 3 + member;
                if (raid.enemies.get(slot)) continue;
                GuardRole role = pmc ? GuardRole.PMC : Math.floorMod(slot + (int) raid.seed, 5) == 0
                    ? GuardRole.HEAVY : member == 0 ? GuardRole.RAIDER : GuardRole.SCOUT;
                // Search several separate positions; never spawn two members on the same block.
                Random positions = new Random(raid.seed ^ (slot * 31L) ^ (player.tickCount / 20L));
                for (int attempt = 0; attempt < 6; attempt++) {
                    BlockPos around = center.offset(positions.nextInt(13) - 6, 0, positions.nextInt(13) - 6);
                    BlockPos place = findFloor(level, around, 4, true);
                    if (place == null || !level.getEntitiesOfClass(Pillager.class,
                        new net.minecraft.world.phys.AABB(place).inflate(1.5),
                        e -> e.isAlive() && currentGuard(level, e)).isEmpty()) continue;
                    if (raid.spawnGuard(level, place, role, group)) {
                        raid.enemies.set(slot);
                        raid.setDirty();
                        budget--;
                        break;
                    }
                }
            }
        }

        // The Tracker is a separate boss, not a member of either faction.
        int bossSlot = guards.size() * 3;
        if (!raid.industrial && budget > 0 && !raid.enemies.get(bossSlot)) {
            var point = ForestRaidAnchors.TRACKER.get(Math.floorMod(raid.seed, ForestRaidAnchors.TRACKER.size()));
            BlockPos center = ForestLandmarks.position(point);
            if (near(player, center) && level.hasChunkAt(center)) {
                BlockPos place = findFloor(level, center, 4, true);
                if (place != null && raid.spawnGuard(level, place, GuardRole.TRACKER, -1)) {
                    raid.enemies.set(bossSlot);
                    raid.setDirty();
                }
            }
        }
    }

    // Group nearby anchors, without allowing an entire chain of distant
    // encounters to become one giant faction. Randomize once per raid.
    private static BitSet assignFactions(List<ForestPlan.Point> points, long seed) {
        java.util.List<java.util.List<Integer>> sectors = new java.util.ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            final int idx = i;
            var a = points.get(i);
            var sector = sectors.stream().filter(s -> s.stream().allMatch(j -> {
                var b = points.get(j);
                long dx = a.x()-b.x(), dz = a.z()-b.z();
                return dx*dx+dz*dz <= 75L*75L;
            })).findFirst();
            if (sector.isPresent()) sector.get().add(idx);
            else sectors.add(new java.util.ArrayList<>(java.util.List.of(idx)));
        }
        java.util.Collections.shuffle(sectors, new Random(seed ^ 0x74c1de92L));
        BitSet result = new BitSet(points.size());
        int target = Math.max(2, Math.min(points.size()-2, (int)Math.round(points.size()*.45)));
        int assigned = 0;
        for (var sector : sectors) {
            if (assigned >= target || assigned + sector.size() > points.size()-2) continue;
            for (int i : sector) result.set(i);
            assigned += sector.size();
        }
        // Preserve a minimum of two groups of each faction for these maps.
        for (int i=0; assigned<2 && i<points.size()-2; i++) {
            if (!result.get(i)) { result.set(i); assigned++; }
        }
        return result;
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return pos.distToCenterSqr(player.position()) <= 60 * 60;
    }

    private static BlockPos findFloor(ServerLevel level, BlockPos center, int radius, boolean headroom) {
        for (BlockPos pos : BlockPos.withinManhattan(center, radius, headroom ? 3 : 0, radius)) {
            if ((!ForestPlan.inside(pos.getX(), pos.getZ()) && !IndustrialZone.inside(pos.getX(), pos.getZ())) || !level.hasChunkAt(pos)
                || !level.getBlockState(pos).isAir() || (headroom && !level.getBlockState(pos.above()).isAir())) {
                continue;
            }
            var below = level.getBlockState(pos.below());
            if (below.isFaceSturdy(level, pos.below(), Direction.UP)
                && !below.is(Blocks.GOLD_BLOCK) && !below.is(Blocks.MOSSY_STONE_BRICKS)
                && !below.is(Blocks.COPPER_BLOCK)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private boolean spawnGuard(ServerLevel level, BlockPos pos, GuardRole role, int squad) {
        Pillager guard = EntityType.PILLAGER.create(level);
        if (guard == null) {
            return false;
        }
        guard.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, level.getRandom().nextFloat() * 360, 0);
        if (!level.noCollision(guard)) {
            return false;
        }
        guard.getPersistentData().putUUID(ENEMY, id);
        guard.setPersistenceRequired();
        role.assign(guard);
        guard.getPersistentData().putInt("taczoperations_squad", squad);
        guard.getPersistentData().putInt("guard_home_x", pos.getX());
        guard.getPersistentData().putInt("guard_home_y", pos.getY());
        guard.getPersistentData().putInt("guard_home_z", pos.getZ());
        guard.setCustomName(Component.literal(role.title));
        // Keep enemy identity in server data without revealing positions through foliage.
        guard.setCustomNameVisible(false);
        guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(role.health);
        guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role.speed);
        guard.setHealth(role.health);
        // Armor variants are deterministic for each guard and preserve role tiers.
        int equipmentRoll = Math.floorMod(guard.getUUID().hashCode(), 6);
        net.minecraft.world.item.Item vest = role.armor;
        if (role == GuardRole.RAIDER || role == GuardRole.SCOUT) {
            if (equipmentRoll == 0) vest = Items.CHAINMAIL_CHESTPLATE;
        } else if (role == GuardRole.SOLDIER || role == GuardRole.MARKSMAN) {
            if (equipmentRoll == 0) vest = Items.IRON_CHESTPLATE;
        } else if (role != GuardRole.TRACKER && equipmentRoll == 0) {
            vest = Items.DIAMOND_CHESTPLATE;
        }
        if (role == GuardRole.PMC) {
            int rarity = guard.getRandom().nextInt(100);
            guard.getPersistentData().putInt("taczoperations_pmc_rarity", rarity < 40 ? 0 : rarity < 70 ? 1 : rarity < 90 ? 2 : rarity < 98 ? 3 : 4);
            vest = rarity < 40 ? Items.LEATHER_CHESTPLATE : rarity < 70 ? Items.CHAINMAIL_CHESTPLATE : rarity < 98 ? Items.IRON_CHESTPLATE : Items.DIAMOND_CHESTPLATE;
        }
        guard.setItemSlot(EquipmentSlot.CHEST, role == GuardRole.PMC
            ? BallisticVest.roll(new Random(guard.getUUID().getLeastSignificantBits()), false)
            : new ItemStack(vest));
        if (equipmentRoll == 1 || equipmentRoll == 4) {
            guard.setItemSlot(EquipmentSlot.LEGS, new ItemStack(
                role.elite() ? Items.IRON_LEGGINGS : Items.LEATHER_LEGGINGS));
        }
        if (role == GuardRole.SCOUT || role == GuardRole.MARKSMAN) {
            guard.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        } else if (role.elite()) {
            guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(role == GuardRole.TRACKER ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
        }
        if (role == GuardRole.PMC) {
            int tier = guard.getPersistentData().getInt("taczoperations_pmc_rarity");
            guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(tier >= 4 ? Items.DIAMOND_HELMET : tier >= 2 ? Items.IRON_HELMET : Items.LEATHER_HELMET));
            guard.setItemSlot(EquipmentSlot.LEGS, new ItemStack(tier >= 4 ? Items.DIAMOND_LEGGINGS : tier >= 2 ? Items.IRON_LEGGINGS : Items.LEATHER_LEGGINGS));
            guard.setItemSlot(EquipmentSlot.FEET, new ItemStack(tier >= 4 ? Items.DIAMOND_BOOTS : tier >= 2 ? Items.IRON_BOOTS : Items.LEATHER_BOOTS));
        }
        if (role == GuardRole.PMC) {
            int tier = guard.getPersistentData().getInt("taczoperations_pmc_rarity");
            if (tier >= 3 && guard.getRandom().nextInt(100) < (tier == 4 ? 30 : 10)) {
                String id = tier == 4 && guard.getRandom().nextBoolean() ? "thermal_goggles" : "night_vision_goggles";
                guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczoperations", id))));
            }
        }
        if (role == GuardRole.PMC) {
            int[] uniformColors = {0x414A35, 0x70644C, 0x33383B, 0x555C4B};
            int color = uniformColors[Math.floorMod(guard.getUUID().hashCode(), uniformColors.length)];
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = guard.getItemBySlot(slot);
                if (armor.is(Items.LEATHER_HELMET) || armor.is(Items.LEATHER_CHESTPLATE)
                    || armor.is(Items.LEATHER_LEGGINGS) || armor.is(Items.LEATHER_BOOTS)) {
                    armor.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                        new net.minecraft.world.item.component.DyedItemColor(color, false));
                }
            }
        }
        guard.setDropChance(EquipmentSlot.LEGS, 0);
        guard.setDropChance(EquipmentSlot.HEAD, 0);
        guard.setDropChance(EquipmentSlot.FEET, 0);
        guard.setDropChance(EquipmentSlot.MAINHAND, 0);
        guard.setDropChance(EquipmentSlot.CHEST, 0);
        ForestCombat.arm(guard);
        GuardPouch.load(guard);
        return level.addFreshEntity(guard);
    }

    private boolean existingCrate(ServerLevel level, BlockPos center, int index) {
        for (BlockPos pos : BlockPos.withinManhattan(center, 2, 0, 2)) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel) {
                CompoundTag data = barrel.getPersistentData();
                if (matches(data, CRATE) && data.contains(CRATE_INDEX) && data.getInt(CRATE_INDEX) == index) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean putCrate(ServerLevel level, BlockPos pos, String name, Random random, int index) {
        if (pos.equals(dropPos)) return false;
        var block = index % 3 == 0 ? Blocks.CHEST : Blocks.BARREL;
        if (!ForestProtection.setRaidContainer(level, pos, block.defaultBlockState())) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel)) {
            return false;
        }
        barrel.getPersistentData().putUUID(CRATE, id);
        barrel.getPersistentData().putInt(CRATE_INDEX, index);
        int kind = locations.get(index).kind();
        ItemStack labeled = new ItemStack(Items.BARREL);
        labeled.set(DataComponents.CUSTOM_NAME, Component.literal(switch (kind) {
            case 0 -> "Caisse médicale";
            case 1 -> "Caisse industrielle";
            case 2 -> "Caisse militaire";
            default -> "Coffre confidentiel";
        }));
        barrel.applyComponentsFromItemStack(labeled);
        var supplies = switch (kind) {
            case 0 -> List.of(Items.COOKED_BEEF, Items.BREAD, Items.GOLDEN_CARROT, Items.HONEY_BOTTLE);
            case 1 -> List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE, Items.COAL, Items.FLINT);
            case 2 -> List.of(Items.CHAINMAIL_BOOTS, Items.LEATHER_HELMET, Items.IRON_HELMET, Items.SHIELD, Items.ARROW);
            default -> List.of(Items.COMPASS, Items.CLOCK, Items.PAPER, Items.EMERALD, Items.GOLD_INGOT);
        };
        var item = supplies.get(random.nextInt(supplies.size()));
        ItemStack primary = new ItemStack(item);
        primary.setCount(Math.min(primary.getMaxStackSize(), 1 + random.nextInt(kind == 1 ? 6 : 3)));
        barrel.setItem(random.nextInt(9), primary);
        int relicChance = switch (kind) { case 0 -> 30; case 1 -> 50; case 2 -> 55; default -> 77; };
        if (random.nextInt(100) < relicChance + (industrial ? 10 : 0))
            barrel.setItem(9 + random.nextInt(8), RaidRelic.roll(random).stack());
        if (random.nextInt(100) < (kind == 3 ? 15 : 4) + (industrial ? 5 : 0))
            barrel.setItem(17, RaidRelic.roll(random).stack());
        switch (kind) {
            case 0 -> {
                barrel.setItem(18, net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                    Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING));
                if (random.nextInt(5) == 0) barrel.setItem(26, new ItemStack(Items.GOLDEN_APPLE));
            }
            case 1 -> {
                barrel.setItem(18, new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(9)));
                barrel.setItem(26, new ItemStack(Items.REDSTONE, 2 + random.nextInt(7)));
            }
            case 2 -> {
                var calibers = new java.util.ArrayList<net.minecraft.resources.ResourceLocation>();
                for (var entry : com.tacz.guns.api.TimelessAPI.getAllCommonGunIndex()) {
                    var data = entry.getValue().getGunData();
                    if (data != null && data.getAmmoId() != null && !calibers.contains(data.getAmmoId()))
                        calibers.add(data.getAmmoId());
                }
                if (!calibers.isEmpty()) {
                    var ammoId = calibers.get(random.nextInt(calibers.size()));
                    barrel.setItem(18, com.tacz.guns.api.item.builder.AmmoItemBuilder.create()
                        .setId(ammoId).setCount(12 + random.nextInt(19)).build());
                }
                if (random.nextInt(industrial ? 5 : 8) == 0)
                    barrel.setItem(25, OperationsShop.attachment(random.nextBoolean() ? "sight_552" : "extended_mag_1"));
            }
            default -> barrel.setItem(18, new ItemStack(Items.PAPER, 2 + random.nextInt(5)));
        }
        barrel.setChanged();
        return true;
    }

    private void removeOldCrates(ServerLevel level, ServerPlayer player) {
        var origin = player.chunkPosition();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                var chunk = level.getChunkSource().getChunkNow(origin.x + dx, origin.z + dz);
                if (chunk == null) continue;
                List<BlockPos> expired = new ArrayList<>();
                for (BlockEntity block : chunk.getBlockEntities().values()) {
                    CompoundTag data = block.getPersistentData();
                    if (block instanceof RandomizableContainerBlockEntity && data.hasUUID(CRATE) && !matches(data, CRATE)) {
                        expired.add(block.getBlockPos());
                    }
                }
                for (BlockPos pos : expired) {
                    if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel) {
                        barrel.clearContent();
                        ForestProtection.setRaidContainer(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ForestExpedition.FOREST)) {
            return;
        }
        if (event.getEntity().getPersistentData().hasUUID(AIRDROP_VISUAL)) {
            ForestRaid raid = get(level);
            if (!raid.matches(event.getEntity().getPersistentData(), AIRDROP_VISUAL) || raid.dropLanded) event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof GuardCorpse corpse && !canKeepCorpse(level, corpse)) {
            // Expired raid corpses must never survive into another session.
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ItemEntity item) {
            // Items are temporary raid loot, including player death drops.
            // Persist their raid identity so stale drops cannot enter a later raid.
            ForestRaid raid = get(level);
            if (event.loadedFromDisk()) {
                if (!raid.matches(item.getPersistentData(), DROP)) {
                    event.setCanceled(true);
                }
            } else if (raid.id != null) {
                item.getPersistentData().putUUID(DROP, raid.id);
            } else {
                event.setCanceled(true);
            }
        } else if (event.getEntity() instanceof Mob mob) {
            if (!(mob instanceof Pillager) || !get(level).matches(mob.getPersistentData(), ENEMY)) {
                event.setCanceled(true);
            }
        } else if (!event.loadedFromDisk() && event.getEntity() instanceof AbstractArrow arrow
            && arrow.getOwner() instanceof Pillager guard && guard.getMainHandItem().is(Items.CROSSBOW)
            && guard.getPersistentData().hasUUID(ENEMY)
            && (!currentGuard(level, guard) || GuardPouch.consume(guard, 1, true) == 0)) {
            event.setCanceled(true);
        }
    }

    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Pillager guard) || !(guard.level() instanceof ServerLevel level)
            || !level.dimension().equals(ForestExpedition.FOREST) || !guard.getPersistentData().hasUUID(ENEMY)) {
            return;
        }
        event.getDrops().clear();
        ForestRaid raid = get(level);
        if (!raid.matches(guard.getPersistentData(), ENEMY)) {
            return;
        }
        GuardRole role = GuardRole.get(guard);
        if (event.getSource().getEntity() instanceof ServerPlayer killer
            && !guard.getPersistentData().getBoolean("taczoperations_kill_rewarded")) {
            guard.getPersistentData().putBoolean("taczoperations_kill_rewarded", true);
            SupplyContract.kill(killer, role.elite(), role == GuardRole.TRACKER);
            int xp = role == GuardRole.TRACKER ? 40 : role.elite() ? 12 : role == GuardRole.PMC ? 8 : 5;
            SupplyContract.activityXp(killer, xp, true, role.title + " éliminé");
        }
        GuardCorpse corpse = OperationsMod.GUARD_CORPSE.get().create(level);
        if (corpse == null) {
            return;
        }
        corpse.moveTo(guard.getX(), guard.getY(), guard.getZ(), guard.getYRot(), 0);
        corpse.getPersistentData().putUUID(CORPSE, raid.id);
        EquipmentSlot[] equipment = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < equipment.length; i++) {
            corpse.put(i, guard.getItemBySlot(equipment[i]));
        }
        var pouch = GuardPouch.load(guard);
        for (int i = 0; i < pouch.size(); i++) {
            corpse.put(9 + i, pouch.get(i));
        }
        if (role == GuardRole.TRACKER) {
            corpse.put(18, RaidRelic.CHIP.stack());
        }
        if (level.addFreshEntity(corpse)) {
            for (EquipmentSlot slot : equipment) {
                guard.setItemSlot(slot, ItemStack.EMPTY);
            }
            GuardPouch.clear(guard);
        }
    }

    public static void onInspect(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(ForestExpedition.FOREST)
            || !(player.level().getBlockEntity(event.getPos()) instanceof RandomizableContainerBlockEntity barrel)
            || !barrel.getPersistentData().hasUUID(CRATE)) {
            return;
        }
        if (!isParticipant(player) || !get(player.serverLevel()).matches(barrel.getPersistentData(), CRATE)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Cette caisse ne fait pas partie de ton raid."), true);
            return;
        }
        // One point per distinct raid container and player, not per click.
        // Scope the record to the current raid UUID to prevent farming by reopening.
        CompoundTag tracking = player.getPersistentData();
        String raidKey = get(player.serverLevel()).id.toString();
        if (!raidKey.equals(tracking.getString("taczoperations_xp_raid"))) {
            tracking.putString("taczoperations_xp_raid", raidKey);
            tracking.put("taczoperations_xp_crates", new CompoundTag());
        }
        CompoundTag opened = tracking.getCompound("taczoperations_xp_crates");
        String crateKey = event.getPos().asLong() + "";
        if (!opened.getBoolean(crateKey)) {
            opened.putBoolean(crateKey, true);
            tracking.put("taczoperations_xp_crates", opened);
            tracking.putInt("taczoperations_run_crates",
                tracking.getInt("taczoperations_run_crates") + 1);
            SupplyContract.activityXp(player, 3, false, "Coffre fouillé");
            SupplyContract.chestOpened(player);
        }
    }

    private static ForestRaid read(CompoundTag tag, HolderLookup.Provider registries) {
        // Raids are temporary sessions, not persistent worlds. Never restore
        // an active raid ID after a server restart: otherwise a returning
        // player could rejoin an abandoned raid with stale loot and enemies.
        // The next departure creates a fresh ID and clears nearby old crates.
        ForestRaid raid = new ForestRaid();
        raid.setDirty();
        return raid;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (id != null) {
            tag.putUUID("id", id);
            tag.putLong("seed", seed);
            tag.putBoolean("industrial", industrial);
            tag.putLongArray("crates", crates.toLongArray());
            tag.putLongArray("enemies", enemies.toLongArray());
        }
        return tag;
    }
}
