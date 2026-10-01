package myau.ui.clickgui.modern;

import myau.ui.clickgui.GuiRender;
import myau.util.font.Fonts;
import myau.util.font.MinecraftFontAdapter;
import myau.util.font.RavenFontRenderer;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 */
public final class ModernUi {
    public static final int ACCENT = 0xFF3B6FF5;
    public static final int ACCENT_SOFT = 0x553B6FF5;
    public static int TEXT = 0xFFFFFFFF;
    public static int TEXT_DIM = 0xFF9AA0A6;
    public static int VALUE = 0xFF7FA8FF;
    public static final int ROW_BG = 0x14FFFFFF;
    public static final int ROW_HOVER = 0x24FFFFFF;
    public static final int SETTING_BG = 0x10FFFFFF;
    public static final int TRACK_BG = 0x33FFFFFF;

    private static final String BOLD_FONT = "Inter-Medium.ttf";
    private static final String REGULAR_FONT = "Inter-Regular.ttf";
    private static final String UI_FONT = "Inter-Regular.ttf";

    private static final Map<String, RavenFontRenderer> FONTS = new ConcurrentHashMap<String, RavenFontRenderer>();
    private static final Map<String, Integer> TEXTURES = new ConcurrentHashMap<String, Integer>();
    private static final Map<String, Long> TEXTURE_STAMPS = new ConcurrentHashMap<String, Long>();
    private static final Map<String, int[]> IMAGE_SIZES = new ConcurrentHashMap<String, int[]>();

    private ModernUi() {
    }

    public static RavenFontRenderer bold(float size) {
        return font(BOLD_FONT, size);
    }

    public static RavenFontRenderer regular(float size) {
        return font(REGULAR_FONT, size);
    }

    public static RavenFontRenderer ui(float size) {
        return font(UI_FONT, size);
    }

    public static RavenFontRenderer mcFont(float size) {
        return shadow(new MinecraftFontAdapter(Minecraft.getMinecraft().fontRendererObj,
                Math.max(0.5F, Math.min(2.0F, size / 8.0F))));
    }

    public static RavenFontRenderer font(String fileName, float size) {
        String key = fileName + "#" + size;
        RavenFontRenderer cached = FONTS.get(key);
        if (cached != null) {
            return cached;
        }
        RavenFontRenderer built = Fonts.renderer(fileName, size);
        if (built == null) {
            built = Fonts.arraylist(0, Math.max(0.5F, Math.min(2.0F, size / 10.0F)));
        }
        RavenFontRenderer wrapped = shadow(built);
        FONTS.put(key, wrapped);
        return wrapped;
    }


    private static boolean textShadowEnabled = true;
    private static int textShadowColour = 0xFF000000;

    public static void setTextShadow(boolean enabled, int argb) {
        textShadowEnabled = enabled;
        textShadowColour = argb;
    }

    public static void setTextColours(int main, int dim, int value) {
        TEXT = 0xFF000000 | (main & 0xFFFFFF);
        TEXT_DIM = 0xFF000000 | (dim & 0xFFFFFF);
        VALUE = 0xFF000000 | (value & 0xFFFFFF);
    }

    private static RavenFontRenderer shadow(RavenFontRenderer inner) {
        return inner == null ? null : new ShadowRenderer(inner);
    }

    private static final class ShadowRenderer implements RavenFontRenderer {
        private final RavenFontRenderer inner;

        private ShadowRenderer(RavenFontRenderer inner) {
            this.inner = inner;
        }

        @Override
        public int drawString(String text, float x, float y, int color, boolean shadow) {
            if (!shadow && textShadowEnabled) {
                this.inner.drawString(text, x + 1.0F, y + 1.0F, textShadowColour, false);
            }
            return this.inner.drawString(text, x, y, color, shadow);
        }

        @Override
        public int getStringWidth(String text) {
            return this.inner.getStringWidth(text);
        }

        @Override
        public int getFontHeight() {
            return this.inner.getFontHeight();
        }
    }

    public static float centerTextY(RavenFontRenderer font, float top, float height) {
        return top + (height - font.getFontHeight()) / 2.0F + 1.0F;
    }

    /**
     */
    public static float visual(float drawRadius) {
        return drawRadius / 2.0F;
    }


    private static final ArrayList<Rect> GLASS_POOL = new ArrayList<Rect>();
    private static int glassUsed;
    private static boolean collecting;
    private static float clipTop = Float.NEGATIVE_INFINITY;
    private static float clipBottom = Float.POSITIVE_INFINITY;

    public static boolean glassReady() {
        return BlurUtils.isReady() && RoundedShader.isReady();
    }

    public static boolean glassCollecting() {
        return collecting;
    }

