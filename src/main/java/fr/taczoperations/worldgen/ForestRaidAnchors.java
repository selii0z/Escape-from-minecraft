package fr.taczoperations.worldgen;

import java.util.List;

public final class ForestRaidAnchors {
    public record Mark(int x, int y, int z) {}

    public static List<Mark> loot() {
        return ForestPlan.SITES.stream()
            .filter(site -> site.kind() != ForestPlan.Kind.TRUCK)
            .flatMap(site -> java.util.stream.Stream.of(
                new Mark(site.x() + site.width() / 2, site.floor() + height(site), site.z() + 2),
                new Mark(site.x() + site.width() / 2 + 1, site.floor() + height(site),
                    site.z() + site.length() - 2))).toList();
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
        new ForestPlan.Point(478, 576), new ForestPlan.Point(764, 321));

    public static final List<ForestPlan.Point> REINFORCEMENTS = List.of(
        new ForestPlan.Point(313, 222), new ForestPlan.Point(576, 207), new ForestPlan.Point(703, 616));

    public static final List<ForestPlan.Point> TRACKER = List.of(
        new ForestPlan.Point(175, 689), new ForestPlan.Point(567, 443), new ForestPlan.Point(653, 622));

    public static final List<ForestPlan.Point> COVER = List.of(
        new ForestPlan.Point(224, 332), new ForestPlan.Point(619, 199),
        new ForestPlan.Point(629, 199), new ForestPlan.Point(480, 566), new ForestPlan.Point(763, 760));

    private ForestRaidAnchors() {}
}
