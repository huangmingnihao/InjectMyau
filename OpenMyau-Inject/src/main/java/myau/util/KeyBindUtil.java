package myau.util;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class KeyBindUtil {
    /**
     */
    public static final int MAX_MOUSE_BUTTONS = 32;

    private static int safeButtonCount() {
        int count;
        try {
            count = Mouse.getButtonCount();
        } catch (Throwable ignored) {
            count = 0;
        }
        if (count < 0) {
            count = 0;
        }
        if (count > MAX_MOUSE_BUTTONS) {
            count = MAX_MOUSE_BUTTONS;
        }
        return count;
    }

    public static boolean isMouseButtonValid(int button) {
        return button >= 0 && button < MAX_MOUSE_BUTTONS;
    }

    public static boolean isKeyboardCodeValid(int keyCode) {
        return keyCode >= 0 && keyCode < Keyboard.KEYBOARD_SIZE;
    }

    public static String getKeyName(int keyCode) {
        if (keyCode < 0) {
            int mouseButton = keyCode + 100;
            switch (mouseButton) {
                case 0:
                    return "LMB";
                case 1:
                    return "RMB";
                case 2:
                    return "MMB";
                case 3:
                    return "MOUSE3";
                case 4:
                    return "MOUSE4";
                case 5:
                    return "MOUSE5";
                case 6:
                    return "MOUSE6";
                case 7:
                    return "MOUSE7";
                default:
                    if (!isMouseButtonValid(mouseButton)) {
                        return "MOUSE" + mouseButton;
                    }
                    try {
                        String buttonName = Mouse.getButtonName(mouseButton);
                        return buttonName != null ? buttonName : "MOUSE" + mouseButton;
                    } catch (Throwable ignored) {
                        return "MOUSE" + mouseButton;
                    }
            }
        }
        try {
            return Keyboard.getKeyName(keyCode);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isKeyDown(int keyCode) {
        if (keyCode < 0) {
            return isMouseButtonDown(keyCode + 100);
        }
        if (!isKeyboardCodeValid(keyCode)) {
            return false;
        }
        try {
            return Keyboard.isKeyDown(keyCode);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isMouseButtonDown(int button) {
        if (!isMouseButtonValid(button)) {
            return false;
        }
        try {
            return Mouse.isButtonDown(button);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void updateKeyState(int keyCode) {
        boolean down;
        if (keyCode < 0) {
            down = isMouseButtonDown(keyCode + 100);
        } else {
            if (!isKeyboardCodeValid(keyCode)) {
                return;
            }
            try {
                down = Keyboard.isKeyDown(keyCode);
            } catch (Throwable ignored) {
                return;
            }
        }
        KeyBindUtil.setKeyBindState(keyCode, down);
    }

    private static final ThreadLocal<Boolean> SYNTHETIC = new ThreadLocal<Boolean>();
    public static boolean isSynthetic() {
        return Boolean.TRUE.equals(SYNTHETIC.get());
    }

    public static void setKeyBindState(int keyCode, boolean pressed) {
        SYNTHETIC.set(Boolean.TRUE);
        try {
            KeyBinding.setKeyBindState(keyCode, pressed);
        } finally {
            SYNTHETIC.remove();
        }
    }
    public static void pressKeyOnce(int keyCode) {
        KeyBinding.onTick(keyCode);
    }

    /**
     * Environment-proof "press a key to bind" listener.
     *
     * Badlion / Lunar patch the GUI keyboard path and never forward some keys
     * (modifier keys such as RSHIFT / LCTRL / LALT, and other character-less
     * keys) to {@code GuiScreen.keyTyped}, so they cannot be captured there.
     * The raw LWJGL key state is still maintained in every environment, so we
     * poll it directly each rendered frame while listening.
     */
    public static final class BindListener {
        private boolean[] heldKeys = new boolean[0];
        private boolean[] heldButtons = new boolean[0];
        private int buttonCount;

        /** Remembers everything currently held, so the click that starts
         *  listening is not captured as the new bind. */
        public void start() {
            this.heldKeys = new boolean[Keyboard.KEYBOARD_SIZE];
            for (int key = 0; key < this.heldKeys.length; key++) {
                try {
                    this.heldKeys[key] = Keyboard.isKeyDown(key);
                } catch (Throwable ignored) {
                    this.heldKeys[key] = false;
                }
            }
            this.buttonCount = safeButtonCount();
            try {
                this.heldButtons = new boolean[Math.max(this.buttonCount, 1)];
            } catch (Throwable ignored) {
                this.buttonCount = 0;
                this.heldButtons = new boolean[1];
            }
            for (int button = 0; button < this.buttonCount; button++) {
                try {
                    this.heldButtons[button] = Mouse.isButtonDown(button);
                } catch (Throwable ignored) {
                    this.heldButtons[button] = false;
                }
            }
        }

        /**
         * Polls the current input state once.
         *
         * @return the new bind code (LWJGL key code, or button - 100 for mouse),
         *         0 when escape clears the bind, or -1 when nothing new is pressed
         */
        public int poll() {
            if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) && !wasKeyDown(Keyboard.KEY_ESCAPE)) {
                return 0;
            }
            for (int key = 1; key < this.heldKeys.length; key++) {
                if (key == Keyboard.KEY_ESCAPE) {
                    continue;
                }
                try {
                    if (Keyboard.isKeyDown(key) && !wasKeyDown(key)) {
                        return key;
                    }
                } catch (Throwable ignored) {
                }
            }
            int buttons = Math.min(this.heldButtons.length, MAX_MOUSE_BUTTONS);
            for (int button = 0; button < buttons; button++) {
                try {
                    if (Mouse.isButtonDown(button) && !wasButtonDown(button)) {
                        return button - 100;
                    }
                } catch (Throwable ignored) {
                }
            }
            return -1;
        }

        private boolean wasKeyDown(int key) {
            return key >= 0 && key < this.heldKeys.length && this.heldKeys[key];
        }

        private boolean wasButtonDown(int button) {
            return button >= 0 && button < this.heldButtons.length && this.heldButtons[button];
        }
    }
}
