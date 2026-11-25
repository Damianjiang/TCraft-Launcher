package tcraft;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/// Runs the Forge or NeoForge installer in-process so the installer can use a download mirror.
///
/// The installer exposes a private `mirror` field and a processor list, but those types are only
/// present inside the installer jar. Reflection keeps this bridge independent of the installer
/// version while still letting us set the mirror and skip processors whose files are prefetched.
public final class ForgeInstallerBridge {

    private static final String FORGE_PACKAGE = "net.minecraftforge.installer";
    private static final String NEOFORGE_PACKAGE = "net.neoforged.installer";
    private static final String MOJMAPS_TASK = "DOWNLOAD_MOJMAPS";

    private ForgeInstallerBridge() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ForgeInstallerBridge <game-dir> <installer-jar> [mirror-url] [skip-mojmaps]");
            System.exit(2);
        }

        File gameDir = new File(args[0]);
        File installer = new File(args[1]);
        String mirrorUrl = args.length > 2 ? args[2].trim() : "";
        boolean skipMojmaps = args.length > 3 && "1".equals(args[3].trim());

        try {
            run(gameDir, installer, mirrorUrl, skipMojmaps);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            cause.printStackTrace();
            System.exit(2);
        }
    }

    private static void run(File gameDir, File installer, String mirrorUrl, boolean skipMojmaps)
            throws Exception {
        String pkg = findPackage();
        Class<?> simpleInstaller = Class.forName(pkg + ".SimpleInstaller");
        setStaticFieldIfPresent(simpleInstaller, "headless", Boolean.TRUE);

        Class<?> util = Class.forName(pkg + ".json.Util");
        Object install = util.getMethod("loadInstallProfile").invoke(null);

        if (!mirrorUrl.isEmpty()) {
            Class<?> mirrorClass = Class.forName(pkg + ".json.Mirror");
            Object mirror = mirrorClass.getDeclaredConstructor().newInstance();
            setField(mirror, "name", "bmclapi");
            setField(mirror, "url", mirrorUrl);
            setField(install, "mirror", mirror);
        }

        if (skipMojmaps) {
            removeProcessor(install, MOJMAPS_TASK);
        }

        Class<?> callbackClass = Class.forName(pkg + ".actions.ProgressCallback");
        Object callback = callbackClass.getMethod("withOutputs", OutputStream[].class)
                .invoke(null, (Object) new OutputStream[]{System.out});

        Class<?> clientClass = Class.forName(pkg + ".actions.ClientInstall");
        Object client = newClientInstall(clientClass, install, callback);
        Object result = runClientInstall(clientClass, client, gameDir, installer);
        boolean ok = result instanceof Boolean value && value;
        System.out.println("forge-install-result=" + ok);
        if (!ok) {
            System.exit(1);
        }
    }

    private static String findPackage() throws ClassNotFoundException {
        try {
            Class.forName(FORGE_PACKAGE + ".SimpleInstaller");
            return FORGE_PACKAGE;
        } catch (ClassNotFoundException ignored) {
            Class.forName(NEOFORGE_PACKAGE + ".SimpleInstaller");
            return NEOFORGE_PACKAGE;
        }
    }

    private static Object newClientInstall(Class<?> clientClass, Object install, Object callback)
            throws Exception {
        for (Constructor<?> constructor : clientClass.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length != 2 || !types[0].isInstance(install) || !types[1].isInstance(callback)) {
                continue;
            }
            return constructor.newInstance(install, callback);
        }
        throw new NoSuchMethodException("ClientInstall constructor for " + install.getClass().getName());
    }

    private static Object runClientInstall(Class<?> clientClass, Object client, File gameDir,
                                            File installer) throws Exception {
        Predicate<String> acceptAll = ignored -> true;
        for (Method method : clientClass.getMethods()) {
            if (!"run".equals(method.getName())) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 2 && types[0] == File.class && types[1] == File.class) {
                return method.invoke(client, gameDir, installer);
            }
            if (types.length == 2 && types[0] == File.class && types[1] == Predicate.class) {
                return method.invoke(client, gameDir, acceptAll);
            }
            if (types.length == 3 && types[0] == File.class && types[1] == Predicate.class
                    && types[2] == File.class) {
                return method.invoke(client, gameDir, acceptAll, installer);
            }
        }
        throw new NoSuchMethodException("ClientInstall.run for " + clientClass.getName());
    }

    private static void removeProcessor(Object install, String taskName) throws Exception {
        Field processorsField = findField(install.getClass(), "processors");
        if (processorsField == null) {
            return;
        }
        processorsField.setAccessible(true);
        Object value = processorsField.get(install);
        if (!(value instanceof List<?> processors)) {
            return;
        }

        List<Object> kept = new ArrayList<>();
        for (Object processor : processors) {
            if (!isProcessorTask(processor, taskName)) {
                kept.add(processor);
            }
        }
        processorsField.set(install, kept);
    }

    private static boolean isProcessorTask(Object processor, String taskName) {
        try {
            Method getArgs = processor.getClass().getMethod("getArgs");
            Object value = getArgs.invoke(processor);
            return value instanceof String[] args && Arrays.asList(args).contains(taskName);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void setStaticFieldIfPresent(Class<?> owner, String name, Object value)
            throws IllegalAccessException {
        try {
            Field field = owner.getField(name);
            field.set(null, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
