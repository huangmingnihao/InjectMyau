package myau.property;

import com.google.gson.JsonObject;
import myau.property.properties.ColorProperty;

import java.awt.Color;

/** Config persistence checks without a running Minecraft client. */
public final class ColorPropertyTest {
    public static void main(String[] args) {
        int[] colors = {Color.GRAY.getRGB(), 0xFF5C6F85, 0xFF91A0B4,
                Color.BLACK.getRGB(), Color.WHITE.getRGB(), 0x123456, 0xFF8080};
        for (int color : colors) {
            ColorProperty property = new ColorProperty("fill-color", color);
            for (int i = 0; i < 20; i++) {
                JsonObject config = new JsonObject();
                property.write(config);
                String expected = String.format("%06X", color & 0xFFFFFF);
                check(expected.equals(config.get("fill-color").getAsString()), "write RGB only");
                ColorProperty restored = new ColorProperty("fill-color", 0);
                check(restored.read(config), "read saved color");
                check(restored.getValue() == (color & 0xFFFFFF), "preserve RGB across reloads");
                property = restored;
            }
        }
        ColorProperty legacy = new ColorProperty("fill-color", 0);
        JsonObject config = new JsonObject();
        config.addProperty("fill-color", "FF808080");
        check(legacy.read(config) && legacy.getValue() == 0x808080, "legacy ARGB gray");
        config.addProperty("fill-color", "#FF5C6F85");
        check(legacy.read(config) && legacy.getValue() == 0x5C6F85, "legacy ARGB outline");
        config.addProperty("fill-color", "FF8080");
        check(legacy.read(config) && legacy.getValue() == 0xFF8080, "preserve explicit RGB red");
        config.addProperty("fill-color", "1234567");
        check(!legacy.read(config) && legacy.getValue() == 0xFF8080, "reject ambiguous lengths");
        check("&c80&a80&980".equals(new ColorProperty("fill-color", Color.GRAY.getRGB()).formatValue()),
                "display gray RGB without alpha");
        System.out.println("Color property config regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
