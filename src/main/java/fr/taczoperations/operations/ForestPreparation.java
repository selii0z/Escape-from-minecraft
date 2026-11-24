package fr.taczoperations.operations;

import com.mojang.logging.LogUtils;
import fr.taczoperations.worldgen.ForestChunkGenerator;
import fr.taczoperations.worldgen.ForestPlan;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ForestPreparation extends SavedData {
    private static final int FIRST_CHUNK = ForestPlan.ORIGIN >> 4;
    private static final int WIDTH = (ForestPlan.EDGE >> 4) - FIRST_CHUNK + 1;
    private static final int CHUNKS = WIDTH * WIDTH;
    private static final TicketType<ChunkPos> PREPARATION = TicketType.create(
        "taczoperations_forest_preparation", Comparator.comparingLong(ChunkPos::toLong));
    private static final Factory<ForestPreparation> FACTORY = new Factory<>(
        ForestPreparation::new, ForestPreparation::read);

    private int nextChunk;
    private int nextAttempt;
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;

    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        var forest = server.getLevel(ForestExpedition.FOREST);
        if (forest == null || !(forest.getChunkSource().getGenerator() instanceof ForestChunkGenerator)) {
            return;
        }
        var preparation = server.overworld().getDataStorage().computeIfAbsent(
            FACTORY, "taczoperations_forest_preparation_v2");
        if (preparation.nextChunk >= CHUNKS) {
            return;
        }

        var cache = forest.getChunkSource();
        int tick = server.getTickCount();
        int row = preparation.nextChunk / WIDTH;
        int column = preparation.nextChunk % WIDTH;
        if ((row & 1) == 1) {
            column = WIDTH - 1 - column;
        }
        ChunkPos pos = new ChunkPos(FIRST_CHUNK + column, FIRST_CHUNK + row);
        if (preparation.pending == null) {
            if (tick < preparation.nextAttempt || tick % 10 != 0 || !event.hasTime()) {
                return;
            }
            cache.addRegionTicket(PREPARATION, pos, 0, pos);
            preparation.pending = CompletableFuture
                .supplyAsync(() -> cache.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true), Util.backgroundExecutor())
                .thenCompose(future -> future);
            return;
        }
        if (!preparation.pending.isDone()) {
            return;
        }

        try {
            var result = preparation.pending.join();
            if (result.isSuccess()) {
                preparation.nextChunk++;
                preparation.setDirty();
            } else {
                preparation.nextAttempt = tick + 100;
                LogUtils.getLogger().warn("Forest chunk {} will be retried: {}", pos, result.getError());
            }
        } catch (CompletionException exception) {
            preparation.nextAttempt = tick + 100;
            LogUtils.getLogger().warn("Forest chunk {} failed and will be retried", pos, exception);
        } finally {
            cache.removeRegionTicket(PREPARATION, pos, 0, pos);
            preparation.pending = null;
        }
    }

    private static ForestPreparation read(CompoundTag tag, HolderLookup.Provider registries) {
        ForestPreparation preparation = new ForestPreparation();
        preparation.nextChunk = Math.clamp(tag.getInt("next_chunk"), 0, CHUNKS);
        return preparation;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_chunk", nextChunk);
        return tag;
    }
}
