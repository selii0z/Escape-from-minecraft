package fr.taczoperations.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

import java.util.ArrayList;
import java.util.List;

public final class IndustrialZone {
    public static final int ORIGIN = 16384;
    public static final int SIZE = 650;
    public static final int FLOOR = 69;
    public static final ForestPlan.Point ENTRY = new ForestPlan.Point(64, 554);
    public static final ForestPlan.Point EXIT = new ForestPlan.Point(586, 94);
    public static final List<ForestPlan.Point> GUARDS = List.of(
        new ForestPlan.Point(125, 540), new ForestPlan.Point(181, 448), new ForestPlan.Point(226, 396),
        new ForestPlan.Point(278, 340), new ForestPlan.Point(331, 288), new ForestPlan.Point(376, 240),
        new ForestPlan.Point(424, 179), new ForestPlan.Point(510, 145), new ForestPlan.Point(554, 120));

    // Buildings form three legible districts around the road grid:
    // logistics (west), factory campus (centre), railway/depot (east).
    private record Building(int x, int z, int width, int depth, int height, Block wall, boolean factory) {}
    private static final List<Building> BUILDINGS = List.of(
        new Building(88, 420, 68, 48, 13, Blocks.STONE_BRICKS, false),
        new Building(168, 420, 66, 48, 14, Blocks.BRICKS, false),
        new Building(88, 488, 68, 42, 12, Blocks.GRAY_CONCRETE, false),
        new Building(168, 488, 66, 42, 12, Blocks.TUFF_BRICKS, false),
        new Building(260, 295, 94, 76, 23, Blocks.BRICKS, true),
        new Building(372, 295, 68, 76, 19, Blocks.POLISHED_ANDESITE, true),
        new Building(260, 390, 86, 48, 15, Blocks.STONE_BRICKS, false),
        new Building(365, 390, 75, 48, 16, Blocks.DEEPSLATE_BRICKS, false),
        new Building(472, 170, 78, 52, 17, Blocks.GRAY_CONCRETE, false),
        new Building(472, 242, 78, 42, 13, Blocks.BRICKS, false),
        new Building(472, 305, 78, 48, 14, Blocks.STONE_BRICKS, false),
        new Building(472, 374, 78, 48, 12, Blocks.POLISHED_ANDESITE, false),
        new Building(565, 115, 22, 20, 9, Blocks.DEEPSLATE_BRICKS, false));

    public static boolean inside(int x, int z) {
        return x >= ORIGIN && z >= ORIGIN && x < ORIGIN + SIZE && z < ORIGIN + SIZE;
    }

    public static List<ForestRaidAnchors.Mark> loot(long seed) {
        var points = new ArrayList<ForestRaidAnchors.Mark>();
        for (Building b : BUILDINGS) {
            points.add(new ForestRaidAnchors.Mark(b.x + b.width / 2, FLOOR + 1, b.z + 4));
            points.add(new ForestRaidAnchors.Mark(b.x + 5, FLOOR + 1, b.z + b.depth - 4));
            points.add(new ForestRaidAnchors.Mark(b.x + b.width - 5, FLOOR + 1, b.z + b.depth / 2));
        }
        java.util.Collections.shuffle(points, new java.util.Random(seed));
        return List.copyOf(points.subList(0, 16));
    }

    private static boolean road(int x, int z) {
        return (x >= 55 && x <= 595 && (Math.abs(z - 405) <= 7 || Math.abs(z - 290) <= 7))
            || (z >= 95 && z <= 575 && (Math.abs(x - 248) <= 7 || Math.abs(x - 455) <= 7))
            || (z >= 420 && z <= 570 && Math.abs(x - 75) <= 6);
    }

    private static boolean yard(int x, int z) {
        return (x >= 75 && x <= 244 && z >= 410 && z <= 540)
            || (x >= 252 && x <= 448 && z >= 282 && z <= 447)
            || (x >= 464 && x <= 558 && z >= 158 && z <= 432);
    }

