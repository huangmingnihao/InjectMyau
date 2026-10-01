package myau.ui.clickgui.modern;

import myau.Myau;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.property.Property;
import myau.ui.clickgui.GuiRender;
import myau.util.font.RavenFontRenderer;

import java.util.ArrayList;

/**
 */
public class ModernModuleEntry {
    public static final float ROW_HEIGHT = 26.0F;
    private static final float ROW_RADIUS = 6.0F;
    private static final float ROW_GAP = 2.0F;
    private static final float SETTINGS_GAP = 2.0F;
    private static final float SETTINGS_INDENT = 24.0F;
    private static final float SETTINGS_RIGHT = 8.0F;
    private static final float BOTTOM_PADDING = 4.0F;
    private static final float DOTS_ZONE = 22.0F;

    private final Module module;
    private final ArrayList<Property<?>> properties = new ArrayList<Property<?>>();
    private final ArrayList<ModernSetting> settings = new ArrayList<ModernSetting>();
    private boolean expanded;

    public ModernModuleEntry(Module module) {
        this.module = module;
        ArrayList<Property<?>> registered = Myau.propertyManager.properties.get(module.getClass());
        if (registered != null) {
            for (Property<?> property : registered) {
                this.properties.add(property);
                ModernSetting setting = ModernSetting.of(property);
                if (setting != null) {
                    this.settings.add(setting);
                }
            }
        }
        this.settings.add(ModernSetting.moduleKey(module));
    }

    public Module getModule() {
        return this.module;
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (!expanded) {
            cancelBindings();
        }
    }

    public static float rowGap() {
        return ROW_GAP;
    }

    public float getHeight() {
        float height = ROW_HEIGHT;
        if (this.expanded) {
            height += getSettingsHeight() + BOTTOM_PADDING;
        }
        return height;
    }

    private float getSettingsHeight() {
        float total = 0.0F;
        int count = 0;
        for (ModernSetting setting : this.settings) {
            if (!setting.isVisible()) {
                continue;
            }
            total += setting.getHeight();
            count++;
        }
        if (count > 1) {
            total += SETTINGS_GAP * (count - 1);
        }
        return total;
    }

    public boolean matches(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lower = query.toLowerCase();
        if (this.module.getName().toLowerCase().contains(lower)) {
            return true;
        }
        for (Property<?> property : this.properties) {
            if (property.getName().toLowerCase().contains(lower)) {
                return true;
            }
        }
        return false;
    }


    public void draw(float x, float y, float width, int mouseX, int mouseY) {
        float centreY = y + ROW_HEIGHT / 2.0F;
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        boolean enabled = this.module.isEnabled();
        int background;
        if (enabled) {
            background = ModernUi.withOpacity(ModernUi.ACCENT, hovered ? 26 : 16);
        } else {
            background = hovered ? ModernUi.ROW_HOVER : ModernUi.ROW_BG;
        }
        boolean collect = ModernUi.glassCollecting();
        if (collect) {
            ModernUi.glassAdd(x, y, width, ROW_HEIGHT, ModernUi.visual(ROW_RADIUS));
        } else {
            GuiRender.drawRoundedRect(x, y, x + width, y + ROW_HEIGHT, ROW_RADIUS, background);

            float indicatorX = x + 8.0F;
            float indicatorY = centreY - 7.0F;
            if (enabled) {
                GuiRender.drawRoundedRect(indicatorX, indicatorY, indicatorX + 14.0F,
                        indicatorY + 14.0F, 10.0F, ModernUi.ACCENT);
                ModernUi.drawCheck(indicatorX + 7.0F, centreY, 0xFFFFFFFF);
            } else {
                float dot = 5.0F;
                GuiRender.drawRoundedRect(indicatorX + 4.5F, centreY - dot / 2.0F,
                        indicatorX + 4.5F + dot, centreY + dot / 2.0F, dot, 0x66FFFFFF);
            }

            float textX = x + 27.0F;
            float textWidth = width - 27.0F - 24.0F;
            RavenFontRenderer bold = ModernUi.bold(12.0F);
            bold.drawString(ModernUi.trim(bold, this.module.getName(), textWidth), textX,
                    y + (ROW_HEIGHT - bold.getFontHeight()) / 2.0F,
                    enabled ? ModernUi.TEXT : 0xFFE6E6E6, false);
            ModernUi.drawDots(x + width - 12.0F, centreY, 0xFF9AA0A6);
        }

        if (!this.expanded) {
            return;
        }
        float settingX = x + SETTINGS_INDENT;
        float settingWidth = width - SETTINGS_INDENT - SETTINGS_RIGHT;
        float settingY = y + ROW_HEIGHT + SETTINGS_GAP;
        for (ModernSetting setting : this.settings) {
            if (!setting.isVisible()) {
                continue;
            }
            setting.draw(settingX, settingY, settingWidth, mouseX, mouseY);
            settingY += setting.getHeight() + SETTINGS_GAP;
        }
    }


    public boolean onClick(float x, float y, float width, int mouseX, int mouseY, int button) {
        boolean onRow = mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        if (onRow) {
            boolean onDots = mouseX > x + width - DOTS_ZONE;
            if (button == 1 || onDots) {
                this.expanded = !this.expanded;
                if (!this.expanded) {
                    cancelBindings();
                }
                return true;
            }
            if (button == 0) {
                if (this.module instanceof GuiModule) {
                    this.expanded = !this.expanded;
                } else {
                    this.module.toggle();
                }
                return true;
            }
            return false;
        }
        if (!this.expanded) {
            return false;
        }
        float settingX = x + SETTINGS_INDENT;
        float settingWidth = width - SETTINGS_INDENT - SETTINGS_RIGHT;
        float settingY = y + ROW_HEIGHT + SETTINGS_GAP;
        for (ModernSetting setting : this.settings) {
            if (!setting.isVisible()) {
                continue;
            }
            if (setting.onClick(settingX, settingY, settingWidth, mouseX, mouseY, button)) {
                return true;
            }
            settingY += setting.getHeight() + SETTINGS_GAP;
        }
        return false;
    }

    public void mouseClickMove(int mouseX) {
        for (ModernSetting setting : this.settings) {
            setting.mouseClickMove(mouseX);
        }
    }

    public void mouseReleased() {
        for (ModernSetting setting : this.settings) {
            setting.mouseReleased();
        }
    }

    public void updateScreen() {
        for (ModernSetting setting : this.settings) {
            setting.updateScreen();
        }
    }

    public void cancelBindings() {
        for (ModernSetting setting : this.settings) {
            setting.cancelBinding();
        }
    }

    public boolean isBinding() {
        for (ModernSetting setting : this.settings) {
            if (setting.isBinding()) {
                return true;
            }
        }
        return false;
    }
}
