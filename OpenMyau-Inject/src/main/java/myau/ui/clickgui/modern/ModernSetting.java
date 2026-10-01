package myau.ui.clickgui.modern;

import myau.module.Module;
import myau.property.Property;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.KeyProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
import myau.ui.callback.GuiInput;
import myau.ui.clickgui.GuiRender;
import myau.util.KeyBindUtil;
import myau.util.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;

/**
 */
public class ModernSetting {
    public enum Kind {
        BOOLEAN, SLIDER, MODE, COLOR, KEY, TEXT
    }

    private static final float ROW_HEIGHT = 16.0F;
    private static final float SETTING_RADIUS = 5.0F;
    private static final float SLIDER_HEIGHT = 20.0F;
    private static final float SLIDER_TEXT_HEIGHT = 11.0F;
    private static final float COLOR_CHANNEL_HEIGHT = 10.0F;
    private static final float COLOR_EXTRA = COLOR_CHANNEL_HEIGHT * 3.0F;
    private static final float SWITCH_WIDTH = 18.0F;
    private static final float SWITCH_HEIGHT = 9.0F;
    private static final float PADDING = 6.0F;
    private static final float TRACK_INSET = 6.0F;
    private static final float FOLDER_ZONE = 18.0F;
    private static final String[] CHANNEL_NAMES = {"R", "G", "B"};
    private static final int SWITCH_OFF = 0x40FFFFFF;
    private static final int KNOB_ON = 0xFFFFFFFF;
    private static final int KNOB_OFF = 0xFF9AA0A6;

    private static ModernSetting activeBind;

    private final Kind kind;
    private final Property<?> property;
    private final Module module;
    private final KeyBindUtil.BindListener bindListener = new KeyBindUtil.BindListener();

    private boolean binding;
    private boolean dragging;
    private float dragLeft;
    private float dragWidth;
    private boolean colorExpanded;
    private int colorDrag = -1;

    private ModernSetting(Kind kind, Property<?> property, Module module) {
        this.kind = kind;
        this.property = property;
        this.module = module;
    }

    public static ModernSetting of(Property<?> property) {
        if (property instanceof BooleanProperty) {
            return new ModernSetting(Kind.BOOLEAN, property, null);
        }
        if (property instanceof KeyProperty) {
            return new ModernSetting(Kind.KEY, property, null);
        }
        if (property instanceof ColorProperty) {
            return new ModernSetting(Kind.COLOR, property, null);
        }
        if (property instanceof ModeProperty) {
            return new ModernSetting(Kind.MODE, property, null);
        }
        if (property instanceof IntProperty || property instanceof FloatProperty
                || property instanceof PercentProperty) {
            return new ModernSetting(Kind.SLIDER, property, null);
        }
        if (property instanceof TextProperty) {
            return new ModernSetting(Kind.TEXT, property, null);
        }
        return null;
    }

    public static ModernSetting moduleKey(Module module) {
        return new ModernSetting(Kind.KEY, null, module);
    }

    public float getHeight() {
        if (this.kind == Kind.SLIDER) {
            return SLIDER_HEIGHT;
        }
        if (this.kind == Kind.COLOR && this.colorExpanded) {
            return ROW_HEIGHT + COLOR_EXTRA;
        }
        return ROW_HEIGHT;
    }

    public boolean isVisible() {
        return this.property == null || this.property.isVisible();
    }

    public boolean isBinding() {
        return this.binding;
    }

    public void cancelBinding() {
        if (this.binding) {
            this.binding = false;
        }
        if (activeBind == this) {
            activeBind = null;
        }
    }

    public String getName() {
        return this.property == null ? "bind" : this.property.getName().replace("-", " ");
    }


