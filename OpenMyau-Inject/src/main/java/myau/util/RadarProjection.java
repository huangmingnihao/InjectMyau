package myau.util;

/** World X/Z to radar coordinates. Minecraft yaw zero faces south (+Z). */
public final class RadarProjection {
    private RadarProjection() {
    }

    public static Point project(double dx, double dz, double yaw, double range, double radius) {
        if (!(range > 0.0) || !(radius > 0.0)) {
            throw new IllegalArgumentException("Radar range and radius must be positive");
        }
        double distance = Math.hypot(dx, dz);
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        double factor = radius / Math.max(range, distance);
        return new Point((-dx * cos - dz * sin) * factor,
                (dx * sin - dz * cos) * factor, distance, distance > range);
    }

    public static double clampCenter(double center, double before, double after, double screenSize) {
        if (before + after > screenSize) return screenSize / 2.0;
        return Math.max(before, Math.min(screenSize - after, center));
    }

    public static final class Point {
        public final double x;
        public final double y;
        public final double distance;
        public final boolean outside;

        private Point(double x, double y, double distance, boolean outside) {
            this.x = x;
            this.y = y;
            this.distance = distance;
            this.outside = outside;
        }
    }
}
