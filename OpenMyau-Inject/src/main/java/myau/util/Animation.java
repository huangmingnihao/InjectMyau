package myau.util;

import java.util.function.DoubleUnaryOperator;

public class Animation {
    public static final DoubleUnaryOperator LINEAR = progress -> progress;
    public static final DoubleUnaryOperator EASE_OUT_EXPO =
            progress -> progress == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * progress);
    private DoubleUnaryOperator easing;
    private long duration;
    private long startTime;
    private double startValue;
    private double destinationValue;
    private double value;
    private boolean finished;
    public Animation(DoubleUnaryOperator easing, long duration) {
        this.easing = easing;
        this.duration = Math.max(1L, duration);
        this.startTime = System.currentTimeMillis();
    }
    public void to(double destination) {
        long now = System.currentTimeMillis();
        if (this.destinationValue != destination) {
            this.destinationValue = destination;
            this.restart();
        } else {
            this.finished = now - this.duration > this.startTime;
            if (this.finished) {
                this.value = destination;
                return;
            }
        }
        double eased = this.easing.applyAsDouble(this.progress());

        if (this.value > destination) {
            this.value = this.startValue - (this.startValue - destination) * eased;
        } else {
            this.value = this.startValue + (destination - this.startValue) * eased;
        }
    }
    public double progress() {
        return (double) (System.currentTimeMillis() - this.startTime) / this.duration;
    }
    public void restart() {
        this.startTime = System.currentTimeMillis();
        this.startValue = this.value;
        this.finished = false;
    }
    public double getValue() {
        return this.value;
    }
    public boolean isFinished() {
        return this.finished;
    }
    public void setEasing(DoubleUnaryOperator easing) {
        this.easing = easing;
    }

    public void setDuration(long duration) {
        this.duration = Math.max(1L, duration);
    }

    public void jumpTo(double value) {
        this.value = value;
        this.startValue = value;
        this.destinationValue = value;
        this.startTime = System.currentTimeMillis();
        this.finished = false;
    }
}
