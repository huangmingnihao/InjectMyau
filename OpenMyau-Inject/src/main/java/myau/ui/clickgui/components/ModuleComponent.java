package myau.ui.clickgui.components;

import myau.Myau;
import myau.module.Module;
import myau.property.Property;
import myau.module.modules.Theme;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.KeyProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.TextProperty;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.Timer;
import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ModuleComponent extends Component {
    private static final float ROW_HEIGHT = 16.0F;
    private static final float SETTINGS_START = ROW_HEIGHT;
    private static final long OPEN_DURATION = 200L;
    private static final float HOVER_DURATION = 75.0F;
    private static final int HOVER_ALPHA = 120;
    private static final int HOVER_COLOR = new Color(0, 0, 0, HOVER_ALPHA).getRGB();
    private static final int ENABLED_COLOR = new Color(24, 154, 255).getRGB();
    private static final int DISABLED_COLOR = new Color(192, 192, 192).getRGB();
    public final Module module;
    public final CategoryComponent category;

    private final List<Component> settings = new ArrayList<Component>();
    private float offset;
    private boolean opened;
    private Timer openTimer;
    private float animatedSettingsHeight;
    private boolean hovering;
    private boolean hoverStarted;
    private Timer hoverTimer;
    private boolean searchVisible = true;
    public ModuleComponent(Module module, CategoryComponent category, float offset) {
        this.module = module;
        this.category = category;
        this.offset = offset;
        this.buildSettings();
    }

    private void buildSettings() {
        List<Property<?>> properties = Myau.propertyManager.properties.get(this.module.getClass());
        float y = SETTINGS_START;
        if (properties != null) {
            for (Property<?> property : properties) {
                Component row = componentFor(property, y);
                if (row == null) {
                    continue;
                }
                this.settings.add(row);
                y += row.getHeightF();
            }
        }
        this.settings.add(new BindComponent(this.module, this, y));
    }
    private Component componentFor(Property<?> property, float y) {
        if (property instanceof BooleanProperty) {
            return new ButtonComponent((BooleanProperty) property, this, y);
        }
        if (this.module instanceof Theme && property == ((Theme) this.module).theme) {
            return new ThemeGridComponent((ModeProperty) property, this, y);
        }
        if (property instanceof KeyProperty) {
            return new KeyComponent((KeyProperty) property, this, y);
        }
        if (ColorComponent.handles(property)) {
            return new ColorComponent((ColorProperty) property, this, y);
        }
        if (SliderComponent.handles(property)) {
            return new SliderComponent(property, this, y);
        }
        if (property instanceof TextProperty) {
            return new TextComponent((TextProperty) property, this, y);
        }
        return null;
    }
    public List<Component> getSettings() {
        return this.settings;
    }
    public boolean isOpened() {
        return this.opened;
    }

    public void setSearchVisible(boolean visible) {
        this.searchVisible = visible;
        if (!visible) {
            this.hovering = false;
        }
    }

    public boolean isSearchVisible() {
        return this.searchVisible;
    }
    public void restoreOpenState(boolean opened) {
        this.opened = opened;
        this.openTimer = null;
        this.animatedSettingsHeight = opened ? this.settingsHeight() : 0.0F;
    }
    public boolean isVisible(Component component) {
        return this.opened && component.isBaseVisible();
    }
    @Override
    public void render() {
        if (!this.searchVisible) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        float x = this.category.getX();
        float y = this.category.getY() + this.offset;
        if (this.hovering || this.hoverTimer != null) {
            float alpha = this.hoverAlpha();
            if (alpha <= 0.0F) {
                this.hoverTimer = null;
            } else {
                GuiRender.drawRoundedRect(x, y, x + this.category.getWidth(), y + ROW_HEIGHT, 8.0F,
                        HOVER_COLOR & 0xFFFFFF | (int) alpha << 24);
            }
        }
        String name = this.module.getName();
        mc.fontRendererObj.drawStringWithShadow(name,
                x + this.category.getWidth() / 2.0F - mc.fontRendererObj.getStringWidth(name) / 2.0F,
                y + 4.0F,
                this.module.isEnabled() ? ENABLED_COLOR : DISABLED_COLOR);
        if (!this.opened && this.openTimer == null) {
            return;
        }

        boolean clipping = this.openTimer != null;
        if (clipping) {
            GuiRender.scissorPush(x - 2.0, this.category.getModuleY() + this.offset,
                    this.category.getWidth() + 4.0, this.getHeightF());
        }
        for (Component setting : this.settings) {
            if (setting.isBaseVisible()) {
                setting.render();
            }
        }
        if (clipping) {
            GuiRender.scissorPop();
        }
    }
    private float hoverAlpha() {
        if (this.hoverTimer == null) {
            return this.hovering ? HOVER_ALPHA : 0.0F;
        }
        return this.hovering
                ? this.hoverTimer.getValueFloat(0.0F, HOVER_ALPHA, Timer.CUBIC_IN_OUT)
                : HOVER_ALPHA - this.hoverTimer.getValueFloat(0.0F, HOVER_ALPHA, Timer.CUBIC_IN_OUT);
    }
    @Override
    public void drawScreen(int mouseX, int mouseY) {
        if (!this.searchVisible) {
            return;
        }
        for (Component setting : this.settings) {
            if (setting.isBaseVisible()) {
                setting.drawScreen(mouseX, mouseY);
            }
        }
        if (this.overName(mouseX, mouseY) && this.category.isOpened()) {
            if (!this.hovering && this.hoverTimer == null) {
                (this.hoverTimer = new Timer(HOVER_DURATION)).start();
                this.hoverStarted = true;
            }
            this.hovering = true;
        } else {
            if (this.hovering && this.hoverStarted) {
                (this.hoverTimer = new Timer(HOVER_DURATION)).start();
            }
            this.hoverStarted = false;
            this.hovering = false;
        }
    }

    public void updateAnimationState() {
        float target = this.opened ? this.settingsHeight() : 0.0F;
        if (this.openTimer == null) {
            this.animatedSettingsHeight = target;
            return;
        }
        this.animatedSettingsHeight = this.openTimer.getValueFloat(
                this.opened ? 0.0F : this.settingsHeight(), target, Timer.CUBIC_IN_OUT);
        if (this.animatedSettingsHeight == target) {
            this.openTimer = null;
        }
    }
    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!this.searchVisible) {
            return false;
        }
        if (this.overName(mouseX, mouseY)) {
            if (button == 0) {
                this.module.toggle();
                return true;
            }
            if (button == 1) {
                this.opened = !this.opened;
                (this.openTimer = new Timer(OPEN_DURATION)).start();
                return true;
            }
            return false;
        }
        if (!this.opened) {
            return false;
        }
        for (Component setting : this.settings) {
            if (setting.isBaseVisible() && setting.onClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        for (Component setting : this.settings) {
            setting.mouseReleased(mouseX, mouseY, button);
        }
    }
    @Override
    public void keyTyped(char typed, int key) {
        for (Component setting : this.settings) {
            setting.keyTyped(typed, key);
        }
    }
    @Override
    public void onScroll(int scroll) {
        for (Component setting : this.settings) {
            setting.onScroll(scroll);
        }
    }
    @Override
    public void onGuiClosed() {
        this.hovering = false;
        this.hoverTimer = null;
        for (Component setting : this.settings) {
            setting.onGuiClosed();
        }
    }
    public boolean isBinding() {
        for (Component setting : this.settings) {
            if (setting instanceof BindComponent && ((BindComponent) setting).isBinding()) {
                return true;
            }
            if (setting instanceof KeyComponent && ((KeyComponent) setting).isBinding()) {
                return true;
            }
        }
        return false;
    }

    private boolean overName(int mouseX, int mouseY) {
        float x = this.category.getX();
        float y = this.category.getModuleY() + this.offset;
        return mouseX > x && mouseX < x + this.category.getWidth()
                && mouseY > y && mouseY < y + ROW_HEIGHT;
    }

    @Override
    public void updateHeight(float offset) {
        this.offset = offset;
        float y = offset + SETTINGS_START;
        for (Component setting : this.settings) {
            if (!setting.isBaseVisible()) {
                continue;
            }
            setting.updateHeight(y);
            y += setting.getHeightF();
        }
    }
    @Override
    public float getOffset() {
        return this.offset;
    }
    @Override
    public float getHeightF() {
        if (!this.searchVisible) {
            return 0.0F;
        }
        return ROW_HEIGHT + this.animatedSettingsHeight;
    }
    public float getScrollExtentHeightF() {
        if (!this.searchVisible) {
            return 0.0F;
        }
        return ROW_HEIGHT + (this.opened ? this.settingsHeight() : 0.0F);
    }
    private float settingsHeight() {
        float height = 0.0F;
        for (Component setting : this.settings) {
            if (setting.isBaseVisible()) {
                height += setting.getHeightF();
            }
        }
        return height;
    }
}
