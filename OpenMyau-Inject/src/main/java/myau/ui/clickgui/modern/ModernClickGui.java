package myau.ui.clickgui.modern;

import me.ksyz.accountmanager.AccountManager;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.gui.GuiAddToken;
import me.ksyz.accountmanager.gui.GuiCookieLogin;
import me.ksyz.accountmanager.gui.GuiSessionLogin;
import myau.Myau;
import myau.module.Module;
import myau.module.modules.GuiModule;
import myau.property.Property;
import myau.ui.clickgui.Categories;
import myau.ui.clickgui.Category;
import myau.ui.clickgui.ClickGuiScreen;
import myau.ui.clickgui.GuiRender;
import myau.util.font.RavenFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 */
public class ModernClickGui extends GuiScreen implements ClickGuiScreen {
    private static final float PANEL_RADIUS = 16.0F;
    private static final float NAV_RADIUS = 8.0F;
    private static final float SEARCH_RADIUS = 8.0F;
    private static final float TAB_RADIUS = 7.0F;
    private static final float BUTTON_RADIUS = 7.0F;
    private static final float ACCOUNT_RADIUS = 6.0F;
    private static final int GLASS_PASSES = 2;
    private static final float GLASS_RADIUS = 6.0F;
    private static final float SEARCH_HEIGHT = 20.0F;
    private static final float NAV_TOP = 56.0F;
    private static final float NAV_HEIGHT = 20.0F;
    private static final float NAV_STEP = 22.0F;
    private static final float TAB_HEIGHT = 16.0F;
    private static final float TAB_GAP = 4.0F;
    private static final float BUTTON_HEIGHT = 16.0F;
    private static final float BUTTON_GAP = 4.0F;
    private static final float PICKER_ROW = 18.0F;

    private static final int NAV_MODULES = 0;
    private static final int NAV_ALTS = 1;
    private static final String[] NAV_LABELS = {"Modules", "Alts"};
    private static final String[] ALT_BUTTONS = {"Session", "Cookie", "Add Token"};
    private static final String ENABLED_TAB = "Enabled";

    private static final String BRAND = "myau";

    private final LinkedHashMap<Category, ArrayList<ModernModuleEntry>> byCategory =
            new LinkedHashMap<Category, ArrayList<ModernModuleEntry>>();
    private final ArrayList<ModernModuleEntry> visible = new ArrayList<ModernModuleEntry>();
    private final ArrayList<Tab> tabs = new ArrayList<Tab>();
    private boolean entriesBuilt;

    private Category selectedCategory;
    private boolean enabledFilter;
    private String search = "";
    private boolean searchFocused;
    private boolean altsPage;
    private float scroll;
    private boolean[] searchHeldKeys;

    private boolean pickerOpen;
    private Property<?> pickerProperty;
    private final ArrayList<String> pickerFiles = new ArrayList<String>();
    private float pickerScroll;

    private float winX;
    private float winY;
    private float winW;
    private float winH;
    private float sidebarW;
    private float contentX;
    private float contentW;
    private float tabsBottom;
    private float listX;
    private float listY;
    private float listW;
    private float listH;

    public ModernClickGui() {
        this.buildEntries();
    }

    @Override
    public String getStyleName() {
        return "Modern";
    }


