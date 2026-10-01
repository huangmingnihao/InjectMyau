package myau.module.modules;

import me.ksyz.accountmanager.gui.GuiAccountManager;
import myau.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public class Accounts extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public Accounts() {
        super("Accounts", false);
        setKey(Keyboard.KEY_F6);
    }

    @Override
    public boolean worksWithGuiOpen() {
        return true;
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        mc.displayGuiScreen(new GuiAccountManager(mc.currentScreen));
    }
}