    public void draw(float x, float y, float width, int mouseX, int mouseY) {
        float height = this.getHeight();
        if (ModernUi.glassCollecting()) {
            ModernUi.glassAdd(x, y, width, height, ModernUi.visual(SETTING_RADIUS));
            return;
        }
        boolean hovered = isHovered(x, y, width, height, mouseX, mouseY);
        int background = hovered ? ModernUi.ROW_HOVER : ModernUi.SETTING_BG;
        if (this.kind == Kind.SLIDER) {
            GuiRender.drawRoundedRect(x, y, x + width, y + height, SETTING_RADIUS, background);
            drawSlider(x, y, width, height);
            return;
        }
        GuiRender.drawRoundedRect(x, y, x + width, y + height, SETTING_RADIUS, background);
        RavenFontRenderer font = ModernUi.regular(9.0F);
        float textY = ModernUi.centerTextY(font, y, height);
        boolean colorExpandedRow = this.kind == Kind.COLOR && this.colorExpanded;
        if (!colorExpandedRow) {
            font.drawString(ModernUi.trim(font, getName(), width * 0.55F), x + PADDING, textY,
                    ModernUi.TEXT_DIM, false);
        }
        switch (this.kind) {
            case BOOLEAN: {
                boolean on = Boolean.TRUE.equals(this.property.getValue());
                drawSwitch(x + width - PADDING - SWITCH_WIDTH, y + (height - SWITCH_HEIGHT) / 2.0F, on);
                break;
            }
            case MODE:
            case KEY: {
                String label = this.valueLabel();
                if (!label.isEmpty()) {
                    font.drawString(label, x + width - PADDING - font.getStringWidth(label), textY,
                            ModernUi.VALUE, false);
                }
                break;
            }
            case TEXT: {
                String label = this.valueLabel();
                float right = x + width - PADDING;
                if (isImageRow()) {
                    ModernUi.drawFolderIcon(right - 6.0F, y + height / 2.0F, 11.0F,
                            mouseX >= x + width - FOLDER_ZONE ? ModernUi.TEXT : ModernUi.TEXT_DIM);
                    right -= FOLDER_ZONE;
                }
                if (!label.isEmpty()) {
                    font.drawString(ModernUi.trim(font, label, right - x - width * 0.45F),
                            right - font.getStringWidth(label), textY, ModernUi.VALUE, false);
                }
                break;
            }
            case COLOR: {
                float swatchSize = 10.0F;
                float swatchX = x + width - PADDING - swatchSize;
                GuiRender.drawRoundedRect(swatchX, y + (ROW_HEIGHT - swatchSize) / 2.0F,
                        swatchX + swatchSize, y + (ROW_HEIGHT + swatchSize) / 2.0F, 3.0F,
                        0xFF000000 | (((ColorProperty) this.property).getValue() & 0xFFFFFF));
                if (!this.colorExpanded) {
                    String label = this.valueLabel();
                    font.drawString(label, swatchX - 4.0F - font.getStringWidth(label), textY,
                            ModernUi.VALUE, false);
                }
                if (this.colorExpanded) {
                    drawColorChannels(x, y + ROW_HEIGHT, width, mouseX, mouseY);
                }
                break;
            }
            default:
                break;
        }
    }

    private boolean isImageRow() {
        return this.kind == Kind.TEXT && this.property != null
                && "background-image".equals(this.property.getName());
    }

    private void drawColorChannels(float x, float y, float width, int mouseX, int mouseY) {
        int rgb = ((ColorProperty) this.property).getValue() & 0xFFFFFF;
        RavenFontRenderer font = ModernUi.regular(8.0F);
        float trackX = colorTrackLeft(x);
        float trackW = colorTrackWidth(width);
        for (int channel = 0; channel < 3; channel++) {
            float rowY = y + channel * COLOR_CHANNEL_HEIGHT;
            float textY = ModernUi.centerTextY(font, rowY, COLOR_CHANNEL_HEIGHT);
            font.drawString(CHANNEL_NAMES[channel], x + PADDING, textY, ModernUi.TEXT_DIM, false);
            int value = (rgb >> ((2 - channel) * 8)) & 0xFF;
            String label = String.valueOf(value);
            font.drawString(label, x + width - PADDING - font.getStringWidth(label), textY,
                    ModernUi.VALUE, false);
            int mask = 0xFF << ((2 - channel) * 8);
            int from = rgb & ~mask;
            int to = from | mask;
            float trackY = rowY + COLOR_CHANNEL_HEIGHT / 2.0F - 1.5F;
            GuiRender.drawHorizontalGradientRect(trackX, trackY, trackX + trackW, trackY + 3.0F,
                    0xFF000000 | from, 0xFF000000 | to);
            float knob = trackX + trackW * (value / 255.0F);
            GuiRender.drawRoundedRect(knob - 2.5F, trackY - 2.0F, knob + 2.5F, trackY + 5.0F,
                    4.0F, KNOB_ON);
        }
    }

    private float colorTrackLeft(float x) {
        return x + PADDING + 12.0F;
    }

    private float colorTrackWidth(float width) {
        return width - PADDING * 2.0F - 12.0F - 24.0F;
    }

