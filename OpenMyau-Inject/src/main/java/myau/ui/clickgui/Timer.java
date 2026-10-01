package myau.ui.clickgui;

public class Timer {
    public static final int CUBIC_IN_OUT = 1;
    public static final int QUINT_OUT = 2;
    public static final int BOUNCE = 3;
    public static final int QUAD_IN_OUT = 4;
    public final float duration;
    private long startedAt;
    private float settled = Float.NaN;
    public Timer(float duration) {
        this.duration = duration;
    }
    public void start() {
        this.settled = Float.NaN;
        this.startedAt = System.currentTimeMillis();
    }
    public float getValueFloat(float begin, float end, int easing) {
        if (!Float.isNaN(this.settled) && this.settled == end) {
            return this.settled;
        }
        float t = (System.currentTimeMillis() - this.startedAt) / this.duration;
        switch (easing) {
            case CUBIC_IN_OUT:
                t = t < 0.5F
                        ? 4.0F * t * t * t
                        : (t - 1.0F) * (2.0F * t - 2.0F) * (2.0F * t - 2.0F) + 1.0F;
                break;
            case QUINT_OUT:
                t = (float) (1.0 - Math.pow(1.0F - t, 5.0));
                break;
            case BOUNCE:
                t = bounce(t);
                break;
            case QUAD_IN_OUT:
                t = t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
                break;
            default:
                break;
        }
        float value = begin + t * (end - begin);
        if (end > begin && value > end || end < begin && value < end) {
            value = end;
        }
        if (value == end) {
            this.settled = value;
        }
        return value;
    }
    public int getValueInt(int begin, int end, int easing) {
        return Math.round(this.getValueFloat(begin, end, easing));
    }
    private static float bounce(float t) {
        final double amplitude = 7.5625;
        final double period = 2.75;
        if (t < 1.0 / period) {
            return (float) (amplitude * t * t);
        }
        if (t < 2.0 / period) {
            t = (float) (t - 1.5 / period);
            return (float) (amplitude * t * t + 0.75);
        }
        if (t < 2.5 / period) {
            t = (float) (t - 2.25 / period);
            return (float) (amplitude * t * t + 0.9375);
        }
        t = (float) (t - 2.625 / period);
        return (float) (amplitude * t * t + 0.984375);
    }
}
