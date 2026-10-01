package me.ksyz.accountmanager.gui;

import me.ksyz.accountmanager.auth.SessionManager;
import me.ksyz.accountmanager.auth.cookie.CookieAuth;
import me.ksyz.accountmanager.auth.cookie.CookieJar;
import me.ksyz.accountmanager.utils.Notification;
import me.ksyz.accountmanager.utils.TextFormatting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class GuiCookieLogin extends GuiScreen {
    private static final int BUTTON_PASTE = 900;
    private static final int BUTTON_FILE = 901;

    private final GuiScreen previousScreen;
    private GuiTextField cookieField = null;
    private GuiButton pasteButton = null;
    private GuiButton fileButton = null;
    private String status = null;
    private String cause = null;
    private ExecutorService executor = null;
    private CompletableFuture<Void> task = null;
    private volatile File pendingFile = null;
    private volatile boolean chooserOpen = false;
    private volatile boolean success = false;

    public GuiCookieLogin(GuiScreen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        ScaledResolution sr = new ScaledResolution(mc);
        cookieField = new GuiTextField(
                1, mc.fontRendererObj, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 - 6, 200, 20
        );
        cookieField.setMaxStringLength(32767);
        cookieField.setFocused(true);
        buttonList.add(pasteButton = new GuiButton(
                BUTTON_PASTE, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 20, 200, 20, "Login with pasted cookies"
        ));
        buttonList.add(fileButton = new GuiButton(
                BUTTON_FILE, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 44, 200, 20, "Open cookie file..."
        ));
        if (status == null) {
            status = "&7Paste a cookie string or open an exported cookie file&r";
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (task != null && !task.isDone()) {
            task.cancel(true);
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    @Override
    public void updateScreen() {
        if (cookieField != null) {
            cookieField.updateCursorCounter();
        }
        File file = pendingFile;
        if (file != null) {
            pendingFile = null;
            startFromFile(file);
        }
        if (success) {
            success = false;
            mc.displayGuiScreen(new GuiAccountManager(
                    previousScreen,
                    new Notification(TextFormatting.translate(String.format(
                            "&aSuccessful login! (%s)&r", SessionManager.get().getUsername()
                    )), 5000L)
            ));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        boolean idle = task == null || task.isDone();
        if (pasteButton != null) {
            pasteButton.enabled = idle && !chooserOpen;
        }
        if (fileButton != null) {
            fileButton.enabled = idle && !chooserOpen;
        }
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(
                fontRendererObj, "Cookie Login",
                width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2 - 14, 11184810
        );
        cookieField.drawTextBox();
        if (status != null) {
            drawCenteredString(
                    fontRendererObj, TextFormatting.translate(status),
                    width / 2, height / 2 - fontRendererObj.FONT_HEIGHT / 2 - 20, -1
            );
        }
        if (cause != null) {
            String causeText = TextFormatting.translate(cause);
            Gui.drawRect(
                    0, height - 2 - fontRendererObj.FONT_HEIGHT - 3,
                    3 + mc.fontRendererObj.getStringWidth(causeText) + 3, height,
                    0x64000000
            );
            drawString(fontRendererObj, causeText, 3, height - 2 - fontRendererObj.FONT_HEIGHT, -1);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        cookieField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        cookieField.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (task == null || task.isDone()) {
                mc.displayGuiScreen(previousScreen);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == BUTTON_PASTE) {
            startFromContent(cookieField.getText());
        } else if (button.id == BUTTON_FILE) {
            openFileChooser();
        }
    }

    private void openFileChooser() {
        chooserOpen = true;
        status = "&7Opening the file picker...&r";
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
                JFrame owner = null;
                try {
                    owner = new JFrame();
                    owner.setUndecorated(true);
                    owner.setAlwaysOnTop(true);
                    owner.setSize(1, 1);
                    owner.setLocationRelativeTo(null);
                    owner.setVisible(true);
                    JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.home"), "Downloads"));
                    chooser.setDialogTitle("Select a cookie file");
                    chooser.setFileFilter(new FileNameExtensionFilter(
                            "Cookie files (*.txt, *.json, *.cookies)", "txt", "json", "cookies"
                    ));
                    int result = chooser.showOpenDialog(owner);
                    if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                        pendingFile = chooser.getSelectedFile();
                    } else {
                        status = "&eFile selection was cancelled.&r";
                    }
                } catch (Throwable t) {
                    status = "&cCould not open the file picker: " + t.getMessage() + "&r";
                } finally {
                    if (owner != null) {
                        owner.dispose();
                    }
                    chooserOpen = false;
                }
            }
        }, "cookie-file-chooser");
        thread.setDaemon(true);
        thread.start();
    }

    private void startFromFile(File file) {
        try {
            start(CookieAuth.parseFile(file));
        } catch (Exception e) {
            status = "&cCould not read the cookie file&r";
            cause = "&c" + e.getMessage() + "&r";
        }
    }

    private void startFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            status = "&cPaste a cookie string first, or open a cookie file&r";
            return;
        }
        start(CookieAuth.parseContent(content));
    }

    private void start(CookieJar jar) {
        if (task != null && !task.isDone()) {
            return;
        }
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        cause = null;
        status = "&7Reading cookies...&r";
        task = CookieAuth.addAccount(jar, executor, new Consumer<String>() {
            @Override
            public void accept(String message) {
                status = message;
            }
        }).thenRun(new Runnable() {
            @Override
            public void run() {
                status = null;
                success = true;
            }
        }).exceptionally(new java.util.function.Function<Throwable, Void>() {
            @Override
            public Void apply(Throwable error) {
                status = String.format("&c%s&r", error.getMessage());
                if (error.getCause() != null && error.getCause().getMessage() != null) {
                    cause = String.format("&c%s&r", error.getCause().getMessage());
                }
                return null;
            }
        });
    }
}
