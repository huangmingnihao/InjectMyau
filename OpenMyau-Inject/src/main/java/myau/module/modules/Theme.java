package myau.module.modules;

import myau.module.Module;
import myau.property.properties.ModeProperty;
import myau.util.Themes;

import java.awt.Color;

public class Theme extends Module {
    public final ModeProperty theme = new ModeProperty("theme", 0, Themes.names());
    public Theme() {
        super("Theme", true, true);
    }
    public Themes getTheme() {
        Themes[] values = Themes.values();
        int index = this.theme.getValue();
        return index >= 0 && index < values.length ? values[index] : values[0];
    }
    public Color getColor(double x, double y) {
        return this.getTheme().getAccentColor(x, y);
    }
    @Override
    public String[] getSuffix() {
        return new String[]{this.getTheme().getThemeName()};
    }
}
