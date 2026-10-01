package myau.module.modules;

import org.lwjgl.input.Keyboard;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.TextProperty;
import myau.ui.clickgui.RavenClickGui;
import myau.ui.clickgui.modern.ModernClickGui;
import net.minecraft.client.Minecraft;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty style = new ModeProperty("style", 0, new String[]{"Modern", "Raven"});
    public final ColorProperty background = new ColorProperty("background", 0x101218,
            () -> this.style.getValue() == 0);
    public final PercentProperty backgroundOpacity = new PercentProperty("background-opacity", 55,
            () -> this.style.getValue() == 0);
    public final BooleanProperty backgroundBlur = new BooleanProperty("background-blur", true,
            () -> this.style.getValue() == 0);
    public final TextProperty backgroundImage = new TextProperty("background-image", "",
            () -> this.style.getValue() == 0);
    public final PercentProperty imageOpacity = new PercentProperty("image-opacity", 65,
            () -> this.style.getValue() == 0);
    public final BooleanProperty textShadow = new BooleanProperty("text-shadow", true,
            () -> this.style.getValue() == 0);
    public final ColorProperty shadowColor = new ColorProperty("shadow-color", 0x000000,
            () -> this.style.getValue() == 0);
    public final ColorProperty textColor = new ColorProperty("text-color", 0xFFFFFF,
            () -> this.style.getValue() == 0);
    public final ColorProperty textDimColor = new ColorProperty("text-dim-color", 0x9AA0A6,
            () -> this.style.getValue() == 0);
    public final ColorProperty textValueColor = new ColorProperty("text-value-color", 0x7FA8FF,
            () -> this.style.getValue() == 0);

    private ModernClickGui modernClickGui;
    private RavenClickGui ravenClickGui;

    public GuiModule() {
        super("Click Gui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (this.style.getValue() == 0) {
            if (this.modernClickGui == null) {
                this.modernClickGui = new ModernClickGui();
            }
            mc.displayGuiScreen(this.modernClickGui);
        } else {
            if (this.ravenClickGui == null) {
                this.ravenClickGui = new RavenClickGui();
            }
            mc.displayGuiScreen(this.ravenClickGui);
        }
    }
}
