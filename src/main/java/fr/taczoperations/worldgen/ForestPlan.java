package fr.taczoperations.worldgen;

import java.util.List;

public final class ForestPlan {
    public static final int ORIGIN = 12288;
    public static final int SIZE = 900;
    public static final int EDGE = ORIGIN + SIZE - 1;
    public static final int WATER = 61;

    public record Point(int x, int z) {}
    public enum Kind { SAWMILL, SHED, CAMP, GUARD, CABIN, STATION, WAREHOUSE, TOWER, BUNKER, TRUCK }
    public record Site(Kind kind, int x, int z, int width, int length) {
        int distance(int x, int z) {
            return Math.max(Math.max(this.x - x, x - (this.x + width - 1)),
                Math.max(this.z - z, z - (this.z + length - 1)));
        }

        int floor() {
            return natural(x + width / 2, z + length / 2);
        }
    }

    public static final List<Point> ENTRIES = List.of(new Point(78, 782), new Point(112, 145), new Point(793, 774));
    public static final Point EXTRACTION = new Point(823, 182);
    public static final Point CONDITIONAL_EXIT = new Point(820, 682);
    public static final Point RELAY = new Point(712, 441);

    public static final List<Site> SITES = List.of(
        new Site(Kind.SAWMILL, 220, 274, 30, 22), new Site(Kind.SHED, 256, 278, 19, 14),
        new Site(Kind.WAREHOUSE, 265, 306, 23, 15), new Site(Kind.SAWMILL, 202, 301, 16, 13),
        new Site(Kind.CAMP, 624, 214, 13, 12), new Site(Kind.CAMP, 643, 218, 15, 10),
        new Site(Kind.GUARD, 660, 204, 8, 8), new Site(Kind.CAMP, 627, 235, 15, 13),
        new Site(Kind.WAREHOUSE, 664, 230, 25, 18),
        new Site(Kind.CABIN, 130, 513, 10, 9), new Site(Kind.CABIN, 338, 688, 12, 8),
        new Site(Kind.CABIN, 746, 594, 9, 11), new Site(Kind.STATION, 704, 448, 18, 14),
        new Site(Kind.TOWER, 358, 170, 8, 8), new Site(Kind.TOWER, 786, 322, 9, 8),
        new Site(Kind.BUNKER, 654, 512, 15, 15), new Site(Kind.TRUCK, 481, 565, 6, 11),
        new Site(Kind.TRUCK, 762, 758, 5, 10),
        new Site(Kind.CABIN, 151, 643, 13, 10), new Site(Kind.CABIN, 159, 659, 10, 12),
        new Site(Kind.STATION, 174, 651, 16, 13), new Site(Kind.SHED, 146, 670, 12, 9),
        new Site(Kind.GUARD, 204, 651, 8, 8), new Site(Kind.WAREHOUSE, 587, 601, 18, 13),
        new Site(Kind.CABIN, 321, 349, 11, 9), new Site(Kind.SHED, 759, 627, 12, 9));

    private record Segment(Point from, Point to, int width) {
        double distance(int x, int z) {
            double dx = to.x - from.x;
            double dz = to.z - from.z;
            double t = Math.clamp(((x - from.x) * dx + (z - from.z) * dz) / (dx * dx + dz * dz), 0, 1);
            return Math.hypot(x - from.x - t * dx, z - from.z - t * dz);
        }
    }

    private static final byte[] TRACKS = buildTrackMask(buildTracks());

    private static List<Segment> buildTracks() {
        var tracks = new java.util.ArrayList<Segment>();
        add(tracks, 3, 78,782, 146,711, 193,642, 226,535, 230,429, 243,348, 246,310, 251,267, 302,224, 355,183, 412,201, 491,223, 558,211, 618,209, 650,197, 733,203, 823,182);
        add(tracks, 3, 246,310, 308,364, 367,423, 427,451, 496,449, 564,426, 631,455, 712,441, 771,396, 796,335, 767,263, 733,203);
        add(tracks, 2, 193,642, 258,680, 334,677, 386,627, 452,576, 479,565, 543,590, 626,622, 697,669, 756,723, 793,774);
        add(tracks, 2, 712,441, 683,490, 661,507, 672,568, 700,610, 740,605, 779,638, 820,682);
        add(tracks, 1, 112,145, 172,186, 191,238, 251,267);
        add(tracks, 1, 226,535, 187,552, 135,508);
        add(tracks, 1, 355,183, 377,286, 350,356, 367,423);
        add(tracks, 1, 334,677, 344,684);
        add(tracks, 1, 479,565, 470,503, 496,449);
        add(tracks, 1, 618,209, 613,235, 640,251, 679,250, 691,221, 650,197);
        add(tracks, 1, 251,267, 263,272, 276,294, 276,303);
        add(tracks, 1, 246,310, 231,310, 211,298);
        add(tracks, 1, 756,723, 773,704, 820,682);
        add(tracks, 2, 193,642, 178,647, 167,654, 147,662, 141,676, 156,690, 193,701, 215,686, 226,672);
        add(tracks, 1, 308,364, 320,356, 326,345);
        add(tracks, 1, 542,590, 565,608, 591,596, 613,605, 626,622);
        return List.copyOf(tracks);
    }