    private void drawSlider(float x, float y, float width, float height) {
        RavenFontRenderer font = ModernUi.regular(9.0F);
        float textY = ModernUi.centerTextY(font, y, SLIDER_TEXT_HEIGHT);
        font.drawString(ModernUi.trim(font, getName(), width * 0.55F), x + PADDING, textY,
                ModernUi.TEXT_DIM, false);
        String label = this.sliderLabel();
        font.drawString(label, x + width - PADDING - font.getStringWidth(label), textY,
                ModernUi.VALUE, false);
        float trackX = x + TRACK_INSET;
        float trackWidth = width - TRACK_INSET * 2.0F;
        float trackY = y + height - 7.0F;
        GuiRender.drawRoundedRect(trackX, trackY, trackX + trackWidth, trackY + 3.0F, 3.0F,
                ModernUi.TRACK_BG);
        double span = maximum() - minimum();
        double ratio = span <= 0.0 ? 0.0 : (currentValue() - minimum()) / span;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        float filled = (float) (trackWidth * ratio);
        if (filled > 0.0F) {
            GuiRender.drawRoundedRect(trackX, trackY, trackX + filled, trackY + 3.0F, 3.0F,
                    ModernUi.ACCENT);
        }
        GuiRender.drawRoundedRect(trackX + filled - 3.0F, trackY - 2.0F,
                trackX + filled + 3.0F, trackY + 5.0F, 5.0F, KNOB_ON);
    }

    private void drawSwitch(float x, float y, boolean on) {
        GuiRender.drawRoundedRect(x, y, x + SWITCH_WIDTH, y + SWITCH_HEIGHT, SWITCH_HEIGHT,
                on ? ModernUi.ACCENT : SWITCH_OFF);
        float knob = SWITCH_HEIGHT - 2.0F;
        float knobX = on ? x + SWITCH_WIDTH - knob - 1.0F : x + 1.0F;
        GuiRender.drawRoundedRect(knobX, y + 1.0F, knobX + knob, y + 1.0F + knob, knob,
                on ? KNOB_ON : KNOB_OFF);
    }


