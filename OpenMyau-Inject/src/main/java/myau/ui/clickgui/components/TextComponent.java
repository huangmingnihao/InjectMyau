package myau.ui.clickgui.components;

import myau.property.properties.TextProperty;
import myau.ui.callback.GuiInput;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class TextComponent extends Component {
    public static final float HEIGHT = 11.0F;
    private static final int TEXT_COLOR = new Color(150, 170, 220).getRGB();
    private final TextProperty property;
    private final ModuleComponent parent;
    private float offset;
    private float x;
    private float y;

    public TextComponent(TextProperty property, ModuleComponent parent, float offset) {
        this.property = property;
        this.parent = parent;
        this.offset = offset;
        this.x = parent.category.getX();
        this.y = parent.category.getY() + offset;
    }

    @Override
    public boolean isBaseVisible() {
        return this.property.isVisible();
    }

    @Override
    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        String value = this.property.getValue();
        if (value.length() > 24) {
            value = value.substring(0, 24) + "...";
        }
        String text = this.property.getName() + ": '§e" + value + "§r'";
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                (this.parent.category.getX() + 4) * 2,
                (this.parent.category.getY() + this.offset + 3) * 2,
                TEXT_COLOR);
        GL11.glPopMatrix();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = this.parent.category.getModuleY() + this.offset;
        this.x = this.parent.category.getX();
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!this.parent.isOpened() || !this.contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 0) {
            GuiInput.prompt(this.property.getName(), this.property.getValue(),
                    this.property::setValue, Minecraft.getMinecraft().currentScreen);
            return true;
        }
        return false;
    }

    private boolean contains(int mouseX, int mouseY) {
        return mouseX > this.x && mouseX < this.x + this.parent.category.getWidth()
                && mouseY > this.y && mouseY < this.y + HEIGHT;
    }

    @Override
    public void updateHeight(float offset) {
        this.offset = offset;
    }

    @Override
    public float getOffset() {
        return this.offset;
    }

    @Override
    public float getHeightF() {
        return HEIGHT;
    }
}
