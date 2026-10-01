package myau.property.properties;

import com.google.gson.JsonObject;
import myau.property.Property;
import myau.util.KeyBindUtil;
import org.lwjgl.input.Keyboard;

import java.util.function.BooleanSupplier;

public class KeyProperty extends Property<Integer> {
    public static final int MOUSE_OFFSET = -100;
    public static final int NONE = 0;
    public KeyProperty(String name, Integer value) {
        this(name, value, null);
    }

    public KeyProperty(String name, Integer value, BooleanSupplier check) {
        super(name, value, key -> true, check);
    }
    public boolean isHeld() {
        return this.isBound() && KeyBindUtil.isKeyDown(this.getValue());
    }
    public boolean isBound() {
        return this.getValue() != NONE;
    }
    public String getKeyName() {
        if (!this.isBound()) {
            return "None";
        }
        String name = KeyBindUtil.getKeyName(this.getValue());
        return name == null ? String.valueOf(this.getValue()) : name;
    }
    @Override
    public String getValuePrompt() {
        return "key name, or none";
    }
    @Override
    public String formatValue() {
        return String.format("&e%s", this.getKeyName());
    }

    @Override
    public boolean parseString(String string) {
        if (string == null) {
            return false;
        }
        String wanted = string.trim();
        if (wanted.isEmpty() || wanted.equalsIgnoreCase("none")) {
            return this.setValue(NONE);
        }
        int index = Keyboard.getKeyIndex(wanted.toUpperCase());
        if (index != Keyboard.KEY_NONE) {
            return this.setValue(index);
        }
        try {
            return this.setValue(Integer.parseInt(wanted));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    @Override
    public boolean read(JsonObject jsonObject) {
        return this.setValue(jsonObject.get(this.getName()).getAsInt());
    }
    @Override
    public void write(JsonObject jsonObject) {
        jsonObject.addProperty(this.getName(), this.getValue());
    }
}
