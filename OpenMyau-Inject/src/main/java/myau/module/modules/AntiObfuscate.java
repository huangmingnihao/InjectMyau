package myau.module.modules;

import myau.module.Module;

public class AntiObfuscate extends Module {
    public AntiObfuscate() {
        super("Anti Obfuscate", false, true);
    }

    public String stripObfuscated(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("§k", "");
    }
}