    public boolean onClick(float x, float y, float width, int mouseX, int mouseY, int button) {
        float height = this.getHeight();
        if (!isHovered(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        switch (this.kind) {
            case BOOLEAN: {
                BooleanProperty property = (BooleanProperty) this.property;
                property.setValue(!Boolean.TRUE.equals(property.getValue()));
                return true;
            }
            case MODE: {
                if (button == 0) {
                    ((ModeProperty) this.property).nextMode();
                    return true;
                }
                if (button == 1) {
                    ((ModeProperty) this.property).previousMode();
                    return true;
                }
                return false;
            }
            case SLIDER: {
                if (button != 0) {
                    return false;
                }
                if (mouseY < y + SLIDER_TEXT_HEIGHT) {
                    GuiInput.prompt(getName(), this.sliderLabel(), this::applyInput,
                            Minecraft.getMinecraft().currentScreen);
                    return true;
                }
                this.dragging = true;
                this.dragLeft = x + TRACK_INSET;
                this.dragWidth = width - TRACK_INSET * 2.0F;
                applyRatio(mouseX);
                return true;
            }
            case COLOR: {
                if (button == 0) {
                    if (mouseY < y + ROW_HEIGHT) {
                        this.colorExpanded = !this.colorExpanded;
                    } else {
                        this.startColorDrag(x, y, width, mouseX, mouseY);
                    }
                    return true;
                }
                return false;
            }
            case KEY: {
                if (button == 0) {
                    toggleBinding();
                    return true;
                }
                if (button == 1 && this.binding) {
                    if (KeyBindUtil.isMouseButtonValid(button)) {
                        setKey(button + KeyProperty.MOUSE_OFFSET);
                    }
                    return true;
                }
                return false;
            }
            case TEXT: {
                if (button != 0) {
                    return false;
                }
                if (isImageRow()) {
                    if (mouseX >= x + width - FOLDER_ZONE) {
                        openImageFolder();
                        return true;
                    }
                    ModernClickGui.openImagePicker(this.property);
                    return true;
                }
                GuiInput.prompt(getName(), String.valueOf(this.property.getValue()),
                        value -> this.property.setValue(value), Minecraft.getMinecraft().currentScreen);
                return true;
            }
            default:
                return false;
        }
    }

    public void mouseClickMove(int mouseX) {
        if (this.colorDrag >= 0) {
            applyColorDrag(mouseX);
            return;
        }
        if (!this.dragging) {
            return;
        }
        applyRatio(mouseX);
    }

    public void mouseReleased() {
        this.dragging = false;
        this.colorDrag = -1;
    }

    private void startColorDrag(float x, float y, float width, int mouseX, int mouseY) {
        int channel = (int) ((mouseY - y - ROW_HEIGHT) / COLOR_CHANNEL_HEIGHT);
        this.colorDrag = Math.max(0, Math.min(2, channel));
        this.dragLeft = colorTrackLeft(x);
        this.dragWidth = colorTrackWidth(width);
        applyColorDrag(mouseX);
    }

    private void applyColorDrag(int mouseX) {
        if (this.dragWidth <= 0.0F || this.colorDrag < 0) {
            return;
        }
        double ratio = (mouseX - this.dragLeft) / (double) this.dragWidth;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        int value = (int) Math.round(ratio * 255.0);
        int rgb = ((ColorProperty) this.property).getValue() & 0xFFFFFF;
        int shift = (2 - this.colorDrag) * 8;
        this.property.setValue((rgb & ~(0xFF << shift)) | (value << shift));
    }

    private static void openImageFolder() {
        try {
            Runtime.getRuntime().exec(new String[]{"explorer.exe",
                    ModernUi.imageFolder().getAbsolutePath()});
        } catch (Throwable ignored) {
        }
    }

    public void updateScreen() {
        if (!this.binding) {
            return;
        }
        int code = this.bindListener.poll();
        if (code >= 0) {
            setKey(code);
        }
    }

    private void toggleBinding() {
        if (this.binding) {
            this.binding = false;
            if (activeBind == this) {
                activeBind = null;
            }
            return;
        }
        if (activeBind != null) {
            activeBind.cancelBinding();
        }
        this.binding = true;
        this.bindListener.start();
        activeBind = this;
    }

    private void setKey(int key) {
        if (this.property != null) {
            this.property.setValue(key);
        } else if (this.module != null) {
            this.module.setKey(key);
        }
        this.binding = false;
        if (activeBind == this) {
            activeBind = null;
        }
    }

    private void applyRatio(int mouseX) {
        if (this.dragWidth <= 0.0F) {
            return;
        }
        double ratio = (mouseX - this.dragLeft) / (double) this.dragWidth;
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        apply(minimum() + ratio * (maximum() - minimum()));
    }

    private void applyInput(String input) {
        if (input == null) {
            return;
        }
        try {
            apply(Double.parseDouble(input.trim().replace("%", "")));
        } catch (NumberFormatException ignored) {
        }
    }

    private void apply(double rawValue) {
        if (this.property instanceof FloatProperty) {
            FloatProperty property = (FloatProperty) this.property;
            property.setValue(property.snap((float) rawValue));
        } else if (this.property instanceof IntProperty) {
            IntProperty property = (IntProperty) this.property;
            property.setValue(property.snap((int) Math.round(rawValue)));
        } else if (this.property instanceof PercentProperty) {
            PercentProperty property = (PercentProperty) this.property;
            int value = (int) Math.round(rawValue);
            property.setValue(Math.max(property.getMinimum(), Math.min(property.getMaximum(), value)));
        }
    }


    private String valueLabel() {
        switch (this.kind) {
            case MODE:
                return ((ModeProperty) this.property).getModeString();
            case COLOR:
                return String.format("#%06X", ((ColorProperty) this.property).getValue() & 0xFFFFFF);
            case KEY:
                return keyLabel();
            case TEXT:
                return String.valueOf(this.property.getValue());
            default:
                return "";
        }
    }

    private String sliderLabel() {
        double value = currentValue();
        if (this.property instanceof PercentProperty) {
            return ((int) value) + "%";
        }
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    private String keyLabel() {
        if (this.binding) {
            return "Press a key...";
        }
        int key = keyValue();
        if (key == KeyProperty.NONE) {
            return "None";
        }
        String name = KeyBindUtil.getKeyName(key);
        return name == null ? String.valueOf(key) : name;
    }

    private int keyValue() {
        if (this.property != null) {
            return (Integer) this.property.getValue();
        }
        return this.module == null ? KeyProperty.NONE : this.module.getKey();
    }

    private double currentValue() {
        Object value = this.property == null ? null : this.property.getValue();
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private double minimum() {
        if (this.property instanceof FloatProperty) {
            return ((FloatProperty) this.property).getMinimum();
        }
        if (this.property instanceof IntProperty) {
            return ((IntProperty) this.property).getMinimum();
        }
        if (this.property instanceof PercentProperty) {
            return ((PercentProperty) this.property).getMinimum();
        }
        return 0.0;
    }

    private double maximum() {
        if (this.property instanceof FloatProperty) {
            return ((FloatProperty) this.property).getMaximum();
        }
        if (this.property instanceof IntProperty) {
            return ((IntProperty) this.property).getMaximum();
        }
        if (this.property instanceof PercentProperty) {
            return ((PercentProperty) this.property).getMaximum();
        }
        return 1.0;
    }

    private static boolean isHovered(float x, float y, float width, float height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
