package fr.taczoperations.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ForestChunkGenerator extends ChunkGenerator {
    public static final MapCodec<ForestChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter(ForestChunkGenerator::getBiomeSource))
            .apply(instance, ForestChunkGenerator::new));
    public static final int FOUNDATION = 0;

    public ForestChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public net.minecraft.world.level.chunk.ChunkGeneratorStructureState createState(
        net.minecraft.core.HolderLookup<net.minecraft.world.level.levelgen.structure.StructureSet> sets,
        RandomState random, long seed) {
        return net.minecraft.world.level.chunk.ChunkGeneratorStructureState.createForFlat(
            random, seed, biomeSource, java.util.stream.Stream.empty());
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                       StructureManager structures, ChunkAccess chunk) {
        Painter out = new Painter(chunk);
        if (!out.intersects(0, 0, ForestPlan.SIZE - 1, ForestPlan.SIZE - 1)) {
            return CompletableFuture.completedFuture(chunk);
        }
        return CompletableFuture.supplyAsync(() -> {
            for (int x = out.minX; x < out.minX + 16; x++) {
                for (int z = out.minZ; z < out.minZ + 16; z++) {
                    if (ForestPlan.edge(x, z) >= 0) {
                        terrain(out, x, z);
                    }
                }
            }
            plantTrees(out);
            for (ForestPlan.Site site : ForestPlan.SITES) {
                ForestBuildings.build(out, site);
            }
            for (ForestPlan.Point point : ForestPlan.ENTRIES) {
                landmark(out, point, Blocks.MOSSY_STONE_BRICKS);
            }
            landmark(out, ForestPlan.EXTRACTION, Blocks.GOLD_BLOCK);
            landmark(out, ForestPlan.CONDITIONAL_EXIT, Blocks.GOLD_BLOCK);
            landmark(out, ForestPlan.RELAY, Blocks.LIGHTNING_ROD);
            roadsideDetails(out);
            Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
            return chunk;
        }, Util.backgroundExecutor());
    }

    private static void terrain(Painter out, int x, int z) {
        int y = ForestPlan.ground(x, z);
        int track = ForestPlan.track(x, z);
        boolean wet = y < ForestPlan.WATER;
        boolean rock = !wet && ForestPlan.noise(x, z, 39, 72) > .57;
        long variant = ForestPlan.hash(x, z, 111) & 15L;
        Block surface = wet ? (variant < 8 ? Blocks.GRAVEL : Blocks.SAND)
            : track > 0 ? (variant < 3 ? Blocks.GRAVEL : variant < 8 ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT)
            : rock ? (variant < 6 ? Blocks.ANDESITE : Blocks.STONE)
            : variant < 2 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;

        out.put(x, FOUNDATION, z, Blocks.BEDROCK);
        for (int depth = FOUNDATION + 1; depth < y; depth++) {
            out.put(x, depth, z, depth > y - 4 ? Blocks.DIRT : Blocks.STONE);
        }
        out.put(x, y, z, surface);
        if (wet) {
            for (int depth = y + 1; depth <= ForestPlan.WATER; depth++) {
                out.put(x, depth, z, Blocks.WATER);
            }
        }
        if (track > 0) {
            int deck = ForestPlan.WATER + 2;
            if (y < deck) {
                for (int height = y + 1; height < deck; height++) {
                    if (!wet || ((x + z) & 7) == 0) {
                        out.put(x, height, z, Blocks.DARK_OAK_LOG);
                    }
                }
                out.put(x, deck, z, Blocks.SPRUCE_PLANKS);
            }
        } else if (!wet && !rock && !ForestPlan.clear(x, z) && ForestPlan.edge(x, z) > 4 && variant == 0) {
            out.put(x, y + 1, z, ForestPlan.hash(x, z, 41) % 3 == 0 ? Blocks.FERN : Blocks.SHORT_GRASS);
        }

        int edge = ForestPlan.edge(x, z);
        if (edge <= 1) {
            int wallTop = y + 5;
            Block wall = switch ((x + z) / 120 % 3) {
                case 0 -> Blocks.GRAY_CONCRETE;
                case 1 -> Blocks.COBBLESTONE;
                default -> Blocks.MOSSY_STONE_BRICKS;
            };
            for (int height = y + 1; height <= wallTop; height++) {
                out.put(x, height, z, wall);
            }
            if (edge == 0) {
                for (int height = out.chunk.getMinBuildHeight(); height < FOUNDATION; height++) {
                    out.put(x, height, z, Blocks.BARRIER);
                }
                for (int height = wallTop + 1; height < out.chunk.getMaxBuildHeight(); height++) {
                    out.put(x, height, z, Blocks.BARRIER);
                }
            }
        }
    }

    private static void plantTrees(Painter out) {
        for (int gx = Math.floorDiv(out.minX - 6, 7); gx <= Math.floorDiv(out.minX + 21, 7); gx++) {
            for (int gz = Math.floorDiv(out.minZ - 6, 7); gz <= Math.floorDiv(out.minZ + 21, 7); gz++) {
                long random = ForestPlan.hash(gx, gz, 31);
                int x = gx * 7 + Math.floorMod(random, 7);
                int z = gz * 7 + Math.floorMod(random >>> 10, 7);
                if (ForestPlan.edge(x, z) < 7 || ForestPlan.clear(x, z) || ForestPlan.track(x, z) > 0
                    || ForestPlan.ground(x, z) <= ForestPlan.WATER + 1) {
                    continue;
                }
                double density = ForestPlan.noise(x, z, 86, 39);
                if (((random >>> 25) & 31) > 20 + density * 13) {
                    continue;
                }
                int y = ForestPlan.ground(x, z) + 1;
                int height = 6 + (int) ((random >>> 18) & 7);
                boolean spruce = (random & 3) != 0;
                Block log = spruce ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG;
                BlockState leaves = (spruce ? Blocks.SPRUCE_LEAVES : Blocks.DARK_OAK_LEAVES)
                    .defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int dy = spruce ? 2 : 4; dy <= height + 1; dy++) {
                    int radius = spruce ? Math.min(3, Math.max(0, (height + 2 - dy) / 2)) : (dy == height + 1 ? 2 : 4);
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) + Math.abs(dz) <= radius * 2 - (radius > 1 ? 1 : 0)) {
                                out.putIfAir(x + dx, y + dy, z + dz, leaves);
                            }
                        }
                    }
                }
                for (int dy = 0; dy < height; dy++) {
                    out.put(x, y + dy, z, log);
                }
            }
        }
    }

    private static void landmark(Painter out, ForestPlan.Point point, Block marker) {
        if (!out.intersects(point.x() - 4, point.z() - 4, point.x() + 4, point.z() + 4)) {
            return;
        }
        int y = ForestPlan.surfaceY(point);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = point.x() + dx;
                int z = point.z() + dz;
                for (int below = Math.min(y - 1, ForestPlan.ground(x, z)); below < y - 1; below++) {
                    out.put(x, below, z, Blocks.STONE_BRICKS);
                }
                out.put(x, y - 1, z, dx == 0 && dz == 0 && marker != Blocks.LIGHTNING_ROD ? marker : Blocks.STONE_BRICKS);
                for (int dy = 0; dy <= 3; dy++) {
                    out.put(x, y + dy, z, Blocks.AIR);
                }
                if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
                    out.put(x, y, z, Blocks.TORCH);
                }
            }
        }
        if (marker == Blocks.LIGHTNING_ROD) {
            out.put(point.x(), y, point.z(), Blocks.COPPER_BLOCK);
            out.put(point.x(), y + 1, point.z(), Blocks.LIGHTNING_ROD);
        }
    }

    private static void roadsideDetails(Painter out) {
        if (out.intersects(214, 324, 236, 339)) {
            for (int x = 214; x < 237; x++) {
                for (int z = 324; z < 340; z += 4) {
                    int y = ForestPlan.ground(x, z) + 1;
                    out.put(x, y, z, Blocks.SPRUCE_LOG.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
                    if (z % 8 == 0 && x > 216 && x < 235) {
                        out.put(x, y + 1, z, Blocks.SPRUCE_LOG.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X));
                    }
                }
            }
        }
        if (!out.intersects(615, 199, 633, 199)) {
            return;
        }
        for (int x = 615; x < 634; x += 2) {
            int z = 199;
            if (x < 621 || x > 627) {
                out.put(x, ForestPlan.ground(x, z) + 1, z, Blocks.GRAY_CONCRETE);
            }
        }
    }

    static final class Painter {
        private final ChunkAccess chunk;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        private final int minX;
        private final int minZ;

        Painter(ChunkAccess chunk) {
            this.chunk = chunk;
            minX = chunk.getPos().getMinBlockX() - ForestPlan.ORIGIN;
            minZ = chunk.getPos().getMinBlockZ() - ForestPlan.ORIGIN;
        }

        boolean intersects(int x1, int z1, int x2, int z2) {
            return x2 >= minX && z2 >= minZ && x1 <= minX + 15 && z1 <= minZ + 15;
        }

        void put(int x, int y, int z, Block block) {
            put(x, y, z, block.defaultBlockState());
        }

        void put(int x, int y, int z, BlockState state) {
            if (intersects(x, z, x, z)) {
                chunk.setBlockState(cursor.set(x + ForestPlan.ORIGIN, y, z + ForestPlan.ORIGIN), state, false);
            }
        }

        void putIfAir(int x, int y, int z, BlockState state) {
            if (intersects(x, z, x, z)
                && chunk.getBlockState(cursor.set(x + ForestPlan.ORIGIN, y, z + ForestPlan.ORIGIN)).isAir()) {
                chunk.setBlockState(cursor, state, false);
            }
        }
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getSeaLevel() { return ForestPlan.WATER; }

    @Override
    public int getMinY() { return -64; }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        if (!ForestPlan.inside(x, z)) {
            return level.getMinBuildHeight();
        }
        return ForestPlan.surfaceY(new ForestPlan.Point(x - ForestPlan.ORIGIN, z - ForestPlan.ORIGIN));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        if (ForestPlan.inside(x, z)) {
            int y = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
            Arrays.fill(column, FOUNDATION - level.getMinBuildHeight(), y - level.getMinBuildHeight(),
                Blocks.STONE.defaultBlockState());
            for (int water = y; water <= ForestPlan.WATER; water++) {
                column[water - level.getMinBuildHeight()] = Blocks.WATER.defaultBlockState();
            }
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add(ForestPlan.inside(pos.getX(), pos.getZ()) ? "Forêt Noire · 900 × 900" : "TaCZ Operations · vide");
    }
}
