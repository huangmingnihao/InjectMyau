package myau.init;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class FMLLoadingPlugin implements IMixinConfigPlugin {
    private static final List<FMLLoadingPlugin> mixinPlugins = new ArrayList<>();
    private String mixinPackage;
    private List<String> mixins = null;

    public static List<FMLLoadingPlugin> getMixinPlugins() {
        return mixinPlugins;
    }

    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
        mixinPlugins.add(this);
    }

    public URL getBaseUrlForClassUrl(URL classUrl) {
        String string = classUrl.toString();
        if (classUrl.getProtocol().equals("jar")) {
            try {
                return new URL(string.substring(4).split("!")[0]);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else if (string.endsWith(".class")) {
            try {
                return new URL(string.replace("\\", "/").replace(this.getClass().getCanonicalName().replace(".", "/") + ".class", ""));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        } else {
            return classUrl;
        }
    }

    public String getMixinPackage() {
        return this.mixinPackage;
    }

    public String getMixinBaseDir() {
        return this.mixinPackage.replace(".", "/");
    }

    public void tryAddMixinClass(String className) {
        String norm = (className.endsWith(".class") ? className.substring(0, className.length() - ".class".length()) : className).replace("\\", "/").replace("/", ".");
        if (norm.startsWith(this.getMixinPackage() + ".") && !norm.endsWith(".")) {
            this.mixins.add(norm.substring(this.getMixinPackage().length() + 1));
        }
    }

    public List<String> getMixins() {
        if (this.mixins != null) {
            return this.mixins;
        } else {
            this.mixins = new ArrayList<>();
            URL classUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
            System.out.println("Found classes at " + classUrl);
            Path file;
            try {
                file = Paths.get(this.getBaseUrlForClassUrl(classUrl).toURI());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Base directory found at " + file);
            if (Files.isDirectory(file)) {
                this.walkDir(file);
            } else {
                this.walkJar(file);
            }
            System.out.println("Found mixins: " + this.mixins);
            return this.mixins;
        }
    }

    private void walkDir(Path classRoot) {
        System.out.println("Trying to find mixins from directory");
        try (Stream<Path> classes = Files.walk(classRoot.resolve(this.getMixinBaseDir()))) {
            classes.map((it) -> classRoot.relativize(it).toString()).forEach(this::tryAddMixinClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void walkJar(Path file) {
        System.out.println("Trying to find mixins from jar file");
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry next;
            while ((next = zis.getNextEntry()) != null) {
                this.tryAddMixinClass(next.getName());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Another freelook provider owns the camera redirects (freelook / PolyFox etc.).
        // Mixin resolves @Redirect conflicts by priority, and the losing config fails
        // its require check, which crashes startup. Defer to the installed mod instead.
        if (mixinClassName != null && mixinClassName.startsWith("myau.mixin.MixinFreeLook")) {
            boolean foreign = foreignFreeLookProvider();
            myau.inject.Diag.log("shouldApplyMixin " + mixinClassName + " foreignFreelook=" + foreign);
            return !foreign;
        }
        return true;
    }

    private static Boolean foreignFreeLook;

    private static boolean foreignFreeLookProvider() {
        if (foreignFreeLook != null) {
            return foreignFreeLook;
        }
        boolean found = false;
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            for (String id : new String[]{"freelook", "freelookmod", "polyfox-freelook", "polyfox"}) {
                Object loaded = fabricLoader.getMethod("isModLoaded", String.class).invoke(instance, id);
                if (loaded instanceof Boolean && (Boolean) loaded) {
                    found = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        if (!found) {
            try {
                Class.forName("org.codeberg.chromatic.freelook.handler.FreelookHandler", false,
                        FMLLoadingPlugin.class.getClassLoader());
                found = true;
            } catch (Throwable ignored) {
            }
        }
        if (!found) {
            found = jarNamedLikeFreelookInModsDir();
        }
        foreignFreeLook = found;
        return found;
    }

    private static boolean jarNamedLikeFreelookInModsDir() {
        for (String dir : new String[]{"mods", System.getProperty("fabric.modsFolder", "")}) {
            if (dir == null || dir.isEmpty()) {
                continue;
            }
            try {
                java.io.File[] files = new java.io.File(dir).listFiles();
                if (files == null) {
                    continue;
                }
                for (java.io.File file : files) {
                    String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                    if (name.endsWith(".jar") && name.contains("freelook")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }
}
