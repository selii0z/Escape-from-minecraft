package fr.taczoperations.operations;

import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import fr.taczoperations.worldgen.ForestPlan;
import fr.taczoperations.worldgen.ForestRaidAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ForestRaid extends SavedData {
    static final String ENEMY = "taczoperations_enemy";
    private static final String PLAYER = "taczoperations_raid_player";
    private static final String CRATE = "taczoperations_raid_crate";
    private static final String CRATE_INDEX = "taczoperations_raid_crate_index";
    private static final ResourceLocation PISTOL_AMMO = ResourceLocation.fromNamespaceAndPath("tacz", "9mm");
    private static final Factory<ForestRaid> FACTORY = new Factory<>(ForestRaid::new, ForestRaid::read);

    private UUID id;
    private long seed;
    private BitSet crates = new BitSet();
    private BitSet enemies = new BitSet();
    private List<ForestRaidAnchors.Mark> locations = List.of();

    private static ForestRaid get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "taczoperations_raid");
    }

    static void begin(ServerLevel level, ServerPlayer player, ServerPlayer partner) {
        ForestRaid raid = get(level);
        raid.id = UUID.randomUUID();
        raid.seed = level.getRandom().nextLong();
        raid.crates.clear();
        raid.enemies.clear();
        raid.locations = ForestRaidAnchors.pickLoot(raid.seed);
        player.getPersistentData().putUUID(PLAYER, raid.id);
        if (partner != null) {
            partner.getPersistentData().putUUID(PLAYER, raid.id);
        }
        raid.setDirty();
    }

    static boolean isParticipant(ServerPlayer player) {
        return player.level().dimension().equals(ForestExpedition.FOREST)
            && get(player.serverLevel()).matches(player.getPersistentData(), PLAYER);
    }

    static boolean currentGuard(ServerLevel level, Pillager guard) {
        return get(level).matches(guard.getPersistentData(), ENEMY);
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
            BlockPos center = new BlockPos(ForestPlan.ORIGIN + mark.x(), mark.y(), ForestPlan.ORIGIN + mark.z());
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

        for (int i = 0; i < ForestRaidAnchors.ENEMIES.size() && budget > 0; i++) {
            if (raid.enemies.get(i)) {
                continue;
            }
            BlockPos center = ForestLandmarks.position(ForestRaidAnchors.ENEMIES.get(i));
            if (!near(player, center) || !level.hasChunkAt(center)) {
                continue;
            }
            BlockPos place = findFloor(level, center, 4, true);
            if (place != null && raid.spawnGuard(level, place)) {
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
            if (!ForestPlan.inside(pos.getX(), pos.getZ()) || !level.hasChunkAt(pos)
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

    private boolean spawnGuard(ServerLevel level, BlockPos pos) {
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
        guard.setCustomName(Component.literal("Garde de la Forêt Noire"));
        guard.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        guard.setDropChance(EquipmentSlot.MAINHAND, 0);
        guard.setDropChance(EquipmentSlot.CHEST, 0);
        ForestCombat.arm(guard);
        return level.addFreshEntity(guard);
    }

    private boolean existingCrate(ServerLevel level, BlockPos center, int index) {
        for (BlockPos pos : BlockPos.withinManhattan(center, 2, 0, 2)) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                CompoundTag data = barrel.getPersistentData();
                if (matches(data, CRATE) && data.contains(CRATE_INDEX) && data.getInt(CRATE_INDEX) == index) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean putCrate(ServerLevel level, BlockPos pos, String name, Random random, int index) {
        if (!ForestProtection.setRaidBarrel(level, pos, Blocks.BARREL.defaultBlockState())) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
            return false;
        }
        barrel.getPersistentData().putUUID(CRATE, id);
        boolean body = index < 0;
        if (!body) {
            barrel.getPersistentData().putInt(CRATE_INDEX, index);
        }
        ItemStack labeled = new ItemStack(Items.BARREL);
        labeled.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        barrel.applyComponentsFromItemStack(labeled);
        barrel.setItem(random.nextInt(9), new ItemStack(Items.BREAD, 1 + random.nextInt(body ? 2 : 4)));
        int supply = 2 + random.nextInt(5);
        ItemStack ammo = random.nextBoolean() ? AmmoItemBuilder.create().setId(PISTOL_AMMO)
            .setCount(supply).build() : ItemStack.EMPTY;
        barrel.setItem(9 + random.nextInt(9), ammo.isEmpty() ? new ItemStack(Items.IRON_INGOT, supply) : ammo);
        if (random.nextInt(3) == 0) {
            barrel.setItem(18 + random.nextInt(9), new ItemStack(body ? Items.COOKED_BEEF : Items.GOLDEN_CARROT));
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
                    if (block instanceof BarrelBlockEntity && data.hasUUID(CRATE) && !matches(data, CRATE)) {
                        expired.add(block.getBlockPos());
                    }
                }
                for (BlockPos pos : expired) {
                    if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                        barrel.clearContent();
                        ForestProtection.setRaidBarrel(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(ForestExpedition.FOREST)
            && event.getEntity() instanceof Mob mob) {
            if (!(mob instanceof Pillager) || !get(level).matches(mob.getPersistentData(), ENEMY)) {
                event.setCanceled(true);
            }
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
        BlockPos pos = findFloor(level, guard.blockPosition(), 2, true);
        if (pos != null) {
            raid.putCrate(level, pos, "Effets du garde", new Random(level.getRandom().nextLong()), -1);
        }
    }

    public static void onInspect(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(ForestExpedition.FOREST)
            || !(player.level().getBlockEntity(event.getPos()) instanceof BarrelBlockEntity barrel)
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
        ForestRaid raid = new ForestRaid();
        if (tag.hasUUID("id")) {
            raid.id = tag.getUUID("id");
            raid.seed = tag.getLong("seed");
            raid.crates = BitSet.valueOf(tag.getLongArray("crates"));
            raid.enemies = BitSet.valueOf(tag.getLongArray("enemies"));
            raid.locations = ForestRaidAnchors.pickLoot(raid.seed);
        }
        return raid;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (id != null) {
            tag.putUUID("id", id);
            tag.putLong("seed", seed);
            tag.putLongArray("crates", crates.toLongArray());
            tag.putLongArray("enemies", enemies.toLongArray());
        }
        return tag;
    }
}