    private void buildEntries() {
        if (this.entriesBuilt) {
            return;
        }
        this.entriesBuilt = true;
        Categories.verifyComplete();
        this.byCategory.clear();
        for (Category category : Category.values()) {
            ArrayList<ModernModuleEntry> entries = new ArrayList<ModernModuleEntry>();
            for (Module module : Categories.modulesOf(category)) {
                entries.add(new ModernModuleEntry(module));
            }
            this.byCategory.put(category, entries);
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        AccountManager.load();
        ModernUi.imageFolder();
        Keyboard.enableRepeatEvents(true);
        this.computeLayout();
    }

    public static void openImagePicker(Property<?> property) {
        if (Minecraft.getMinecraft().currentScreen instanceof ModernClickGui) {
            ((ModernClickGui) Minecraft.getMinecraft().currentScreen).showImagePicker(property);
        }
    }

    private void showImagePicker(Property<?> property) {
        this.pickerProperty = property;
        this.pickerFiles.clear();
        File[] files = ModernUi.imageFolder().listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(file -> file.getName().toLowerCase()));
            for (File file : files) {
                if (file.isFile() && isImageName(file.getName())) {
                    this.pickerFiles.add(file.getName());
                }
            }
        }
        this.pickerScroll = 0.0F;
        this.pickerOpen = true;
    }

    private static boolean isImageName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp");
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }


    private void computeLayout() {
        this.winW = Math.max(180.0F, Math.min(this.width - 40.0F, 470.0F));
        this.winH = Math.max(140.0F, Math.min(this.height - 40.0F, 320.0F));
        this.winX = (this.width - this.winW) / 2.0F;
        this.winY = (this.height - this.winH) / 2.0F;
        this.sidebarW = Math.max(80.0F, this.winW * 0.22F);
        this.contentX = this.winX + this.sidebarW;
        this.contentW = this.winW - this.sidebarW;
        this.layoutTabs();
        this.listX = this.contentX + 6.0F;
        this.listW = Math.max(20.0F, this.contentW - 12.0F);
        this.listY = this.tabsBottom + 4.0F;
        this.listH = this.winY + this.winH - 8.0F - this.listY;
        this.refreshVisible();
    }

    private void layoutTabs() {
        this.tabs.clear();
        RavenFontRenderer font = ModernUi.regular(9.0F);
        float left = this.contentX + 10.0F;
        float right = this.contentX + this.contentW - 10.0F;
        float x = left;
        float y = this.winY + 38.0F;
        Category[] values = Category.values();
        for (int index = 0; index <= values.length + 1; index++) {
            Category category = null;
            boolean enabledOnly = false;
            String label;
            if (index == 0) {
                label = "All";
            } else if (index == 1) {
                label = ENABLED_TAB;
                enabledOnly = true;
            } else {
                category = values[index - 2];
                label = category.getLabel();
            }
            float width = font.getStringWidth(label) + 14.0F;
            if (x > left && x + width > right) {
                x = left;
                y += TAB_HEIGHT + TAB_GAP;
            }
            this.tabs.add(new Tab(category, enabledOnly, x, y, width));
            x += width + TAB_GAP;
        }
        this.tabsBottom = y + TAB_HEIGHT;
    }

    private void refreshVisible() {
        this.visible.clear();
        for (Map.Entry<Category, ArrayList<ModernModuleEntry>> group : this.byCategory.entrySet()) {
            if (this.selectedCategory != null && group.getKey() != this.selectedCategory) {
                continue;
            }
            for (ModernModuleEntry entry : group.getValue()) {
                if (this.enabledFilter && !entry.getModule().isEnabled()) {
                    continue;
                }
                if (entry.matches(this.search)) {
                    this.visible.add(entry);
                }
            }
        }
    }

    private ArrayList<ModernModuleEntry> allEntries() {
        ArrayList<ModernModuleEntry> all = new ArrayList<ModernModuleEntry>();
        for (ArrayList<ModernModuleEntry> group : this.byCategory.values()) {
            all.addAll(group);
        }
        return all;
    }

    private float navTop(int index) {
        return this.winY + NAV_TOP + index * NAV_STEP;
    }

    private float searchX() {
        return this.contentX + 10.0F;
    }

    private float searchW() {
        return Math.max(40.0F, this.contentW - 20.0F);
    }

    private float altButtonY() {
        return this.winY + this.winH - 22.0F;
    }

    private float altButtonWidth() {
        return Math.max(20.0F, (this.contentW - 20.0F - 2.0F * BUTTON_GAP) / 3.0F);
    }

    private float altButtonX(int column) {
        return this.contentX + 10.0F + column * (this.altButtonWidth() + BUTTON_GAP);
    }

    private boolean insideList(int mouseX, int mouseY) {
        return mouseX >= this.listX && mouseX <= this.listX + this.listW
                && mouseY >= this.listY && mouseY <= this.listY + this.listH;
    }

    private GuiModule guiModule() {
        Module module = Myau.moduleManager.getModule(GuiModule.class);
        return module instanceof GuiModule ? (GuiModule) module : null;
    }


    private float pickerW() {
        return Math.min(220.0F, this.width - 40.0F);
    }

    private float pickerH() {
        return Math.min(180.0F, this.height - 40.0F);
    }

    private float pickerX() {
        return (this.width - this.pickerW()) / 2.0F;
    }

    private float pickerY() {
        return (this.height - this.pickerH()) / 2.0F;
    }

    private float pickerListY() {
        return this.pickerY() + 24.0F;
    }

    private float pickerListH() {
        return this.pickerH() - 24.0F - 16.0F;
    }

    private void drawPicker(int mouseX, int mouseY) {
        float px = this.pickerX();
        float py = this.pickerY();
        float pw = this.pickerW();
        float ph = this.pickerH();
        drawRect(0, 0, this.width, this.height, 0xA0000000);
        GuiRender.drawRoundedRect(px, py, px + pw, py + ph, PANEL_RADIUS, 0xF01A1D24);

        RavenFontRenderer title = ModernUi.bold(11.0F);
        title.drawString("Background image", px + 10.0F, py + 8.0F, ModernUi.TEXT, false);

        float listY = this.pickerListY();
        float listH = this.pickerListH();
        float contentHeight = (1 + this.pickerFiles.size()) * PICKER_ROW;
        float maxScroll = Math.max(0.0F, contentHeight - listH);
        this.pickerScroll = Math.max(0.0F, Math.min(maxScroll, this.pickerScroll));

        RavenFontRenderer font = ModernUi.regular(9.5F);
        String current = this.pickerProperty == null
                ? "" : String.valueOf(this.pickerProperty.getValue());
        GuiRender.scissorPush(px + 4.0F, listY, pw - 8.0F, listH);
        float rowY = listY - this.pickerScroll;
        this.drawPickerRow(font, "None", current.isEmpty(), px + 6.0F, rowY, pw - 12.0F,
                mouseX, mouseY);
        rowY += PICKER_ROW;
        for (String name : this.pickerFiles) {
            if (rowY + PICKER_ROW >= listY && rowY <= listY + listH) {
                this.drawPickerRow(font, name, name.equals(current), px + 6.0F, rowY,
                        pw - 12.0F, mouseX, mouseY);
            }
            rowY += PICKER_ROW;
        }
        GuiRender.scissorPop();

        RavenFontRenderer small = ModernUi.regular(8.0F);
        small.drawString("Put images in config/Myau/clickgui", px + 10.0F,
                py + ph - 12.0F, ModernUi.TEXT_DIM, false);
    }

    private void drawPickerRow(RavenFontRenderer font, String label, boolean selected,
                               float x, float y, float width, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + PICKER_ROW;
        GuiRender.drawRoundedRect(x, y, x + width, y + PICKER_ROW, ACCOUNT_RADIUS,
                selected ? ModernUi.withOpacity(ModernUi.ACCENT, 45)
                        : (hovered ? ModernUi.ROW_HOVER : ModernUi.ROW_BG));
        font.drawString(ModernUi.trim(font, label, width - 12.0F), x + 6.0F,
                ModernUi.centerTextY(font, y, PICKER_ROW),
                selected ? ModernUi.TEXT : ModernUi.TEXT_DIM, false);
    }

    private void handlePickerClick(int mouseX, int mouseY) {
        float px = this.pickerX();
        float py = this.pickerY();
        float pw = this.pickerW();
        float ph = this.pickerH();
        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) {
            this.pickerOpen = false;
            return;
        }
        float listY = this.pickerListY();
        float listH = this.pickerListH();
        if (mouseX >= px + 6.0F && mouseX <= px + pw - 6.0F
                && mouseY >= listY && mouseY <= listY + listH) {
            int index = (int) ((mouseY - listY + this.pickerScroll) / PICKER_ROW);
            if (index == 0) {
                this.setPickerValue("");
                this.pickerOpen = false;
            } else if (index >= 1 && index <= this.pickerFiles.size()) {
                this.setPickerValue(this.pickerFiles.get(index - 1));
                this.pickerOpen = false;
            }
        }
    }

    private void setPickerValue(String value) {
        if (this.pickerProperty != null) {
            this.pickerProperty.setValue(value);
        }
    }


    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GuiRender.setRenderScale(1.0);
        this.pollSearchKeys();
        GuiModule gui = this.guiModule();
        if (gui != null) {
            ModernUi.setTextShadow(Boolean.TRUE.equals(gui.textShadow.getValue()),
                    0xFF000000 | (gui.shadowColor.getValue() & 0xFFFFFF));
            ModernUi.setTextColours(gui.textColor.getValue(), gui.textDimColor.getValue(),
                    gui.textValueColor.getValue());
        } else {
            ModernUi.setTextShadow(true, 0xFF000000);
            ModernUi.setTextColours(0xFFFFFF, 0x9AA0A6, 0x7FA8FF);
        }
        this.computeLayout();
        this.drawGlass(mouseX, mouseY);
        this.drawPanel();
        this.drawSidebar(mouseX, mouseY);
        if (this.altsPage) {
            this.drawAlts(mouseX, mouseY);
        } else {
            this.drawModules(mouseX, mouseY);
        }
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            float delta = wheel / 120.0F * 18.0F;
            if (this.pickerOpen) {
                this.pickerScroll -= delta;
            } else if (!this.altsPage) {
                this.scroll -= delta;
            }
        }
        if (this.pickerOpen) {
            this.drawPicker(mouseX, mouseY);
        }
        GuiRender.resetScissors();
    }

    /**
     */
    private void drawGlass(int mouseX, int mouseY) {
        GuiModule gui = this.guiModule();
        if (gui == null || !gui.backgroundBlur.getValue() || !ModernUi.glassReady()) {
            return;
        }
        ModernUi.glassBegin();
        this.drawSidebar(mouseX, mouseY);
        if (this.altsPage) {
            this.drawAlts(mouseX, mouseY);
        } else {
            this.drawModules(mouseX, mouseY);
        }
        ModernUi.glassFlush(GLASS_PASSES, GLASS_RADIUS);
    }

    private void drawPanel() {
        GuiModule gui = this.guiModule();
        int colour = gui == null ? 0x101218 : gui.background.getValue() & 0xFFFFFF;
        int opacity = gui == null ? 55 : gui.backgroundOpacity.getValue();
        GuiRender.drawRoundedRect(this.winX, this.winY, this.winX + this.winW, this.winY + this.winH,
                PANEL_RADIUS, ModernUi.withOpacity(colour, opacity));
        if (gui == null) {
            return;
        }
        int texture = ModernUi.texture(gui.backgroundImage.getValue());
        if (texture > 0) {
            int[] size = ModernUi.imageDimensions(gui.backgroundImage.getValue());
            if (size != null && size[0] > 0 && size[1] > 0) {
                float panelAspect = this.winW / this.winH;
                float imageAspect = (float) size[0] / (float) size[1];
                float u0 = 0.0F;
                float v0 = 0.0F;
                float u1 = 1.0F;
                float v1 = 1.0F;
                if (imageAspect > panelAspect) {
                    float keep = panelAspect / imageAspect;
                    u0 = (1.0F - keep) / 2.0F;
                    u1 = u0 + keep;
                } else if (imageAspect < panelAspect) {
                    float keep = imageAspect / panelAspect;
                    v0 = (1.0F - keep) / 2.0F;
                    v1 = v0 + keep;
                }
                ModernUi.drawRoundedTextureRegion(texture, this.winX, this.winY,
                        this.winX + this.winW, this.winY + this.winH, PANEL_RADIUS,
                        gui.imageOpacity.getValue() / 100.0F, u0, v0, u1, v1);
            }
        }
    }

    private void drawSidebar(int mouseX, int mouseY) {
        if (ModernUi.glassCollecting()) {
            for (int index = 0; index < NAV_LABELS.length; index++) {
                ModernUi.glassAdd(this.winX + 8.0F, this.navTop(index),
                        this.sidebarW - 16.0F, NAV_HEIGHT, ModernUi.visual(NAV_RADIUS));
            }
            return;
        }

        int logo = ModernUi.logoTexture();
        if (logo > 0) {
            float logoW = Math.min(64.0F, this.sidebarW - 24.0F);
            float logoH = logoW / ModernUi.logoAspect();
            ModernUi.drawTexture(logo, this.winX + 16.0F, this.winY + 13.0F,
                    this.winX + 16.0F + logoW, this.winY + 13.0F + logoH, 1.0F);
        } else {
            RavenFontRenderer brand = ModernUi.bold(22.0F);
            brand.drawString(BRAND, this.winX + 16.0F, this.winY + 13.0F, ModernUi.TEXT, false);
        }

        RavenFontRenderer label = ModernUi.ui(10.0F);
        for (int index = 0; index < NAV_LABELS.length; index++) {
            float top = this.navTop(index);
            float left = this.winX + 8.0F;
            float right = this.winX + this.sidebarW - 8.0F;
            boolean active = (index == NAV_ALTS) == this.altsPage;
            boolean hovered = mouseX >= left && mouseX <= right
                    && mouseY >= top && mouseY <= top + NAV_HEIGHT;
            if (active) {
                GuiRender.drawRoundedRect(left, top, right, top + NAV_HEIGHT, NAV_RADIUS, ModernUi.ACCENT);
            } else if (hovered) {
                GuiRender.drawRoundedRect(left, top, right, top + NAV_HEIGHT, NAV_RADIUS, 0x1AFFFFFF);
            }
            int iconColour = active ? ModernUi.TEXT : ModernUi.TEXT_DIM;
            if (index == NAV_MODULES) {
                ModernUi.drawGridIcon(this.winX + 20.0F, top + NAV_HEIGHT / 2.0F, 11.0F, iconColour);
            } else {
                ModernUi.drawUserIcon(this.winX + 20.0F, top + NAV_HEIGHT / 2.0F, 11.0F, iconColour);
            }
            label.drawString(NAV_LABELS[index], this.winX + 32.0F,
                    ModernUi.centerTextY(label, top, NAV_HEIGHT),
                    active ? ModernUi.TEXT : ModernUi.TEXT_DIM, false);
        }
    }

    private void drawModules(int mouseX, int mouseY) {
        float searchX = this.searchX();
        float searchY = this.winY + 10.0F;
        float searchW = this.searchW();
        boolean collect = ModernUi.glassCollecting();
        if (collect) {
            ModernUi.glassAdd(searchX, searchY, searchW, SEARCH_HEIGHT, ModernUi.visual(SEARCH_RADIUS));
            for (Tab tab : this.tabs) {
                ModernUi.glassAdd(tab.x, tab.y, tab.width, TAB_HEIGHT, ModernUi.visual(TAB_RADIUS));
            }
            ModernUi.glassClip(this.listY, this.listY + this.listH);
        } else {
            boolean searchHovered = mouseX >= searchX && mouseX <= searchX + searchW
                    && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT;
            GuiRender.drawRoundedRect(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT,
                    SEARCH_RADIUS,
                    this.searchFocused ? 0x24FFFFFF : (searchHovered ? 0x1AFFFFFF : 0x14FFFFFF));
            ModernUi.drawSearchIcon(searchX + 12.0F, searchY + SEARCH_HEIGHT / 2.0F, 11.0F,
                    this.searchFocused ? ModernUi.ACCENT : ModernUi.TEXT_DIM);

            RavenFontRenderer searchFont = ModernUi.regular(9.5F);
            float textX = searchX + 22.0F;
            float textY = ModernUi.centerTextY(searchFont, searchY, SEARCH_HEIGHT);
            String shown = this.search;
            int textColour = ModernUi.TEXT;
            if (shown.isEmpty() && !this.searchFocused) {
                shown = "Search";
                textColour = ModernUi.TEXT_DIM;
            }
            searchFont.drawString(ModernUi.trim(searchFont, shown, searchW - 34.0F), textX, textY,
                    textColour, false);
            if (this.searchFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
                float caretX = textX + searchFont.getStringWidth(this.search) + 1.0F;
                GuiRender.drawRoundedRect(caretX, searchY + 5.0F, caretX + 1.4F,
                        searchY + SEARCH_HEIGHT - 5.0F, 1.0F, ModernUi.TEXT);
            }

            RavenFontRenderer tabFont = ModernUi.regular(9.0F);
            for (Tab tab : this.tabs) {
                boolean active = tab.isActive(this.selectedCategory, this.enabledFilter);
                boolean hovered = tab.contains(mouseX, mouseY);
                GuiRender.drawRoundedRect(tab.x, tab.y, tab.x + tab.width, tab.y + TAB_HEIGHT,
                        TAB_RADIUS,
                        active ? ModernUi.ACCENT : (hovered ? 0x24FFFFFF : 0x14FFFFFF));
                tabFont.drawString(tab.label(), tab.x + 7.0F,
                    ModernUi.centerTextY(tabFont, tab.y, TAB_HEIGHT),
                    active ? ModernUi.TEXT : ModernUi.TEXT_DIM, false);
            }
        }

        if (this.listH <= 0.0F) {
            return;
        }
        float contentHeight = 0.0F;
        for (ModernModuleEntry entry : this.visible) {
            contentHeight += entry.getHeight() + ModernModuleEntry.rowGap();
        }
        float maxScroll = Math.max(0.0F, contentHeight - this.listH);
        this.scroll = Math.max(0.0F, Math.min(maxScroll, this.scroll));

        if (collect) {
            float rowY = this.listY - this.scroll;
            for (ModernModuleEntry entry : this.visible) {
                float height = entry.getHeight();
                if (rowY + height >= this.listY && rowY <= this.listY + this.listH) {
                    entry.draw(this.listX, rowY, this.listW, Integer.MIN_VALUE, Integer.MIN_VALUE);
                }
                rowY += height + ModernModuleEntry.rowGap();
            }
            return;
        }

        boolean inside = this.insideList(mouseX, mouseY);
        int rowMouseX = inside ? mouseX : Integer.MIN_VALUE;
        int rowMouseY = inside ? mouseY : Integer.MIN_VALUE;
        GuiRender.scissorPush(this.listX, this.listY, this.listW, this.listH);
        float y = this.listY - this.scroll;
        for (ModernModuleEntry entry : this.visible) {
            float height = entry.getHeight();
            if (y + height >= this.listY && y <= this.listY + this.listH) {
                entry.draw(this.listX, y, this.listW, rowMouseX, rowMouseY);
            }
            y += height + ModernModuleEntry.rowGap();
        }
        GuiRender.scissorPop();

        if (this.visible.isEmpty()) {
            RavenFontRenderer empty = ModernUi.regular(9.5F);
            empty.drawString("No modules found", this.listX + 6.0F,
                    ModernUi.centerTextY(empty, this.listY, 20.0F), ModernUi.TEXT_DIM, false);
        }
    }

    private void drawAlts(int mouseX, int mouseY) {
        RavenFontRenderer font = ModernUi.regular(9.5F);
        boolean collect = ModernUi.glassCollecting();

        if (collect) {
            for (int column = 0; column < ALT_BUTTONS.length; column++) {
                ModernUi.glassAdd(this.altButtonX(column), this.altButtonY(), this.altButtonWidth(),
                        BUTTON_HEIGHT, ModernUi.visual(BUTTON_RADIUS));
            }
            return;
        }

        RavenFontRenderer caption = ModernUi.regular(8.5F);
        caption.drawString("Signed in as", this.contentX + 10.0F, this.winY + 38.0F,
                ModernUi.TEXT_DIM, false);
        RavenFontRenderer name = ModernUi.bold(12.0F);
        name.drawString(ModernUi.trim(name, this.currentAccountName(), this.contentW - 20.0F),
                this.contentX + 10.0F, this.winY + 50.0F, ModernUi.TEXT, false);

        for (int column = 0; column < ALT_BUTTONS.length; column++) {
            float left = this.altButtonX(column);
            float width = this.altButtonWidth();
            float top = this.altButtonY();
            boolean hovered = mouseX >= left && mouseX <= left + width
                    && mouseY >= top && mouseY <= top + BUTTON_HEIGHT;
            GuiRender.drawRoundedRect(left, top, left + width, top + BUTTON_HEIGHT, BUTTON_RADIUS,
                    hovered ? 0x2EFFFFFF : 0x1AFFFFFF);
            font.drawString(ALT_BUTTONS[column],
                    left + (width - font.getStringWidth(ALT_BUTTONS[column])) / 2.0F,
                    ModernUi.centerTextY(font, top, BUTTON_HEIGHT), ModernUi.TEXT, false);
        }
    }

    private String currentAccountName() {
        try {
            String username = SessionManager.get().getUsername();
            return username == null || username.trim().isEmpty() ? "Not logged in" : username;
        } catch (Throwable ignored) {
            return "Not logged in";
        }
    }


    /**
     */
    private void pollSearchKeys() {
        if (this.searchHeldKeys == null || this.searchHeldKeys.length != Keyboard.KEYBOARD_SIZE) {
            this.searchHeldKeys = new boolean[Keyboard.KEYBOARD_SIZE];
        }
        boolean listening = this.searchFocused && !this.pickerOpen && !this.isBinding();
        boolean shift = rawKeyDown(Keyboard.KEY_LSHIFT) || rawKeyDown(Keyboard.KEY_RSHIFT);
        for (int key = 1; key < this.searchHeldKeys.length; key++) {
            boolean down = rawKeyDown(key);
            if (listening && down && !this.searchHeldKeys[key] && this.search.length() < 64) {
                char typed = searchChar(key, shift);
                if (typed != 0) {
                    this.search = this.search + typed;
                    this.scroll = 0.0F;
                }
            }
            this.searchHeldKeys[key] = down;
        }
    }

    private static boolean rawKeyDown(int key) {
        try {
            return Keyboard.isKeyDown(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static char searchChar(int key, boolean shift) {
        String name;
        try {
            name = Keyboard.getKeyName(key);
        } catch (Throwable ignored) {
            return 0;
        }
        if (name == null || name.isEmpty()) {
            return 0;
        }
        if (name.length() == 1) {
            char base = name.charAt(0);
            if (base >= '0' && base <= '9') {
                if (!shift) {
                    return base;
                }
                switch (base) {
                    case '1': return '!';
                    case '2': return '@';
                    case '3': return '#';
                    case '4': return '$';
                    case '5': return '%';
                    case '6': return '^';
                    case '7': return '&';
                    case '8': return '*';
                    case '9': return '(';
                    default: return ')';
                }
            }
            return shift ? base : Character.toLowerCase(base);
        }
        switch (name) {
            case "SPACE":
                return ' ';
            case "MINUS":
                return shift ? '_' : '-';
            case "EQUALS":
                return shift ? '+' : '=';
            case "LBRACKET":
                return shift ? '{' : '[';
            case "RBRACKET":
                return shift ? '}' : ']';
            case "SEMICOLON":
                return shift ? ':' : ';';
            case "APOSTROPHE":
                return shift ? '"' : '\'';
            case "GRAVE":
                return shift ? '~' : '`';
            case "COMMA":
                return shift ? '<' : ',';
            case "PERIOD":
                return shift ? '>' : '.';
            case "SLASH":
                return shift ? '?' : '/';
            case "BACKSLASH":
                return shift ? '|' : '\\';
            default:
                return 0;
        }
    }


    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.pickerOpen) {
            this.handlePickerClick(mouseX, mouseY);
            return;
        }
        for (int index = 0; index < NAV_LABELS.length; index++) {
            float top = this.navTop(index);
            if (mouseX >= this.winX + 8.0F && mouseX <= this.winX + this.sidebarW - 8.0F
                    && mouseY >= top && mouseY <= top + NAV_HEIGHT) {
                boolean alts = index == NAV_ALTS;
                if (alts != this.altsPage) {
                    this.altsPage = alts;
                    this.searchFocused = false;
                    this.scroll = 0.0F;
                    this.cancelBindings();
                }
                return;
            }
        }
        if (this.altsPage) {
            this.handleAltsClick(mouseX, mouseY);
            return;
        }
        float searchX = this.searchX();
        float searchY = this.winY + 10.0F;
        if (mouseX >= searchX && mouseX <= searchX + this.searchW()
                && mouseY >= searchY && mouseY <= searchY + SEARCH_HEIGHT) {
            this.searchFocused = true;
            return;
        }
        this.searchFocused = false;
        for (Tab tab : this.tabs) {
            if (tab.contains(mouseX, mouseY)) {
                this.selectedCategory = tab.category;
                this.enabledFilter = tab.enabledOnly;
                this.scroll = 0.0F;
                this.cancelBindings();
                return;
            }
        }
        if (!this.insideList(mouseX, mouseY)) {
            return;
        }
        float y = this.listY - this.scroll;
        for (ModernModuleEntry entry : this.visible) {
            if (entry.onClick(this.listX, y, this.listW, mouseX, mouseY, button)) {
                return;
            }
            y += entry.getHeight() + ModernModuleEntry.rowGap();
        }
    }

    private void handleAltsClick(int mouseX, int mouseY) {
        for (int column = 0; column < ALT_BUTTONS.length; column++) {
            float left = this.altButtonX(column);
            if (mouseX >= left && mouseX <= left + this.altButtonWidth()
                    && mouseY >= this.altButtonY() && mouseY <= this.altButtonY() + BUTTON_HEIGHT) {
                this.performAltAction(column);
                return;
            }
        }
    }

    private void performAltAction(int column) {
        switch (column) {
            case 0:
                this.mc.displayGuiScreen(new GuiSessionLogin(this));
                break;
            case 1:
                this.mc.displayGuiScreen(new GuiCookieLogin(this));
                break;
            case 2:
                this.mc.displayGuiScreen(new GuiAddToken(this));
                break;
            default:
                break;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        for (ModernModuleEntry entry : this.allEntries()) {
            entry.mouseReleased();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        for (ModernModuleEntry entry : this.allEntries()) {
            entry.mouseClickMove(mouseX);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (this.isBinding()) {
            return;
        }
        if (this.pickerOpen) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
                this.pickerOpen = false;
            }
            return;
        }
        if (this.searchFocused) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
                this.searchFocused = false;
                return;
            }
            if (key == Keyboard.KEY_BACK) {
                if (!this.search.isEmpty()) {
                    this.search = this.search.substring(0, this.search.length() - 1);
                    this.scroll = 0.0F;
                }
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void updateScreen() {
        for (ModernModuleEntry entry : this.allEntries()) {
            entry.updateScreen();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        this.cancelBindings();
        this.searchFocused = false;
        this.searchHeldKeys = null;
    }

    private void cancelBindings() {
        for (ModernModuleEntry entry : this.allEntries()) {
            entry.cancelBindings();
        }
    }

    private boolean isBinding() {
        for (ModernModuleEntry entry : this.allEntries()) {
            if (entry.isBinding()) {
                return true;
            }
        }
        return false;
    }

    private static final class Tab {
        private final Category category;
        private final boolean enabledOnly;
        private final float x;
        private final float y;
        private final float width;

        private Tab(Category category, boolean enabledOnly, float x, float y, float width) {
            this.category = category;
            this.enabledOnly = enabledOnly;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        private String label() {
            if (this.category != null) {
                return this.category.getLabel();
            }
            return this.enabledOnly ? ENABLED_TAB : "All";
        }

        private boolean isActive(Category selected, boolean enabledFilter) {
            return this.category == selected && this.enabledOnly == enabledFilter;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width
                    && mouseY >= this.y && mouseY <= this.y + TAB_HEIGHT;
        }
    }
}
