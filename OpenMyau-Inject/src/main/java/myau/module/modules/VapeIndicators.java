package myau.module.modules;

import myau.access.AccessorEntityArrow;
import myau.access.AccessorRenderManager;
import myau.util.VapeRenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockFire;
import net.minecraft.block.BlockLiquid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

final class VapeIndicators {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final Indicators settings;
    private final Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
    private final Set<Entity> tracked = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
    private final Map<Item, Long> lastUse = new HashMap<>();
    private final List<Indicator> projected = new ArrayList<>();
    private final FloatBuffer model = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer screen = BufferUtils.createFloatBuffer(3);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private Object world, player;

    VapeIndicators(Indicators settings) { this.settings = settings; }

    public enum Kind {
        Arrow(Items.arrow, null),
        Fireball(Items.fire_charge, new Color(255, 150, 0, 255)),
        Ender_pearl(Items.ender_pearl, new Color(210, 0, 255, 255)),
        Egg(Items.egg, new Color(255, 230, 120, 255)),
        Snowball(Items.snowball, new Color(200, 235, 255, 255)),
        Potion(Items.potionitem, new Color(255, 90, 200, 255)),
        Player(null, null);

        public final ItemStack item;
        public final Color color;

        Kind(Item item, Color color) {
            this.item = item == null ? null : new ItemStack(item);
            this.color = color;
        }
    }


    private static final class Indicator {
        final Entity entity;
        final Kind kind;
        final double x, y;
        final boolean inFront;
        Indicator(Entity entity, Kind kind, double x, double y, boolean inFront) {
            this.entity = entity; this.kind = kind; this.x = x; this.y = y; this.inFront = inFront;
        }
    }

    void used(C08PacketPlayerBlockPlacement packet) {
        if (world != mc.theWorld || player != mc.thePlayer) {
            reset(); world = mc.theWorld; player = mc.thePlayer;
        }
        ItemStack stack = packet.getStack();
        if (stack != null) lastUse.put(stack.getItem(), System.currentTimeMillis());
    }

    private Kind kind(Entity entity) {
        if (entity instanceof EntityArrow) return settings.vapeRenderArrows.getValue() ? Kind.Arrow : null;
        if (entity instanceof EntityEnderPearl) return settings.vapeRenderEnderPearls.getValue() ? Kind.Ender_pearl : null;
        if (entity instanceof EntityLargeFireball) return settings.vapeRenderFireballs.getValue() ? Kind.Fireball : null;
        if (entity instanceof EntityEgg) return settings.vapeRenderEggs.getValue() ? Kind.Egg : null;
        if (entity instanceof EntitySnowball) return settings.vapeRenderSnowballs.getValue() ? Kind.Snowball : null;
        if (entity instanceof EntityPotion) return settings.vapeRenderPotions.getValue() ? Kind.Potion : null;
        return null;
    }

    private boolean own(Entity entity, Kind kind) {
        if (entity instanceof EntityArrow) return ((EntityArrow) entity).shootingEntity == mc.thePlayer;
        if (entity instanceof EntityThrowable && ((EntityThrowable) entity).getThrower() == mc.thePlayer) return true;
        if (entity instanceof EntityFireball && ((EntityFireball) entity).shootingEntity == mc.thePlayer) return true;
        Long time = lastUse.get(kind.item.getItem());
        return time != null && System.currentTimeMillis() - time <= 400
                && mc.thePlayer.getDistanceSqToEntity(entity) <= 8.0;
    }

    private void update() {
        if (world != mc.theWorld || player != mc.thePlayer) {
            reset();
            world = mc.theWorld;
            player = mc.thePlayer;
        }
        seen.retainAll(mc.theWorld.loadedEntityList);
        tracked.retainAll(mc.theWorld.loadedEntityList);
        for (Entity entity : mc.theWorld.loadedEntityList) {
            Kind kind = kind(entity);
            if (kind == null || entity.isDead || !seen.add(entity)) continue;
            if (kind == Kind.Arrow && mc.thePlayer.getDistanceSqToEntity(entity) <= 49.0) continue;
            if (!own(entity, kind)) tracked.add(entity);
        }
    }

    private boolean threat(Entity entity) {
        if (!settings.vapeRenderOnlyThreats.getValue()) return true;
        double x = entity.posX, y = entity.posY, z = entity.posZ;
        double mx = entity.posX - entity.lastTickPosX;
        double my = entity.posY - entity.lastTickPosY;
        double mz = entity.posZ - entity.lastTickPosZ;
        if (mx == 0 && my == 0 && mz == 0) return false;
        for (int i = 0; i < 400; i++) {
            if (mc.thePlayer.getDistanceSq(x,y,z) <= 49.0) return true;
            Block block = mc.theWorld.getBlockState(new BlockPos(x,y,z)).getBlock();
            if (!(block instanceof BlockAir || block instanceof BlockLiquid || block instanceof BlockFire)) break;
            x += mx * 0.5; y += my * 0.5; z += mz * 0.5;
        }
        return false;
    }

    void capture(float pt) {
        projected.clear();
        if (mc.thePlayer == null || mc.theWorld == null) { reset(); return; }
        update();
        model.clear(); projection.clear(); viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        ScaledResolution sr = new ScaledResolution(mc);
        RenderManager rm = mc.getRenderManager();
        double cx = AccessorRenderManager.getRenderPosX(rm);
        double cy = AccessorRenderManager.getRenderPosY(rm);
        double cz = AccessorRenderManager.getRenderPosZ(rm);
        for (Entity entity : tracked) {
            Kind kind = kind(entity);
            if (kind == null || entity.isDead) continue;
            if (entity instanceof EntityArrow && AccessorEntityArrow.isInGround((EntityArrow) entity)) continue;
            if (kind == Kind.Fireball && !threat(entity)) continue;
            double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * pt - cx;
            double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * pt + entity.height / 2.0 - cy;
            double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * pt - cz;
            screen.clear();
            if (GLU.gluProject((float)x, (float)y, (float)z, model, projection, viewport, screen)) {
                double sx = screen.get(0) / sr.getScaleFactor();
                double sy = (mc.displayHeight - screen.get(1)) / sr.getScaleFactor();
                if (Double.isFinite(sx) && Double.isFinite(sy)) {
                    projected.add(new Indicator(entity, kind, sx, sy, screen.get(2) < 1.0003684));
                }
            }
        }
    }

