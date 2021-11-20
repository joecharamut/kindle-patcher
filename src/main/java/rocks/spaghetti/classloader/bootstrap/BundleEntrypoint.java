package rocks.spaghetti.classloader.bootstrap;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.File;

@SuppressWarnings("unused")
public class BundleEntrypoint implements BundleActivator {
    private static final String STARTED_FILE_PATH = "/var/run/patcher.dat";
    private static final File STARTED_FILE = new File(STARTED_FILE_PATH);

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        if (!STARTED_FILE.exists() && STARTED_FILE.createNewFile()) {
            PatchingClassLoader.inject();
            new Stage2().run();
        }
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception { }
}
