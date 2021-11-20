package rocks.spaghetti.classloader.bootstrap;

import rocks.spaghetti.classloader.patch.CompiledPatch;
import sun.misc.Resource;
import sun.misc.URLClassPath;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.*;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class PatchingClassLoader extends URLClassLoader {
    public static PatchingClassLoader inject() throws Exception {
        ClassLoader target = PatchingClassLoader.class.getClassLoader();
        if (!(target instanceof URLClassLoader)) {
            throw new IllegalStateException();
        }

        ClassLoader parent = target.getParent();
        if (parent instanceof PatchingClassLoader) {
            return (PatchingClassLoader) parent;
        }

        URL[] urls = ((URLClassLoader) target).getURLs();

        PatchingClassLoader patchLoader = new PatchingClassLoader(urls, parent, target);
        replaceParent(target, patchLoader);

        System.out.println("loader 1: " + target);
        System.out.println("parent 1: " + target.getParent());
        return patchLoader;
    }

    private static void replaceParent(ClassLoader target, ClassLoader newParent) throws NoSuchFieldException, IllegalAccessException {
        Field f = ClassLoader.class.getDeclaredField("parent");
        f.setAccessible(true);
        f.set(target, newParent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Package> getLoadedPackages(ClassLoader loader) {
        try {
            Field f = ClassLoader.class.getDeclaredField("packages");
            f.setAccessible(true);
            return (Map<String, Package>) f.get(loader);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i == -1) return null;
        return className.substring(0, i);
    }

    private static boolean isSealed(String name, Manifest manifest) {
        String path = name.replace('.', '/').concat("/");

        Attributes attrs = manifest.getAttributes(path);
        if (attrs == null) {
            attrs = manifest.getMainAttributes();
        }

        return Boolean.parseBoolean(attrs.getValue(Attributes.Name.SEALED));
    }

    private final URLClassPath classPath;
    private final AccessControlContext accessContext;
    private final Map<String, Package> alreadyLoadedPackages;

    private PatchingClassLoader(URL[] urls, ClassLoader parent, ClassLoader child) {
        super(urls, parent);
        classPath = new URLClassPath(urls);
        accessContext = AccessController.getContext();
        alreadyLoadedPackages = getLoadedPackages(child);
        init();
    }

    private void init() {
        System.out.println("");
        System.out.println("Log start timestamp: " + new Date());
        System.out.println("Bootstrap OK, PatchingClassLoader instantiated");
        System.out.println("   Packages still handled by original ClassLoader:");
        for (String packageName : alreadyLoadedPackages.keySet()) {
            System.out.println("   - " + packageName);
        }
        System.out.println("");
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>) () -> {
                String path = name.replace('.', '/').concat(".class");
                Resource res = classPath.getResource(path, false);
                if (res != null) {
                    try {
                        return defineClass(name, res);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                } else {
                    throw new ClassNotFoundException(name);
                }
            }, accessContext);
        } catch (PrivilegedActionException e) {
            throw (ClassNotFoundException) e.getException();
        }
    }

    @Override
    protected void addURL(URL url) {
        classPath.addURL(url);
        super.addURL(url);
    }

    private Class<?> defineClass(String name, Resource res) throws IOException {
        URL url = res.getCodeSourceURL();
        String packageName = getPackageName(name);
        if (packageName != null) {
            defineOrVerifyPackage(res, url, packageName);
        }
        return defineClass(name, res, url);
    }

    private void defineOrVerifyPackage(Resource res, URL url, String packageName) throws IOException {
        Manifest manifest = res.getManifest();
        Package pkg = getPackage(packageName);
        if (pkg != null) {
            verifyPackageSecurity(url, packageName, pkg, manifest);
        } else {
            definePackage(url, packageName, manifest);
        }
    }

    private void verifyPackageSecurity(URL url, String packageName, Package pkg, Manifest manifest) {
        // Package found, so check package sealing.
        if (pkg.isSealed()) {
            // Verify that code source URL is the same.
            if (!pkg.isSealed(url)) {
                throw new SecurityException("sealing violation: package " + packageName + " is sealed");
            }
        } else {
            // Make sure we are not attempting to seal the package at this code source URL.
            if ((manifest != null) && isSealed(packageName, manifest)) {
                throw new SecurityException("sealing violation: can't seal package " + packageName + ": already loaded");
            }
        }
    }

    private void definePackage(URL url, String packageName, Manifest manifest) {
        if (manifest != null) {
            definePackage(packageName, manifest, url);
        } else {
            definePackage(packageName, null, null, null, null, null, null, null);
        }
    }

    private Class<?> defineClass(String name, Resource res, URL url) throws IOException {
        byte[] classBytes = res.getBytes();

        if (shouldTransform(name)) {
            classBytes = patches.get(name).apply(classBytes);
        }

        Certificate[] certs = res.getCertificates();
        CodeSource source = new CodeSource(url, certs);
        return defineClass(name, classBytes, 0, classBytes.length, source);
    }

    private boolean shouldTransform(String className) {
//        System.out.println("query: " + className);
        return patches.containsKey(className);
    }

    public boolean injectUrl(URL url) {
        System.out.println("adding url to classpath: " + url);
        URL[] before = getURLs();
        addURL(url);
        URL[] after = getURLs();
        return before.length + 1 == after.length;
    }

    private Map<String, CompiledPatch> patches = new HashMap<>();
    public void injectPatches(CompiledPatch[] newPatches) {
        for (CompiledPatch p : newPatches) {
            patches.put(p.getTarget(), p);
            System.out.println("Added patch for: " + p.getTarget());
        }
    }
}
