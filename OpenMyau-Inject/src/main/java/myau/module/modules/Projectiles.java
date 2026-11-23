package myau.module.modules;

import myau.access.AccessorEntityArrow;
import myau.access.AccessorRenderManager;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.util.VapeRenderUtil;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.List;

/** Flight predictions for projectiles already in the world, ported from src. */
public class Projectiles extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty arrows = new BooleanProperty("arrows", true);
    public final BooleanProperty pearls = new BooleanProperty("pearls", true);
    public final BooleanProperty potions = new BooleanProperty("potions", true);
    public final BooleanProperty eggs = new BooleanProperty("eggs", false);
    public final BooleanProperty snowballs = new BooleanProperty("snowballs", false);
    public final BooleanProperty impactMarker = new BooleanProperty("impact-marker", true);
    public final FloatProperty lineWidth = new FloatProperty("line-width", 2.0f, 1.0f, 5.0f);
    public final IntProperty red = new IntProperty("red", 85, 0, 255);
    public final IntProperty green = new IntProperty("green", 200, 0, 255);
    public final IntProperty blue = new IntProperty("blue", 255, 0, 255);

    public Projectiles() { super("Projectiles", false, true); }

    private boolean enabledFor(Entity entity) {
        if (entity instanceof EntityArrow) return arrows.getValue() && !AccessorEntityArrow.isInGround((EntityArrow)entity);
        if (entity instanceof EntityEnderPearl) return pearls.getValue();
        if (entity instanceof EntityPotion) return potions.getValue();
        if (entity instanceof EntityEgg) return eggs.getValue();
        return entity instanceof EntitySnowball && snowballs.getValue();
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (!isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;
        RenderManager rm = mc.getRenderManager();
        double cx = AccessorRenderManager.getRenderPosX(rm);
        double cy = AccessorRenderManager.getRenderPosY(rm);
        double cz = AccessorRenderManager.getRenderPosZ(rm);
        try (VapeRenderUtil.State ignored = VapeRenderUtil.save()) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GlStateManager.color(red.getValue() / 255.0f, green.getValue() / 255.0f, blue.getValue() / 255.0f, 1);
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!entity.isDead && enabledFor(entity)) drawPath(entity, event.getPartialTicks(), cx, cy, cz);
            }
        }
    }

    private void drawPath(Entity entity, float pt, double cx, double cy, double cz) {
        double x = entity.posX, y = entity.posY, z = entity.posZ;
        double mx = entity.motionX, my = entity.motionY, mz = entity.motionZ;
        if (!Double.isFinite(x + y + z + mx + my + mz)) return;
        // Interpolate the visible origin; simulate from the actual current tick.
        List<Vec3> points = new ArrayList<>();
        points.add(new Vec3(entity.lastTickPosX + (x - entity.lastTickPosX) * pt,
                entity.lastTickPosY + (y - entity.lastTickPosY) * pt,
                entity.lastTickPosZ + (z - entity.lastTickPosZ) * pt));
        points.add(new Vec3(x, y, z));
        double gravity = entity instanceof EntityArrow || entity instanceof EntityPotion ? 0.05 : 0.03;
        Vec3 impact = null;
        for (int i = 0; i < 160; i++) {
            Vec3 from = new Vec3(x,y,z), next = from.addVector(mx,my,mz);
            if (!mc.theWorld.isBlockLoaded(new BlockPos(next))) break;
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(from, next, false, true, false);
            if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                impact = hit.hitVec;
                points.add(impact);
                break;
            }
            points.add(next);
            x = next.xCoord; y = next.yCoord; z = next.zCoord;
            if (y < 0) break;
            boolean water = mc.theWorld.getBlockState(new BlockPos(next)).getBlock().getMaterial() == Material.water;
            double drag = water ? (entity instanceof EntityArrow ? 0.6 : 0.8) : 0.99;
            // Vanilla moves first, then applies drag and gravity for the next tick.
            mx *= drag; my = my * drag - gravity; mz *= drag;
        }
        GL11.glLineWidth(lineWidth.getValue());
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3 p : points) GL11.glVertex3d(p.xCoord - cx, p.yCoord - cy, p.zCoord - cz);
        GL11.glEnd();
        if (impactMarker.getValue() && impact != null) {
            double ix = impact.xCoord - cx, iy = impact.yCoord - cy, iz = impact.zCoord - cz, s = 0.12;
            GL11.glLineWidth(Math.max(1.0f, lineWidth.getValue() - 0.5f));
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(ix-s,iy,iz); GL11.glVertex3d(ix+s,iy,iz);
            GL11.glVertex3d(ix,iy-s,iz); GL11.glVertex3d(ix,iy+s,iz);
            GL11.glVertex3d(ix,iy,iz-s); GL11.glVertex3d(ix,iy,iz+s);
            GL11.glEnd();
        }
    }
}
