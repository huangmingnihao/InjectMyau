package myau.module.modules;

import myau.Myau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.Render2DFrameEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.RadarProjection;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
    public final ModeProperty colorMode = new ModeProperty("color", 1, new String[]{"DEFAULT", "TEAMS", "HUD"});
    // Keep the existing position indices and property names compatible with saved configs.
    public final IntProperty position = new IntProperty("position", 0, 0, 4);
    public final IntProperty offsetX = new IntProperty("offset-x", 80, 0, 1000, () -> position.getValue() != 4);
    public final IntProperty offsetY = new IntProperty("offset-y", 80, 0, 1000, () -> position.getValue() != 4);
    public final IntProperty radarRadius = new IntProperty("radar-radius", 60, 10, 200);
    public final IntProperty range = new IntProperty("range", 64, 8, 512);
    public final ModeProperty orientation = new ModeProperty("orientation", 0, new String[]{"HEADING", "NORTH"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 3.0F, 0.1F);
    public final FloatProperty dotRadius = new FloatProperty("dot-radius", 2.5F, 0.1F, 5.0F);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);
    public final BooleanProperty showOutside = new BooleanProperty("show-outside", false);
    public final BooleanProperty showHeight = new BooleanProperty("show-height", true);
    public final BooleanProperty showNames = new BooleanProperty("show-names", false);
    public final BooleanProperty showDistance = new BooleanProperty("show-distance", false);
    public final BooleanProperty showCompass = new BooleanProperty("show-compass", true);
    public final BooleanProperty showInfo = new BooleanProperty("show-info", true);
    public final BooleanProperty showPVP = new BooleanProperty("show-pvp", false);
    public final IntProperty opacity = new IntProperty("opacity", 100, 0, 255);
    public final ColorProperty fillColor = new ColorProperty("fill-color", Color.GRAY.getRGB());
    public final ColorProperty outlineColor = new ColorProperty("outline-color", new Color(92, 111, 133).getRGB());
    public final ColorProperty crossColor = new ColorProperty("cross-color", new Color(145, 160, 180).getRGB());

    public Radar() {
        super("Radar", false);
    }

    private boolean shouldRender(EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.getRenderViewEntity()
                || player.isDead || player.deathTime > 0 || player.isSpectator()) return false;
        if (TeamUtil.isBot(player)) return showBots.getValue();
        if (TeamUtil.isFriend(player)) return showFriends.getValue();
        return TeamUtil.isTarget(player) ? showEnemies.getValue() : showPlayers.getValue();
    }

    private int getEntityColor(EntityPlayer player, HUD hud) {
        if (TeamUtil.isFriend(player)) return Myau.friendManager.getColor().getRGB();
        if (TeamUtil.isTarget(player)) return Myau.targetManager.getColor().getRGB();
        switch (colorMode.getValue()) {
            case 0: return TeamUtil.getTeamColor(player, 1.0F).getRGB();
            case 1: return TeamUtil.isSameTeam(player) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
            case 2: return hud == null ? Color.WHITE.getRGB() : hud.getColor(System.currentTimeMillis()).getRGB();
            default: return Color.WHITE.getRGB();
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRender(Render2DFrameEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null
                || mc.gameSettings.hideGUI || mc.skipRenderWorld) return;

        float partialTicks = event.getPartialTicks();
        double selfX = interpolate(mc.thePlayer.lastTickPosX, mc.thePlayer.posX, partialTicks);
        double selfY = interpolate(mc.thePlayer.lastTickPosY, mc.thePlayer.posY, partialTicks);
        double selfZ = interpolate(mc.thePlayer.lastTickPosZ, mc.thePlayer.posZ, partialTicks);
        // Mouse input and RotationManager already update yaw every rendered frame.
        // Interpolating it again with the tick fraction creates a sawtooth delay at tick boundaries.
        // Only positions need partial-tick interpolation; use the current heading in all camera modes.
        double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
        double rotation = orientation.getValue() == 0 ? yaw : Math.PI;
        double radius = radarRadius.getValue();
        double plotRadius = getPlotRadius(radius);
        HUD hud = (HUD) Myau.moduleManager.modules.get(HUD.class);
        int accent = hud == null ? 0xFF72BFFF : hud.getColor(System.currentTimeMillis()).getRGB();

        List<Contact> contacts = new ArrayList<>();
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (!shouldRender(player)) continue;
            double dx = interpolate(player.lastTickPosX, player.posX, partialTicks) - selfX;
            double dz = interpolate(player.lastTickPosZ, player.posZ, partialTicks) - selfZ;
            RadarProjection.Point point = RadarProjection.project(dx, dz, rotation, range.getValue(), plotRadius);
            if (point.distance > 512.0 || (point.outside && !showOutside.getValue())) continue;
            double height = interpolate(player.lastTickPosY, player.posY, partialTicks) - selfY;
            contacts.add(new Contact(player, point, height, withAlpha(getEntityColor(player, hud), 255)));
        }
        // Near contacts draw last, so distant dots cannot cover them.
        contacts.sort(Comparator.comparingDouble((Contact contact) -> contact.point.distance).reversed());
        Contact nearest = null;
        int nearby = 0;
        for (Contact contact : contacts) {
            if (!contact.point.outside) {
                nearby++;
                nearest = contact;
            }
        }

        String summary = range.getValue() + "m  |  " + nearby + " players";
        String nearestText = nearest == null ? "No nearby players" : "Near " + Math.round(nearest.point.distance) + "m";
        ScaledResolution sr = new ScaledResolution(mc);
        float uiScale = Math.max(0.5F, Math.min(3.0F, scale.getValue()));
        // Translate in screen coordinates before scaling to keep the anchor fixed.
        RadarProjection.Anchor anchor = RadarProjection.anchor(position.getValue(), offsetX.getValue(),
                offsetY.getValue(), sr.getScaledWidth(), sr.getScaledHeight());

        boolean lineSmooth = GL11.glIsEnabled(GL11.GL_LINE_SMOOTH);
        float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean framebuffers = OpenGlHelper.isFramebufferEnabled();
        int framebuffer = framebuffers ? RenderUtil.getBoundFramebuffer() : 0;
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        savedViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport);
        // Establish our own overlay projection after the HUD has finished compositing.
        // Do not clear the depth/color buffers or draw into a launcher's cached HUD texture.
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        try {
            if (framebuffers) mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0, 1000.0, 3000.0);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0, 0.0, -2000.0);
            GlStateManager.translate(anchor.x, anchor.y, 0.0);
            GlStateManager.scale(uiScale, uiScale, 1.0F);
            GlStateManager.disableLighting();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            RenderUtil.enableRenderState();
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            drawBackground(plotRadius, rotation);
            double selfAngle = orientation.getValue() == 0 ? -Math.PI / 2.0 : yaw + Math.PI / 2.0;
            drawViewCone(plotRadius, selfAngle, accent);
            for (Contact contact : contacts) drawContact(contact, contact == nearest);
            drawArrow(0, 0, selfAngle, 4.0, Color.WHITE.getRGB());
            if (showCompass.getValue()) drawCompass(plotRadius + 7.0, rotation);

            // Only label the closest few contacts, keeping labels inside the circle.
            if (showNames.getValue() || showDistance.getValue()) {
                List<double[]> labels = new ArrayList<>();
                for (int i = contacts.size() - 1; i >= 0 && labels.size() < 4; i--) {
                    Contact contact = contacts.get(i);
                    if (contact.point.outside) continue;
                    String label = showNames.getValue() ? contact.player.getName() : "";
                    if (showDistance.getValue()) label += (label.isEmpty() ? "" : " ") + Math.round(contact.point.distance) + "m";
                    drawContactLabel(contact.point, label, radius, contact.color, labels);
                }
            }
            if (showPVP.getValue()) {
                RadarProjection.Point origin = RadarProjection.project(-selfX, -selfZ, rotation, range.getValue(), plotRadius);
                drawArrow(origin.x, origin.y, Math.atan2(origin.y, origin.x), 3.5, 0xFFFFD166);
                drawContactLabel(origin, "PVP", radius, 0xFFFFD166, new ArrayList<>());
            }
            if (showInfo.getValue()) {
                drawText(summary, 0, radius + 13.0, 0xFFCAD5E2, true);
                drawText(nearestText, 0, radius + 23.0, nearest == null ? 0xFF8C9AAB : nearest.color, true);
            }
        } finally {
            GL11.glLineWidth(lineWidth);
            if (!lineSmooth) GL11.glDisable(GL11.GL_LINE_SMOOTH);
            RenderUtil.disableRenderState();
            if (lighting) GlStateManager.enableLighting();
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GlStateManager.resetColor();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(matrixMode);
            if (framebuffers) RenderUtil.bindFramebuffer(framebuffer);
            GlStateManager.viewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
        }
    }

    private void drawBackground(double plotRadius, double rotation) {
        RenderUtil.fillCircle(0, 0, plotRadius, 96, withAlpha(fillColor.getValue(), opacity.getValue()));
        geometry();
        // Use the same range radius as contacts: quarters are equally spaced.
        // Keep the fill and outline on this radius too, without an outer shaded band.
        for (int quarter = 1; quarter < 4; quarter++) {
            drawRing(0, 0, plotRadius * quarter / 4.0, withAlpha(crossColor.getValue(), 65));
        }
        drawRing(0, 0, plotRadius, withAlpha(outlineColor.getValue(), 220));
        RadarProjection.Point north = RadarProjection.project(0, -1, rotation, 1, plotRadius);
        RadarProjection.Point east = RadarProjection.project(1, 0, rotation, 1, plotRadius);
        RenderUtil.setColor(withAlpha(crossColor.getValue(), 45));
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(-north.x, -north.y); GL11.glVertex2d(north.x, north.y);
        GL11.glVertex2d(-east.x, -east.y); GL11.glVertex2d(east.x, east.y);
        GL11.glEnd();
    }

    private void drawViewCone(double radius, double angle, int color) {
        geometry();
        RenderUtil.setColor(withAlpha(color, 20));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2d(0, 0);
        double halfFov = Math.toRadians(Math.max(30.0, Math.min(110.0, mc.gameSettings.fovSetting))) / 2.0;
        for (int i = 0; i <= 24; i++) {
            double direction = angle - halfFov + halfFov * 2 * i / 24.0;
            GL11.glVertex2d(Math.cos(direction) * radius, Math.sin(direction) * radius);
        }
        GL11.glEnd();
    }

    private void drawContact(Contact contact, boolean nearest) {
        RadarProjection.Point p = contact.point;
        double size = getDotSize(radarRadius.getValue());
        int color = withAlpha(contact.color, p.outside ? 150 : 255);
        geometry();
        if (p.outside) {
            drawArrow(p.x, p.y, Math.atan2(p.y, p.x), size + 1.0, color);
        } else {
            RenderUtil.fillCircle(p.x, p.y, size + 1.0, 16, 0xCC000000);
            RenderUtil.fillCircle(p.x, p.y, size, 16, color);
            if (nearest) drawRing(p.x, p.y, size + 2.0, withAlpha(color, 170));
        }
        if (showHeight.getValue() && Math.abs(contact.height) >= 3.0) {
            double direction = contact.height > 0 ? -1.0 : 1.0;
            double y = p.y + direction * (size + 4.0);
            geometry();
            RenderUtil.setColor(color);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex2d(p.x - 2.0, y - direction * 2.0);
            GL11.glVertex2d(p.x, y);
            GL11.glVertex2d(p.x + 2.0, y - direction * 2.0);
            GL11.glEnd();
        }
    }

    private void drawCompass(double radius, double rotation) {
        String[] directions = {"N", "E", "S", "W"};
        double[] dx = {0, 1, 0, -1}, dz = {-1, 0, 1, 0};
        for (int i = 0; i < directions.length; i++) {
            RadarProjection.Point p = RadarProjection.project(dx[i], dz[i], rotation, 1.0, radius);
            drawText(directions[i], p.x, p.y - mc.fontRendererObj.FONT_HEIGHT / 2.0,
                    withAlpha(crossColor.getValue(), 220), true);
        }
    }

    private void drawContactLabel(RadarProjection.Point point, String text, double radius, int color, List<double[]> occupied) {
        double fontScale = 0.75;
        double width = mc.fontRendererObj.getStringWidth(text) * fontScale;
        double height = mc.fontRendererObj.FONT_HEIGHT * fontScale;
        double y = point.y + 7.0;
        if (y + height >= radius - 3.0) y = point.y - height - 7.0;
        double farY = Math.max(Math.abs(y), Math.abs(y + height));
        double limit = radius - 3.0;
        if (farY >= limit) return;
        double halfWidth = Math.sqrt(limit * limit - farY * farY);
        if (width > halfWidth * 2.0) return;
        double x = Math.max(-halfWidth, Math.min(halfWidth - width, point.x - width / 2.0));
        if (x < 6 && x + width > -6 && y < 6 && y + height > -6) return;
        for (double[] box : occupied) {
            if (x < box[2] + 2 && x + width + 2 > box[0] && y < box[3] + 2 && y + height + 2 > box[1]) return;
        }
        occupied.add(new double[]{x, y, x + width, y + height});
        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(fontScale, fontScale, 1.0);
            drawText(text, x / fontScale, y / fontScale, color, false);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawText(String text, double x, double y, int color, boolean centered) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.color(1, 1, 1, 1);
        mc.fontRendererObj.drawString(text, (float) (centered ? x - mc.fontRendererObj.getStringWidth(text) / 2.0 : x),
                (float) y, color, true);
        geometry();
    }

    private static void geometry() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private static void drawRing(double x, double y, double radius, int color) {
        geometry();
        RenderUtil.setColor(color);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 96; i++) {
            double angle = i * Math.PI * 2.0 / 96.0;
            GL11.glVertex2d(x + Math.cos(angle) * radius, y + Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private static void drawArrow(double x, double y, double angle, double size, int color) {
        geometry();
        RenderUtil.setColor(color);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2d(x + cos * size, y + sin * size);
        GL11.glVertex2d(x - cos * size * 0.65 - sin * size * 0.7, y - sin * size * 0.65 + cos * size * 0.7);
        GL11.glVertex2d(x - cos * size * 0.65 + sin * size * 0.7, y - sin * size * 0.65 - cos * size * 0.7);
        GL11.glEnd();
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private double getDotSize(double radius) {
        return Math.max(0.5, Math.min(Math.min(5.0, dotRadius.getValue()), radius / 4.0));
    }

    private double getPlotRadius(double radius) {
        // Leave room for the nearest ring and the height chevrons, even at minimum size.
        return radius - getDotSize(radius) - 5.0;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    private static final class Contact {
        final EntityPlayer player;
        final RadarProjection.Point point;
        final double height;
        final int color;

        Contact(EntityPlayer player, RadarProjection.Point point, double height, int color) {
            this.player = player;
            this.point = point;
            this.height = height;
            this.color = color;
        }
    }
}
