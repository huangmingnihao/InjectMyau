package myau.util;

/** Geometry regressions, without requiring a Minecraft instance or OpenGL context. */
public final class RadarProjectionTest {
    public static void main(String[] args) {
        double[] headings = {0, Math.PI / 2, Math.PI, -Math.PI / 2, Math.PI * 1.25};
        for (double yaw : headings) {
            // Forward must be up; right must be right, regardless of facing direction.
            assertPoint(-Math.sin(yaw) * 32, Math.cos(yaw) * 32, yaw, 0, -27);
            assertPoint(-Math.cos(yaw) * 32, -Math.sin(yaw) * 32, yaw, 27, 0);
        }
        assertPoint(0, -64, Math.PI, 0, -54);
        assertPoint(64, 0, Math.PI, 54, 0);
        assertPoint(0, 64, Math.PI, 0, 54);
        assertPoint(-64, 0, Math.PI, -54, 0);
        assertPoint(0, 0, 0, 0, 0);

        RadarProjection.Point far = RadarProjection.project(300, 400, 0, 64, 54);
        close(far.distance, 500);
        close(Math.hypot(far.x, far.y), 54);
        check(far.outside, "far contact is an edge marker");
        check(!RadarProjection.project(0, 64, 0, 64, 54).outside, "range boundary is included");
        check(!RadarProjection.project(0, 32, 0, 64, 54).outside, "nearby contact is included");
        close(RadarProjection.project(0, 32, 0, 128, 54).y, -13.5);
        close(RadarProjection.project(0, 32, 0, 64, 108).y, -54);

        close(RadarProjection.clampCenter(80, 72, 93, 320), 80);
        close(RadarProjection.clampCenter(5, 72, 93, 320), 72);
        close(RadarProjection.clampCenter(315, 72, 93, 320), 227);
        close(RadarProjection.clampCenter(5, 200, 220, 320), 160);
        try {
            RadarProjection.project(0, 0, 0, 0, 54);
            throw new AssertionError("zero range must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        System.out.println("Radar projection regression checks passed");
    }

    private static void assertPoint(double dx, double dz, double yaw, double x, double y) {
        RadarProjection.Point point = RadarProjection.project(dx, dz, yaw, 64, 54);
        close(point.x, x);
        close(point.y, y);
    }

    private static void close(double actual, double expected) {
        check(Math.abs(actual - expected) < 1.0E-8, "expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
