package myau.ui.clickgui.components;

import myau.property.GroupState;
import myau.property.properties.ModeProperty;
import myau.ui.clickgui.GuiRender;
import myau.util.Themes;
import net.minecraft.client.Minecraft;

import java.awt.Color;

public class ThemeGridComponent extends Component {
    private static final float PANEL_WIDTH = 92.0F;
    private static final float PADDING = 5.0F;
    private static final int COLUMNS = 6;
    private static final float SWATCH_WIDTH = 12.0F;
    private static final float SWATCH_HEIGHT = 10.0F;
    private static final float GAP = 2.0F;
    private static final float LABEL_HEIGHT = 11.0F;
    private static final float HEADER_HEIGHT = 11.0F;
    private static final int SELECTED_OUTLINE = new Color(255, 255, 255).getRGB();
    private static final int HOVER_OUTLINE = new Color(255, 255, 255, 140).getRGB();
    private static final int LABEL_COLOR = new Color(210, 210, 210).getRGB();
    private static final int HEADER_COLOR = new Color(150, 170, 220).getRGB();
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ModeProperty property;
    private final ModuleComponent module;
    private final GroupState.Registry<Themes.ThemeGroup> groups =
            new GroupState.Registry<Themes.ThemeGroup>();
    private float offset;
    private int hovered = -1;
    public ThemeGridComponent(ModeProperty property, ModuleComponent module, float offset) {
        this.property = property;
        this.module = module;
        this.offset = offset;
    }
    private GroupState groupFor(Themes.ThemeGroup group) {
        Themes[] themes = Themes.values();
        int selected = this.property.getValue();
        boolean holdsSelection = selected >= 0 && selected < themes.length
                && themes[selected].getGroup() == group;
        return this.groups.get(group, group.getLabel(), holdsSelection);
    }
    private static int rowsIn(Themes.ThemeGroup group) {
        int count = 0;
        for (Themes theme : Themes.values()) {
            if (theme.getGroup() == group) {
                count++;
            }
        }
        return (count + COLUMNS - 1) / COLUMNS;
    }
    @Override
    public float getHeightF() {
        float height = LABEL_HEIGHT;
        for (Themes.ThemeGroup group : Themes.ThemeGroup.values()) {
            height += HEADER_HEIGHT;
            if (this.groupFor(group).isOpened()) {
                height += rowsIn(group) * (SWATCH_HEIGHT + GAP);
            }
        }
        return height + GAP;
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
    public boolean isBaseVisible() {
        return this.property.isVisible();
    }

    private void walk(float panelX, float panelY, Visitor visitor) {
        Themes[] themes = Themes.values();
        float y = panelY + LABEL_HEIGHT;
        for (Themes.ThemeGroup group : Themes.ThemeGroup.values()) {
            GroupState state = this.groupFor(group);
            visitor.header(state, panelX, y);
            y += HEADER_HEIGHT;
            if (!state.isOpened()) {
                continue;
            }
            int index = 0;
            for (int i = 0; i < themes.length; i++) {
                if (themes[i].getGroup() != group) {
                    continue;
                }
                float left = panelX + PADDING + (index % COLUMNS) * (SWATCH_WIDTH + GAP);
                float top = y + (index / COLUMNS) * (SWATCH_HEIGHT + GAP);
                visitor.swatch(i, themes[i], left, top);
                index++;
            }
            y += rowsIn(group) * (SWATCH_HEIGHT + GAP);
        }
    }
    private interface Visitor {
        void header(GroupState state, float x, float y);
        void swatch(int index, Themes theme, float left, float top);
    }
    @Override
    public void render() {
        final float x = this.module.category.getX();
        float y = this.module.category.getY() + this.offset;
        Themes[] themes = Themes.values();
        int named = this.hovered >= 0 && this.hovered < themes.length
                ? this.hovered : this.property.getValue();
        if (named >= 0 && named < themes.length) {
            mc.fontRendererObj.drawString(
                    themes[named].getThemeName(), x + PADDING, y + 2.0F, LABEL_COLOR, false);
        }

        final int selected = this.property.getValue();
        final int hoveredIndex = this.hovered;
        this.walk(x, y, new Visitor() {
            @Override
            public void header(GroupState state, float headerX, float headerY) {
                mc.fontRendererObj.drawString(state.getMarker() + "  " + state.getLabel(),
                        headerX + PADDING, headerY + 2.0F, HEADER_COLOR, false);
            }
            @Override
            public void swatch(int index, Themes theme, float left, float top) {
                GuiRender.drawHorizontalGradientRect(left, top, left + SWATCH_WIDTH,
                        top + SWATCH_HEIGHT,
                        theme.getPrimary().getRGB() | 0xFF000000,
                        theme.getSecondary().getRGB() | 0xFF000000);
                if (index == selected) {
                    GuiRender.drawOutline(left, top, left + SWATCH_WIDTH, top + SWATCH_HEIGHT,
                            1.5F, SELECTED_OUTLINE);
                } else if (index == hoveredIndex) {
                    GuiRender.drawOutline(left, top, left + SWATCH_WIDTH, top + SWATCH_HEIGHT,
                            1.0F, HOVER_OUTLINE);
                }
            }
        });
    }
    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.hovered = this.swatchAt(mouseX, mouseY);
    }
    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        GroupState header = this.headerAt(mouseX, mouseY);
        if (header != null) {
            header.toggle();
            return true;
        }
        int index = this.swatchAt(mouseX, mouseY);
        if (index < 0) {
            return false;
        }
        this.property.setValue(index);
        return true;
    }

    private int swatchAt(final int mouseX, final int mouseY) {
        float x = this.module.category.getX();
        float y = this.module.category.getY() + this.offset;
        if (mouseX < x || mouseX > x + PANEL_WIDTH) {
            return -1;
        }
        final int[] found = {-1};
        this.walk(x, y, new Visitor() {
            @Override
            public void header(GroupState state, float headerX, float headerY) {
            }
            @Override
            public void swatch(int index, Themes theme, float left, float top) {
                if (mouseX >= left && mouseX <= left + SWATCH_WIDTH
                        && mouseY >= top && mouseY <= top + SWATCH_HEIGHT) {
                    found[0] = index;
                }
            }
        });
        return found[0];
    }
    private GroupState headerAt(final int mouseX, final int mouseY) {
        final float x = this.module.category.getX();
        float y = this.module.category.getY() + this.offset;
        if (mouseX < x || mouseX > x + PANEL_WIDTH) {
            return null;
        }
        final GroupState[] found = {null};
        this.walk(x, y, new Visitor() {
            @Override
            public void header(GroupState state, float headerX, float headerY) {
                if (mouseY >= headerY && mouseY <= headerY + HEADER_HEIGHT) {
                    found[0] = state;
                }
            }
            @Override
            public void swatch(int index, Themes theme, float left, float top) {
            }
        });
        return found[0];
    }
}