    static void generate(ForestChunkGenerator.Painter out) {
        for (int x = out.minX; x < out.minX + 16; x++) {
            for (int z = out.minZ; z < out.minZ + 16; z++) {
                if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
                boolean asphalt = road(x, z);
                boolean paved = yard(x, z);
                long noise = ForestPlan.hash(x, z, 400);
                Block surface = asphalt ? ((noise & 63) == 0 ? Blocks.ANDESITE : Blocks.GRAY_CONCRETE)
                    : paved ? ((noise & 31) == 0 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS)
                    : (noise & 7) == 0 ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
                out.put(x, 0, z, Blocks.BEDROCK);
                for (int y = 1; y < FLOOR; y++) out.put(x, y, z, Blocks.STONE);
                out.put(x, FLOOR, z, surface);
                int edge = Math.min(Math.min(x, z), Math.min(SIZE - 1 - x, SIZE - 1 - z));
                if (edge <= 1) {
                    for (int y = FLOOR + 1; y <= FLOOR + 6; y++) out.put(x, y, z, Blocks.DEEPSLATE_BRICKS);
                    if (edge == 0) {
                        for (int y = -64; y < 0; y++) out.put(x, y, z, Blocks.BARRIER);
                        for (int y = FLOOR + 7; y < 320; y++) out.put(x, y, z, Blocks.BARRIER);
                    }
                }
                // Road markings, drains and street lighting follow the grid.
                if (asphalt && (z == 405 || z == 290) && x % 12 < 5) {
                    out.put(x, FLOOR, z, Blocks.YELLOW_CONCRETE);
                }
                if (asphalt && (x == 248 || x == 455) && z % 12 < 5) {
                    out.put(x, FLOOR, z, Blocks.YELLOW_CONCRETE);
                }
                if ((x == 239 || x == 257 || x == 446 || x == 464)
                    && z >= 110 && z <= 565 && z % 38 == 0) {
                    streetlight(out, x, z);
                }
                if ((z == 396 || z == 414 || z == 281 || z == 299)
                    && x >= 80 && x <= 580 && x % 45 == 0) {
                    streetlight(out, x, z);
                }
                // The depot has continuous tracks, ballast and loading platforms.
                if (x >= 465 && x <= 585 && z >= 130 && z <= 150) {
                    out.put(x, FLOOR, z, Blocks.GRAVEL);
                    if (z == 136 || z == 143) {
                        out.put(x, FLOOR + 1, z, Blocks.RAIL.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                                net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
                    }
                    if (x % 3 == 0 && z >= 135 && z <= 144) out.put(x, FLOOR, z, Blocks.DARK_OAK_PLANKS);
                }
                // Container stacks and cargo pallets, laid out in numbered bays.
                if (x >= 565 && x <= 618 && z >= 175 && z <= 340) {
                    int bx = (x - 565) % 15, bz = (z - 175) % 27;
                    if (bx < 11 && bz < 19) {
                        Block cargo = ((x - 565) / 15 + (z - 175) / 27) % 3 == 0
                            ? Blocks.RED_TERRACOTTA : ((x - 565) / 15) % 2 == 0
                            ? Blocks.BLUE_TERRACOTTA : Blocks.ORANGE_TERRACOTTA;
                        for (int y = FLOOR + 1; y <= FLOOR + 5 + (((x - 565) / 15) % 2) * 5; y++) {
                            if (bx == 0 || bx == 10 || bz == 0 || bz == 18 || y == FLOOR + 5 || y == FLOOR + 10)
                                out.put(x, y, z, cargo);
                        }
                    }
                }
                // Loading-yard cover is grouped into realistic pallet stacks.
                // Keep roads and warehouse thresholds clear for navigation.
                if (x >= 82 && x <= 234 && z >= 545 && z <= 560
                    && !asphalt && (x - 82) % 17 < 9 && (z - 545) % 8 < 5) {
                    int localX = (x - 82) % 17;
                    int localZ = (z - 545) % 8;
                    out.put(x, FLOOR, z, Blocks.SPRUCE_PLANKS);
                    if (localX > 0 && localX < 8 && localZ > 0 && localZ < 4) {
                        out.put(x, FLOOR + 1, z, Blocks.BARREL);
                        if (((x - 82) / 17) % 3 == 0) out.put(x, FLOOR + 2, z, Blocks.BARREL);
                    }
                }
                // Low concrete barricades and scattered debris provide cover
                // between the logistics, factory and railway districts.
                if (!asphalt && !paved && x > 85 && x < 615 && z > 115 && z < 560
                    && (ForestPlan.hash(x / 5, z / 5, 971) & 127) == 0
                    && x % 5 <= 2 && z % 5 == 0) {
                    out.put(x, FLOOR + 1, z, Blocks.STONE_BRICK_WALL);
                }
                // Perimeter fencing around industrial compounds, with road gates.
                boolean entryGate = z == 570 && x >= 54 && x <= 91;
                boolean exitGate = z == 100 && x >= 573 && x <= 602;
                if ((x == 68 || x == 626 || z == 100 || z == 570)
                    && x >= 68 && x <= 626 && z >= 100 && z <= 570
                    && !road(x, z) && !entryGate && !exitGate) {
                    out.put(x, FLOOR + 1, z, Blocks.COBBLESTONE_WALL);
                    out.put(x, FLOOR + 2, z, Blocks.IRON_BARS);
                    out.put(x, FLOOR + 3, z, Blocks.IRON_BARS);
                }
            }
        }
        for (Building building : BUILDINGS) building(out, building);
        out.put(EXIT.x(), FLOOR, EXIT.z(), Blocks.GOLD_BLOCK);
        out.put(EXIT.x() + 1, FLOOR + 1, EXIT.z(), Blocks.TORCH);
        out.put(ENTRY.x(), FLOOR, ENTRY.z(), Blocks.POLISHED_ANDESITE);
    }

    private static void streetlight(ForestChunkGenerator.Painter out, int x, int z) {
        for (int y = FLOOR + 1; y <= FLOOR + 6; y++) out.put(x, y, z, Blocks.IRON_BARS);
        out.put(x, FLOOR + 7, z, Blocks.SEA_LANTERN);
        out.put(x, FLOOR + 8, z, Blocks.POLISHED_DEEPSLATE);
    }

    private static void building(ForestChunkGenerator.Painter out, Building b) {
        if (!out.intersects(b.x, b.z, b.x + b.width - 1, b.z + b.depth - 1)) return;
        for (int dx = Math.max(0, out.minX - b.x); dx < Math.min(b.width, out.minX + 16 - b.x); dx++) {
            for (int dz = Math.max(0, out.minZ - b.z); dz < Math.min(b.depth, out.minZ + 16 - b.z); dz++) {
                int x = b.x + dx, z = b.z + dz;
                boolean side = dx == 0 || dx == b.width - 1 || dz == 0 || dz == b.depth - 1;
                boolean doorway = (dz == 0 || dz == b.depth - 1) && Math.abs(dx - b.width / 2) < 4;
                boolean sideDoor = (dx == 0 || dx == b.width - 1)
                    && Math.abs(dz - b.depth / 2) < 2;
                boolean column = side && (dx % 12 == 0 || dz % 12 == 0);
                boolean window = side && !doorway && !sideDoor
                    && (dx % 12 >= 3 && dx % 12 <= 8 || dz % 12 >= 3 && dz % 12 <= 8);
                out.put(x, FLOOR, z, Blocks.SMOOTH_STONE);
                if (side) {
                    for (int dy = 1; dy < b.height; dy++) {
                        if ((doorway && dy <= 5) || (sideDoor && dy <= 3)) continue;
                        Block facade = column ? Blocks.POLISHED_DEEPSLATE
                            : dy >= 5 && dy <= 8 && window ? Blocks.GRAY_STAINED_GLASS
                            : dy == 1 || dy == b.height - 1 ? Blocks.DEEPSLATE_BRICKS
                            : b.wall;
                        out.put(x, FLOOR + dy, z, facade);
                    }
                } else if (b.factory && (dx <= 9 || dx >= b.width - 10 || dz >= b.depth - 12)) {
                    if (dx > 3 || dz < 7 || dz > 12) out.put(x, FLOOR + 6, z, Blocks.SMOOTH_STONE);
                    if (dx == 9 || dx == b.width - 10) out.put(x, FLOOR + 7, z, Blocks.IRON_BARS);
                }
                if (b.factory && dx >= 1 && dx <= 3 && dz >= 7 && dz <= 12) {
                    out.put(x, FLOOR + dz - 6, z, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
                }
                if (!side && dx % 11 == 3 && dz % 11 == 4 && Math.abs(dx - b.width / 2) > 3) {
                    out.put(x, FLOOR + 1, z, Blocks.IRON_BLOCK);
                    out.put(x, FLOOR + 2, z, Blocks.REDSTONE_LAMP);
                }
                // Raised sawtooth roof bays on factories, low parapets elsewhere.
                int roof = FLOOR + b.height + (b.factory && dx % 21 < 10 ? 2 : 0);
                out.put(x, roof, z, Blocks.DEEPSLATE_TILES);
                if (side) out.put(x, roof + 1, z, Blocks.POLISHED_DEEPSLATE);
                if (b.factory && !side && dx % 21 == 9 && dz % 7 == 0)
                    out.put(x, roof + 1, z, Blocks.IRON_BARS);
                // Roof ventilation housings, skylights and factory exhausts.
                if (!side && dx > 5 && dz > 5 && dx < b.width - 6 && dz < b.depth - 6) {
                    if (b.factory && dx % 24 >= 4 && dx % 24 <= 7
                        && dz % 19 >= 4 && dz % 19 <= 7) {
                        out.put(x, roof + 1, z, Blocks.IRON_BLOCK);
                        if (dx % 24 == 4 || dz % 19 == 4)
                            out.put(x, roof + 2, z, Blocks.IRON_BARS);
                    }
                    if (!b.factory && dx % 17 >= 4 && dx % 17 <= 9
                        && dz % 17 >= 4 && dz % 17 <= 8) {
                        out.put(x, roof, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
                    }
                    if (b.factory && dx % 31 == 15 && dz % 29 == 14) {
                        for (int y = roof + 1; y <= roof + 8; y++)
                            out.put(x, y, z, Blocks.DEEPSLATE_BRICKS);
                        out.put(x, roof + 9, z, Blocks.IRON_BARS);
                    }
                }
                // Warehouse interiors: racks and pallets run in aisles,
                // leaving a clear central corridor and accessible entrances.
                if (!side && !b.factory && dx >= 5 && dx < b.width - 5
                    && dz >= 6 && dz < b.depth - 6
                    && Math.abs(dx - b.width / 2) > 5
                    && dx % 13 >= 2 && dx % 13 <= 4 && dz % 9 >= 2 && dz % 9 <= 6) {
                    out.put(x, FLOOR + 1, z, Blocks.SPRUCE_PLANKS);
                    if (dz % 9 == 2 || dz % 9 == 6) {
                        out.put(x, FLOOR + 2, z, Blocks.BARREL);
                        out.put(x, FLOOR + 3, z, Blocks.IRON_TRAPDOOR);
                    }
                }
                if (!side && b.factory && dx >= 14 && dx < b.width - 14
                    && dz >= 10 && dz < b.depth - 14 && dx % 17 <= 3 && dz % 15 <= 3) {
                    out.put(x, FLOOR + 1, z, Blocks.DEEPSLATE_BRICKS);
                    out.put(x, FLOOR + 2, z, Blocks.IRON_BLOCK);
                    out.put(x, FLOOR + 3, z, Blocks.FURNACE);
                    out.put(x, FLOOR + 4, z, Blocks.IRON_TRAPDOOR);
                }
            }
        }
    }

    private IndustrialZone() {}
}
