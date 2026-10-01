package myau.ui.clickgui.components;

import myau.module.Module;
import myau.ui.clickgui.Categories;
import myau.ui.clickgui.Category;
import myau.ui.clickgui.GuiRender;
import myau.ui.clickgui.Timer;
import myau.ui.clickgui.animation.ScrollOffsetAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryComponent {
    private static long interactionSequence;
    private static final int BACKGROUND = new Color(0, 0, 0, 110).getRGB();
    private static final int OUTLINE_LEFT = new Color(81, 99, 149).getRGB();
    private static final int OUTLINE_RIGHT = new Color(97, 67, 133).getRGB();
    private static final int NAME_COLOR = new Color(220, 220, 220).getRGB();
    private static final float WIDTH = 92.0F;
    private static final float TITLE_HEIGHT = 13.0F;
    private static final float OPEN_DURATION = 250.0F;

    private static final float MAX_SCREEN_FRACTION = 0.9F;
    private static final float SCROLL_SPEED = 20.0F;

    public final Category category;
    private final List<ModuleComponent> modules = new ArrayList<ModuleComponent>();
    private final ScrollOffsetAnimation scroll = new ScrollOffsetAnimation(200L);
    private float x = 5.0F;
    private float y = 5.0F;
    private float moduleY = 5.0F;
    private boolean opened;
    private boolean dragging;
    private float grabX;
    private float grabY;
    private boolean hoveringTitle;
    private boolean hoveringPanel;
    private float screenWidth;
    private float screenHeight;

    private Timer openTimer;
    private Timer nameTimer;
    private float visibleModulesHeight;
    private float lastBottom;
    private float lastNameX;
    private float animationStartBottom;
    private float animationStartNameX;
    public long lastInteractedTime;
    public CategoryComponent(Category category) {
        this.category = category;
        this.scroll.reset(this.y);
        this.lastBottom = this.y + TITLE_HEIGHT + 4.0F;
        this.animationStartBottom = this.lastBottom;
        this.reloadModules();
    }
    public void reloadModules() {
        Map<String, Boolean> openStates = new HashMap<String, Boolean>();
        for (ModuleComponent component : this.modules) {
            openStates.put(component.module.getName(), component.isOpened());
        }
        this.modules.clear();
        float y = TITLE_HEIGHT + 3.0F;
        for (Module module : Categories.modulesOf(this.category)) {
            ModuleComponent component = new ModuleComponent(module, this, y);
            component.restoreOpenState(Boolean.TRUE.equals(openStates.get(module.getName())));
            this.modules.add(component);
            y += component.getHeightF();
        }
    }
    public List<ModuleComponent> getModules() {
        return this.modules;
    }
    public float getX() {
        return this.x;
    }
    public float getY() {
        return this.y;
    }
    public float getWidth() {
        return WIDTH;
    }

    public float getModuleY() {
        return this.moduleY;
    }
    public boolean isOpened() {
        return this.opened;
    }
    public void setScreenSize(float width, float height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
    public void setX(float x, boolean clamp) {
        if (clamp) {
            x = Math.max(2.0F, Math.min(x, this.screenWidth - WIDTH - 4.0F));
        }
        this.x = x;
    }

    public void setY(float y, boolean clamp) {
        if (clamp) {
            y = Math.max(1.0F, Math.min(y, this.screenHeight - TITLE_HEIGHT - 5.0F));
        }
        float scrollOffset = this.scroll.getTarget() - this.y;
        this.y = y;
        this.moduleY = y + scrollOffset;
        this.scroll.reset(this.moduleY);
    }
    public void limitPositions() {
        this.setX(this.x, true);
        this.setY(this.y, true);
    }
    public void markInteracted() {
        this.lastInteractedTime = ++interactionSequence;
    }
    public void setOpened(boolean opened) {
        this.animationStartBottom = this.lastBottom;
        this.animationStartNameX = this.lastNameX;
        this.opened = opened;
        (this.openTimer = new Timer(OPEN_DURATION)).start();
        (this.nameTimer = new Timer(OPEN_DURATION)).start();
    }

    public void setDragging(boolean dragging, int mouseX, int mouseY) {
        this.dragging = dragging;
        this.grabX = mouseX - this.x;
        this.grabY = mouseY - this.y;
    }
    public boolean overTitle(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX <= this.x + WIDTH
                && mouseY >= this.y + 2.0F && mouseY <= this.y + TITLE_HEIGHT + 1.0F;
    }
    public boolean overPanel(int mouseX, int mouseY) {
        return mouseX >= this.x - 2.0F && mouseX <= this.x + WIDTH + 2.0F
                && mouseY >= this.y + 2.0F
                && mouseY <= this.y + TITLE_HEIGHT + this.visibleModulesHeight + 1.0F;
    }
    public boolean overRect(int mouseX, int mouseY) {
        return mouseX >= this.x - 2.0F && mouseX <= this.x + WIDTH + 2.0F
                && mouseY >= this.y && mouseY <= this.lastBottom;
    }

    public void mousePosition(int mouseX, int mouseY, boolean topmost) {
        if (this.dragging) {
            this.setX(Math.max(2.0F, Math.min(mouseX - this.grabX, this.screenWidth - WIDTH - 4.0F)), false);
            this.setY(Math.max(1.0F, Math.min(mouseY - this.grabY, this.screenHeight - TITLE_HEIGHT - 5.0F)), false);
        }
        this.hoveringPanel = topmost && this.overPanel(mouseX, mouseY);
        this.hoveringTitle = topmost && this.overTitle(mouseX, mouseY);
    }

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        for (ModuleComponent component : this.modules) {
            component.updateAnimationState();
        }
        Layout layout = this.layout(this.opened || this.openTimer != null);
        this.visibleModulesHeight = this.opened || this.openTimer != null ? layout.visibleHeight : 0.0F;
        this.scroll.clampTarget(layout.minScrollY, this.y);
        this.moduleY = Math.max(layout.minScrollY, Math.min(this.y, this.scroll.getValue()));
        float bottom = layout.contentBottom;
        if (this.openTimer != null) {
            float target = this.opened ? layout.contentBottom : this.y + TITLE_HEIGHT + 4.0F;
            bottom = this.openTimer.getValueFloat(this.animationStartBottom, target, Timer.CUBIC_IN_OUT);
            if (bottom == target) {
                this.openTimer = null;
            }
        }
        this.lastBottom = bottom;
        String label = this.category.getLabel();
        float centred = this.x + WIDTH / 2.0F - mc.fontRendererObj.getStringWidth(label) / 2.0F;
        float targetNameX = this.opened ? centred : this.x + 12.0F;
        float nameX = targetNameX;
        if (this.nameTimer != null) {
            nameX = this.nameTimer.getValueFloat(this.animationStartNameX, targetNameX, Timer.CUBIC_IN_OUT);
            if (nameX == targetNameX) {
                this.nameTimer = null;
            }
        }
        this.lastNameX = nameX;
        GL11.glPushMatrix();
        GuiRender.drawRoundedGradientOutlinedRect(this.x - 2.0F, this.y, this.x + WIDTH + 2.0F, bottom,
                10.0F, BACKGROUND, OUTLINE_LEFT, OUTLINE_RIGHT);
        this.renderIcon((int) (this.x + 1.0F), (int) (this.y + 4.0F), this.opened || this.hoveringTitle);
        mc.fontRendererObj.drawStringWithShadow(label, nameX, this.y + 4.0F, NAME_COLOR);
        if (this.opened || this.openTimer != null) {
            float top = this.y + TITLE_HEIGHT + 3.0F;
            float height = Math.max(0.0F, bottom - 2.0F - top);
            GuiRender.scissorPush(0.0, top, this.x + WIDTH + 4.0F, height);
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, this.moduleY - this.y, 0.0F);
            for (ModuleComponent component : this.modules) {
                component.render();
            }
            GL11.glPopMatrix();
            GuiRender.scissorPop();
        }
        GL11.glPopMatrix();
    }
    private void renderIcon(int x, int y, boolean active) {
        ItemStack stack = this.category.getIcon(active);
        if (stack == null) {
            return;
        }
        final double scale = 0.55;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableBlend();
        GlStateManager.translate(x / scale, y / scale, 0.0);
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
        GlStateManager.enableBlend();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }
    public void drawScreen(int mouseX, int mouseY) {
        for (ModuleComponent component : this.modules) {
            component.drawScreen(mouseX, mouseY);
        }
    }
    public void onScroll(int wheel) {
        for (ModuleComponent component : this.modules) {
            component.onScroll(wheel);
        }
        if (!this.hoveringPanel || !this.opened) {
            return;
        }
        this.markInteracted();
        float delta = SCROLL_SPEED * (wheel / 120.0F);
        if (delta != 0.0F) {
            this.scroll.extend(delta);
        }
        this.scroll.clampTarget(this.layout(false).minScrollY, this.y);
    }
    private static final class Layout {
        final float visibleHeight;
        final float minScrollY;
        final float contentBottom;
        Layout(float visibleHeight, float minScrollY, float contentBottom) {
            this.visibleHeight = visibleHeight;
            this.minScrollY = minScrollY;
            this.contentBottom = contentBottom;
        }
    }
    private Layout layout(boolean placeModules) {
        if (this.modules.isEmpty() || !this.opened && this.openTimer == null) {
            return new Layout(0.0F, this.y, this.y + TITLE_HEIGHT + 4.0F);
        }
        float allowance = this.screenHeight * MAX_SCREEN_FRACTION - TITLE_HEIGHT - 4.0F;
        float visible = 0.0F;
        float extent = 0.0F;
        float offset = TITLE_HEIGHT + 3.0F;

        for (ModuleComponent component : this.modules) {
            if (placeModules) {
                component.updateHeight(offset);
            }
            float height = component.getHeightF();
            offset += height;
            extent += component.getScrollExtentHeightF();
            if (visible < allowance) {
                visible += Math.min(height, allowance - visible);
            }
        }
        float viewport = Math.min(allowance, extent);
        float overflow = Math.max(0.0F, extent - viewport);
        float bottomLimit = this.y + this.screenHeight * MAX_SCREEN_FRACTION;
        return new Layout(Math.max(0.0F, visible),
                overflow > 0.0F ? this.y - overflow : this.y,
                Math.min(this.y + TITLE_HEIGHT + visible + 4.0F, bottomLimit));
    }
    public void applySavedState(float x, float y, boolean opened) {
        this.setX(x, true);
        this.setY(y, true);
        this.opened = opened;
        this.openTimer = null;
        this.nameTimer = null;
        this.moduleY = this.y;
        this.scroll.reset(this.y);
        Layout layout = this.layout(opened);
        this.visibleModulesHeight = opened ? layout.visibleHeight : 0.0F;
        this.lastBottom = opened ? layout.contentBottom : this.y + TITLE_HEIGHT + 4.0F;
        this.animationStartBottom = this.lastBottom;
    }

    public void onGuiClosed() {
        this.dragging = false;
        this.openTimer = null;
        this.nameTimer = null;
        this.moduleY = this.scroll.getTarget();
        this.scroll.reset(this.moduleY);
        for (ModuleComponent component : this.modules) {
            component.onGuiClosed();
        }
    }
}
