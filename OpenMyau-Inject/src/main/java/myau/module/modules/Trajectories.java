package myau.module.modules;

import myau.event.EventTarget;
import myau.events.LoadWorldEvent;
import myau.property.properties.ModeProperty;
import myau.events.Render3DEvent;
import myau.access.AccessorRenderManager;
import myau.module.Module;
import myau.util.RenderUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;

public class Trajectories extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Myau", "Vape"});
    public final BooleanProperty vapeAutoScaleTarget = new BooleanProperty("vape-auto-scale-target", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeDisableUnchargedBow = new BooleanProperty("vape-disable-uncharged-bow", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeHighlightOnEntity = new BooleanProperty("vape-highlight-on-entity", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeDrawBlockOutline = new BooleanProperty("vape-draw-block-outline", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeShortenLine = new BooleanProperty("vape-shorten-line", false, () -> mode.getValue() == 1);
    public final BooleanProperty vapeBow = new BooleanProperty("vape-bow", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeEgg = new BooleanProperty("vape-egg", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeEnderPearl = new BooleanProperty("vape-ender-pearl", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeEnderPearlThrown = new BooleanProperty("vape-ender-pearl-thrown", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeFireballEnemy = new BooleanProperty("vape-fireball-enemy", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeFireballThrown = new BooleanProperty("vape-fireball-thrown", true, () -> mode.getValue() == 1);
    public final BooleanProperty vapeSnowball = new BooleanProperty("vape-snowball", true, () -> mode.getValue() == 1);
    private final VapeTrajectories vape = new VapeTrajectories(this);
    public final PercentProperty opacity = new PercentProperty("opacity", 100, () -> mode.getValue() == 0);
    public final BooleanProperty bow = new BooleanProperty("bow", true, () -> mode.getValue() == 0);
    public final BooleanProperty projectiles = new BooleanProperty("projectiles", false, () -> mode.getValue() == 0);
    public final BooleanProperty pearls = new BooleanProperty("pearls", true, () -> mode.getValue() == 0);

    public Trajectories() {
        super("Trajectories", false, true);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (isEnabled() && mode.getValue() == 1) {
            vape.render(event.getPartialTicks());
            return;
        }
        if (this.isEnabled() && mc.thePlayer.getHeldItem() != null && mc.gameSettings.thirdPersonView == 0) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            RenderManager renderManager = mc.getRenderManager();
            boolean isBow = false;
            float velocityMultiplier = 1.5F;
            float drag = 0.99F;
            float gravity;
            float hitboxExpand;
            if (item instanceof ItemBow && this.bow.getValue()) {
                if (!mc.thePlayer.isUsingItem()) {
                    return;
                }
                isBow = true;
                gravity = 0.05F;
                hitboxExpand = 0.3F;
                float charge = (float) mc.thePlayer.getItemInUseDuration() / 20.0F;
                charge = (charge * charge + charge * 2.0F) / 3.0F;
                if (charge < 0.1F) {
                    return;
                }
                if (charge > 1.0F) {
                    charge = 1.0F;
                }
                velocityMultiplier = charge * 3.0F;
            } else if (item instanceof ItemFishingRod && this.projectiles.getValue()) {
                gravity = 0.04F;
                hitboxExpand = 0.25F;
                drag = 0.92F;
            } else if ((item instanceof ItemSnowball || item instanceof ItemEgg) && this.projectiles.getValue()) {
                gravity = 0.03F;
                hitboxExpand = 0.25F;
            } else {
                if (!(item instanceof ItemEnderPearl) || !this.pearls.getValue()) {
                    return;
                }
                gravity = 0.03F;
                hitboxExpand = 0.25F;
            }
            float yaw = mc.thePlayer.rotationYaw;
            float pitch = mc.thePlayer.rotationPitch;
            double x = AccessorRenderManager.getRenderPosX(renderManager) - (double) MathHelper.cos(yaw / 180.0F * (float) Math.PI) * 0.16;
            double y = AccessorRenderManager.getRenderPosY(renderManager) + (double) mc.thePlayer.getEyeHeight() - 0.1F;
            double z = AccessorRenderManager.getRenderPosZ(renderManager) - (double) MathHelper.sin(yaw / 180.0F * (float) Math.PI) * 0.16;
            double mx = (double) (MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI))
                    * (isBow ? 1.0 : 0.4)
                    * -1.0;
            double my = (double) MathHelper.sin(pitch / 180.0F * (float) Math.PI) * (isBow ? 1.0 : 0.4) * -1.0;
            double mz = (double) (MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI)) * (isBow ? 1.0 : 0.4);
            float mag = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
            mx /= mag;
            my /= mag;
            mz /= mag;
            mx *= velocityMultiplier;
            my *= velocityMultiplier;
            mz *= velocityMultiplier;
            MovingObjectPosition mop = null;
            boolean hasHitBlock = false;
            boolean hasHitEntity = false;
            WorldRenderer worldRenderer = Tessellator.getInstance().getWorldRenderer();
            ArrayList<Vec3> trajectoryPoints = new ArrayList<>();
            while (!hasHitBlock && y > 0.0) {
                Vec3 start = new Vec3(x, y, z);
                Vec3 end = new Vec3(x + mx, y + my, z + mz);
                mop = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
                start = new Vec3(x, y, z);
                end = new Vec3(x + mx, y + my, z + mz);
                if (mop != null) {
                    hasHitBlock = true;
                    end = new Vec3(mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord);
                }
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) hitboxExpand,
                        y - (double) hitboxExpand,
                        z - (double) hitboxExpand,
                        x + (double) hitboxExpand,
                        y + (double) hitboxExpand,
                        z + (double) hitboxExpand
                )
                        .addCoord(mx, my, mz)
                        .expand(1.0, 1.0, 1.0);
                int minChunkX = MathHelper.floor_double((aabb.minX - 2.0) / 16.0);
                int maxChunkX = MathHelper.floor_double((aabb.maxX + 2.0) / 16.0);
                int minChunkZ = MathHelper.floor_double((aabb.minZ - 2.0) / 16.0);
                int maxChunkZ = MathHelper.floor_double((aabb.maxZ + 2.0) / 16.0);
                ArrayList<Entity> possibleEntities = new ArrayList<>();
                for (int x1 = minChunkX; x1 <= maxChunkX; ++x1) {
                    for (int z1 = minChunkZ; z1 <= maxChunkZ; ++z1) {
                        mc.theWorld.getChunkFromChunkCoords(x1, z1).getEntitiesWithinAABBForEntity(mc.thePlayer, aabb, possibleEntities, null);
                    }
                }
                for (Entity entity : possibleEntities) {
                    if (entity.canBeCollidedWith() && entity != mc.thePlayer) {
                        AxisAlignedBB entityBox = entity.getEntityBoundingBox().expand(hitboxExpand, hitboxExpand, hitboxExpand);
                        MovingObjectPosition intercept = entityBox.calculateIntercept(start, end);
                        if (intercept != null) {
                            hasHitEntity = true;
                            hasHitBlock = true;
                            mop = intercept;
                        }
                    }
                }
                x += mx;
                y += my;
                z += mz;
                if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial() == Material.water) {
                    mx *= 0.6;
                    my *= 0.6;
                    mz *= 0.6;
                } else {
                    mx *= drag;
                    my *= drag;
                    mz *= drag;
                }
                my -= gravity;
                trajectoryPoints.add(
                        new Vec3(
                                x - AccessorRenderManager.getRenderPosX(renderManager),
                                y - AccessorRenderManager.getRenderPosY(renderManager),
                                z - AccessorRenderManager.getRenderPosZ(renderManager)
                        )
                );
            }
            if (trajectoryPoints.size() > 1) {
                RenderUtil.enableRenderState();
                RenderUtil.setColor(new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB());
                GL11.glLineWidth(1.5F);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
                trajectoryPoints.forEach(vec3 -> worldRenderer.pos(vec3.xCoord, vec3.yCoord, vec3.zCoord).endVertex());
                Tessellator.getInstance().draw();
                GlStateManager.pushMatrix();
                GlStateManager.translate(
                        x - AccessorRenderManager.getRenderPosX(renderManager),
                        y - AccessorRenderManager.getRenderPosY(renderManager),
                        z - AccessorRenderManager.getRenderPosZ(renderManager)
                );
                if (mop != null) {
                    switch (mop.sideHit.getAxis().ordinal()) {
                        case 0:
                            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                            break;
                        case 1:
                            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                    }
                    RenderUtil.drawLine(
                            -0.25F,
                            -0.25F,
                            0.25F,
                            0.25F,
                            1.5F,
                            new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB()
                    );
                    RenderUtil.drawLine(
                            -0.25F,
                            0.25F,
                            0.25F,
                            -0.25F,
                            1.5F,
                            new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB()
                    );
                }
                GlStateManager.popMatrix();
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(2.0F);
                GlStateManager.resetColor();
                RenderUtil.disableRenderState();
            }
        }
    }
    @EventTarget
    public void onWorld(LoadWorldEvent event) { vape.reset(); }

    @Override
    public void onDisabled() { vape.reset(); }

    @Override
    public void verifyValue(String name) { if (name.equals("mode")) vape.reset(); }
}
