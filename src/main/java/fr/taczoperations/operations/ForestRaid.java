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
    private static final Factory<ForestRaid> FACTORY = new Factory<>(ForestRaid::new, ForestRaid::read);

    private static final long RECONNECT_GRACE_MS = 10 * 60 * 1000L;
    private final Set<UUID> participants = new HashSet<>();
    private long offlineSince;
    private UUID id;
    private long seed;
    private boolean industrial;
    private BitSet crates = new BitSet();
    private BitSet enemies = new BitSet();
    private List<ForestRaidAnchors.Mark> locations = List.of();

    private static ForestRaid get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "taczoperations_raid");
    }

    static void begin(ServerLevel level, ServerPlayer player, ServerPlayer partner, boolean industrial) {
        ForestRaid raid = get(level);
        // A previous raid may have left corpses in loaded chunks. Remove them
        // before assigning a new session ID, so their inventories cannot be looted.
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GuardCorpse || entity instanceof ItemEntity
                || entity instanceof Pillager guard && guard.getPersistentData().hasUUID(ENEMY)) {
                entity.discard();
            }
        }
        raid.id = UUID.randomUUID();
        raid.participants.clear();
        raid.participants.add(player.getUUID());
        if (partner != null) raid.participants.add(partner.getUUID());
        raid.offlineSince = 0;
        raid.seed = level.getRandom().nextLong();
        raid.industrial = industrial;
        raid.crates.clear();
        raid.enemies.clear();
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
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(ForestExpedition.FOREST)) return;
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
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GuardCorpse || entity instanceof ItemEntity
                || entity instanceof Pillager guard && guard.getPersistentData().hasUUID(ENEMY)) {
                entity.discard();
            }
        }
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

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getOriginal() instanceof ServerPlayer original)
            || !(event.getEntity() instanceof ServerPlayer respawned)
            || !original.serverLevel().dimension().equals(ForestExpedition.FOREST)) {
            return;
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

    static boolean hasActiveSession(ServerLevel level) {
        return get(level).id != null;
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
        for (int i = 0; i < guards.size() + (raid.industrial ? 0 : 1) && budget > 0; i++) {
            if (raid.enemies.get(i)) {
                continue;
            }
            boolean boss = i == guards.size();
            GuardRole role = boss ? GuardRole.TRACKER : GuardRole.values()[Math.floorMod(i + (int) raid.seed, 6)];
            var point = boss ? ForestRaidAnchors.TRACKER.get(Math.floorMod(raid.seed, ForestRaidAnchors.TRACKER.size()))
                : guards.get(i);
            BlockPos center = raid.industrial ? ForestExpedition.industrialPosition(point) : ForestLandmarks.position(point);
            if (!near(player, center) || !level.hasChunkAt(center)) {
                continue;
            }
            BlockPos place = findFloor(level, center, 4, true);
            if (place != null && raid.spawnGuard(level, place, role)) {
                raid.enemies.set(i);
                raid.setDirty();
                budget--;
            }
        }
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

    private boolean spawnGuard(ServerLevel level, BlockPos pos, GuardRole role) {
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
        guard.getPersistentData().putInt("guard_home_x", pos.getX());
        guard.getPersistentData().putInt("guard_home_y", pos.getY());
        guard.getPersistentData().putInt("guard_home_z", pos.getZ());
        guard.setCustomName(Component.literal(role.title));
        // Keep enemy identity in server data without revealing positions through foliage.
        guard.setCustomNameVisible(false);
        guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(role.health);
        guard.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(role.speed);
        guard.setHealth(role.health);
        guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(role.armor));
        if (role == GuardRole.SCOUT || role == GuardRole.MARKSMAN) {
            guard.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        } else if (role.elite()) {
            guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(role == GuardRole.TRACKER ? Items.DIAMOND_HELMET : Items.IRON_HELMET));
        }
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
        var block = index % 3 == 0 ? Blocks.CHEST : Blocks.BARREL;
        if (!ForestProtection.setRaidContainer(level, pos, block.defaultBlockState())) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity barrel)) {
            return false;
        }
        barrel.getPersistentData().putUUID(CRATE, id);
        barrel.getPersistentData().putInt(CRATE_INDEX, index);
        ItemStack labeled = new ItemStack(Items.BARREL);
        labeled.set(DataComponents.CUSTOM_NAME, Component.literal(switch (index % 3) {
            case 0 -> "Coffre de terrain";
            case 1 -> "Casier de campagne";
            default -> name;
        }));
        barrel.applyComponentsFromItemStack(labeled);
        var supplies = List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.TORCH,
            Items.IRON_INGOT, Items.CHAINMAIL_BOOTS, Items.GOLDEN_CARROT);
        barrel.setItem(random.nextInt(9), new ItemStack(supplies.get(random.nextInt(supplies.size())), 1));
        if (random.nextInt(100) < 53) barrel.setItem(9 + random.nextInt(9), RaidRelic.roll(random).stack());
        int supply = 2 + random.nextInt(5);
        String[] calibers = {"9mm", "762x39", "12g", "338", "556x45"};
        ItemStack ammo = random.nextBoolean() ? OperationsShop.ammo(calibers[random.nextInt(calibers.length)], supply) : ItemStack.EMPTY;
        barrel.setItem(18 + random.nextInt(8), ammo.isEmpty() ? new ItemStack(Items.IRON_INGOT, supply) : ammo);
        if (random.nextInt(3) == 0) {
            barrel.setItem(26, new ItemStack(Items.GOLDEN_CARROT));
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
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            SupplyContract.kill(killer, role.elite(), role == GuardRole.TRACKER);
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
