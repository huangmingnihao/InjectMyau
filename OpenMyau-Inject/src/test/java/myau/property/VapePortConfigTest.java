package myau.property;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.module.Module;
import myau.module.modules.AntiDebuff;
import myau.module.modules.Indicators;
import myau.module.modules.Projectiles;
import myau.module.modules.Trajectories;
import myau.property.properties.ModeProperty;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exercise real module properties with old configs and mode round trips, without a GL context. */
public final class VapePortConfigTest {
    public static void main(String[] args) throws Exception {
        roundTrip(new Indicators(), new Indicators(), "{\"scale\":1.25,\"offset\":73,\"direction-check\":false,\"egg\":false}");
        roundTrip(new Trajectories(), new Trajectories(), "{\"opacity\":62,\"bow\":false,\"projectiles\":true,\"pearls\":false}");
        roundTrip(new AntiDebuff(), new AntiDebuff(), "{\"blindness\":false,\"nausea\":true}");
        Projectiles projectiles = new Projectiles();
        Map<String, Property<?>> properties = properties(projectiles);
        check(properties.size() == 10, "all new projectile settings are registered");
        check(projectiles.arrows.getValue() && projectiles.pearls.getValue() && projectiles.potions.getValue()
                && !projectiles.eggs.getValue() && !projectiles.snowballs.getValue(), "reference projectile defaults");
        System.out.println("Vape port config checks passed (legacy configs, mode isolation/visibility, save/reload, reset without GL)");
    }

    private static void roundTrip(Module module, Module restored, String json) throws Exception {
        Map<String, Property<?>> fields = properties(module);
        ModeProperty mode = (ModeProperty) fields.get("mode");
        JsonObject legacy = new JsonParser().parse(json).getAsJsonObject();
        for (Property<?> property : fields.values()) if (legacy.has(property.getName())) check(property.read(legacy), "read legacy property");
        check(mode.getValue() == 0 && "Myau".equals(mode.getModeString()), "old configs retain old behavior");
        JsonObject before = save(fields);
        for (int i = 0; i < 20; i++) {
            check(mode.parseString("vape"), "case-insensitive Vape selection");
            for (Property<?> property : fields.values()) {
                if (property.getName().startsWith("vape-")) check(property.isVisible(), "Vape settings visible");
            }
            if (fields.containsKey("scale")) check(!fields.get("scale").isVisible(), "hide legacy indicator controls");
            if (fields.containsKey("opacity")) check(!fields.get("opacity").isVisible(), "hide legacy trajectory controls");
            check(mode.parseString("Myau"), "return to old mode");
            check(before.equals(save(fields)), "mode changes preserve all saved values");
        }
        mode.parseString("Vape");
        JsonObject saved = save(fields);
        Map<String, Property<?>> restoredFields = properties(restored);
        for (Property<?> property : restoredFields.values()) check(property.read(saved), "reload settings");
        check(saved.equals(save(restoredFields)), "full round trip including selected mode");
        module.setEnabled(true);
        module.setEnabled(false);
    }

    private static Map<String, Property<?>> properties(Module module) throws Exception {
        Map<String, Property<?>> result = new LinkedHashMap<>();
        for (Field field : module.getClass().getDeclaredFields()) {
            if (!Property.class.isAssignableFrom(field.getType())) continue;
            field.setAccessible(true);
            Property<?> property = (Property<?>) field.get(module);
            property.setOwner(module);
            check(result.put(property.getName(), property) == null, "no duplicate config names");
        }
        return result;
    }

    private static JsonObject save(Map<String, Property<?>> fields) {
        JsonObject json = new JsonObject();
        for (Property<?> property : fields.values()) property.write(json);
        return json;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