    void render() {
        if (mc.thePlayer == null || mc.theWorld == null || world != mc.theWorld || player != mc.thePlayer
                || mc.currentScreen != null) return;
        ScaledResolution sr = new ScaledResolution(mc);
        VapeRenderUtil.overlay(() -> {
            for (Indicator indicator : projected) {
                if (!indicator.entity.isDead && kind(indicator.entity) != null) render(indicator, sr);
            }
        });
    }

    void reset() {
        tracked.clear(); seen.clear(); projected.clear(); lastUse.clear(); world = null; player = null;
    }

    private void render(Indicator indicator, ScaledResolution sr) {
        Entity entity = indicator.entity;
        Kind kind = indicator.kind;
        double dx = indicator.x - sr.getScaledWidth() / 2.0;
        double dy = indicator.y - sr.getScaledHeight() / 2.0;
        boolean inFront = indicator.inFront;
        int n = settings.vapeItemColors.getValue() && kind.color != null ? kind.color.getRGB() : -1;
        if (!inFront) {
            dx *= -1.0;
            dy *= -1.0;
        }
        double angle = Math.atan2(dx, dy);
        double rot = Math.atan2(dy, dx) * (double) 57.29578f + 90.0;
        double dist = Math.hypot(dx, dy);
        double r = settings.vapeCircleRadius.getValue();
        if (settings.vapeRenderItem.getValue()) {
            r += 20.0;
        }
        if (inFront && dist < r + 15.0) {
            return;
        }
        double cx = (double) sr.getScaledWidth() / 2.0;
        double cy = (double) sr.getScaledHeight() / 2.0;
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double px = cx + r * sin;
        double py = cy + r * cos;
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0.0);
        GlStateManager.rotate((float) rot, 0.0f, 0.0f, 1.0f);
        GlStateManager.scale(1.0f, 1.0f, 1.0f);
        if (settings.vapeArrow.getValue() == 0) {
            if (n == -1) {
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GlStateManager.color(kind.color.getRed() / 255.0f, kind.color.getGreen() / 255.0f, kind.color.getBlue() / 255.0f);
            }
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.blendFunc(770, 771);
            GL11.glEnable(2848);
            double a = 35.0f * ((float) Math.PI / 180);
            double len = 9.0;
            double off = 5.0;
            GL11.glLineWidth(3.0f);
            GL11.glBegin(3);
            GL11.glVertex2d(Math.sin(-a) * len, Math.cos(-a) * len - off);
            GL11.glVertex2d(0.0, -off);
            GL11.glVertex2d(Math.sin(a) * len, Math.cos(a) * len - off);
            GL11.glEnd();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GL11.glDisable(2848);
            if (n != -1) {
                GlStateManager.resetColor();
            }
        } else if (settings.vapeArrow.getValue() == 1) {
            GlStateManager.rotate(-90.0f, 0.0f, 0.0f, 1.0f);
            GlStateManager.scale(1.5, 1.5, 1.5);
            mc.fontRendererObj.drawString(">", -2.0f, -4.0f, n, false);
            if (n != -1) {
                GlStateManager.resetColor();
            }
        } else if (settings.vapeArrow.getValue() == 2) {
            myau.util.RenderUtil.setColor(n);
            GlStateManager.enableBlend();
            GlStateManager.disableTexture2D();
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2d(0, -5);
            GL11.glVertex2d(-4.33, 2.5);
            GL11.glVertex2d(4.33, 2.5);
            GL11.glEnd();
            GlStateManager.enableTexture2D();
        }
        GlStateManager.popMatrix();
        px = cx + (r - 13.0) * sin;
        py = cy + (r - 13.0) * cos;
        GlStateManager.pushMatrix();
        GlStateManager.translate(px, py, 0.0);
        GlStateManager.scale(0.8, 0.8, 0.8);
        if (settings.vapeShowDistance.getValue()) {
            String s = (int) mc.thePlayer.getDistanceToEntity(entity) + "m";
            mc.fontRendererObj.drawString(s, (float) (-mc.fontRendererObj.getStringWidth(s)) / 2.0f, -4.0f, -1, true);
        }
        GlStateManager.popMatrix();
        if (settings.vapeRenderItem.getValue()) {
            GlStateManager.pushMatrix();
            if (kind == Kind.Arrow) {
                px = cx + (r - 26.0) * sin;
                py = cy + (r - 26.0) * cos;
                GlStateManager.translate(px, py, 0.0);
                GlStateManager.scale(1.0f, 1.0f, 1.0f);
                GlStateManager.rotate((float) rot - 45.0f, 0.0f, 0.0f, 1.0f);
                mc.getRenderItem().renderItemIntoGUI(kind.item, -12, -4);
            } else {
                px = cx + (r - 29.0) * sin;
                py = cy + (r - 29.0) * cos;
                GlStateManager.translate(px, py, 0.0);
                GlStateManager.scale(1.0f, 1.0f, 1.0f);
                mc.getRenderItem().renderItemIntoGUI(kind.item, -8, -9);
            }
            GlStateManager.popMatrix();
        }
    }

}
