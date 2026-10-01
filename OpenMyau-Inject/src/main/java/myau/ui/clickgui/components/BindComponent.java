package myau.ui.clickgui.components;

import java.awt.Color;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import myau.module.Module;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;

public class BindComponent extends Component {
    public static final float HEIGHT = 11.0F;
    private static final int BIND_COLOR = new Color(150, 170, 220).getRGB();
    private final Module module;
    private final ModuleComponent parent;
    private float offset;
    private float x;
    private float y;
    private boolean binding;
    private final KeyBindUtil.BindListener bindListener = new KeyBindUtil.BindListener();
    public BindComponent(Module module, ModuleComponent parent, float offset) {
        this.module = module;
        this.parent = parent;
        this.offset = offset;
        this.x = parent.category.getX();
        this.y = parent.category.getY() + offset;
    }
    @Override
    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        String text = this.binding
                ? "Press a key..."
                : "Bind: '§e" + this.keyName() + "§r'";
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                (this.parent.category.getX() + 4) * 2,
                (this.parent.category.getY() + this.offset + 3) * 2,
                BIND_COLOR);
        GL11.glPopMatrix();
    }
    private String keyName() {
        int key = this.module.getKey();
        return key == 0 ? "None" : KeyBindUtil.getKeyName(key);
    }
    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = this.parent.category.getModuleY() + this.offset;
        this.x = this.parent.category.getX();
        if (this.binding) {
            int code = this.bindListener.poll();
            if (code != -1) {
                this.module.setKey(code);
                this.binding = false;
            }
        }
    }
    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (!this.parent.isOpened() || !this.contains(mouseX, mouseY)) {
            return false;
        }
        if (button == 0) {
            this.binding = !this.binding;
            if (this.binding) {
                this.bindListener.start();
            }
            return true;
        }
        if (this.binding && button > 1 && KeyBindUtil.isMouseButtonValid(button)) {
            this.module.setKey(button - 100);
            this.binding = false;
            return true;
        }
        return false;
    }
    @Override
    public void keyTyped(char typed, int key) {
        if (!this.binding) {
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            this.module.setKey(0);
            this.binding = false;
            return;
        }
        // With an IME active some keys arrive as character-only events (key == KEY_NONE).
        // Derive the real key code from the typed character so bindings like V / Y work.
        if (key == Keyboard.KEY_NONE) {
            key = resolveKeyCode(typed);
            if (key == Keyboard.KEY_NONE) {
                return;
            }
        }
        this.module.setKey(key);
        this.binding = false;
    }

    private static int resolveKeyCode(char typed) {
        if (typed == 0) {
            return Keyboard.KEY_NONE;
        }
        return Keyboard.getKeyIndex(String.valueOf(typed).toUpperCase());
    }

    public boolean isBinding() {
        return this.binding;
    }
    @Override
    public void onGuiClosed() {
        this.binding = false;
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
