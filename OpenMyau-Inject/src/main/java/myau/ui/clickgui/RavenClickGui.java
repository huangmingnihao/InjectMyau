package myau.ui.clickgui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.ui.clickgui.components.CategoryComponent;
import myau.ui.clickgui.components.ModuleComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 */
public class RavenClickGui extends GuiScreen implements ClickGuiScreen {
    private static RavenClickGui instance;
    private final List<CategoryComponent> categories = new ArrayList<CategoryComponent>();
    private final File file = new File("./config/Myau/", "clickgui.txt");
    public RavenClickGui() {
        instance = this;
        Categories.verifyComplete();
        float y = 5.0F;
        for (Category category : Category.values()) {
            CategoryComponent panel = new CategoryComponent(category);
            panel.setY(y, false);
            this.categories.add(panel);
            y += 20.0F;
        }
        this.load();
    }

    public static RavenClickGui getInstance() {
        return instance;
    }

    @Override
    public String getStyleName() {
        return "Raven";
    }

    @Override
    public void initGui() {
        super.initGui();
        for (CategoryComponent panel : this.categories) {
            panel.setScreenSize(this.width, this.height);
            panel.limitPositions();
        }
    }

    private List<CategoryComponent> inRenderOrder() {
        List<CategoryComponent> order = new ArrayList<CategoryComponent>(this.categories);
        order.sort(Comparator.comparingLong(panel -> panel.lastInteractedTime));
        return order;
    }
    private CategoryComponent topmostUnder(List<CategoryComponent> order, int mouseX, int mouseY) {
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i).overRect(mouseX, mouseY)) {
                return order.get(i);
            }
        }
        return null;
    }
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiRender.setRenderScale(1.0);
        drawRect(0, 0, this.width, this.height, new Color(0, 0, 0, 100).getRGB());
        this.mc.fontRendererObj.drawStringWithShadow("Myau Inject " + Myau.version,
                4, this.height - 3 - this.mc.fontRendererObj.FONT_HEIGHT,
                new Color(60, 162, 253).getRGB());
        List<CategoryComponent> order = this.inRenderOrder();
        CategoryComponent topmost = this.topmostUnder(order, mouseX, mouseY);
        for (CategoryComponent panel : order) {
            panel.render();
            panel.mousePosition(mouseX, mouseY, panel == topmost);
            panel.drawScreen(mouseX, mouseY);
        }
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            for (CategoryComponent panel : this.categories) {
                panel.onScroll(wheel);
            }
        }
        GuiRender.resetScissors();
    }
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        List<CategoryComponent> order = this.inRenderOrder();
        CategoryComponent target = this.topmostUnder(order, mouseX, mouseY);
        if (target == null) {
            return;
        }
        target.markInteracted();

        if (target.overTitle(mouseX, mouseY)) {
            if (button == 0) {
                target.setDragging(true, mouseX, mouseY);
            } else if (button == 1) {
                target.setOpened(!target.isOpened());
            }
            return;
        }
        if (!target.isOpened()) {
            return;
        }
        for (ModuleComponent module : target.getModules()) {
            if (module.onClick(mouseX, mouseY, button)) {
                return;
            }
        }
    }
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        for (CategoryComponent panel : this.categories) {
            panel.setDragging(false, mouseX, mouseY);
            for (ModuleComponent module : panel.getModules()) {
                module.mouseReleased(mouseX, mouseY, button);
            }
        }
    }
    @Override
    protected void keyTyped(char typed, int key) {
        for (CategoryComponent panel : this.categories) {
            for (ModuleComponent module : panel.getModules()) {
                if (module.isBinding()) {
                    module.keyTyped(typed, key);
                    return;
                }
            }
        }
        for (CategoryComponent panel : this.categories) {
            for (ModuleComponent module : panel.getModules()) {
                module.keyTyped(typed, key);
            }
        }
        if (key == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }
    @Override
    public void onGuiClosed() {
        for (CategoryComponent panel : this.categories) {
            panel.onGuiClosed();
        }
        this.save();
    }
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (CategoryComponent panel : this.categories) {
                JsonObject entry = new JsonObject();
                entry.addProperty("x", panel.getX());
                entry.addProperty("y", panel.getY());
                entry.addProperty("opened", panel.isOpened());
                root.add(panel.category.getLabel(), entry);
            }
            this.file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(this.file);
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            writer.close();
        } catch (Throwable ignored) {

        }
    }
    private void load() {
        if (!this.file.exists()) {
            return;
        }
        try {
            FileReader reader = new FileReader(this.file);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();
            ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
            for (CategoryComponent panel : this.categories) {
                panel.setScreenSize(resolution.getScaledWidth(), resolution.getScaledHeight());
                if (!root.has(panel.category.getLabel())) {
                    continue;
                }
                JsonObject entry = root.getAsJsonObject(panel.category.getLabel());
                panel.applySavedState(entry.get("x").getAsFloat(),
                        entry.get("y").getAsFloat(),
                        entry.has("opened") && entry.get("opened").getAsBoolean());
            }
        } catch (Throwable ignored) {
        }
    }
}
