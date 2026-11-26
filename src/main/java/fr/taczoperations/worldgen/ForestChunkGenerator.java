package fr.taczoperations.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        Painter industrial = new Painter(chunk, IndustrialZone.ORIGIN);
        if (industrial.intersects(0, 0, IndustrialZone.SIZE - 1, IndustrialZone.SIZE - 1)) {
            return CompletableFuture.supplyAsync(() -> {
                IndustrialZone.generate(industrial);
                Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                    Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
                return chunk;
            }, Util.backgroundExecutor());
        }
        Painter out = new Painter(chunk, ForestPlan.ORIGIN);
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
            extraction(out,ForestPlan.EXTRACTION,ExtractionLayout.Style.FOREST);
            extraction(out,ForestPlan.CONDITIONAL_EXIT,ExtractionLayout.Style.RELAY);
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
            : track > 0 ? trailSurface(x, z, variant)
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

    private static Block trailSurface(int x, int z, long variant) {
        double patch = ForestPlan.noise(x, z, 12, 310);
        if (patch > .42 && variant < 7) return Blocks.GRAVEL;
        if (patch < -.38 && variant < 8) return Blocks.PODZOL;
        if (variant == 0) return Blocks.ANDESITE;
        if (variant < 4) return Blocks.ROOTED_DIRT;
        if (variant < 7) return Blocks.COARSE_DIRT;
        if (variant < 13) return Blocks.DIRT_PATH;
        return Blocks.DIRT;
    }

    private static void plantTrees(Painter out) {
        // Each candidate is deterministic across chunk boundaries. The margin
        // covers all crowns and branches even when the trunk is in another chunk.
        for (int gx = Math.floorDiv(out.minX - 7, 10); gx <= Math.floorDiv(out.minX + 22, 10); gx++) {
            for (int gz = Math.floorDiv(out.minZ - 7, 10); gz <= Math.floorDiv(out.minZ + 22, 10); gz++) {
                long random = ForestPlan.hash(gx, gz, 31);
                int x = gx * 10 + 1 + Math.floorMod(random, 8);
                int z = gz * 10 + 1 + Math.floorMod(random >>> 10, 8);
                if (ForestPlan.edge(x, z) < 8 || ForestPlan.clear(x, z)
                    || ForestPlan.nearTrack(x, z, 4) || ForestPlan.ground(x, z) <= ForestPlan.WATER + 1) continue;
                double density = ForestPlan.noise(x, z, 76, 39);
                double clusters = ForestPlan.noise(x, z, 27, 315);
                if (((random >>> 25) & 31) > 17 + density * 10 + clusters * 5) continue;
                int y = ForestPlan.ground(x, z) + 1;
                int kind = Math.floorMod(random >>> 33, 16);
                if (kind == 0) {
                    // Broken snags: leave open views and irregular silhouettes.
                    int height = 3 + (int) ((random >>> 18) & 3);
                    for (int dy = 0; dy < height; dy++) out.put(x, y + dy, z, Blocks.STRIPPED_SPRUCE_LOG);
                    out.putIfAir(x + 1, y + height - 2, z, horizontalLog(Blocks.SPRUCE_LOG, true));
                    continue;
                }
                if (kind == 1) {
                    // A stump and a short fallen trunk; do not cross a trail.
                    out.put(x, y, z, Blocks.SPRUCE_LOG);
                    boolean alongX = (random & 1) == 0;
                    int length = 3 + (int) ((random >>> 18) & 3);
                    for (int n = 1; n <= length; n++) {
                        int xx = x + (alongX ? n : 0), zz = z + (alongX ? 0 : n);
                        if (ForestPlan.edge(xx, zz) < 4 || ForestPlan.clear(xx, zz)
                            || ForestPlan.nearTrack(xx, zz, 1)
                            || Math.abs(ForestPlan.ground(xx, zz) + 1 - y) > 1) break;
                        out.putIfAir(xx, ForestPlan.ground(xx, zz) + 1, zz, horizontalLog(Blocks.SPRUCE_LOG, alongX));
                    }
                    continue;
                }
                boolean broadleaf = kind >= 13;
                boolean young = kind >= 9 && kind <= 12;
                int height = broadleaf ? 8 + (int) ((random >>> 18) & 3) : young ? 5 + (int)((random >>> 18)&3) : 11 + (int) ((random >>> 18) & 7);
                Block log = broadleaf ? Blocks.DARK_OAK_LOG : Blocks.SPRUCE_LOG;
                BlockState leaves = (broadleaf ? Blocks.DARK_OAK_LEAVES : Blocks.SPRUCE_LEAVES)
                    .defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int dy = 0; dy < height; dy++) out.put(x, y + dy, z, log);
                if (broadleaf) {
                    // Rounded, overlapping lobes rather than a square canopy.
                    foliage(out, x, y + height - 1, z, 3, 2, leaves, random);
                    int sx = (random & 1) == 0 ? 1 : -1;
                    int sz = (random & 2) == 0 ? 1 : -1;
                    foliage(out, x + 2 * sx, y + height - 2, z, 2, 2, leaves, random + 3);
                    foliage(out, x, y + height - 2, z + 2 * sz, 2, 2, leaves, random + 7);
                    out.putIfAir(x + sx, y + height - 3, z, horizontalLog(log, true));
                    out.putIfAir(x, y + height - 3, z + sz, horizontalLog(log, false));
                } else {
                    // Conifer branches start high enough to let players move and
                    // see between trunks. Tapered, alternating whorls break symmetry.
                    int crownStart = Math.max(young?2:5,height/2) + (int) ((random >>> 39) & 1);
                    for (int dy = crownStart; dy <= height + 1; dy++) {
                        int remaining = height + 1 - dy;
                        int radius = remaining <= 1 ? remaining : Math.min(young?2:3, 1 + remaining / 4);
                        if (remaining > 2 && Math.floorMod(dy + (int) random, 3) != 0) radius = Math.max(1, radius - 1);
                        int sx = radius > 1 ? (int) ((ForestPlan.hash(x, z, dy) >>> 20) % 3) - 1 : 0;
                        int sz = radius > 1 ? (int) ((ForestPlan.hash(x, z, dy + 71) >>> 20) % 3) - 1 : 0;
                        foliage(out, x + sx, y + dy, z + sz, radius, 0, leaves, random + dy);
                    }
                }
            }
        }
        groundDetails(out);
    }

    private static BlockState horizontalLog(Block log, boolean alongX) {
        return log.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
            alongX ? Direction.Axis.X : Direction.Axis.Z);
    }

    private static void foliage(Painter out, int x, int y, int z, int radius, int vertical, BlockState leaves, long salt) {
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = (dx * dx + dz * dz) / (double) Math.max(1, radius * radius)
                        + dy * dy / (double) Math.max(1, vertical * vertical);
                    double roughness = (ForestPlan.hash(x + dx, z + dz, salt + dy) & 7) / 14.0;
                    if (distance <= 1.15 - roughness || dx == 0 && dz == 0) {
                        out.putIfAir(x + dx, y + dy, z + dz, leaves);
                    }
                }
            }
        }
    }

    private static void groundDetails(Painter out) {
        for (int x = out.minX; x < out.minX + 16; x++) {
            for (int z = out.minZ; z < out.minZ + 16; z++) {
                if (ForestPlan.edge(x, z) < 7 || ForestPlan.clear(x, z) || ForestPlan.track(x, z) > 0
                    || ForestPlan.ground(x, z) <= ForestPlan.WATER + 1) continue;
                int y = ForestPlan.ground(x, z) + 1;
                int roll = Math.floorMod(ForestPlan.hash(x, z, 768), 100);
                boolean roadside = ForestPlan.nearTrack(x, z, 3);
                if (roll < (roadside ? 10 : 6)) {
                    out.putIfAir(x, y, z, (roll % 3 == 0 ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
                } else if (roll == 12 && ForestPlan.noise(x, z, 19, 733) > .1) {
                    out.putIfAir(x, y, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                } else if (roll == 17 && !roadside && ForestPlan.noise(x,z,11,912)>.25) {
                    out.putIfAir(x, y, z, Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                } else if(roll==23 && !roadside && ForestPlan.noise(x,z,18,921)>.3){
                    out.putIfAir(x,y,z,Blocks.BROWN_MUSHROOM.defaultBlockState());
                }
            }
        }
    }

    private static void extraction(Painter out,ForestPlan.Point point,ExtractionLayout.Style style){
        landmark(out,point,Blocks.STONE_BRICKS);
        if(!out.intersects(point.x()-4,point.z()-4,point.x()+4,point.z()+4))return;
        int y=ForestPlan.surfaceY(point);
        ExtractionLayout.paint(style,(dx,dy,dz,block)->out.put(point.x()+dx,y+dy,point.z()+dz,block));
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
        final int minX;
        final int minZ;
        private final int origin;

        Painter(ChunkAccess chunk, int origin) {
            this.chunk = chunk;
            this.origin = origin;
            minX = chunk.getPos().getMinBlockX() - origin;
            minZ = chunk.getPos().getMinBlockZ() - origin;
        }

        boolean intersects(int x1, int z1, int x2, int z2) {
            return x2 >= minX && z2 >= minZ && x1 <= minX + 15 && z1 <= minZ + 15;
        }

        void put(int x, int y, int z, Block block) {
            put(x, y, z, block.defaultBlockState());
        }

        void put(int x, int y, int z, BlockState state) {
            if (intersects(x, z, x, z)) {
                chunk.setBlockState(cursor.set(x + origin, y, z + origin), state, false);
            }
        }

        void putIfAir(int x, int y, int z, BlockState state) {
            if (intersects(x, z, x, z)
                && chunk.getBlockState(cursor.set(x + origin, y, z + origin)).isAir()) {
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
        if (IndustrialZone.inside(x, z)) return IndustrialZone.FLOOR + 1;
        if (!ForestPlan.inside(x, z)) {
            return level.getMinBuildHeight();
        }
        return ForestPlan.surfaceY(new ForestPlan.Point(x - ForestPlan.ORIGIN, z - ForestPlan.ORIGIN));
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        BlockState[] column = new BlockState[level.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        if (ForestPlan.inside(x, z) || IndustrialZone.inside(x, z)) {
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
