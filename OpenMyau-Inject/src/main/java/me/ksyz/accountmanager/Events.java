package me.ksyz.accountmanager;

import me.ksyz.accountmanager.auth.Account;
import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.gui.GuiAccountManager;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.commons.lang3.StringUtils;

import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Events {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] SERVER_LIST_KEYS = {
            "selectServer.select", "selectServer.direct", "selectServer.add",
            "selectServer.edit", "selectServer.delete", "selectServer.refresh"
    };
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 6;
    private static final Map<Class<?>, Boolean> FOREIGN_CACHE = new HashMap<Class<?>, Boolean>();

    private static boolean isVanillaListScreen(GuiScreen gui) {
        return gui instanceof GuiMultiplayer || gui instanceof GuiSelectWorld;
    }

    private static boolean isServerListScreen(GuiScreen gui) {
        return isVanillaListScreen(gui) || isForeignListScreen(gui);
    }

    private static boolean isForeignListScreen(GuiScreen gui) {
        if (gui == null || isVanillaListScreen(gui) || gui instanceof GuiAccountManager) {
            return false;
        }
        Boolean cached = FOREIGN_CACHE.get(gui.getClass());
        if (cached != null) {
            return cached;
        }
        boolean found = holdsServerList(gui.getClass());
        FOREIGN_CACHE.put(gui.getClass(), found);
        return found;
    }

    private static boolean holdsServerList(Class<?> type) {
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (ServerList.class.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasServerListButtons(List<GuiButton> buttons) {
        List<String> labels = new ArrayList<String>();
        for (GuiButton entry : buttons) {
            if (entry != null && entry.displayString != null) {
                labels.add(entry.displayString);
            }
        }
        if (labels.isEmpty()) {
            return false;
        }
        int matches = 0;
        for (String key : SERVER_LIST_KEYS) {
            String translated = I18n.format(key);
            for (String label : labels) {
                if (translated.equalsIgnoreCase(label)) {
                    matches++;
                    break;
                }
            }
        }
        return matches >= 2;
    }

    private static int buttonX(GuiScreen gui) {
        return gui.width - BUTTON_WIDTH - BUTTON_MARGIN;
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!isForeignListScreen(event.gui)) {
            return;
        }
        int x = buttonX(event.gui);
        boolean over = event.mouseX >= x && event.mouseX <= x + BUTTON_WIDTH
                && event.mouseY >= BUTTON_MARGIN && event.mouseY <= BUTTON_MARGIN + BUTTON_HEIGHT;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        Gui.drawRect(x, BUTTON_MARGIN, x + BUTTON_WIDTH, BUTTON_MARGIN + BUTTON_HEIGHT,
                over ? 0xF0303030 : 0xC0101010);
        Gui.drawRect(x, BUTTON_MARGIN, x + BUTTON_WIDTH, BUTTON_MARGIN + 1, 0xFF808080);
        Gui.drawRect(x, BUTTON_MARGIN + BUTTON_HEIGHT - 1, x + BUTTON_WIDTH,
                BUTTON_MARGIN + BUTTON_HEIGHT, 0xFF808080);
        Gui.drawRect(x, BUTTON_MARGIN, x + 1, BUTTON_MARGIN + BUTTON_HEIGHT, 0xFF808080);
        Gui.drawRect(x + BUTTON_WIDTH - 1, BUTTON_MARGIN, x + BUTTON_WIDTH,
                BUTTON_MARGIN + BUTTON_HEIGHT, 0xFF808080);
        String text = "Accounts";
        event.gui.drawCenteredString(mc.fontRendererObj, text, x + BUTTON_WIDTH / 2,
                BUTTON_MARGIN + (BUTTON_HEIGHT - 8) / 2, over ? 0xFFFFA0 : 0xE0E0E0);
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    @SubscribeEvent
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!isForeignListScreen(event.gui) || Mouse.getEventButton() != 0
                || !Mouse.getEventButtonState()) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int mouseX = Mouse.getEventX() * resolution.getScaledWidth() / mc.displayWidth;
        int mouseY = resolution.getScaledHeight()
                - Mouse.getEventY() * resolution.getScaledHeight() / mc.displayHeight - 1;
        int x = buttonX(event.gui);
        if (mouseX >= x && mouseX <= x + BUTTON_WIDTH
                && mouseY >= BUTTON_MARGIN && mouseY <= BUTTON_MARGIN + BUTTON_HEIGHT) {
            event.setCanceled(true);
            mc.displayGuiScreen(new GuiAccountManager(event.gui));
        }
    }
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.currentScreen == null) {
            return;
        }
        if (isServerListScreen(mc.currentScreen)) {
            String text = TextFormatting.translate(String.format(
                    "&7Username: &3%s&r", SessionManager.get().getUsername()
            ));
            GlStateManager.disableLighting();
            mc.currentScreen.drawString(mc.fontRendererObj, text, 3, 3, -1);
            GlStateManager.enableLighting();
        }
    }
    @SubscribeEvent
    public void initGuiEvent(InitGuiEvent.Post event) {
        if (isVanillaListScreen(event.gui)) {
            event.buttonList.add(new GuiButton(
                    69, buttonX(event.gui), BUTTON_MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT, "Accounts"
            ));
        } else if (!(event.gui instanceof GuiAccountManager)) {
            Class<?> type = event.gui.getClass();
            Boolean known = FOREIGN_CACHE.get(type);
            if (known == null || !known) {
                FOREIGN_CACHE.put(type,
                        holdsServerList(type) || hasServerListButtons(event.buttonList));
            }
        }
        if (event.gui instanceof GuiDisconnected) {
            try {
                Field f = ReflectionHelper.findField(GuiDisconnected.class, "message", "field_146304_f");
                IChatComponent message = (IChatComponent) f.get(event.gui);
                String text = message.getFormattedText().split("\n\n")[0];
                if (
                        text.equals("§r§cYou are permanently banned from this server!") ||
                                text.equals("§r§cYour account has been blocked.")
                ) {
                    AccountManager.load();
                    for (Account account : AccountManager.accounts) {
                        if (mc.getSession().getUsername().equals(account.getUsername())) {
                            account.setUnban(-1L);
                        }
                    }
                    AccountManager.save();
                    return;
                }
                if (
                        text.matches("§r§cYou are temporarily banned for §r§f.*§r§c from this server!") ||
                                text.matches("§r§cYour account is temporarily blocked for §r§f.*§r§c from this server!")
                ) {
                    String unban = StringUtils.substringBetween(text, "§r§f", "§r§c");
                    if (unban != null) {
                        long time = System.currentTimeMillis();
                        for (String duration : unban.split(" ")) {
                            String type = duration.substring(duration.length() - 1);
                            long value = Long.parseLong(duration.substring(0, duration.length() - 1));
                            switch (type) {
                                case "d": {
                                    time += value * 86400000L;
                                }
                                break;
                                case "h": {
                                    time += value * 3600000L;
                                }
                                break;
                                case "m": {
                                    time += value * 60000L;
                                }
                                break;
                                case "s": {
                                    time += value * 1000L;
                                }
                                break;
                            }
                        }
                        AccountManager.load();
                        for (Account account : AccountManager.accounts) {
                            if (mc.getSession().getUsername().equals(account.getUsername())) {
                                account.setUnban(time);
                            }
                        }
                        AccountManager.save();
                    }
                }
            } catch (Exception e) {
            }
        }
    }
    @SubscribeEvent
    public void onClick(ActionPerformedEvent event) {
        if (isVanillaListScreen(event.gui)) {
            if (event.button.id == 69) {
                mc.displayGuiScreen(new GuiAccountManager(event.gui));
            }
        }
    }
    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        ServerData serverData = mc.getCurrentServerData();
        if (serverData != null) {
            String serverIP = serverData.serverIP;
            if (serverIP.endsWith("hypixel.net") || serverIP.endsWith("hypixel.io")) {
                AccountManager.load();
                for (Account account : AccountManager.accounts) {
                    if (mc.getSession().getUsername().equals(account.getUsername())) {
                        account.setUnban(0L);
                    }
                }
                AccountManager.save();
            }
        }
    }
}
