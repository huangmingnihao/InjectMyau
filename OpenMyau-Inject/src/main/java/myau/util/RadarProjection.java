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

    /** Offset is the requested center coordinate, even when the radar extends off screen. */
    public static Anchor anchor(int position, double offsetX, double offsetY, double width, double height) {
        if (position < 0 || position > 4) throw new IllegalArgumentException("Invalid radar position");
        if (position == 4) return new Anchor(width / 2.0, height / 2.0);
        return new Anchor((position & 1) != 0 ? width - offsetX : offsetX,
                (position & 2) != 0 ? height - offsetY : offsetY);
    }

    public static final class Anchor {
        public final double x;
        public final double y;

        private Anchor(double x, double y) {
            this.x = x;
            this.y = y;
        }
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
