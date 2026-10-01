package myau.util;

import net.minecraft.util.EnumChatFormatting;

import java.awt.Color;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public enum Themes {
    AUBERGINE("Aubergine", new Color(170, 7, 107), new Color(97, 4, 95), EnumChatFormatting.DARK_PURPLE),
    AQUA("Aqua", new Color(185, 250, 255), new Color(79, 199, 200), EnumChatFormatting.AQUA),
    BANANA("Banana", new Color(253, 236, 177), new Color(255, 255, 255), EnumChatFormatting.YELLOW),
    BLEND("Blend", new Color(71, 148, 253), new Color(71, 253, 160), EnumChatFormatting.AQUA),
    BLOSSOM("Blossom", new Color(226, 208, 249), new Color(49, 119, 115), EnumChatFormatting.DARK_AQUA),
    BUBBLEGUM("Bubblegum", new Color(243, 145, 216), new Color(152, 165, 243), EnumChatFormatting.LIGHT_PURPLE),
    CANDY_CANE("Candy Cane", new Color(255, 0, 0), new Color(255, 255, 255), EnumChatFormatting.RED),
    CHERRY("Cherry", new Color(187, 55, 125), new Color(251, 211, 233), EnumChatFormatting.RED),
    CHRISTMAS("Christmas", new Color(255, 64, 64), new Color(255, 255, 255), new Color(64, 255, 64), EnumChatFormatting.RED),
    CORAL("Coral", new Color(244, 168, 150), new Color(52, 133, 151), EnumChatFormatting.DARK_AQUA),
    DIGITAL_HORIZON("Digital Horizon", new Color(95, 195, 228), new Color(229, 93, 135), EnumChatFormatting.AQUA),
    EXPRESS("Express", new Color(173, 83, 137), new Color(60, 16, 83), EnumChatFormatting.DARK_PURPLE),
    LIME_WATER("Lime Water", new Color(18, 255, 247), new Color(179, 255, 171), EnumChatFormatting.AQUA),
    LUSH("Lush", new Color(168, 224, 99), new Color(86, 171, 47), EnumChatFormatting.GREEN),
    HALOGEN("Halogen", new Color(255, 65, 108), new Color(255, 75, 43), EnumChatFormatting.RED),
    HYPER("Hyper", new Color(236, 110, 173), new Color(52, 148, 230), EnumChatFormatting.LIGHT_PURPLE),
    MAGIC("Magic", new Color(74, 0, 224), new Color(142, 45, 226), EnumChatFormatting.BLUE),
    MAY("May", new Color(238, 79, 238), new Color(253, 219, 245), EnumChatFormatting.LIGHT_PURPLE),
    ORANGE_JUICE("Orange Juice", new Color(252, 74, 26), new Color(247, 183, 51), EnumChatFormatting.GOLD),
    PASTEL("Pastel", new Color(243, 155, 178), new Color(207, 196, 243), EnumChatFormatting.LIGHT_PURPLE),
    PUMPKIN("Pumpkin", new Color(241, 166, 98), new Color(255, 216, 169), new Color(227, 139, 42), EnumChatFormatting.GOLD),
    SATIN("Satin", new Color(215, 60, 67), new Color(140, 23, 39), EnumChatFormatting.RED),
    SNOWY_SKY("Snowy Sky", new Color(1, 171, 179), new Color(234, 234, 234), new Color(18, 232, 232), EnumChatFormatting.AQUA),
    STEEL_FADE("Steel Fade", new Color(66, 134, 244), new Color(55, 59, 68), EnumChatFormatting.BLUE),
    SUNDAE("Sundae", new Color(206, 74, 126), new Color(122, 44, 77), EnumChatFormatting.RED),
    SUNKIST("Sunkist", new Color(242, 201, 76), new Color(242, 153, 74), EnumChatFormatting.YELLOW),
    WATER("Water", new Color(12, 232, 199), new Color(12, 163, 232), EnumChatFormatting.AQUA),
    LEGACY("Legacy", new Color(7393023), new Color(7393023), EnumChatFormatting.AQUA),
    WINTER("Winter", Color.WHITE, Color.WHITE, EnumChatFormatting.GRAY),
    PEONY("Peony", new Color(226, 208, 249), new Color(207, 171, 255), EnumChatFormatting.DARK_AQUA),
    SHADOW("Shadow", new Color(97, 131, 255), new Color(206, 212, 255), EnumChatFormatting.AQUA),
    WOOD("Wood", new Color(79, 109, 81), new Color(170, 139, 87), new Color(240, 235, 206), EnumChatFormatting.DARK_GREEN),
    CREIDA("Creida", new Color(-11644304).brighter().brighter(), new Color(-11644304).darker(), EnumChatFormatting.RESET),
    CREIDA_TWO("Creida Two", new Color(-6632725), new Color(-8406042).darker(), EnumChatFormatting.RESET),
    GOTHIC("Gothic", new Color(31, 30, 30), new Color(196, 190, 190), EnumChatFormatting.RESET),
    RUE("Rue", new Color(234, 118, 176), new Color(31, 30, 30), EnumChatFormatting.DARK_PURPLE),
    PURPLE("Purple", new Color(5391249), new Color(5391249).brighter(), EnumChatFormatting.RESET),
    RAINBOW("Rainbow", (x, y) -> rainbow((int) ((x + y) * 10.0)), EnumChatFormatting.RED),
    NORD("Nord", new Color(143, 188, 187), new Color(163, 190, 140), new Color(236, 239, 244), EnumChatFormatting.AQUA),
    OPAL("Opal", new Color(45, 191, 254), new Color(36, 153, 203), EnumChatFormatting.AQUA),
    SPEARMINT("Spearmint", new Color(97, 194, 162), new Color(65, 130, 108), EnumChatFormatting.GREEN),
    JADE_GREEN("Jade Green", new Color(0, 168, 107), new Color(0, 105, 66), EnumChatFormatting.DARK_GREEN),
    GREEN_SPIRIT("Green Spirit", new Color(159, 226, 191), new Color(0, 135, 62), EnumChatFormatting.GREEN),
    ROSY_PINK("Rosy Pink", new Color(255, 102, 204), new Color(191, 77, 153), EnumChatFormatting.LIGHT_PURPLE),
    MAGENTA("Magenta", new Color(213, 63, 119), new Color(157, 68, 110), EnumChatFormatting.LIGHT_PURPLE),
    HOT_PINK("Hot Pink", new Color(231, 84, 128), new Color(172, 79, 198), EnumChatFormatting.LIGHT_PURPLE),
    LAVENDER("Lavender", new Color(219, 166, 247), new Color(152, 115, 172), EnumChatFormatting.LIGHT_PURPLE),
    AMETHYST("Amethyst", new Color(144, 99, 205), new Color(98, 67, 140), EnumChatFormatting.DARK_PURPLE),
    PURPLE_FIRE("Purple Fire", new Color(177, 162, 202), new Color(104, 71, 141), EnumChatFormatting.DARK_PURPLE),
    SUNSET_PINK("Sunset Pink", new Color(255, 145, 20), new Color(245, 105, 231), EnumChatFormatting.GOLD),
    BLAZE_ORANGE("Blaze Orange", new Color(255, 169, 77), new Color(255, 130, 0), EnumChatFormatting.GOLD),
    PINK_BLOOD("Pink Blood", new Color(255, 166, 201), new Color(228, 0, 70), EnumChatFormatting.RED),
    PASTEL_RED("Pastel Red", new Color(255, 109, 106), new Color(191, 82, 80), EnumChatFormatting.RED),
    NEON_RED("Neon Red", new Color(210, 39, 48), new Color(184, 25, 42), EnumChatFormatting.RED),
    RED_COFFEE("Red Coffee", new Color(225, 34, 59), new Color(75, 19, 19), EnumChatFormatting.DARK_RED),
    DEEP_OCEAN("Deep Ocean", new Color(60, 82, 145), new Color(0, 20, 64), EnumChatFormatting.DARK_BLUE),
    CHAMBRAY_BLUE("Chambray Blue", new Color(60, 82, 145), new Color(33, 46, 182), EnumChatFormatting.BLUE),
    MINT_BLUE("Mint Blue", new Color(66, 158, 157), new Color(40, 94, 93), EnumChatFormatting.DARK_AQUA),
    PACIFIC_BLUE("Pacific Blue", new Color(5, 169, 199), new Color(4, 115, 135), EnumChatFormatting.AQUA),
    TROPICAL_ICE("Tropical Ice", new Color(102, 255, 209), new Color(6, 149, 255), EnumChatFormatting.AQUA),
    BLUE_PURPLE("Blue Purple", new Color(104, 77, 178), new Color(4, 60, 174), EnumChatFormatting.BLUE),
    RAINBOW_GRADIENT("Rainbow Gradient", () -> opalRainbow(1), () -> opalRainbow(40), EnumChatFormatting.LIGHT_PURPLE),
    RAVEN_CHROMA("Raven Chroma", () -> ravenChroma(0), () -> ravenChroma(40), EnumChatFormatting.LIGHT_PURPLE),
    RAVEN_CHERRY("Raven Cherry", new Color(255, 200, 200), new Color(243, 58, 106), EnumChatFormatting.RED),
    COTTON_CANDY("Cotton Candy", new Color(99, 249, 255), new Color(255, 104, 204), EnumChatFormatting.AQUA),
    FLARE("Flare", new Color(231, 39, 24), new Color(245, 173, 49), EnumChatFormatting.RED),
    FLOWER("Flower", new Color(215, 166, 231), new Color(211, 90, 232), EnumChatFormatting.LIGHT_PURPLE),
    GOLD("Gold", new Color(255, 215, 0), new Color(240, 159, 0), EnumChatFormatting.GOLD),
    GRAYSCALE("Grayscale", new Color(240, 240, 240), new Color(110, 110, 110), EnumChatFormatting.GRAY),
    ROYAL("Royal", new Color(125, 204, 241), new Color(30, 71, 170), EnumChatFormatting.BLUE),
    SKY("Sky", new Color(160, 230, 225), new Color(15, 190, 220), EnumChatFormatting.AQUA),
    VINE("Vine", new Color(17, 192, 45), new Color(201, 234, 198), EnumChatFormatting.GREEN);
    public enum ThemeGroup {
        RISE("Rise"),
        OPAL("Opal"),
        RAVEN("Raven");

        private final String label;
        ThemeGroup(String label) {
            this.label = label;
        }
        public String getLabel() {
            return this.label;
        }
    }
    static {
        for (Themes theme : values()) {
            theme.group = theme.ordinal() >= RAVEN_CHROMA.ordinal() ? ThemeGroup.RAVEN
                    : theme.ordinal() >= OPAL.ordinal() ? ThemeGroup.OPAL
                    : ThemeGroup.RISE;
        }
    }
    private ThemeGroup group;
    private final String themeName;
    private final EnumChatFormatting chatAccentColor;
    private Color first;
    private Color second;
    private Color third;
    private Supplier<Color> firstSupplier;
    private Supplier<Color> secondSupplier;
    private BiFunction<Double, Double, Color> function;
    private final boolean triColor;

    Themes(String themeName, Color first, Color second, EnumChatFormatting chatAccentColor) {
        this.themeName = themeName;
        this.first = first;
        this.second = second;
        this.chatAccentColor = chatAccentColor;
        this.triColor = false;
    }

    Themes(String themeName, Supplier<Color> first, Supplier<Color> second, EnumChatFormatting chatAccentColor) {
        this.themeName = themeName;
        this.firstSupplier = first;
        this.secondSupplier = second;
        this.chatAccentColor = chatAccentColor;
        this.triColor = false;
    }
    Themes(String themeName, Color first, Color second, Color third, EnumChatFormatting chatAccentColor) {
        this.themeName = themeName;
        this.first = first;
        this.second = second;
        this.third = third;
        this.chatAccentColor = chatAccentColor;
        this.triColor = true;
    }
    Themes(String themeName, BiFunction<Double, Double, Color> function, EnumChatFormatting chatAccentColor) {
        this.themeName = themeName;
        this.function = function;
        this.chatAccentColor = chatAccentColor;
        this.triColor = true;
    }
    public String getThemeName() {
        return this.themeName;
    }
    public ThemeGroup getGroup() {
        return this.group;
    }
    public EnumChatFormatting getChatAccentColor() {
        return this.chatAccentColor;
    }
    public Color getPrimary() {
        if (this.function != null) {
            return this.getAccentColor(0.0, 0.0);
        }
        return this.firstSupplier != null ? this.firstSupplier.get() : this.first;
    }
    public Color getSecondary() {
        if (this.function != null) {
            return this.getAccentColor(0.0, 50.0);
        }
        return this.secondSupplier != null ? this.secondSupplier.get() : this.second;
    }
    public Color getTertiary() {
        if (this.function != null) {
            return this.getAccentColor(0.0, 100.0);
        }
        return this.triColor ? this.third : this.getPrimary();
    }
    public Color getAccentColor(double x, double y) {
        if (this.function != null) {
            return this.function.apply(x, y);
        }
        double blend = getBlendFactor(x, y);
        if (this.triColor) {

            return blend <= 0.5
                    ? blendColors(this.getSecondary(), this.getPrimary(), blend * 2.0)
                    : blendColors(this.getTertiary(), this.getSecondary(), (blend - 0.5) * 2.0);
        }
        return blendColors(this.getPrimary(), this.getSecondary(), blend);
    }

    public static double getBlendFactor(double x, double y) {
        return Math.sin(System.currentTimeMillis() / 600.0 + x * 0.005 + y * 0.06) * 0.5 + 0.5;
    }
    public static Color blendColors(Color a, Color b, double factor) {
        double inverse = 1.0 - factor;
        return new Color(
                (int) (a.getRed() * factor + b.getRed() * inverse),
                (int) (a.getGreen() * factor + b.getGreen() * inverse),
                (int) (a.getBlue() * factor + b.getBlue() * inverse)
        );
    }
    private static Color opalRainbow(int index) {
        int angle = (int) ((System.currentTimeMillis() / 20L + index) % 360L);
        return Color.getHSBColor(angle / 360.0F, 1.0F, 1.0F);
    }
    private static Color ravenChroma(long offset) {
        long period = 7500L;
        float hue = (float) ((System.currentTimeMillis() + offset * 40L) % period) / (float) period;
        return Color.getHSBColor(hue, 1.0F, 1.0F);
    }
    private static Color rainbow(int offset) {
        return Color.getHSBColor((float) (Math.ceil((System.currentTimeMillis() + offset) / 10.0) % 360.0 / 360.0), 0.6F, 1.0F);
    }
    public static String[] names() {
        Themes[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].themeName;
        }
        return names;
    }
}
