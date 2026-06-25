package io.github.htanwar922.eclipse.clangformat;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator. Holds the singleton instance used to reach the
 * preference store from the rest of the plug-in.
 */
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "io.github.htanwar922.eclipse.clangformat";

    private static Activator plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return plugin;
    }
}
