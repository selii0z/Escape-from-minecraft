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

    private record Building(int x, int z, int width, int depth, int height, Block wall, boolean factory) {}
    private static final List<Building> BUILDINGS = List.of(
        new Building(225, 307, 94, 70, 21, Blocks.BRICKS, true),
        new Building(326, 318, 46, 39, 10, Blocks.GRAY_CONCRETE, false),
        new Building(308, 234, 58, 37, 13, Blocks.CYAN_TERRACOTTA, false),
        new Building(412, 181, 46, 24, 8, Blocks.BRICKS, false),
        new Building(137, 445, 36, 25, 9, Blocks.SMOOTH_STONE, false),
        new Building(497, 111, 25, 20, 7, Blocks.LIGHT_GRAY_CONCRETE, false),
        new Building(565, 115, 16, 15, 6, Blocks.DEEPSLATE_BRICKS, false));

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

    static void generate(ForestChunkGenerator.Painter out) {
        for (int x = out.minX; x < out.minX + 16; x++) {
            for (int z = out.minZ; z < out.minZ + 16; z++) {
                if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) continue;
                boolean road = Math.abs(x - (620 - z)) < 7 || Math.abs(z - 283) < 5;
                var surface = road ? Blocks.GRAY_CONCRETE : (ForestPlan.hash(x, z, 400) & 7) == 0
                    ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
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
                if (x >= 381 && x <= 535 && (z == 158 || z == 163)) {
                    out.put(x, FLOOR, z, Blocks.POLISHED_ANDESITE);
                    out.put(x, FLOOR + 1, z, Blocks.RAIL.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                            net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
                }
                if (x >= 384 && x <= 528 && z >= 155 && z <= 160 && (x - 384) % 24 < 17) {
                    out.put(x, FLOOR + 1, z, Blocks.BLACK_CONCRETE);
                    out.put(x, FLOOR + 2, z, Blocks.SMOOTH_STONE);
                    if (z == 155 || z == 160) out.put(x, FLOOR + 3, z, Blocks.RED_TERRACOTTA);
                }
                if (x >= 368 && x <= 419 && z >= 258 && z <= 299
                    && (x - 368) % 17 < 13 && (z - 258) % 12 < 7) {
                    var wall = (x / 17 + z / 12) % 2 == 0 ? Blocks.BLUE_TERRACOTTA : Blocks.ORANGE_TERRACOTTA;
                    if ((x - 368) % 17 == 0 || (x - 368) % 17 == 12 || (z - 258) % 12 == 0) {
                        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) out.put(x, y, z, wall);
                    }
                    out.put(x, FLOOR + 4, z, wall);
                }
            }
        }
        for (Building b : BUILDINGS) building(out, b);
        out.put(EXIT.x(), FLOOR, EXIT.z(), Blocks.GOLD_BLOCK);
        out.put(EXIT.x() + 1, FLOOR + 1, EXIT.z(), Blocks.TORCH);
        out.put(ENTRY.x(), FLOOR, ENTRY.z(), Blocks.POLISHED_ANDESITE);
    }

    private static void building(ForestChunkGenerator.Painter out, Building b) {
        if (!out.intersects(b.x, b.z, b.x + b.width - 1, b.z + b.depth - 1)) return;
        for (int dx = Math.max(0, out.minX - b.x); dx < Math.min(b.width, out.minX + 16 - b.x); dx++) {
            for (int dz = Math.max(0, out.minZ - b.z); dz < Math.min(b.depth, out.minZ + 16 - b.z); dz++) {
                int x = b.x + dx, z = b.z + dz;
                boolean side = dx == 0 || dx == b.width - 1 || dz == 0 || dz == b.depth - 1;
                boolean doorway = (dz == 0 || dz == b.depth - 1) && Math.abs(dx - b.width / 2) < 4;
                out.put(x, FLOOR, z, Blocks.SMOOTH_STONE);
                if (side) {
                    for (int dy = 1; dy < b.height; dy++) {
                        if (doorway && dy <= 4) continue;
                        out.put(x, FLOOR + dy, z, dy > 3 && dy % 5 == 1 && (dx + dz) % 7 >= 2
                            ? Blocks.WHITE_STAINED_GLASS : (dx + dz) % 10 == 0 ? Blocks.POLISHED_DEEPSLATE : b.wall);
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
                out.put(x, FLOOR + b.height + (b.factory && dx % 21 < 10 ? 2 : 0), z, Blocks.DEEPSLATE_TILES);
            }
        }
    }

    private IndustrialZone() {}
}
