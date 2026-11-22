package fr.taczoperations.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class ForestBuildings {
    static void build(ForestChunkGenerator.Painter out, ForestPlan.Site site) {
        int w = site.width();
        int d = site.length();
        int y = site.floor();
        int x = site.x();
        int z = site.z();
        if (!out.intersects(x, z, x + w - 1, z + d - 1)) {
            return;
        }
        switch (site.kind()) {
            case CAMP -> tent(out, x, y, z, w, d);
            case TOWER, GUARD -> tower(out, x, y, z, w, d, site.kind() == ForestPlan.Kind.TOWER ? 9 : 5);
            case TRUCK -> truck(out, x, y, z, w, d);
            case BUNKER -> bunker(out, x, y, z, w, d);
            default -> building(out, site, x, y, z, w, d);
        }
    }

    private static void building(ForestChunkGenerator.Painter out, ForestPlan.Site site,
                                 int x, int y, int z, int w, int d) {
        Block wall = switch (site.kind()) {
            case SAWMILL -> Blocks.SPRUCE_PLANKS;
            case SHED -> Blocks.STRIPPED_DARK_OAK_LOG;
            case WAREHOUSE -> Blocks.GRAY_CONCRETE;
            case STATION -> Blocks.BRICKS;
            default -> Blocks.DARK_OAK_PLANKS;
        };
        Block floor = site.kind() == ForestPlan.Kind.WAREHOUSE ? Blocks.SMOOTH_STONE : Blocks.SPRUCE_PLANKS;
        int top = switch (site.kind()) {
            case SAWMILL -> 6;
            case WAREHOUSE -> 7;
            case SHED -> 5;
            default -> 4;
        };
        int door = site.kind() == ForestPlan.Kind.WAREHOUSE ? 3 : w / 2;
        boolean loadingBay = site.kind() == ForestPlan.Kind.SAWMILL || site.kind() == ForestPlan.Kind.SHED;
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                out.put(x + dx, y, z + dz, floor);
                boolean side = dx == 0 || dx == w - 1;
                boolean end = dz == 0 || dz == d - 1;
                if (side || end) {
                    boolean opening = (dx == door && dz == 0)
                        || (loadingBay && dz == d - 1 && Math.abs(dx - door) <= 1);
                    for (int dy = 1; dy < top; dy++) {
                        if (opening && dy <= (dz == 0 ? 2 : 3)) {
                            continue;
                        }
                        Block material = dy == 2 && (side ? dz % 5 == 2 : dx % 5 == 2)
                            ? Blocks.GLASS : side && end ? Blocks.STRIPPED_SPRUCE_LOG : wall;
                        out.put(x + dx, y + dy, z + dz, material);
                    }
                }
                int roof = top + (site.kind() == ForestPlan.Kind.WAREHOUSE ? 0 : Math.min(dx, w - 1 - dx) / 2);
                out.put(x + dx, y + roof, z + dz,
                    site.kind() == ForestPlan.Kind.STATION ? Blocks.COPPER_BLOCK : Blocks.DEEPSLATE_TILES);
                if (end || side) {
                    for (int dy = top; dy < roof; dy++) {
                        out.put(x + dx, y + dy, z + dz, wall);
                    }
                }
            }
        }
        door(out, x + door, y + 1, z, Direction.NORTH);
        if (site.kind() == ForestPlan.Kind.STATION || site.kind() == ForestPlan.Kind.WAREHOUSE) {
            for (int dx = 1; dx < w - 1; dx++) {
                if (Math.abs(dx - w / 2) > 1) {
                    for (int dy = 1; dy <= 3; dy++) {
                        out.put(x + dx, y + dy, z + d / 2, wall);
                    }
                }
            }
        }
        for (int dz = 3; dz < d - 2; dz += 3) {
            switch (site.kind()) {
                case SAWMILL -> {
                    out.put(x + 2, y + 1, z + dz, Blocks.STRIPPED_SPRUCE_LOG);
                    out.put(x + 3, y + 1, z + dz, Blocks.SMOOTH_STONE);
                    out.put(x + w - 2, y + 1, z + dz, Blocks.SPRUCE_LOG);
                }
                case WAREHOUSE, SHED -> {
                    out.put(x + 2, y + 1, z + dz, Blocks.STRIPPED_OAK_WOOD);
                    out.put(x + 2, y + 2, z + dz, Blocks.SPRUCE_PLANKS);
                    out.put(x + w - 2, y + 1, z + dz, Blocks.STRIPPED_SPRUCE_WOOD);
                    out.put(x + w - 2, y + 2, z + dz, Blocks.STRIPPED_OAK_WOOD);
                }
                default -> {
                    out.put(x + 2, y + 1, z + dz, Blocks.BOOKSHELF);
                    out.put(x + w - 3, y + 1, z + dz, Blocks.SPRUCE_PLANKS);
                    out.put(x + w - 3, y + 1, z + dz + 1, Blocks.SPRUCE_PLANKS);
                }
            }
            if (dz % 6 == 3) {
                out.put(x + 1, y + 1, z + dz, Blocks.TORCH);
            }
        }
    }

    private static void tent(ForestChunkGenerator.Painter out, int x, int y, int z, int w, int d) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                int roof = 2 + Math.min(dx, w - 1 - dx) / 2;
                out.put(x + dx, y, z + dz, Blocks.SPRUCE_PLANKS);
                out.put(x + dx, y + roof, z + dz, Blocks.GREEN_WOOL);
                boolean doorway = dz == 0 && Math.abs(dx - w / 2) <= 1;
                if (dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1) {
                    for (int dy = 1; dy < roof; dy++) {
                        if (!doorway || dy > 2) {
                            out.put(x + dx, y + dy, z + dz, Blocks.GREEN_WOOL);
                        }
                    }
                }
            }
        }
        for (int dz = 2; dz < d - 2; dz += 3) {
            out.put(x + 2, y + 1, z + dz, Blocks.BROWN_CARPET);
            out.put(x + w - 3, y + 1, z + dz, Blocks.STRIPPED_OAK_WOOD);
        }
        out.put(x + w / 2 + 2, y + 1, z + d - 2, Blocks.TORCH);
    }

    private static void tower(ForestChunkGenerator.Painter out, int x, int y, int z, int w, int d, int h) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                if ((dx == 0 || dx == w - 1) && (dz == 0 || dz == d - 1)) {
                    for (int dy = 1; dy <= h + 3; dy++) {
                        out.put(x + dx, y + dy, z + dz, Blocks.SPRUCE_LOG);
                    }
                }
                if (dx != 2 || dz != 1) {
                    out.put(x + dx, y + h, z + dz, Blocks.SPRUCE_PLANKS);
                }
                if (dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1) {
                    out.put(x + dx, y + h + 1, z + dz, Blocks.SPRUCE_PLANKS);
                }
                out.put(x + dx, y + h + 4, z + dz, Blocks.DARK_OAK_PLANKS);
            }
        }
        for (int dy = 1; dy <= h + 1; dy++) {
            out.put(x + 2, y + dy, z, Blocks.SPRUCE_LOG);
            out.put(x + 2, y + dy, z + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        }
        out.put(x + w - 2, y + h + 1, z + d - 2, Blocks.TORCH);
    }

    private static void bunker(ForestChunkGenerator.Painter out, int x, int y, int z, int w, int d) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                boolean boundary = dx == 0 || dz == 0 || dx == w - 1 || dz == d - 1;
                boolean entrance = dx >= 3 && dx <= 5 && dz <= 6;
                out.put(x + dx, y - 7, z + dz, Blocks.DEEPSLATE_BRICKS);
                for (int dy = -6; dy <= -1; dy++) {
                    out.put(x + dx, y + dy, z + dz, boundary && !entrance ? Blocks.DEEPSLATE_BRICKS : Blocks.AIR);
                }
                if (entrance) {
                    out.put(x + dx, y, z + dz, Blocks.AIR);
                    if (dz < 6) {
                        out.put(x + dx, y - dz, z + dz, Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH));
                    } else {
                        out.put(x + dx, y - dz, z + dz, Blocks.STONE_BRICKS);
                    }
                } else {
                    out.put(x + dx, y, z + dz, Blocks.MOSSY_STONE_BRICKS);
                }
            }
        }
        for (int dz = 5; dz < d - 1; dz += 3) {
            out.put(x + w - 2, y - 6, z + dz, Blocks.TORCH);
            out.put(x + w - 3, y - 6, z + dz, Blocks.STRIPPED_SPRUCE_WOOD);
        }
    }

    private static void truck(ForestChunkGenerator.Painter out, int x, int y, int z, int w, int d) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                if ((dx == 0 || dx == w - 1) && (dz == 1 || dz == d - 3)) {
                    out.put(x + dx, y + 1, z + dz, Blocks.BLACK_CONCRETE);
                }
                out.put(x + dx, y + 2, z + dz, Blocks.GRAY_CONCRETE);
                if (dz < 4 || dx == 0 || dx == w - 1) {
                    out.put(x + dx, y + 3, z + dz, dz == 0 ? Blocks.GLASS : Blocks.GREEN_TERRACOTTA);
                }
                if (dz < 4) {
                    out.put(x + dx, y + 4, z + dz, Blocks.GREEN_TERRACOTTA);
                }
                if (dz == d - 1 && dx > 0 && dx < w - 1) {
                    out.put(x + dx, y + 1, z + dz, Blocks.STONE_BRICKS);
                }
            }
        }
    }

    private static void door(ForestChunkGenerator.Painter out, int x, int y, int z, Direction direction) {
        var lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, direction);
        out.put(x, y, z, lower);
        out.put(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private ForestBuildings() {}
}
