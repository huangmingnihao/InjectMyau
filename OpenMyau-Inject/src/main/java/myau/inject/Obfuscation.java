package myau.inject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

final class Obfuscation {
    enum Naming {
        SRG,
        NOTCH,
        MCP,
        INTERMEDIARY
    }
    private static final Map<String, String> CLASSES = new HashMap<String, String>();
    private static final Map<String, String> METHODS_EXACT = new HashMap<String, String>();
    private static final Map<String, String> FIELDS_EXACT = new HashMap<String, String>();
    private static final Map<String, String> METHODS_FLAT = new HashMap<String, String>();
    private static final Map<String, String> FIELDS_FLAT = new HashMap<String, String>();
    private static final Map<String, String> METHODS_BY_SRG = new HashMap<String, String>();
    private static final Map<String, String> FIELDS_BY_NAME = new HashMap<String, String>();
    private static final java.util.Set<String> MISSES = new java.util.LinkedHashSet<String>();
    private static boolean prefixNamed;
    private static boolean prefixUnmapped;
    private static volatile Naming naming;
    private static volatile Remapper remapper;
    private static volatile boolean prepared;
    private Obfuscation() {
    }
    static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        resolveNaming();
        if (naming != Naming.SRG) {
            buildRemapper();
        }
    }
    static Naming naming() {
        Naming resolved = naming;
        if (resolved != null) {
            return resolved;
        }
        prepare();
        return naming;
    }

    static java.util.List<String> misses() {
        return new ArrayList<String>(MISSES);
    }
    private static void resolveNaming() {
        ClassLoader loader = Obfuscation.class.getClassLoader();
        Class<?> minecraft = null;
        try {
            minecraft = Class.forName("net.minecraft.client.Minecraft", false, loader);
        } catch (Throwable absent) {
            Log.swallowed(absent);
        }
        // Calamus keeps the real class names for some classes and renames the members
        // to m_/f_ ids, so a Minecraft class that exists but has no runTick is a calamus
        // game, not a named one. Probe for that before trusting the class name.
        boolean calamus = intermediaryGamePresent(loader);
        if (calamus && minecraft != null
                && !has(minecraft, "runTick") && !has(minecraft, "func_71410_x")) {
            naming = Naming.INTERMEDIARY;
            Log.line("the game is named by calamus (Ornithe/Fabric), members are m_/f_ ids"
                    + " -- client classes will be remapped as they load"
                    + " [unmapped prefix: " + prefixUnmapped + ", named: " + prefixNamed + "]");
            return;
        }
        if (minecraft == null) {
            if (calamus) {
                naming = Naming.INTERMEDIARY;
                Log.line("the game is named by calamus (Ornithe/Fabric)"
                        + " -- client classes will be remapped as they load"
                        + " [unmapped prefix: " + prefixUnmapped + ", named: " + prefixNamed + "]");
                return;
            }
            naming = Naming.NOTCH;
            Log.line("the game is obfuscated -- client classes will be remapped as they load");
            return;
        }
        naming = has(minecraft, "func_71410_x") ? Naming.SRG : Naming.MCP;
        Log.line(naming == Naming.SRG
                ? "the game uses SRG members -- no remapping needed"
                : "the game is fully named -- client classes will be remapped as they load");
    }
    /**
     * Ornithe keeps two kinds of class names in its mapping set: bare ids such as
     * {@code net/minecraft/C_1234567} and real names such as
     * {@code net/minecraft/client/Minecraft}. Depending on the build, the runtime
     * either wraps them in an {@code unmapped/} package or leaves them alone,
     * and the two kinds are not necessarily treated the same. Probe both instead
     * of assuming.
     */
    private static boolean intermediaryGamePresent(ClassLoader loader) {
        Map<String, String> table = classTable();
        if (table == null) {
            return false;
        }
        String minecraft = table.get("net/minecraft/client/Minecraft");
        if (minecraft == null) {
            return false;
        }
        if (probe(unmapped(minecraft), loader)) {
            prefixNamed = true;
        } else if (probe(minecraft, loader)) {
            prefixNamed = false;
        } else {
            return false;
        }
        String screen = table.get("net/minecraft/client/gui/GuiScreen");
        if (screen != null) {
            prefixUnmapped = probe(unmapped(screen), loader);
        }
        return true;
    }
    private static boolean probe(String internalName, ClassLoader loader) {
        try {
            Class.forName(internalName.replace('/', '.'), false, loader);
            return true;
        } catch (Throwable absent) {
            return false;
        }
    }
    private static String unmapped(String name) {
        String root = "net/minecraft/";
        String prefix = root + "unmapped/";
        if (name.startsWith(prefix)) {
            return name;
        }
        return name.startsWith(root) ? prefix + name.substring(root.length()) : name;
    }
    static String mapCalamus(String name) {
        if (name.startsWith("net/minecraft/unmapped/")) {
            return name;
        }
        boolean prefix = name.startsWith("net/minecraft/C_") ? prefixUnmapped : prefixNamed;
        return prefix ? unmapped(name) : name;
    }
    private static Map<String, String> classTable() {
        Map<String, String> table = new HashMap<String, String>();
        try {
            InputStream in = Obfuscation.class.getResourceAsStream("/myau_intermediary.tsv");
            if (in == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, "UTF-8"), 1 << 14);
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t");
                    if (parts.length >= 3 && "C".equals(parts[0])) {
                        table.put(parts[1], parts[2]);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable absent) {
            Log.swallowed(absent);
            return null;
        }
        return table;
    }
    private static boolean has(Class<?> owner, String method) {
        try {
            owner.getDeclaredMethod(method);
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }
    static boolean needsRemapping() {
        return naming() != Naming.SRG;
    }
    static byte[] remap(String internalName, byte[] classfile) {
        try {
            Remapper active = remapper();
            if (active == null) {
                return classfile;
            }
            ClassReader reader = new ClassReader(classfile);
            OWN_SUPERS.put(internalName, reader.getSuperName());
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassRemapper(writer, active), 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            StackTraceElement[] frames = t.getStackTrace();
            Log.line("remap failed for " + internalName + ": " + t
                    + (frames.length > 0 ? " at " + frames[0] : ""));
            return classfile;
        }
    }

    private static final Map<String, String> OWN_SUPERS = new HashMap<String, String>();
    private static int overridesRenamed;

    static String overrides() {
        return overridesRenamed + " overrides renamed";
    }

    /** Resolves the calamus id a game ancestor declares this member under. */
    private static String overrideId(String owner, String name, String descriptor) {
        String parent = OWN_SUPERS.get(owner);
        int guard = 0;
        while (parent != null && guard++ < 16) {
            if (CLASSES.containsKey(parent)) {
                String id = METHODS_EXACT.get(parent + "\t" + name + "\t" + descriptor);
                if (id != null) {
                    overridesRenamed++;
                    return id;
                }
                return null;
            }
            parent = OWN_SUPERS.get(parent);
        }
        return null;
    }
    private static Remapper remapper() {
        return remapper;
    }

    private static void buildRemapper() {
        String table = naming == Naming.NOTCH ? "/myau_obf.tsv"
                : naming == Naming.INTERMEDIARY ? "/myau_intermediary.tsv"
                : "/myau_mcp.tsv";
        if (!load(table)) {
            return;
        }
        if (naming == Naming.INTERMEDIARY) {
            // the main table is keyed by srg names (func_71411_s); our own source
            // uses mcp names (runTick), so the named aliases have to be in here too
            load("/myau_intermediary_named.tsv");
            for (Map.Entry<String, String> entry : CLASSES.entrySet()) {
                entry.setValue(mapCalamus(entry.getValue()));
            }
        }
        remapper = new Remapper() {
            @Override
            public String map(String internalName) {
                String mapped = CLASSES.get(internalName);
                return mapped == null ? internalName : mapped;
            }
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {

                if (isSrgName(name)) {
                    String override = METHODS_FLAT.get(name + "\t" + descriptor);
                    if (override != null) {
                        return override;
                    }
                }
                if (!isGameClass(owner)) {
                    // our own class may still override a game method; such a
                    // declaration has to take the very id of the class that
                    // declares it, else the JVM sees two unrelated methods
                    String inherited = overrideId(owner, name, descriptor);
                    return inherited == null ? name : inherited;
                }
                String exact = METHODS_EXACT.get(owner + "\t" + name + "\t" + descriptor);
                if (exact != null) {
                    return exact;
                }
                String flat = METHODS_FLAT.get(name + "\t" + descriptor);
                if (flat == null && isSrgName(name)) {
                    flat = METHODS_BY_SRG.get(name);
                }
                if (flat == null) {
                    // a calamus call site needs the id of the class that declares
                    // the member; when the table has no row for this owner the
                    // name would reach the game unchanged, so record it
                    MISSES.add("method " + owner + "." + name + descriptor);
                }
                return flat == null ? name : flat;
            }
            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                if (isSrgName(name)) {
                    String inherited = FIELDS_FLAT.get(name);
                    if (inherited != null) {
                        return inherited;
                    }
                }
                if (!isGameClass(owner)) {
                    return name;
                }
                String exact = FIELDS_EXACT.get(owner + "\t" + name);
                if (exact != null) {
                    return exact;
                }
                String flat = FIELDS_FLAT.get(name);
                if (flat == null && isSrgName(name)) {
                    flat = FIELDS_BY_NAME.get(name);
                }
                return flat == null ? name : flat;
            }
        };
        Log.line("mapping table " + table + ": " + CLASSES.size() + " classes, "
                + METHODS_EXACT.size() + " methods, " + FIELDS_EXACT.size() + " fields");
    }
    static String mapDescriptor(String mcpDescriptor) {
        if (mcpDescriptor == null || CLASSES.isEmpty()) {
            return mcpDescriptor;
        }
        StringBuilder out = new StringBuilder(mcpDescriptor.length());
        int i = 0;
        while (i < mcpDescriptor.length()) {
            char c = mcpDescriptor.charAt(i);
            if (c != 'L') {
                out.append(c);
                i++;
                continue;
            }
            int end = mcpDescriptor.indexOf(';', i);
            if (end < 0) {
                out.append(mcpDescriptor.substring(i));
                break;
            }
            String internal = mcpDescriptor.substring(i + 1, end);
            String mapped = CLASSES.get(internal);
            out.append('L').append(mapped == null ? internal : mapped).append(';');
            i = end + 1;
        }
        return out.toString();
    }
    private static boolean isSrgName(String name) {
        return name.startsWith("func_") || name.startsWith("field_");
    }
    private static boolean isGameClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || CLASSES.containsKey(internalName);
    }
    private static boolean load(String resource) {
        InputStream in = Obfuscation.class.getResourceAsStream(resource);
        if (in == null) {
            Log.line(resource + " is missing from the jar -- cannot remap");
            return false;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 1 << 16);
            Map<String, String> methodSeen = new HashMap<String, String>();
            Map<String, String> fieldSeen = new HashMap<String, String>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    continue;
                }
                if ("C".equals(parts[0])) {
                    CLASSES.put(parts[1], parts[2]);
                } else if ("M".equals(parts[0]) && parts.length == 5) {
                    METHODS_EXACT.put(parts[1] + "\t" + parts[2] + "\t" + parts[3], parts[4]);
                    // calamus hands one id out per signature, so a call made
                    // through a subclass (EntityPlayerSP.isUsingItem, declared on
                    // EntityLivingBase) can be resolved from any class that has
                    // the same srg name
                    if (!METHODS_BY_SRG.containsKey(parts[2])) {
                        METHODS_BY_SRG.put(parts[2], parts[4]);
                    }
                    remember(methodSeen, METHODS_FLAT, parts[2] + "\t" + parts[3], parts[4]);
                } else if ("F".equals(parts[0]) && parts.length == 4) {
                    FIELDS_EXACT.put(parts[1] + "\t" + parts[2], parts[3]);
                    if (!FIELDS_BY_NAME.containsKey(parts[2])) {
                        FIELDS_BY_NAME.put(parts[2], parts[3]);
                    }
                    remember(fieldSeen, FIELDS_FLAT, parts[2], parts[3]);
                }
            }
            return true;
        } catch (Throwable t) {
            Log.line("could not read " + resource + ": " + t);
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable swallowed) {
            Log.swallowed(swallowed);
                }
            }
        }
    }
    private static void remember(Map<String, String> seen, Map<String, String> flat,
                                 String key, String value) {
        String previous = seen.put(key, value);
        if (previous == null) {
            flat.put(key, value);
        } else if (!previous.equals(value)) {
            flat.remove(key);
        }
    }
}
