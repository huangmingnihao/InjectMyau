package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;























public class FireballPredict extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();


    private static final double NEAR_DISTANCE = 8.0;
    private static final double FAR_DISTANCE = 48.0;
    private static final double MID_DISTANCE = 24.0;


    private static final int ALERT_COLOR = 16756736;

    public final BooleanProperty realFireballs =
            new BooleanProperty("Real-fireballs", true);
    public final FloatProperty renderRadius =
            new FloatProperty("Render-radius", 2.0F, 1.0F, 2.0F);
    public final BooleanProperty heldFireCharges =
            new BooleanProperty("Held-fire-charges", true);
    public final FloatProperty predictRange =
            new FloatProperty("Predict-range", 100.0F, 16.0F, 200.0F);
    public final PercentProperty opacity = new PercentProperty("Opacity", 50);


    private BlockPos impactPos;

    private int impactColor;

    public FireballPredict() {
        super("FireballPredict", false);
    }

    @Override
    public void onDisabled() {
        this.impactPos = null;
        this.impactColor = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Predict"};
    }








    private int colorForDistance(double distance) {
        if (distance <= NEAR_DISTANCE) {
            return 16711680;
        }
        if (distance >= FAR_DISTANCE) {
            return 65280;
        }
        if (distance <= MID_DISTANCE) {
            float t = (float) ((distance - NEAR_DISTANCE) / 16.0);
            return rgb(255, Math.round(255.0F * t), 0);
        }
        float t = (float) ((distance - MID_DISTANCE) / 24.0);
        return rgb(Math.round(255.0F * (1.0F - t)), 255, 0);
    }

    private static int rgb(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }


    private static int alertify(int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        return red > 200 && green < 96 && blue < 96 ? ALERT_COLOR : color;
    }




    private BlockPos predictRealFireball(int[] outColor) {
        WorldClient world = mc.theWorld;
        if (world == null) {
            return null;
        }
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos bestPos = null;
        int bestColor = 0;
        double bestDistance = Double.MAX_VALUE;
        double range = this.predictRange.getValue().doubleValue();

        for (Object element : world.loadedEntityList) {
            Entity entity = (Entity) element;

            if (!(entity instanceof EntityFireball) || entity instanceof EntityWitherSkull) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) entity;

            double speedSq = fireball.motionX * fireball.motionX
                    + fireball.motionY * fireball.motionY
                    + fireball.motionZ * fireball.motionZ;
            if (speedSq < 1.0E-4) {
                continue;
            }

            Vec3 origin = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
            Vec3 direction = new Vec3(fireball.motionX, fireball.motionY, fireball.motionZ).normalize();
            Vec3 target = origin.addVector(
                    direction.xCoord * range, direction.yCoord * range, direction.zCoord * range);

            MovingObjectPosition hit = world.rayTraceBlocks(origin, target, false, true, false);
            if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK) {
                continue;
            }
            BlockPos hitPos = hit.getBlockPos();
            Vec3 center = new Vec3(
                    hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5);
            double distanceSq = playerPos.squareDistanceTo(center);
            if (distanceSq >= bestDistance) {
                continue;
            }
            bestDistance = distanceSq;
            bestPos = hitPos;
            bestColor = this.colorForDistance(origin.distanceTo(hit.hitVec));
        }

        if (bestPos != null && outColor != null) {
            outColor[0] = bestColor;
        }
        return bestPos;
    }


    private BlockPos predictHeldFireCharge(int[] outColor) {
        WorldClient world = mc.theWorld;
        if (world == null) {
            return null;
        }
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        double range = this.predictRange.getValue().doubleValue();

        for (Object element : world.playerEntities) {
            EntityPlayer player = (EntityPlayer) element;
            ItemStack held = player.getHeldItem();
            if (held == null || held.getItem() != Items.fire_charge) {
                continue;
            }
            Vec3 eyes = player.getPositionEyes(1.0F);
            Vec3 look = player.getLook(1.0F);
            Vec3 target = eyes.addVector(
                    look.xCoord * range, look.yCoord * range, look.zCoord * range);

            MovingObjectPosition hit = world.rayTraceBlocks(eyes, target, false, true, false);
            if (hit == null || hit.typeOfHit != MovingObjectType.BLOCK) {
                continue;
            }
            BlockPos hitPos = hit.getBlockPos();
            Vec3 center = new Vec3(
                    hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5);
            double distanceSq = playerPos.squareDistanceTo(center);
            if (distanceSq >= bestDistance) {
                continue;
            }
            bestDistance = distanceSq;
            bestPos = hitPos;
        }

        if (bestPos != null && outColor != null) {
            outColor[0] = 16776960;
        }
        return bestPos;
    }

    private double radius() {
        return Math.max(1.0, Math.min(2.0, this.renderRadius.getValue().doubleValue()));
    }

    private boolean isSolidBlock(WorldClient world, BlockPos pos) {
        if (world.isAirBlock(pos)) {
            return false;
        }
        Block block = world.getBlockState(pos).getBlock();
        return block.isFullCube();
    }


    private int alphaFor(int baseAlpha, boolean highlight) {
        double percent = this.opacity.getValue().doubleValue();
        if (highlight && percent > 0.0) {
            percent = Math.min(100.0, percent * 1.4);
        }
        int alpha = Math.max(0, Math.min(baseAlpha, (int) (baseAlpha * percent / 100.0)));
        return alpha;
    }



    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != myau.event.types.EventType.PRE) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        BlockPos pos = null;
        int[] colorHolder = new int[]{0};

        if (this.realFireballs.getValue()) {
            pos = this.predictRealFireball(colorHolder);
        }

        if (pos == null && this.heldFireCharges.getValue()) {
            pos = this.predictHeldFireCharge(colorHolder);
        }

        if (pos == null) {
            this.impactPos = null;
            this.impactColor = 0;
        } else {
            this.impactPos = pos;
            this.impactColor = colorHolder[0];
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (this.impactPos == null) {
            return;
        }

        WorldClient world = mc.theWorld;
        int radius = (int) Math.max(1, Math.min(2, (int) this.renderRadius.getValue().doubleValue()));
        int color = alertify(this.impactColor);
        boolean highlight = color == ALERT_COLOR;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;

        int outlineAlpha = this.alphaFor(255, highlight);
        int fillAlpha = this.alphaFor(highlight ? 70 : 42, highlight);

        if (outlineAlpha <= 0 && fillAlpha <= 0) {
            return;
        }

        RenderUtil.enableRenderState();
        try {
            if (fillAlpha > 0) {
                this.drawImpactArea(world, radius, red, green, blue, fillAlpha, true);
            }
            if (outlineAlpha > 0) {
                this.drawImpactArea(world, radius, red, green, blue, outlineAlpha, false);
                RenderUtil.drawBlockBoundingBox(
                        this.impactPos, 1.0, 255, 255, 255, outlineAlpha, 2.2F);
            }
        } finally {
            RenderUtil.disableRenderState();
            RenderUtil.setColor(-1);
        }
    }





    private void drawImpactArea(WorldClient world, int radius,
                                int red, int green, int blue, int alpha, boolean filled) {
        BlockPos origin = this.impactPos;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!this.isSolidBlock(world, pos)) {
                        continue;
                    }
                    if (filled) {
                        RenderUtil.drawBlockBoxAlpha(pos, 1.0, red, green, blue, alpha);
                    } else {
                        RenderUtil.drawBlockBoundingBox(pos, 1.0, red, green, blue, alpha, 1.5F);
                    }
                }
            }
        }
    }
}
