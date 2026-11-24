package fr.taczoperations.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class ForestRaidAnchors {
    public record Mark(int x, int y, int z, int kind) {
        public Mark(int x, int y, int z) { this(x, y, z, 2); }
    }

    public static List<Mark> loot() {
        var marks = new ArrayList<Mark>();
        for (ForestPlan.Site site : ForestPlan.SITES) {
            if (site.kind() == ForestPlan.Kind.TRUCK) continue;
            int y = site.floor() + height(site);
            int middle = site.x() + site.width() / 2;
            int kind = switch (site.kind()) {
                case SAWMILL, SHED, WAREHOUSE -> 1;
                case CAMP, CABIN -> 0;
                case GUARD, TOWER -> 2;
                case BUNKER, STATION -> 3;
                default -> 2;
            };
            marks.add(new Mark(middle, y, site.z() + 2, kind));
            marks.add(new Mark(middle + 1, y, site.z() + site.length() - 2, kind));
            if (site.width() >= 12) marks.add(new Mark(middle - 2, y, site.z() + 4, kind));
        }
        for (ForestPlan.Point spot : OUTDOOR) marks.add(new Mark(spot.x(), ForestPlan.surfaceY(spot), spot.z()));
        return List.copyOf(marks);
    }

    private static final List<ForestPlan.Point> OUTDOOR = List.of(
        new ForestPlan.Point(109,749), new ForestPlan.Point(149,709), new ForestPlan.Point(179,661),
        new ForestPlan.Point(212,585), new ForestPlan.Point(228,496), new ForestPlan.Point(238,397),
        new ForestPlan.Point(245,342), new ForestPlan.Point(257,258), new ForestPlan.Point(297,229),
        new ForestPlan.Point(329,203), new ForestPlan.Point(364,187), new ForestPlan.Point(388,193),
        new ForestPlan.Point(512,220), new ForestPlan.Point(554,212), new ForestPlan.Point(591,214),
        new ForestPlan.Point(634,202), new ForestPlan.Point(712,202), new ForestPlan.Point(754,199),
        new ForestPlan.Point(787,190), new ForestPlan.Point(775,382), new ForestPlan.Point(753,410),
        new ForestPlan.Point(733,425), new ForestPlan.Point(691,446), new ForestPlan.Point(636,453),
        new ForestPlan.Point(592,438), new ForestPlan.Point(549,431), new ForestPlan.Point(688,479),
        new ForestPlan.Point(677,507), new ForestPlan.Point(667,541), new ForestPlan.Point(681,578),
        new ForestPlan.Point(720,610), new ForestPlan.Point(766,628), new ForestPlan.Point(789,649),
        new ForestPlan.Point(799,663), new ForestPlan.Point(770,744), new ForestPlan.Point(751,719),
        new ForestPlan.Point(685,663), new ForestPlan.Point(647,637), new ForestPlan.Point(610,616));

    public static List<Mark> pickLoot(long seed) {
        var shuffled = new ArrayList<>(loot());
        Collections.shuffle(shuffled, new Random(seed));
        return List.copyOf(shuffled.subList(0, Math.min(shuffled.size(), 40)));
    }

    private static int height(ForestPlan.Site site) {
        return switch (site.kind()) {
            case BUNKER -> -6;
            case GUARD -> 6;
            case TOWER -> 10;
            default -> 1;
        };
    }

    public static final List<List<ForestPlan.Point>> PATROLS = List.of(
        List.of(new ForestPlan.Point(243, 346), new ForestPlan.Point(246, 312), new ForestPlan.Point(251, 265)),
        List.of(new ForestPlan.Point(618, 208), new ForestPlan.Point(652, 197), new ForestPlan.Point(688, 217)),
        List.of(new ForestPlan.Point(710, 433), new ForestPlan.Point(682, 490), new ForestPlan.Point(662, 507)),
        List.of(new ForestPlan.Point(201, 629), new ForestPlan.Point(225, 536), new ForestPlan.Point(188, 550)));

    public static final List<ForestPlan.Point> ENEMIES = List.of(
        new ForestPlan.Point(240, 327), new ForestPlan.Point(272, 324), new ForestPlan.Point(646, 244),
        new ForestPlan.Point(657, 211), new ForestPlan.Point(727, 453), new ForestPlan.Point(353, 171),
        new ForestPlan.Point(478, 576), new ForestPlan.Point(764, 321),
        new ForestPlan.Point(151, 709), new ForestPlan.Point(189, 649), new ForestPlan.Point(226, 541),
        new ForestPlan.Point(230, 430), new ForestPlan.Point(307, 364), new ForestPlan.Point(339, 684),
        new ForestPlan.Point(375, 417), new ForestPlan.Point(413, 206), new ForestPlan.Point(306, 226),
        new ForestPlan.Point(568, 207), new ForestPlan.Point(681, 247), new ForestPlan.Point(740, 201),
        new ForestPlan.Point(780, 393), new ForestPlan.Point(682, 490), new ForestPlan.Point(672, 557),
        new ForestPlan.Point(701, 611), new ForestPlan.Point(785, 637), new ForestPlan.Point(758, 731),
        new ForestPlan.Point(625, 619), new ForestPlan.Point(579, 600));

    public static final List<ForestPlan.Point> REINFORCEMENTS = List.of(
        new ForestPlan.Point(313, 222), new ForestPlan.Point(576, 207), new ForestPlan.Point(703, 616));

    public static final List<ForestPlan.Point> TRACKER = List.of(
        new ForestPlan.Point(175, 689), new ForestPlan.Point(567, 443), new ForestPlan.Point(653, 622));

    public static final List<ForestPlan.Point> COVER = List.of(
        new ForestPlan.Point(224, 332), new ForestPlan.Point(619, 199),
        new ForestPlan.Point(629, 199), new ForestPlan.Point(480, 566), new ForestPlan.Point(763, 760));

    private ForestRaidAnchors() {}
}
