package classloader;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.api.SyntaxError;
import rocks.spaghetti.classloader.patch.CompiledPatch;
import rocks.spaghetti.classloader.patch.PatchCompileException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PatchLoader {
    public List<CompiledPatch> scanPatches() {
        File[] jars = Config.PATCH_DIR.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) {
            jars = new File[0];
        }

        Log.log("Found " + jars.length + " potential patch jars");
        ClassLoader loader = this.getClass().getClassLoader();

        for (File file : jars) {
            try {
                injectUrl(file.toURI().toURL(), loader);
            } catch (MalformedURLException e) {
                Log.log(e);
            }
        }

        List<CompiledPatch> patches = new ArrayList<>();
        for (File file : jars) {
            Log.log("Scanning jar: " + file);

            try (JarFile jar = new JarFile(file)) {
                JarEntry entry = jar.getJarEntry("META-INF/patch.json5");
                if (entry != null) {
                    Log.log(file + " contains a patch manifest");

                    String content = new String(getBytes(jar.getInputStream(jar.getEntry(entry.getName()))));
                    Jankson jankson = Jankson.builder().build();
                    PatchManifest manifest = jankson.fromJson(jankson.load(content), PatchManifest.class);

                    for (String className : manifest.patches) {
                        byte[] classBytes = getBytes(jar.getInputStream(jar.getEntry(Util.qualifiedToClass(className))));
                        Log.log("Loading patch: " + className);
                        CompiledPatch patch = new CompiledPatch(classBytes);
                        Log.log("Patch target: " + patch.getTarget());
                        patches.add(patch);
                    }
                }
            } catch (IOException | PatchCompileException | SyntaxError e) {
                Log.log("Error scanning jar: " + e.getMessage());
                Log.log(e);
            }
        }

        return patches;
    }

    private static byte[] getBytes(InputStream stream) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buf = new byte[0xFFFF];
            for (int len; (len = stream.read(buf)) != -1;) {
                os.write(buf, 0, len);
            }
            os.flush();
            return os.toByteArray();
        }
    }

    private static List<String> getClassFilesInJar(File jar) {
        List<String> classes = new ArrayList<>();
        try (JarFile jarfile = new JarFile(jar)) {
            Enumeration<JarEntry> files = jarfile.entries();
            while (files.hasMoreElements()) {
                String name = files.nextElement().getName();
                if (name.endsWith(".class")) {
                    classes.add(Util.classToQualified(name));
                }
            }
        } catch (IOException e) {
            Log.log(e);
        }
        return classes;
    }

    private static boolean injectUrl(URL url, ClassLoader loader) {
        Method injectUrl;
        try {
            injectUrl = loader.getClass().getDeclaredMethod("injectUrl", URL.class);
            injectUrl.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Log.log(e);
            return false;
        }

        Boolean b;
        try {
            b = (Boolean) injectUrl.invoke(loader, url);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Log.log(e);
            return false;
        }

        return b;
    }

    private static class PatchManifest {
        public String[] patches;
    }
}
