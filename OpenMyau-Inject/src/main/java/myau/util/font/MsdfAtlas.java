package myau.util.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MsdfAtlas {
    private static final String RESOURCE_ROOT = "/myau/msdf/";
    private static final char FALLBACK_CHAR = '?';
    private static final float SHADER_DISTANCE_RANGE = 10.0F;
    private static final String ALPHABET = "ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Map<String, MsdfAtlas> LOADED = new ConcurrentHashMap<String, MsdfAtlas>();
    private static final Map<String, Boolean> MISSING = new ConcurrentHashMap<String, Boolean>();
    public static final class Glyph {
        public final float advance;
        public final float planeLeft;
        public final float planeBottom;
        public final float planeRight;
        public final float planeTop;
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;
        public final boolean hasInk;
        Glyph(float advance, float planeLeft, float planeBottom, float planeRight, float planeTop,
              float u0, float v0, float u1, float v1, boolean hasInk) {
            this.advance = advance;
            this.planeLeft = planeLeft;
            this.planeBottom = planeBottom;
            this.planeRight = planeRight;
            this.planeTop = planeTop;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.hasInk = hasInk;
        }
    }
    private static final Glyph BLANK = new Glyph(0.0F, 0, 0, 0, 0, 0, 0, 0, 0, false);
    private final Glyph[] glyphs;
    private final int minChar;
    private final Glyph fallback;
    private final String name;
    public final float lineHeight;
    public final float inkTop;
    public final float inkBottom;
    public final float width;
    public final float height;
    private BufferedImage pending;
    private int textureId = -1;
    private MsdfAtlas(Glyph[] glyphs, int minChar, String name, BufferedImage pending,
                      float lineHeight, float width, float height, float padding) {
        this.glyphs = glyphs;
        this.minChar = minChar;
        this.name = name;
        this.pending = pending;
        this.lineHeight = lineHeight;
        this.width = width;
        this.height = height;
        this.fallback = this.lookup(FALLBACK_CHAR);

        float top = 0.0F;
        float bottom = 0.0F;
        for (int i = 0; i < ALPHABET.length(); i++) {
            Glyph glyph = this.lookup(ALPHABET.charAt(i));
            if (!glyph.hasInk) {
                continue;
            }
            top = Math.max(top, glyph.planeTop);
            bottom = Math.min(bottom, glyph.planeBottom);
        }
        this.inkTop = top - padding;
        this.inkBottom = bottom + padding;
    }

    public static MsdfAtlas get(String name) {
        MsdfAtlas cached = LOADED.get(name);
        if (cached != null) {
            return cached;
        }
        if (MISSING.containsKey(name)) {
            return null;
        }
        MsdfAtlas loaded = load(name);
        if (loaded == null) {
            MISSING.put(name, Boolean.TRUE);
            return null;
        }
        LOADED.put(name, loaded);
        return loaded;
    }
    private static MsdfAtlas load(String name) {
        String base = RESOURCE_ROOT + name;
        JsonObject root;
        try (InputStream in = MsdfAtlas.class.getResourceAsStream(base + ".json")) {
            if (in == null) {
                return null;
            }
            root = new JsonParser()
                    .parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception unreadable) {
            return null;
        }
        BufferedImage image;
        try (InputStream in = MsdfAtlas.class.getResourceAsStream(base + ".png")) {
            if (in == null) {
                return null;
            }
            image = ImageIO.read(in);
        } catch (Exception unreadable) {
            return null;
        }
        if (image == null) {
            return null;
        }
        JsonObject atlas = root.getAsJsonObject("atlas");
        float range = atlas.get("distanceRange").getAsFloat();
        if (Math.abs(range - SHADER_DISTANCE_RANGE) > 1.0E-3F) {
            System.out.println("[Myau] MSDF atlas " + name + " has distanceRange " + range
                    + " but the shader is built for " + SHADER_DISTANCE_RANGE);
            return null;
        }

        float emSize = Math.max(1.0F, atlas.get("size").getAsFloat());
        float atlasWidth = atlas.get("width").getAsFloat();
        float atlasHeight = atlas.get("height").getAsFloat();
        boolean yOriginBottom = !"top".equals(atlas.get("yOrigin").getAsString());
        float lineHeight = root.getAsJsonObject("metrics").get("lineHeight").getAsFloat();
        JsonArray table = root.getAsJsonArray("glyphs");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (JsonElement element : table) {
            int code = element.getAsJsonObject().get("unicode").getAsInt();
            min = Math.min(min, code);
            max = Math.max(max, code);
        }
        if (min > max) {
            return null;
        }
        Glyph[] glyphs = new Glyph[max - min + 1];
        for (JsonElement element : table) {
            JsonObject entry = element.getAsJsonObject();
            int code = entry.get("unicode").getAsInt();
            float advance = entry.get("advance").getAsFloat();
            JsonObject plane = entry.getAsJsonObject("planeBounds");
            JsonObject bounds = entry.getAsJsonObject("atlasBounds");
            if (plane == null || bounds == null) {
                glyphs[code - min] = new Glyph(advance, 0, 0, 0, 0, 0, 0, 0, 0, false);
                continue;
            }
            float left = bounds.get("left").getAsFloat();
            float right = bounds.get("right").getAsFloat();
            float bottom = bounds.get("bottom").getAsFloat();
            float top = bounds.get("top").getAsFloat();
            float v0 = yOriginBottom ? 1.0F - top / atlasHeight : top / atlasHeight;
            float v1 = yOriginBottom ? 1.0F - bottom / atlasHeight : bottom / atlasHeight;
            glyphs[code - min] = new Glyph(advance,
                    plane.get("left").getAsFloat(), plane.get("bottom").getAsFloat(),
                    plane.get("right").getAsFloat(), plane.get("top").getAsFloat(),
                    left / atlasWidth, v0, right / atlasWidth, v1, true);
        }
        return new MsdfAtlas(glyphs, min, name, image, lineHeight, atlasWidth, atlasHeight,
                (range / 2.0F) / emSize);
    }
    public Glyph lookup(char character) {
        int offset = character - this.minChar;
        if (offset < 0 || offset >= this.glyphs.length || this.glyphs[offset] == null) {
            return this.fallback == null ? BLANK : this.fallback;
        }
        return this.glyphs[offset];
    }
    public int texture() {
        if (this.textureId >= 0) {
            return this.textureId;
        }
        this.textureId = 0;
        BufferedImage image = this.pending;
        this.pending = null;
        if (image == null) {
            return 0;
        }
        try {
            this.textureId = upload(image);
        } catch (Throwable noContext) {
            System.out.println("[Myau] MSDF atlas " + this.name + " could not be uploaded");
        }
        return this.textureId;
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
                buffer.put((byte) ((pixel >>> 24) & 0xFF));
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
        return id;
    }
}
