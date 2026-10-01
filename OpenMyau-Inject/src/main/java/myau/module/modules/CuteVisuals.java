package myau.module.modules;

import myau.access.AccessorRenderManager;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.PacketEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public class CuteVisuals extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MAX_HEARTS = 50;
    private static final int MAX_DOTS = 100;
    private static final int MAX_BED_PARTICLES = 200;
    private static final int MAX_RAINBOWS = 5;

    private static final int TRAIL_HEART_SEGMENTS = 30;
    private static final int BED_HEART_SEGMENTS = 20;
    private static final int DOT_SEGMENTS = 8;
    private static final int DOT_FILL_SEGMENTS = 6;
    private static final int RAINBOW_SEGMENTS = 30;
    private static final int STAR_SEGMENTS = 8;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final double HEART_RADIUS = 1.5;
    private static final double HEART_SIZE = 0.15;
    private static final double HEART_FLOAT_SPEED = 1.0;
    private static final float HEART_LINE_WIDTH = 1.5F;
    private static final double DOT_SIZE = 0.04;
    private static final double DOT_SPREAD = 0.9;
    private static final double DOT_DRIFT_SPEED = 0.3;
    private static final float DOT_LINE_WIDTH = 2.0F;
    private static final double RAINBOW_SIZE = 3.0;
    private static final double RAINBOW_BAND_WIDTH = 0.15;

    private static final double[] RAINBOW_RED = {0.85, 0.60, 0.50, 0.50, 1.00, 1.00, 1.00};
    private static final double[] RAINBOW_GREEN = {0.50, 0.50, 0.75, 1.00, 0.90, 0.60, 0.40};
    private static final double[] RAINBOW_BLUE = {1.00, 1.00, 1.00, 0.65, 0.50, 0.40, 0.50};
    private static final double[] BED_RED = {1.00, 1.00, 1.00, 0.50, 0.50, 0.60, 0.85};
    private static final double[] BED_GREEN = {0.40, 0.60, 0.90, 1.00, 0.75, 0.50, 0.50};
    private static final double[] BED_BLUE = {0.50, 0.40, 0.50, 0.65, 1.00, 1.00, 1.00};

    private static final double[] TRAIL_HEART_X = new double[TRAIL_HEART_SEGMENTS + 1];
    private static final double[] TRAIL_HEART_Y = new double[TRAIL_HEART_SEGMENTS + 1];
    private static final double[] BED_HEART_X = new double[BED_HEART_SEGMENTS + 1];
    private static final double[] BED_HEART_Y = new double[BED_HEART_SEGMENTS + 1];
    private static final double[] DOT_CIRCLE_X = new double[DOT_SEGMENTS + 1];
    private static final double[] DOT_CIRCLE_Y = new double[DOT_SEGMENTS + 1];
    private static final double[] DOT_FILL_X = new double[DOT_FILL_SEGMENTS + 1];
    private static final double[] DOT_FILL_Y = new double[DOT_FILL_SEGMENTS + 1];
    private static final double[] STAR_X = new double[STAR_SEGMENTS + 1];
    private static final double[] STAR_Y = new double[STAR_SEGMENTS + 1];
    private static final double[] ARC_COS = new double[RAINBOW_SEGMENTS + 1];
    private static final double[] ARC_SIN = new double[RAINBOW_SEGMENTS + 1];

    static {
        for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
            double t = (double) i / TRAIL_HEART_SEGMENTS * TWO_PI;
            double sin = Math.sin(t);
            TRAIL_HEART_X[i] = 16.0 * sin * sin * sin;
            TRAIL_HEART_Y[i] = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
        }
        for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
            double t = (double) i / BED_HEART_SEGMENTS * TWO_PI;
            double sin = Math.sin(t);
            BED_HEART_X[i] = 16.0 * sin * sin * sin;
            BED_HEART_Y[i] = 13.0 * Math.cos(t) - 5.0 * Math.cos(2.0 * t)
                    - 2.0 * Math.cos(3.0 * t) - Math.cos(4.0 * t);
        }
        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            double angle = (double) i / DOT_SEGMENTS * TWO_PI;
            DOT_CIRCLE_X[i] = Math.cos(angle);
            DOT_CIRCLE_Y[i] = Math.sin(angle);
        }
        for (int i = 0; i <= DOT_FILL_SEGMENTS; i++) {
            double angle = (double) i / DOT_FILL_SEGMENTS * TWO_PI;
            DOT_FILL_X[i] = Math.cos(angle);
            DOT_FILL_Y[i] = Math.sin(angle);
        }
        for (int i = 0; i <= STAR_SEGMENTS; i++) {
            double angle = i * Math.PI / 4.0 - Math.PI / 2.0;
            double radius = (i % 2 == 0) ? 12.0 : 5.0;
            STAR_X[i] = Math.cos(angle) * radius;
            STAR_Y[i] = Math.sin(angle) * radius;
        }
    }

    public final BooleanProperty bedSound = new BooleanProperty("bed-sound", true);
    public final BooleanProperty bedBurst = new BooleanProperty("bed-burst", true);
    public final IntProperty burstCount = new IntProperty("burst-count", 20, 5, 40);
    public final FloatProperty burstSize = new FloatProperty("burst-size", 0.20F, 0.05F, 0.60F);
    public final FloatProperty burstSpeed = new FloatProperty("burst-speed", 2.5F, 0.5F, 7.0F);
    public final IntProperty burstLifetime = new IntProperty("burst-lifetime", 1500, 500, 3000);
    public final BooleanProperty rainbow = new BooleanProperty("rainbow", true);
    public final FloatProperty rainbowLineWidth =
            new FloatProperty("rainbow-line-width", 5.0F, 1.0F, 12.0F);
    public final IntProperty rainbowDuration =
            new IntProperty("rainbow-duration", 3000, 1000, 6000);
    public final BooleanProperty onlyWhileMoving = new BooleanProperty("only-while-moving", true);
    public final PercentProperty opacity = new PercentProperty("opacity", 85);
    public final BooleanProperty hearts = new BooleanProperty("hearts", true);
    public final IntProperty heartsRate = new IntProperty("hearts-spawn-rate", 200, 50, 500);
    public final IntProperty heartsLifetime = new IntProperty("hearts-lifetime", 1500, 500, 4000);
    public final BooleanProperty dots = new BooleanProperty("dots", true);
    public final IntProperty dotsRate = new IntProperty("dots-spawn-rate", 100, 20, 200);
    public final IntProperty dotsLifetime = new IntProperty("dots-lifetime", 1500, 500, 5000);
    public final BooleanProperty pulse = new BooleanProperty("pulse", false);

    private final Random random = new Random();

    private final double[] heartX = new double[MAX_HEARTS];
    private final double[] heartY = new double[MAX_HEARTS];
    private final double[] heartZ = new double[MAX_HEARTS];
    private final long[] heartTime = new long[MAX_HEARTS];
    private final float[] heartRotY = new float[MAX_HEARTS];
    private final float[] heartRotZ = new float[MAX_HEARTS];
    private final float[] heartScale = new float[MAX_HEARTS];
    private final int[] heartType = new int[MAX_HEARTS];
    private final boolean[] heartActive = new boolean[MAX_HEARTS];
    private int activeHearts;
    private long lastHeartSpawn;

    private final double[] dotX = new double[MAX_DOTS];
    private final double[] dotY = new double[MAX_DOTS];
    private final double[] dotZ = new double[MAX_DOTS];
    private final double[] dotDriftX = new double[MAX_DOTS];
    private final double[] dotDriftY = new double[MAX_DOTS];
    private final double[] dotDriftZ = new double[MAX_DOTS];
    private final long[] dotTime = new long[MAX_DOTS];
    private final float[] dotScale = new float[MAX_DOTS];
    private final int[] dotType = new int[MAX_DOTS];
    private final boolean[] dotActive = new boolean[MAX_DOTS];
    private int activeDots;
    private long lastDotSpawn;
    private double lastDotPosX;
    private double lastDotPosZ;
    private boolean hasLastDotPos;

    private final double[] bedX = new double[MAX_BED_PARTICLES];
    private final double[] bedY = new double[MAX_BED_PARTICLES];
    private final double[] bedZ = new double[MAX_BED_PARTICLES];
    private final double[] bedVX = new double[MAX_BED_PARTICLES];
    private final double[] bedVY = new double[MAX_BED_PARTICLES];
    private final double[] bedVZ = new double[MAX_BED_PARTICLES];
    private final float[] bedScale = new float[MAX_BED_PARTICLES];
    private final int[] bedType = new int[MAX_BED_PARTICLES];
    private final long[] bedTime = new long[MAX_BED_PARTICLES];
    private final boolean[] bedActive = new boolean[MAX_BED_PARTICLES];
    private int activeBedParticles;

    private final double[] rainbowX = new double[MAX_RAINBOWS];
    private final double[] rainbowY = new double[MAX_RAINBOWS];
    private final double[] rainbowZ = new double[MAX_RAINBOWS];
    private final float[] rainbowYaw = new float[MAX_RAINBOWS];
    private final long[] rainbowTime = new long[MAX_RAINBOWS];
    private final boolean[] rainbowActive = new boolean[MAX_RAINBOWS];
    private int activeRainbows;

    private boolean diggingBed;
    private double digX;
    private double digY;
    private double digZ;

    public CuteVisuals() {
        super("Cute Visuals", false);
    }

    @Override
    public void onEnabled() {
        this.clearAll();
        this.lastHeartSpawn = 0L;
        this.lastDotSpawn = 0L;
        this.hasLastDotPos = false;
        this.diggingBed = false;
    }

    private void clearAll() {
        java.util.Arrays.fill(this.heartActive, false);
        java.util.Arrays.fill(this.dotActive, false);
        java.util.Arrays.fill(this.bedActive, false);
        java.util.Arrays.fill(this.rainbowActive, false);
        this.activeHearts = 0;
        this.activeDots = 0;
        this.activeBedParticles = 0;
        this.activeRainbows = 0;
    }

    private static int freeSlot(boolean[] active, long[] times) {
        for (int i = 0; i < active.length; i++) {
            if (!active[i]) {
                return i;
            }
        }
        long oldest = Long.MAX_VALUE;
        int index = 0;
        for (int i = 0; i < times.length; i++) {
            if (times[i] < oldest) {
                oldest = times[i];
                index = i;
            }
        }
        return index;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        boolean heartsOn = this.hearts.getValue();
        boolean dotsOn = this.dots.getValue();
        if (!heartsOn && this.activeHearts > 0) {
            java.util.Arrays.fill(this.heartActive, false);
            this.activeHearts = 0;
        }
        if (!dotsOn && this.activeDots > 0) {
            java.util.Arrays.fill(this.dotActive, false);
            this.activeDots = 0;
        }

        double x = mc.thePlayer.posX;
        double y = mc.thePlayer.posY;
        double z = mc.thePlayer.posZ;
        boolean canSpawnDots = this.hasLastDotPos;
        if (!this.hasLastDotPos) {
            this.lastDotPosX = x;
            this.lastDotPosZ = z;
            this.hasLastDotPos = true;
        }
        if (!heartsOn && !dotsOn) {
            this.lastDotPosX = x;
            this.lastDotPosZ = z;
            return;
        }
        if (this.onlyWhileMoving.getValue() && !isMoving()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (heartsOn && now - this.lastHeartSpawn >= this.heartsRate.getValue()) {
            this.lastHeartSpawn = now;
            this.spawnHeart(x, y + 0.5, z, now);
        }
        if (dotsOn && canSpawnDots && now - this.lastDotSpawn >= this.dotsRate.getValue()) {
            this.lastDotSpawn = now;
            this.spawnDot(y, now);
        }
        this.lastDotPosX = x;
        this.lastDotPosZ = z;
    }

    private static boolean isMoving() {
        double dx = mc.thePlayer.motionX;
        double dz = mc.thePlayer.motionZ;
        if (dx * dx + dz * dz > 1.0E-6) {
            return true;
        }
        return mc.thePlayer.movementInput != null
                && (Math.abs(mc.thePlayer.movementInput.moveForward) > 0.01F
                || Math.abs(mc.thePlayer.movementInput.moveStrafe) > 0.01F);
    }

    private void spawnHeart(double x, double y, double z, long now) {
        int slot = freeSlot(this.heartActive, this.heartTime);
        boolean wasActive = this.heartActive[slot];
        double angle = this.random.nextDouble() * TWO_PI;
        double distance = this.random.nextDouble() * HEART_RADIUS;
        this.heartX[slot] = x + Math.cos(angle) * distance;
        this.heartZ[slot] = z + Math.sin(angle) * distance;
        this.heartY[slot] = y + this.random.nextDouble() * 0.5;
        this.heartTime[slot] = now;
        this.heartRotY[slot] = (float) (this.random.nextDouble() * 360.0);
        this.heartRotZ[slot] = (float) (this.random.nextDouble() * 30.0 - 15.0);
        this.heartScale[slot] = (float) (HEART_SIZE * (0.6 + this.random.nextDouble() * 0.8));
        this.heartType[slot] = this.random.nextInt(3);
        this.heartActive[slot] = true;
        if (!wasActive) {
            this.activeHearts++;
        }
    }

    private void spawnDot(double playerY, long now) {
        int slot = freeSlot(this.dotActive, this.dotTime);
        boolean wasActive = this.dotActive[slot];
        this.dotX[slot] = this.lastDotPosX + (this.random.nextDouble() - 0.5) * DOT_SPREAD;
        this.dotY[slot] = playerY + 0.3 + this.random.nextDouble() * 1.2;
        this.dotZ[slot] = this.lastDotPosZ + (this.random.nextDouble() - 0.5) * DOT_SPREAD;
        this.dotDriftX[slot] = (this.random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
        this.dotDriftY[slot] = (0.3 + this.random.nextDouble() * 0.7) * DOT_DRIFT_SPEED;
        this.dotDriftZ[slot] = (this.random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
        this.dotTime[slot] = now;
        this.dotScale[slot] = (float) (DOT_SIZE * (0.5 + this.random.nextDouble()));
        this.dotType[slot] = this.random.nextInt(4);
        this.dotActive[slot] = true;
        if (!wasActive) {
            this.activeDots++;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || mc.theWorld == null) {
            return;
        }
        if (!(event.getPacket() instanceof C07PacketPlayerDigging)) {
            return;
        }
        C07PacketPlayerDigging packet = (C07PacketPlayerDigging) event.getPacket();
        BlockPos pos = packet.getPosition();
        if (packet.getStatus() == null || pos == null) {
            return;
        }
        switch (packet.getStatus()) {
            case START_DESTROY_BLOCK: {
                if (!(mc.theWorld.getBlockState(pos).getBlock() instanceof BlockBed)) {
                    this.diggingBed = false;
                    return;
                }
                double x = pos.getX() + 0.5;
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5;
                if (mc.thePlayer != null && mc.thePlayer.capabilities.isCreativeMode) {
                    this.diggingBed = false;
                    this.spawnBedBreak(x, y, z);
                } else {
                    this.diggingBed = true;
                    this.digX = x;
                    this.digY = y;
                    this.digZ = z;
                }
                return;
            }
            case STOP_DESTROY_BLOCK: {
                if (this.diggingBed) {
                    this.spawnBedBreak(this.digX, this.digY, this.digZ);
                    this.diggingBed = false;
                }
                return;
            }
            case ABORT_DESTROY_BLOCK: {
                this.diggingBed = false;
                return;
            }
            default:
                break;
        }
    }

    private void spawnBedBreak(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (this.rainbow.getValue()) {
            int slot = freeSlot(this.rainbowActive, this.rainbowTime);
            boolean wasActive = this.rainbowActive[slot];
            this.rainbowX[slot] = x;
            this.rainbowY[slot] = y;
            this.rainbowZ[slot] = z;
            this.rainbowTime[slot] = now;
            this.rainbowActive[slot] = true;
            this.rainbowYaw[slot] = mc.thePlayer == null ? 0.0F
                    : (float) Math.toDegrees(Math.atan2(mc.thePlayer.posX - x,
                            mc.thePlayer.posZ - z));
            if (!wasActive) {
                this.activeRainbows++;
            }
        }
        if (this.bedBurst.getValue()) {
            double speed = this.burstSpeed.getValue();
            double baseSize = this.burstSize.getValue();
            int count = this.burstCount.getValue();
            for (int i = 0; i < count; i++) {
                int slot = freeSlot(this.bedActive, this.bedTime);
                boolean wasActive = this.bedActive[slot];
                this.bedX[slot] = x;
                this.bedY[slot] = y;
                this.bedZ[slot] = z;
                double theta = this.random.nextDouble() * TWO_PI;
                double phi = this.random.nextDouble() * Math.PI * 0.67 - Math.PI / 6.0;
                double particleSpeed = (0.8 + this.random.nextDouble() * 1.2) * speed;
                double cosPhi = Math.cos(phi);
                this.bedVX[slot] = cosPhi * Math.cos(theta) * particleSpeed;
                this.bedVY[slot] = Math.sin(phi) * particleSpeed + 1.0;
                this.bedVZ[slot] = cosPhi * Math.sin(theta) * particleSpeed;
                int roll = this.random.nextInt(5);
                this.bedType[slot] = roll < 2 ? 0 : roll - 1;
                this.bedScale[slot] = (float) (baseSize * (0.6 + this.random.nextDouble() * 0.8));
                this.bedTime[slot] = now;
                this.bedActive[slot] = true;
                if (!wasActive) {
                    this.activeBedParticles++;
                }
            }
        }
        if (this.bedSound.getValue() && mc.thePlayer != null) {
            mc.thePlayer.playSound("random.orb", 1.0F, 1.5F);
            mc.thePlayer.playSound("random.levelup", 0.5F, 2.0F);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.activeHearts <= 0 && this.activeDots <= 0
                && this.activeBedParticles <= 0 && this.activeRainbows <= 0) {
            return;
        }
        double camX = AccessorRenderManager.getRenderPosX(mc.getRenderManager());
        double camY = AccessorRenderManager.getRenderPosY(mc.getRenderManager());
        double camZ = AccessorRenderManager.getRenderPosZ(mc.getRenderManager());
        long now = System.currentTimeMillis();
        this.renderTrail(camX, camY, camZ, now);
        this.renderBedVisuals(camX, camY, camZ, now);
    }

    private void renderTrail(double camX, double camY, double camZ, long now) {
        boolean renderHearts = this.activeHearts > 0 && this.hearts.getValue();
        boolean renderDots = this.activeDots > 0 && this.dots.getValue();
        if (!renderHearts && !renderDots) {
            return;
        }
        double opacityValue = this.opacity.getValue() / 100.0;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.disableCull();

        if (renderHearts) {
            this.renderHearts(camX, camY, camZ, now, opacityValue);
        }
        if (renderDots) {
            this.renderDots(camX, camY, camZ, now, opacityValue);
        }

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void renderHearts(double camX, double camY, double camZ, long now, double opacityValue) {
        long lifetime = this.heartsLifetime.getValue();
        float lineWidth = Math.max(HEART_LINE_WIDTH * 0.6F, 1.0F);
        GL11.glLineWidth(lineWidth);

        for (int i = 0; i < MAX_HEARTS; i++) {
            if (!this.heartActive[i]) {
                continue;
            }
            long age = now - this.heartTime[i];
            if (age > lifetime) {
                this.heartActive[i] = false;
                this.activeHearts--;
                continue;
            }
            double progress = (double) age / (double) lifetime;
            double alpha = progress < 0.1 ? progress / 0.1
                    : (progress > 0.6 ? (1.0 - progress) / 0.4 : 1.0);
            alpha *= opacityValue;
            double scaleAnimation = progress < 0.1 ? progress / 0.1
                    : (progress > 0.8 ? (1.0 - progress) / 0.2 : 1.0);

            double drawX = this.heartX[i] + Math.sin(age * 0.002 + i * 1.7) * 0.1 - camX;
            double drawY = this.heartY[i] + HEART_FLOAT_SPEED * progress * 1.5 - camY;
            double drawZ = this.heartZ[i] + Math.cos(age * 0.0015 + i * 2.3) * 0.1 - camZ;

            double red;
            double green;
            double blue;
            if (this.heartType[i] == 0) {
                red = 1.0;
                green = 0.5;
                blue = 0.8;
            } else if (this.heartType[i] == 1) {
                red = 1.0;
                green = 0.3;
                blue = 0.6;
            } else {
                red = 0.9;
                green = 0.4;
                blue = 0.9;
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) Math.toDegrees(Math.atan2(-drawX, -drawZ)),
                    0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((float) ((age * 0.1 + this.heartRotY[i]) % 360.0),
                    0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(this.heartRotZ[i], 0.0F, 0.0F, 1.0F);
            GL11.glLineWidth(lineWidth + 3.0F);
            drawShape(TRAIL_HEART_X, TRAIL_HEART_Y, TRAIL_HEART_SEGMENTS,
                    this.heartScale[i] * scaleAnimation / 16.0, alpha, red, green, blue, 3, 0.3);
            GL11.glLineWidth(lineWidth);
            GlStateManager.popMatrix();
        }
    }

    private void renderDots(double camX, double camY, double camZ, long now, double opacityValue) {
        long lifetime = this.dotsLifetime.getValue();
        boolean pulsing = this.pulse.getValue();
        GL11.glLineWidth(DOT_LINE_WIDTH);

        for (int i = 0; i < MAX_DOTS; i++) {
            if (!this.dotActive[i]) {
                continue;
            }
            long age = now - this.dotTime[i];
            if (age > lifetime) {
                this.dotActive[i] = false;
                this.activeDots--;
                continue;
            }
            double progress = (double) age / (double) lifetime;
            double fade = progress < 0.1 ? progress / 0.1
                    : (progress > 0.5 ? (1.0 - progress) / 0.5 : 1.0);
            double pulseFactor = 1.0;
            if (pulsing) {
                double flicker = 3.0 + this.dotType[i] * 1.5;
                pulseFactor = 0.5 + 0.5 * Math.sin(age * 0.01 * flicker + i * 2.7);
            }
            double seconds = age / 1000.0;
            double drawX = this.dotX[i] + this.dotDriftX[i] * seconds
                    + Math.sin(seconds * 1.5 + i * 1.3) * 0.15 - camX;
            double drawY = this.dotY[i] + this.dotDriftY[i] * seconds - camY;
            double drawZ = this.dotZ[i] + this.dotDriftZ[i] * seconds
                    + Math.cos(seconds * 1.2 + i * 2.1) * 0.15 - camZ;

            double red = 1.0;
            double green;
            double blue;
            if (this.dotType[i] == 0) {
                green = 0.45;
                blue = 0.7;
            } else if (this.dotType[i] == 1) {
                green = 0.6;
                blue = 0.85;
            } else if (this.dotType[i] == 2) {
                green = 0.3;
                blue = 0.55;
            } else {
                green = 0.75;
                blue = 0.95;
            }

            double alpha = fade * pulseFactor * opacityValue;
            if (alpha < 0.02) {
                continue;
            }
            double size = this.dotScale[i];

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) Math.toDegrees(Math.atan2(-drawX, -drawZ)),
                    0.0F, 1.0F, 0.0F);
            GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);

            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0.0, 0.0, 0.0);
            for (int j = 0; j <= DOT_FILL_SEGMENTS; j++) {
                GL11.glVertex3d(DOT_FILL_X[j] * size, DOT_FILL_Y[j] * size, 0.0);
            }
            GL11.glEnd();

            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int j = 0; j <= DOT_SEGMENTS; j++) {
                GL11.glVertex3d(DOT_CIRCLE_X[j] * size, DOT_CIRCLE_Y[j] * size, 0.0);
            }
            GL11.glEnd();

            GlStateManager.popMatrix();
        }
    }

    private void renderBedVisuals(double camX, double camY, double camZ, long now) {
        if (this.activeBedParticles <= 0 && this.activeRainbows <= 0) {
            return;
        }
        float lineWidth = this.rainbowLineWidth.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GlStateManager.disableCull();

        if (this.activeRainbows > 0) {
            this.renderRainbows(camX, camY, camZ, now, this.rainbowDuration.getValue(), lineWidth);
        }
        if (this.activeBedParticles > 0) {
            this.renderBedParticles(camX, camY, camZ, now, this.burstLifetime.getValue(), lineWidth);
        }

        GlStateManager.enableCull();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void renderRainbows(double camX, double camY, double camZ, long now,
                                long duration, float lineWidth) {
        for (int r = 0; r < MAX_RAINBOWS; r++) {
            if (!this.rainbowActive[r]) {
                continue;
            }
            long age = now - this.rainbowTime[r];
            if (age > duration) {
                this.rainbowActive[r] = false;
                this.activeRainbows--;
                continue;
            }
            double progress = (double) age / (double) duration;
            double alpha = progress < 0.15 ? progress / 0.15
                    : (progress > 0.6 ? (1.0 - progress) / 0.4 : 1.0);
            double arcProgress = progress < 0.2 ? Math.pow(progress / 0.2, 2.0) : 1.0;

            for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
                double angle = (double) segment / RAINBOW_SEGMENTS * Math.PI * arcProgress;
                ARC_COS[segment] = Math.cos(angle);
                ARC_SIN[segment] = Math.sin(angle);
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(this.rainbowX[r] - camX, this.rainbowY[r] - camY,
                    this.rainbowZ[r] - camZ);
            GlStateManager.rotate(this.rainbowYaw[r], 0.0F, 1.0F, 0.0F);

            for (int band = 0; band < 7; band++) {
                double radius = RAINBOW_SIZE + (band - 3) * RAINBOW_BAND_WIDTH;
                if (radius < 0.1) {
                    continue;
                }
                drawArc(radius, RAINBOW_RED[band], RAINBOW_GREEN[band], RAINBOW_BLUE[band],
                        alpha * 0.15, lineWidth + 3.0F);
                drawArc(radius, RAINBOW_RED[band], RAINBOW_GREEN[band], RAINBOW_BLUE[band],
                        alpha * 0.30, lineWidth + 1.5F);
                drawArc(radius, RAINBOW_RED[band], RAINBOW_GREEN[band], RAINBOW_BLUE[band],
                        alpha * 0.85, lineWidth);
            }
            if (arcProgress > 0.5) {
                drawSparkles(now, arcProgress, alpha);
            }
            GlStateManager.popMatrix();
        }
    }

    private static void drawArc(double radius, double red, double green, double blue,
                                double alpha, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
            GL11.glVertex3d(ARC_COS[segment] * radius, ARC_SIN[segment] * radius, 0.0);
        }
        GL11.glEnd();
    }

    private static void drawSparkles(long now, double arcProgress, double alpha) {
        double size = 0.15;
        double rotation = now * 0.003;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        GlStateManager.color(1.0F, 1.0F, 0.8F, (float) (alpha * 0.7));
        for (int end = 0; end < 2; end++) {
            double endAngle = end == 0 ? 0.0 : Math.PI * arcProgress;
            double x = Math.cos(endAngle) * RAINBOW_SIZE;
            double y = Math.sin(endAngle) * RAINBOW_SIZE;
            drawRay(x, y, cos, sin, size);
            drawRay(x, y, -sin, cos, size);
            drawRay(x, y, -cos, -sin, size);
            drawRay(x, y, sin, -cos, size);
        }
    }

    private static void drawRay(double x, double y, double dirX, double dirY, double size) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(x, y, 0.0);
        GL11.glVertex3d(x + dirX * size, y + dirY * size, 0.0);
        GL11.glEnd();
    }

    private void renderBedParticles(double camX, double camY, double camZ, long now,
                                    long lifetime, float lineWidth) {
        GL11.glLineWidth(lineWidth);
        for (int i = 0; i < MAX_BED_PARTICLES; i++) {
            if (!this.bedActive[i]) {
                continue;
            }
            long age = now - this.bedTime[i];
            if (age > lifetime) {
                this.bedActive[i] = false;
                this.activeBedParticles--;
                continue;
            }
            double progress = (double) age / (double) lifetime;
            double seconds = age / 1000.0;
            double px = this.bedX[i] + this.bedVX[i] * seconds
                    + Math.sin(age * 0.002 + i * 1.7) * 0.05;
            double py = this.bedY[i] + this.bedVY[i] * seconds - 1.5 * seconds * seconds;
            double pz = this.bedZ[i] + this.bedVZ[i] * seconds
                    + Math.cos(age * 0.0015 + i * 2.3) * 0.05;

            double alpha = progress < 0.1 ? progress / 0.1
                    : (progress > 0.7 ? (1.0 - progress) / 0.3 : 1.0);
            double scaleAnimation = progress < 0.1 ? progress / 0.1
                    : (progress > 0.7 ? (1.0 - progress) / 0.3 : 1.0);

            double drawX = px - camX;
            double drawY = py - camY;
            double drawZ = pz - camZ;
            int colour = i % 7;
            double size = this.bedScale[i] * scaleAnimation;

            GlStateManager.pushMatrix();
            GlStateManager.translate(drawX, drawY, drawZ);
            GlStateManager.rotate((float) Math.toDegrees(Math.atan2(drawX, drawZ)),
                    0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((float) (seconds * 40.0 + i * 60.0), 0.0F, 0.0F, 1.0F);

            switch (this.bedType[i]) {
                case 0:
                    drawShape(BED_HEART_X, BED_HEART_Y, BED_HEART_SEGMENTS, size / 16.0,
                            alpha, BED_RED[colour], BED_GREEN[colour], BED_BLUE[colour], 2, 0.25);
                    break;
                case 1:
                    drawShape(STAR_X, STAR_Y, STAR_SEGMENTS, size / 16.0,
                            alpha, BED_RED[colour], BED_GREEN[colour], BED_BLUE[colour], 2, 0.25);
                    break;
                case 2:
                    drawFilledDot(size / 2.0, alpha, BED_RED[colour], BED_GREEN[colour],
                            BED_BLUE[colour]);
                    break;
                default:
                    drawDiamond(size / 16.0, alpha, BED_RED[colour], BED_GREEN[colour],
                            BED_BLUE[colour]);
                    break;
            }
            GlStateManager.popMatrix();
        }
    }

    private static void drawShape(double[] shapeX, double[] shapeY, int segments, double scale,
                                  double baseAlpha, double red, double green, double blue,
                                  int layers, double glowFactor) {
        for (int layer = layers; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * (layers == 3 ? 0.08 : 0.1));
            double alpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (glowFactor / layer);
            GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= segments; i++) {
                GL11.glVertex3d(shapeX[i] * glowScale, shapeY[i] * glowScale, 0.0);
            }
            GL11.glEnd();
        }
    }

    private static void drawFilledDot(double scale, double baseAlpha, double red, double green,
                                      double blue) {
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.15);
            double alpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);
            GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex3d(0.0, 0.0, 0.0);
            for (int i = 0; i <= DOT_SEGMENTS; i++) {
                GL11.glVertex3d(DOT_CIRCLE_X[i] * glowScale, DOT_CIRCLE_Y[i] * glowScale, 0.0);
            }
            GL11.glEnd();
        }
    }

    private static void drawDiamond(double scale, double baseAlpha, double red, double green,
                                    double blue) {
        for (int layer = 2; layer >= 0; layer--) {
            double glowScale = scale * (1.0 + layer * 0.1);
            double alpha = layer == 0 ? baseAlpha * 0.9 : baseAlpha * (0.25 / layer);
            GlStateManager.color((float) red, (float) green, (float) blue, (float) alpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(0.0, 14.0 * glowScale, 0.0);
            GL11.glVertex3d(8.0 * glowScale, 0.0, 0.0);
            GL11.glVertex3d(0.0, -14.0 * glowScale, 0.0);
            GL11.glVertex3d(-8.0 * glowScale, 0.0, 0.0);
            GL11.glVertex3d(0.0, 14.0 * glowScale, 0.0);
            GL11.glEnd();
        }
    }
}
