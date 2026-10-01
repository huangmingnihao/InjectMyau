package myau;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.ksyz.accountmanager.AccountManager;
import myau.bot.BotManager;
import myau.command.CommandManager;
import myau.command.commands.BindCommand;
import myau.command.commands.ConfigCommand;
import myau.command.commands.DenickCommand;
import myau.command.commands.FriendCommand;
import myau.command.commands.HelpCommand;
import myau.command.commands.HideCommand;
import myau.command.commands.IgnCommand;
import myau.command.commands.ItemCommand;
import myau.command.commands.ListCommand;
import myau.command.commands.ModuleCommand;
import myau.command.commands.PlayerCommand;
import myau.command.commands.ShowCommand;
import myau.command.commands.TargetCommand;
import myau.command.commands.ToggleCommand;
import myau.command.commands.VclipCommand;
import myau.config.Config;
import myau.event.EventManager;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.lag.api.EnumLagDirection;
import myau.lag.handler.UnifiedLagHandler;
import myau.management.BlinkManager;
import myau.management.DelayManager;
import myau.management.FloatManager;
import myau.management.FriendManager;
import myau.management.LagManager;
import myau.management.MovementFix;
import myau.management.PlayerStateManager;
import myau.management.RotationManager;
import myau.management.TargetManager;
import myau.module.Module;
import myau.module.ModuleManager;
import myau.module.modules.Accounts;
import myau.module.modules.AimAssist;
import myau.module.modules.AntiAFK;
import myau.module.modules.AntiBot;
import myau.module.modules.AntiDebuff;
import myau.module.modules.AntiFireball;
import myau.module.modules.AntiObbyTrap;
import myau.module.modules.AntiObfuscate;
import myau.module.modules.AntiVoid;
import myau.module.modules.AutoAnduril;
import myau.module.modules.AutoClicker;
import myau.module.modules.AutoHeadHitter;
import myau.module.modules.AutoHeal;
import myau.module.modules.AutoPot;
import myau.module.modules.AutoTool;
import myau.module.modules.Autoblock;
import myau.module.modules.Backtrack;
import myau.module.modules.BedDefender;
import myau.module.modules.BedESP;
import myau.module.modules.BedNuker;
import myau.module.modules.BedTracker;
import myau.module.modules.Blink;
import myau.module.modules.BlockCounter;
import myau.module.modules.BlockIn;
import myau.module.modules.BridgeAssist;
import myau.module.modules.Chams;
import myau.module.modules.ChestESP;
import myau.module.modules.ChestStealer;
import myau.module.modules.Clutch;
import myau.module.modules.CuteVisuals;
import myau.module.modules.Disabler;
import myau.module.modules.Displace;
import myau.module.modules.ESP;
import myau.module.modules.FakeLag;
import myau.module.modules.FallView;
import myau.module.modules.FastBreak;
import myau.module.modules.FastPlace;
import myau.module.modules.Fly;
import myau.module.modules.FreeLook;
import myau.module.modules.Freeze;
import myau.module.modules.FullBright;
import myau.module.modules.GhostHand;
import myau.module.modules.GodBridge;
import myau.module.modules.GuiModule;
import myau.module.modules.HUD;
import myau.module.modules.HitBox;
import myau.module.modules.HitSelect;
import myau.module.modules.Indicators;
import myau.module.modules.InvManager;
import myau.module.modules.InventoryClicker;
import myau.module.modules.InventoryMove;
import myau.module.modules.ItemESP;
import myau.module.modules.Jesus;
import myau.module.modules.KeepSprint;
import myau.module.modules.KillAura;
import myau.module.modules.KnockbackDelay;
import myau.module.modules.LadderClutch;
import myau.module.modules.LagRange;
import myau.module.modules.LegitScaffold;
import myau.module.modules.LightningTracker;
import myau.module.modules.LongJump;
import myau.module.modules.MCF;
import myau.module.modules.MoreKB;
import myau.module.modules.NameTags;
import myau.module.modules.NickHider;
import myau.module.modules.NoFall;
import myau.module.modules.NoHitDelay;
import myau.module.modules.NoHurtCam;
import myau.module.modules.NoJumpDelay;
import myau.module.modules.NoRotate;
import myau.module.modules.NoSlow;
import myau.module.modules.Radar;
import myau.module.modules.Reach;
import myau.module.modules.Refill;
import myau.module.modules.SafeWalk;
import myau.module.modules.Scaffold;
import myau.module.modules.Spammer;
import myau.module.modules.Speed;
import myau.module.modules.Sprint;
import myau.module.modules.Stasis;
import myau.module.modules.Stuck;
import myau.module.modules.TargetHUD;
import myau.module.modules.TargetStrafe;
import myau.module.modules.Telly;
import myau.module.modules.Theme;
import myau.module.modules.Timer;
import myau.module.modules.Tracers;
import myau.module.modules.Trajectories;
import myau.module.modules.UnInject;
import myau.module.modules.Velocity;
import myau.module.modules.ViewClip;
import myau.module.modules.Wtap;
import myau.module.modules.Xray;
import myau.property.Property;
import myau.property.PropertyManager;
import myau.ui.clickgui.ClickGuiScreen;
import myau.util.BadPacketsUtil;
import myau.util.MovementTicks;
import myau.util.ServerPing;
import net.minecraft.client.Minecraft;

