package myau.util;

import myau.inject.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

/** Optional notifications for attacks that do not pass through the vanilla controller. */
public final class MeowtilsCompat {
    private static final String ATTACK_EVENT = "wtf.tatp.meowtils.event.AttackEntityEvent";
    private static final String EVENT_MANAGER = "wtf.tatp.meowtils.event.api.EventManager";
    private static final long RETRY_INTERVAL_MS = 5000L;
    private static volatile AttackDispatcher dispatcher;
    private static volatile long nextLookup;
    private static boolean reported;

    private MeowtilsCompat() {
    }

    public static void onAttack(Object player, Object target) {
        if (player == null || target == null) {
            return;
        }
        try {
            AttackDispatcher active = dispatcher;
            if (active == null) {
                active = resolve(player, target);
            }
            if (active != null) {
                active.post.invoke(null, active.constructor.newInstance(player, target));
            }
        } catch (Throwable failure) {
            // A missing/incompatible optional mod must not interrupt the attack itself.
            dispatcher = null;
            nextLookup = System.currentTimeMillis() + RETRY_INTERVAL_MS;
            report(failure);
        }
    }

    private static synchronized AttackDispatcher resolve(Object player, Object target) {
        if (dispatcher != null) {
            return dispatcher;
        }
        long now = System.currentTimeMillis();
        if (now < nextLookup) {
            return null;
        }
        nextLookup = now + RETRY_INTERVAL_MS;
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(player.getClass().getClassLoader());
        loaders.add(MeowtilsCompat.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> event = Class.forName(ATTACK_EVENT, false, loader);
                Class<?> manager = Class.forName(EVENT_MANAGER, false, event.getClassLoader());
                Constructor<?> constructor = null;
                for (Constructor<?> candidate : event.getConstructors()) {
                    Class<?>[] arguments = candidate.getParameterTypes();
                    if (arguments.length == 2 && arguments[0].isInstance(player)
                            && arguments[1].isInstance(target)) {
                        constructor = candidate;
                        break;
                    }
                }
                if (constructor == null) {
                    throw new NoSuchMethodException(ATTACK_EVENT + "(player, target)");
                }
                Method post = manager.getMethod("post", event.getSuperclass());
                if (!Modifier.isStatic(post.getModifiers())) {
                    throw new NoSuchMethodException(EVENT_MANAGER + ".post must be static");
                }
                dispatcher = new AttackDispatcher(constructor, post);
                Log.line("Meowtils attack compatibility enabled");
                return dispatcher;
            } catch (ClassNotFoundException absent) {
                // Try the game's other existing loader; do not create another copy of the mod.
            } catch (Throwable failure) {
                report(failure);
            }
        }
        return null;
    }

    private static synchronized void report(Throwable failure) {
        if (!reported) {
            reported = true;
            Log.line("Meowtils attack compatibility failed: " + failure);
        }
    }

    private static final class AttackDispatcher {
        private final Constructor<?> constructor;
        private final Method post;

        private AttackDispatcher(Constructor<?> constructor, Method post) {
            this.constructor = constructor;
            this.post = post;
        }
    }
}
