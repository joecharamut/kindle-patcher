package classloader;

import rocks.spaghetti.classloader.patch.CompiledPatch;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static rocks.spaghetti.classloader.Log.log;

public class PatcherMain {
    private static final String DISABLE_FILE_PATH = "/mnt/us/DISABLE_PATCHER";
    private static final File DISABLE_FILE = new File(DISABLE_FILE_PATH);

    public static void main() {
        log("Patcher main says: Hello, World!");

        boolean enabled = true;
        if (DISABLE_FILE.exists()) {
            enabled = false;
            log("/mnt/us/ENABLE_PATCHER is present, disabling");
        }

        if (!enabled) {
            return;
        }

        log("Patcher enabled");

        if (!Config.DATA_DIR.exists() && !Config.DATA_DIR.mkdirs()) {
            log("Error creating DATA_DIR");
        }

        if (!Config.PATCH_DIR.exists() && !Config.PATCH_DIR.mkdirs()) {
            log("Error creating PATCH_DIR");
        }

        log(Config.getConfig());

        log("Scanning for patches...");

        PatchLoader patchLoader = new PatchLoader();
        List<CompiledPatch> patches = patchLoader.scanPatches();
        log("Scan finished");

        log("Injecting " + patches.size() + " patches");
        injectPatches(patches.toArray(new CompiledPatch[0]), PatcherMain.class.getClassLoader());
        log("Done");
    }

    private static void injectPatches(CompiledPatch[] patches, ClassLoader loader) {
        Method inject;
        try {
            inject = loader.getClass().getDeclaredMethod("injectPatches", CompiledPatch[].class);
            inject.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Log.log(e);
            return;
        }

        try {
            inject.invoke(loader, (Object) patches);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.log(e);
            return;
        }
    }
}
