package myau.ui.clickgui.components;

import myau.property.properties.BooleanProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class ButtonComponent extends Component {
    private static final int ENABLED_COLOR = new Color(20, 255, 0).getRGB();
    public static final float HEIGHT = 11.0F;
    private final BooleanProperty property;
    private final ModuleComponent module;
    private float offset;
    private float x;
    private float y;
    public ButtonComponent(BooleanProperty property, ModuleComponent module, float offset) {
        this.property = property;
        this.module = module;
        this.offset = offset;
        this.x = module.category.getX();
        this.y = module.category.getY() + offset;
    }
    @Override
    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glPushMatrix();

        GL11.glScaled(0.5, 0.5, 0.5);
        String text = (this.property.getValue() ? "[+]  " : "[-]  ") + this.property.getName();
        mc.fontRendererObj.drawStringWithShadow(text,
                (this.module.category.getX() + 4) * 2,
                (this.module.category.getY() + this.offset + 3) * 2,
                this.property.getValue() ? ENABLED_COLOR : -1);
        GL11.glPopMatrix();
    }
    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = this.module.category.getModuleY() + this.offset;
        this.x = this.module.category.getX();
    }
    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (button == 0 && this.contains(mouseX, mouseY)
                && this.module.isOpened() && this.module.isVisible(this)) {
            this.property.setValue(!this.property.getValue());
        }
        return false;
    }
    private boolean contains(int mouseX, int mouseY) {
        return mouseX > this.x && mouseX < this.x + this.module.category.getWidth()
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

    @Override
    public boolean isBaseVisible() {
        return this.property.isVisible();
    }
}