    public static void glassBegin() {
        glassUsed = 0;
        collecting = true;
        glassClip(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    public static void glassClip(float top, float bottom) {
        clipTop = top;
        clipBottom = bottom;
    }

    public static void glassAdd(float x, float y, float width, float height, float radius) {
        float top = Math.max(y, clipTop);
        float bottom = Math.min(y + height, clipBottom);
        if (width <= 0.0F || bottom - top <= 0.0F) {
            return;
        }
        while (GLASS_POOL.size() <= glassUsed) {
            GLASS_POOL.add(new Rect());
        }
        GLASS_POOL.get(glassUsed++).set(x, top, width, bottom - top, radius);
    }

    public static void glassFlush(int passes, float blurRadius) {
        collecting = false;
        glassClip(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        if (glassUsed <= 0 || !glassReady()) {
            return;
        }
        BlurUtils.prepareBlur();
        for (int index = 0; index < glassUsed; index++) {
            Rect rect = GLASS_POOL.get(index);
            RoundedShader.drawRound(rect.x, rect.y, rect.width, rect.height, rect.radius, 0xFFFFFFFF);
        }
        BlurUtils.blurEnd(passes, blurRadius);
    }

    private static final class Rect {
        private float x;
        private float y;
        private float width;
        private float height;
        private float radius;

        private void set(float x, float y, float width, float height, float radius) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
        }
    }

    public static int withOpacity(int rgb, int percent) {
        double alpha = Math.max(0.0, Math.min(1.0, percent / 100.0));
        return GuiRender.setAlpha(rgb & 0xFFFFFF, alpha);
    }


    public static File imageFolder() {
        File folder = new File(Minecraft.getMinecraft().mcDataDir, "config/Myau/clickgui/");
        if (!folder.isDirectory()) {
            folder.mkdirs();
        }
        return folder;
    }

    private static File resolve(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(imageFolder(), path);
    }

    public static int texture(String path) {
        if (path == null || path.trim().isEmpty()) {
            return 0;
        }
        File file = resolve(path.trim());
        if (!file.isFile()) {
            return 0;
        }
        String key = file.getAbsolutePath();
        Integer cached = TEXTURES.get(key);
        Long stamp = TEXTURE_STAMPS.get(key);
        long currentStamp = file.lastModified();
        if (cached != null && cached > 0 && stamp != null && stamp == currentStamp) {
            return cached;
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (Throwable unreadable) {
            return cached == null ? 0 : cached;
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return cached == null ? 0 : cached;
        }
        int id = upload(image);
        if (id <= 0) {
            return cached == null ? 0 : cached;
        }
        if (cached != null && cached > 0) {
            GL11.glDeleteTextures(cached);
        }
        TEXTURES.put(key, id);
        TEXTURE_STAMPS.put(key, currentStamp);
        IMAGE_SIZES.put(key, new int[]{image.getWidth(), image.getHeight()});
        return id;
    }

    public static int[] imageDimensions(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = resolve(path.trim());
        if (!file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath();
        int[] size = IMAGE_SIZES.get(key);
        if (size != null) {
            return size;
        }
        if (texture(path) <= 0) {
            return null;
        }
        return IMAGE_SIZES.get(key);
    }

    private static int upload(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[x + y * width];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();
        int id = GL11.glGenTextures();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GlStateManager.bindTexture(0);
        return id;
    }

    private static int logoTexture = -1;
    private static float logoAspect = 3.0F;

    public static int logoTexture() {
        if (logoTexture >= 0) {
            return logoTexture;
        }
        logoTexture = 0;
        try (InputStream in = ModernUi.class.getResourceAsStream("/myau/gui/logo.png")) {
            if (in == null) {
                return 0;
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return 0;
            }
            image = cropTransparent(image);
            if (image == null) {
                return 0;
            }
            logoAspect = (float) image.getWidth() / (float) image.getHeight();
            logoTexture = upload(image);
        } catch (Throwable ignored) {
        }
        return logoTexture;
    }

    public static float logoAspect() {
        logoTexture();
        return logoAspect;
    }

    private static BufferedImage cropTransparent(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((image.getRGB(x, y) >> 24) & 0xFF) > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return null;
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public static void drawTexture(int textureId, float x, float y, float x2, float y2, float alpha) {
        if (textureId <= 0 || x2 <= x || y2 <= y) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.bindTexture(textureId);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        renderer.pos(x, y2, 0.0).tex(0.0F, 1.0F).color(1.0F, 1.0F, 1.0F, clamped).endVertex();
        renderer.pos(x2, y2, 0.0).tex(1.0F, 1.0F).color(1.0F, 1.0F, 1.0F, clamped).endVertex();
        renderer.pos(x2, y, 0.0).tex(1.0F, 0.0F).color(1.0F, 1.0F, 1.0F, clamped).endVertex();
        renderer.pos(x, y, 0.0).tex(0.0F, 0.0F).color(1.0F, 1.0F, 1.0F, clamped).endVertex();
        tessellator.draw();
        GlStateManager.bindTexture(0);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }


    /**
     */
    public static void drawRoundedTextureRegion(int textureId, float x, float y, float x2, float y2,
                                                float radius, float alpha,
                                                float u0, float v0, float u1, float v1) {
        if (textureId <= 0 || x2 <= x || y2 <= y || u1 <= u0 || v1 <= v0) {
            return;
        }
        float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
        if (clamped <= 0.0F) {
            return;
        }
        float width = x2 - x;
        if (width < 3.0F) {
            radius = Math.min(radius, width / 2.0F);
        }
        double dx = x * 2.0F;
        double dy = y * 2.0F;
        double dx2 = x2 * 2.0F;
        double dy2 = y2 * 2.0F;
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GlStateManager.bindTexture(textureId);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, clamped);
        GL11.glBegin(GL11.GL_POLYGON);
        texturedArc(dx + radius, dy + radius, radius, 0, 90, -1.0, -1.0,
                dx, dy, dx2, dy2, u0, v0, u1, v1);
        texturedArc(dx + radius, dy2 - radius, radius, 90, 180, -1.0, -1.0,
                dx, dy, dx2, dy2, u0, v0, u1, v1);
        texturedArc(dx2 - radius, dy2 - radius, radius, 0, 90, 1.0, 1.0,
                dx, dy, dx2, dy2, u0, v0, u1, v1);
        texturedArc(dx2 - radius, dy + radius, radius, 90, 180, 1.0, 1.0,
                dx, dy, dx2, dy2, u0, v0, u1, v1);
        GL11.glEnd();
        GlStateManager.bindTexture(0);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void texturedArc(double centreX, double centreY, double radius,
                                    int fromDegrees, int toDegrees, double signX, double signY,
                                    double dx, double dy, double dx2, double dy2,
                                    double u0, double v0, double u1, double v1) {
        for (int degrees = fromDegrees; degrees <= toDegrees; degrees += 1) {
            double radians = degrees * 0.017453292F;
            double vx = centreX + Math.sin(radians) * radius * signX;
            double vy = centreY + Math.cos(radians) * radius * signY;
            double u = u0 + (vx - dx) / (dx2 - dx) * (u1 - u0);
            double v = v0 + (vy - dy) / (dy2 - dy) * (v1 - v0);
            GL11.glTexCoord2d(u, v);
            GL11.glVertex2d(vx, vy);
        }
    }

    public static String trim(RavenFontRenderer font, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.getStringWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = builder.toString() + text.charAt(i);
            if (font.getStringWidth(next + "...") > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder + "...";
    }


    public static void drawCheck(float centreX, float centreY, int color) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.6F);
        colour(color);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex2f(centreX - 3.0F, centreY);
        GL11.glVertex2f(centreX - 1.0F, centreY + 2.2F);
        GL11.glVertex2f(centreX + 3.2F, centreY - 2.6F);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopAttrib();
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawGridIcon(float centreX, float centreY, float size, int color) {
        float half = size / 2.0F;
        float gap = 1.4F;
        float cell = (size - gap) / 2.0F;
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                float left = centreX - half + column * (cell + gap);
                float top = centreY - half + row * (cell + gap);
                GuiRender.drawRoundedRect(left, top, left + cell, top + cell, 1.5F, color);
            }
        }
    }

    public static void drawUserIcon(float centreX, float centreY, float size, int color) {
        float head = size * 0.42F;
        GuiRender.drawRoundedRect(centreX - head / 2.0F, centreY - size * 0.44F,
                centreX + head / 2.0F, centreY - size * 0.44F + head, head / 2.0F, color);
        float body = size * 0.84F;
        GuiRender.drawRoundedRect(centreX - body / 2.0F, centreY + size * 0.04F,
                centreX + body / 2.0F, centreY + size * 0.44F, 2.0F, color);
    }

    public static void drawSearchIcon(float centreX, float centreY, float size, int color) {
        float radius = size * 0.32F;
        float offset = -size * 0.06F;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.4F);
        colour(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 18; i++) {
            double angle = i * (Math.PI * 2.0 / 18.0);
            GL11.glVertex2d(centreX + offset + Math.cos(angle) * radius,
                    centreY + offset + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(centreX + offset + radius * 0.72, centreY + offset + radius * 0.72);
        GL11.glVertex2d(centreX + offset + radius * 1.7, centreY + offset + radius * 1.7);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopAttrib();
        GL11.glLineWidth(1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawDots(float centreX, float centreY, int color) {
        for (int i = -1; i <= 1; i++) {
            float y = centreY + i * 3.6F;
            GuiRender.drawRoundedRect(centreX - 1.0F, y - 1.0F, centreX + 1.0F, y + 1.0F, 1.0F, color);
        }
    }

    public static void drawFolderIcon(float centreX, float centreY, float size, int color) {
        float width = size;
        float height = size * 0.78F;
        float x = centreX - width / 2.0F;
        float y = centreY - height / 2.0F;
        GuiRender.drawRoundedRect(x, y, x + width * 0.45F, y + height * 0.30F, 1.5F, color);
        GuiRender.drawRoundedRect(x, y + height * 0.20F, x + width, y + height, 2.0F, color);
    }

    private static void colour(int argb) {
        GL11.glColor4f((argb >> 16 & 0xFF) / 255.0F,
                (argb >> 8 & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F,
                (argb >> 24 & 0xFF) / 255.0F);
    }
}