    private static void add(List<Segment> tracks, int width, int... points) {
        for (int i = 2; i < points.length; i += 2) {
            Point from = new Point(points[i - 2], points[i - 1]);
            Point to = new Point(points[i], points[i + 1]);
            route(tracks, from, to, width);
        }
    }

    // Route between existing landmarks using a narrow corridor of candidate
    // waypoints. Dynamic programming favors gentle slopes and smooth bends.
    private static void route(List<Segment> tracks, Point from, Point to, int width) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double length = Math.hypot(dx, dz);
        if (length < 1) return;
        int steps = Math.max(1, (int) Math.ceil(length / 7.0));
        int middle = 6, choices = 13;
        Point[][] points = new Point[steps + 1][choices];
        int[][] height = new int[steps + 1][choices];
        double[][] cost = new double[steps + 1][choices];
        int[][] back = new int[steps + 1][choices];
        for (int s = 0; s <= steps; s++) {
            java.util.Arrays.fill(cost[s], Double.POSITIVE_INFINITY);
            double t = s / (double) steps;
            for (int j = 0; j < choices; j++) {
                if ((s == 0 || s == steps) && j != middle) continue;
                double offset = (j - middle) * 3.0;
                int x = (int) Math.round(from.x + t * dx - dz / length * offset);
                int z = (int) Math.round(from.z + t * dz + dx / length * offset);
                if (edge(x, z) < 5) continue;
                points[s][j] = new Point(x, z);
                height[s][j] = ground(x, z);
            }
        }
        cost[0][middle] = 0;
        for (int s = 1; s <= steps; s++) {
            for (int j = 0; j < choices; j++) {
                Point p = points[s][j];
                if (p == null) continue;
                for (int k = Math.max(0, j - 2); k <= Math.min(choices - 1, j + 2); k++) {
                    Point prev = points[s - 1][k];
                    if (prev == null || !Double.isFinite(cost[s - 1][k])) continue;
                    double run = Math.max(1, Math.hypot(p.x - prev.x, p.z - prev.z));
                    double rise = Math.abs(height[s][j] - height[s - 1][k]);
                    boolean water = height[s][j] < WATER || height[s - 1][k] < WATER;
                    // Inspect intermediate blocks as well as waypoint heights.
                    int steep = 0, last = height[s - 1][k];
                    for (int m = 1; m <= 4; m++) {
                        int h = ground(prev.x + (p.x - prev.x) * m / 4, prev.z + (p.z - prev.z) * m / 4);
                        steep += Math.max(0, Math.abs(h - last) - 1);
                        last = h;
                    }
                    double preferred = noise(p.x, p.z, 26, 932) * 8;
                    double value = cost[s - 1][k] + run + rise * rise * 4.0 + steep * 16.0
                        + (water ? 12 : 0) + Math.abs((j - middle) * 3.0 - preferred) * .22
                        + Math.abs(j - k) * 1.8;
                    if (value < cost[s][j]) {
                        cost[s][j] = value;
                        back[s][j] = k;
                    }
                }
            }
        }
        if (!Double.isFinite(cost[steps][middle])) {
            tracks.add(new Segment(from, to, width));
            return;
        }
        int j = middle;
        for (int s = steps; s > 0; s--) {
            int k = back[s][j];
            Point p = points[s - 1][k], q = points[s][j];
            if (!p.equals(q)) tracks.add(new Segment(p, q, width));
            j = k;
        }
    }

    // Rasterize once so chunk generation does not search hundreds of segments
    // per block. Width is a diameter target rather than the old radius.
    private static byte[] buildTrackMask(List<Segment> segments) {
        byte[] mask = new byte[SIZE * SIZE];
        for (Segment s : segments) {
            for (int x = Math.max(0, Math.min(s.from.x, s.to.x) - 3);
                 x <= Math.min(SIZE - 1, Math.max(s.from.x, s.to.x) + 3); x++) {
                for (int z = Math.max(0, Math.min(s.from.z, s.to.z) - 3);
                     z <= Math.min(SIZE - 1, Math.max(s.from.z, s.to.z) + 3); z++) {
                    double radius = (s.width == 3 ? 1.6 : s.width == 2 ? 1.3 : 1.05)
                        + noise(x, z, 7, 734) * .42;
                    if (s.distance(x, z) <= radius) {
                        int i = z * SIZE + x;
                        mask[i] = (byte) Math.max(mask[i], s.width);
                    }
                }
            }
        }
        return mask;
    }

    public static boolean inside(int x, int z) {
        return x >= ORIGIN && z >= ORIGIN && x <= EDGE && z <= EDGE;
    }

    public static int edge(int x, int z) {
        return Math.min(Math.min(x, z), Math.min(SIZE - 1 - x, SIZE - 1 - z));
    }

    static double riverDistance(int x, int z) {
        double center = 460 + 44 * Math.sin(z / 102.0 + .8) + 22 * Math.sin(z / 47.0);
        return Math.abs(x - center);
    }

    static double channelWidth(int z) {
        return 8 + 3 * Math.sin(z / 39.0) + 2 * noise(412, z, 43, 885);
    }

    static int track(int x, int z) {
        return edge(x, z) < 0 ? 0 : TRACKS[z * SIZE + x];
    }

    static boolean nearTrack(int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                if (dx * dx + dz * dz <= radius * radius && track(x + dx, z + dz) > 0) return true;
        return false;
    }

    private static int natural(int x, int z) {
        double hills = 86 + 23 * noise(x, z, 175, 613) + 11 * noise(x, z, 83, 88)
            + 5 * noise(x, z, 34, 20) + 1.6 * noise(x, z, 13, 52);
        double bank = riverDistance(x, z) - channelWidth(z);
        double height = bank < 0 ? WATER - 4 - 2.0 * Math.min(1, -bank / 9)
            : WATER + 1 + smooth(Math.clamp(bank / 54, 0, 1)) * (Math.max(70, hills) - (WATER + 1));
        return (int) Math.round(height);
    }

    static int ground(int x, int z) {
        int natural = natural(x, z);
        int closest = 12;
        int grade = natural;
        for (Site site : SITES) {
            int distance = site.distance(x, z);
            if (distance < closest) {
                closest = distance;
                grade = site.floor();
            }
        }
        if (closest <= 2) {
            return grade;
        }
        return (int) Math.round(grade + (natural - grade) * smooth((closest - 2) / 10.0));
    }

    static boolean clear(int x, int z) {
        for (Site site : SITES) {
            if (site.distance(x, z) < 7) {
                return true;
            }
        }
        for (Point p : ENTRIES) {
            if (Math.abs(x - p.x) <= 9 && Math.abs(z - p.z) <= 9) {
                return true;
            }
        }
        for (Point p : List.of(EXTRACTION, CONDITIONAL_EXIT, RELAY)) {
            if (Math.abs(x - p.x) <= 9 && Math.abs(z - p.z) <= 9) {
                return true;
            }
        }
        return noise(x, z, 70, 53) > .40;
    }

    public static int surfaceY(Point point) {
        return ground(point.x, point.z) + 1;
    }

    static long hash(int x, int z, long salt) {
        long n = x * 341873128712L + z * 132897987541L + salt * 1442695040888963407L;
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    static double noise(int x, int z, int scale, int salt) {
        int gx = Math.floorDiv(x, scale);
        int gz = Math.floorDiv(z, scale);
        double dx = smooth(Math.floorMod(x, scale) / (double) scale);
        double dz = smooth(Math.floorMod(z, scale) / (double) scale);
        double a = random(gx, gz, salt) * (1 - dx) + random(gx + 1, gz, salt) * dx;
        double b = random(gx, gz + 1, salt) * (1 - dx) + random(gx + 1, gz + 1, salt) * dx;
        return a * (1 - dz) + b * dz;
    }

    private static double random(int x, int z, int salt) {
        return (hash(x, z, salt) >>> 11) * 0x1.0p-52 - 1;
    }

    private static double smooth(double x) {
        return x * x * (3 - 2 * x);
    }

    private ForestPlan() {}
}