public class Myau {
    public static String clientName = "&7[&bAnti&dImperialist&7]&r ";
    public static String version;
    public static RotationManager rotationManager;
    public static FloatManager floatManager;
    public static BlinkManager blinkManager;
    public static UnifiedLagHandler lagHandler;
    public static DelayManager delayManager;
    public static LagManager lagManager;
    public static PlayerStateManager playerStateManager;
    public static FriendManager friendManager;
    public static TargetManager targetManager;
    public static BotManager botManager;
    public static PropertyManager propertyManager;
    public static ModuleManager moduleManager;
    public static CommandManager commandManager;

    public Myau() {
        this.init();
    }

    public static synchronized void shutdown() {
        Minecraft mc = Minecraft.getMinecraft();
        if (moduleManager != null) {
            for (Module module : moduleManager.modules.values()) {
                if (module.isEnabled()) {
                    try {
                        module.setEnabled(false);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        try {
            if (blinkManager != null) {
                blinkManager.setBlinkState(false, blinkManager.getBlinkingModule());
            }
            if (delayManager != null) {
                delayManager.setDelayState(false, delayManager.getDelayModule());
            }
            if (lagManager != null) {
                lagManager.setDelay(0);
            }
            if (lagHandler != null) {
                lagHandler.releaseExpiredPackets(EnumLagDirection.INBOUND, 0L);
                lagHandler.releaseExpiredPackets(EnumLagDirection.OUTBOUND, 0L);
            }
            if (mc.thePlayer != null) {
                EventManager.call(new TickEvent(EventType.POST));
            }
        } catch (Throwable ignored) {
        }
        try {
            new Config("default", true).save();
        } catch (Throwable ignored) {
        }
        if (mc.currentScreen instanceof ClickGuiScreen) {
            mc.displayGuiScreen(null);
        }
        EventManager.clear();
    }

    public void init() {
        EventManager.clear();
        rotationManager = new RotationManager();
        floatManager = new FloatManager();
        blinkManager = new BlinkManager();
        lagHandler = new UnifiedLagHandler();
        delayManager = new DelayManager();
        lagManager = new LagManager();
        playerStateManager = new PlayerStateManager();
        friendManager = new FriendManager();
        targetManager = new TargetManager();
        botManager = new BotManager();
        propertyManager = new PropertyManager();
        moduleManager = new ModuleManager();
        commandManager = new CommandManager();
        EventManager.register(rotationManager);
        EventManager.register(floatManager);
        EventManager.register(blinkManager);
        EventManager.register(lagHandler);
        EventManager.register(delayManager);
        EventManager.register(lagManager);
        EventManager.register(new BadPacketsUtil());
        EventManager.register(new MovementTicks());
        EventManager.register(new ServerPing());
        EventManager.register(moduleManager);
        EventManager.register(commandManager);
        moduleManager.modules.put(AimAssist.class, new AimAssist());
        moduleManager.modules.put(AntiAFK.class, new AntiAFK());
        moduleManager.modules.put(AntiDebuff.class, new AntiDebuff());
        moduleManager.modules.put(AntiFireball.class, new AntiFireball());
        moduleManager.modules.put(AntiObbyTrap.class, new AntiObbyTrap());
        moduleManager.modules.put(AntiObfuscate.class, new AntiObfuscate());
        moduleManager.modules.put(AntiVoid.class, new AntiVoid());
        moduleManager.modules.put(AutoClicker.class, new AutoClicker());
        moduleManager.modules.put(AutoAnduril.class, new AutoAnduril());
        moduleManager.modules.put(AutoHeal.class, new AutoHeal());
        moduleManager.modules.put(AutoTool.class, new AutoTool());
        moduleManager.modules.put(BedNuker.class, new BedNuker());
        moduleManager.modules.put(BedESP.class, new BedESP());
        moduleManager.modules.put(BedTracker.class, new BedTracker());
        moduleManager.modules.put(BedDefender.class, new BedDefender());
        moduleManager.modules.put(CuteVisuals.class, new CuteVisuals());
        moduleManager.modules.put(Blink.class, new Blink());
        moduleManager.modules.put(FakeLag.class, new FakeLag());
        moduleManager.modules.put(Chams.class, new Chams());
        moduleManager.modules.put(ChestESP.class, new ChestESP());
        moduleManager.modules.put(ChestStealer.class, new ChestStealer());
        moduleManager.modules.put(LegitScaffold.class, new LegitScaffold());
        moduleManager.modules.put(ESP.class, new ESP());
        moduleManager.modules.put(BlockCounter.class, new BlockCounter());
        moduleManager.modules.put(FallView.class, new FallView());
        moduleManager.modules.put(FastPlace.class, new FastPlace());
        moduleManager.modules.put(FreeLook.class, new FreeLook());
        moduleManager.modules.put(Freeze.class, new Freeze());
        moduleManager.modules.put(Fly.class, new Fly());
        moduleManager.modules.put(FullBright.class, new FullBright());
        moduleManager.modules.put(GhostHand.class, new GhostHand());
        moduleManager.modules.put(GuiModule.class, new GuiModule());
        moduleManager.modules.put(Accounts.class, new Accounts());
        moduleManager.modules.put(Theme.class, new Theme());
        moduleManager.modules.put(FastBreak.class, new FastBreak());
        moduleManager.modules.put(AutoPot.class, new AutoPot());
        moduleManager.modules.put(AntiBot.class, new AntiBot());
        moduleManager.modules.put(Disabler.class, new Disabler());
        moduleManager.modules.put(HitSelect.class, new HitSelect());
        moduleManager.modules.put(HUD.class, new HUD());
        moduleManager.modules.put(MoreKB.class, new MoreKB());
        moduleManager.modules.put(Indicators.class, new Indicators());
        moduleManager.modules.put(InventoryClicker.class, new InventoryClicker());
        moduleManager.modules.put(InvManager.class, new InvManager());
        moduleManager.modules.put(ItemESP.class, new ItemESP());
        moduleManager.modules.put(Jesus.class, new Jesus());
        moduleManager.modules.put(KeepSprint.class, new KeepSprint());
        moduleManager.modules.put(HitBox.class, new HitBox());
        moduleManager.modules.put(Autoblock.class, new Autoblock());
        moduleManager.modules.put(Backtrack.class, new Backtrack());
        moduleManager.modules.put(Displace.class, new Displace());
        moduleManager.modules.put(Clutch.class, new Clutch());
        moduleManager.modules.put(GodBridge.class, new GodBridge());
        moduleManager.modules.put(BridgeAssist.class, new BridgeAssist());
        moduleManager.modules.put(UnInject.class, new UnInject());
        moduleManager.modules.put(KillAura.class, new KillAura());
        moduleManager.modules.put(Stuck.class, new Stuck());
        moduleManager.modules.put(Telly.class, new Telly());
        moduleManager.modules.put(AutoHeadHitter.class, new AutoHeadHitter());
        moduleManager.modules.put(LagRange.class, new LagRange());
        moduleManager.modules.put(KnockbackDelay.class, new KnockbackDelay());
        moduleManager.modules.put(LightningTracker.class, new LightningTracker());
        moduleManager.modules.put(LongJump.class, new LongJump());
        moduleManager.modules.put(MCF.class, new MCF());
        moduleManager.modules.put(NameTags.class, new NameTags());
        moduleManager.modules.put(NickHider.class, new NickHider());
        moduleManager.modules.put(NoFall.class, new NoFall());
        moduleManager.modules.put(NoHitDelay.class, new NoHitDelay());
        moduleManager.modules.put(NoHurtCam.class, new NoHurtCam());
        moduleManager.modules.put(NoJumpDelay.class, new NoJumpDelay());
        moduleManager.modules.put(NoRotate.class, new NoRotate());
        moduleManager.modules.put(NoSlow.class, new NoSlow());
        moduleManager.modules.put(Radar.class, new Radar());
        moduleManager.modules.put(Reach.class, new Reach());
        moduleManager.modules.put(Refill.class, new Refill());
        moduleManager.modules.put(SafeWalk.class, new SafeWalk());
        moduleManager.modules.put(Scaffold.class, new Scaffold());
        moduleManager.modules.put(BlockIn.class, new BlockIn());
        moduleManager.modules.put(Spammer.class, new Spammer());
        moduleManager.modules.put(Speed.class, new Speed());
        moduleManager.modules.put(LadderClutch.class, new LadderClutch());
        moduleManager.modules.put(Sprint.class, new Sprint());
        moduleManager.modules.put(Stasis.class, new Stasis());
        moduleManager.modules.put(Timer.class, new Timer());
        moduleManager.modules.put(InventoryMove.class, new InventoryMove());
        moduleManager.modules.put(TargetHUD.class, new TargetHUD());
        moduleManager.modules.put(TargetStrafe.class, new TargetStrafe());
        moduleManager.modules.put(Tracers.class, new Tracers());
        moduleManager.modules.put(Trajectories.class, new Trajectories());
        moduleManager.modules.put(Velocity.class, new Velocity());
        moduleManager.modules.put(ViewClip.class, new ViewClip());
        moduleManager.modules.put(Wtap.class, new Wtap());
        moduleManager.modules.put(Xray.class, new Xray());
        commandManager.commands.add(new BindCommand());
        commandManager.commands.add(new ConfigCommand());
        commandManager.commands.add(new DenickCommand());
        commandManager.commands.add(new FriendCommand());
        commandManager.commands.add(new HelpCommand());
        commandManager.commands.add(new HideCommand());
        commandManager.commands.add(new IgnCommand());
        commandManager.commands.add(new ItemCommand());
        commandManager.commands.add(new ListCommand());
        commandManager.commands.add(new ModuleCommand());
        commandManager.commands.add(new PlayerCommand());
        commandManager.commands.add(new ShowCommand());
        commandManager.commands.add(new TargetCommand());
        commandManager.commands.add(new ToggleCommand());
        commandManager.commands.add(new VclipCommand());
        for (Module module : moduleManager.modules.values()) {
            ArrayList<Property<?>> properties = new ArrayList<>();
            for (final Field field : module.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                final Object obj;
                try {
                    obj = field.get(module);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                if (obj instanceof Property<?>) {
                    ((Property<?>) obj).setOwner(module);
                    properties.add((Property<?>) obj);
                }
            }
            propertyManager.properties.put(module.getClass(), properties);
            EventManager.register(module);
        }
        EventManager.register(new MovementFix());
        Config config = new Config("default", true);
        if (config.file.exists()) {
            config.load();
        }
        if (friendManager.file.exists()) {
            friendManager.load();
        }
        if (targetManager.file.exists()) {
            targetManager.load();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(config::save));

        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(Myau.class.getResourceAsStream("/version.json")), StandardCharsets.UTF_8)) {
            JsonObject modInfo = new JsonParser().parse(reader).getAsJsonObject();
            version = modInfo.get("version").getAsString();
        } catch (Exception e) {
            version = "dev";
        }

        AccountManager.init();
    }
}
