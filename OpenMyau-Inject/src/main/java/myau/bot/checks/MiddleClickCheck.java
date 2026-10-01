package myau.bot.checks;

import myau.bot.BotCheck;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class MiddleClickCheck extends BotCheck {
    private boolean held;
    public MiddleClickCheck() {
        super("middle-click");
    }
    @Override
    public void update() {

        boolean down = Mouse.isButtonDown(2)
                || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                && mc.gameSettings.keyBindAttack.isKeyDown();
        if (!down) {
            this.held = false;
            return;
        }
        if (this.held) {
            return;
        }
        this.held = true;
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectType.ENTITY
                || mc.objectMouseOver.entityHit == null) {
            return;
        }
        Entity entity = mc.objectMouseOver.entityHit;
        this.set(entity, !this.isMarked(entity));
    }
    @Override
    public void onDisabled() {
        this.held = false;
        super.onDisabled();
    }
}
