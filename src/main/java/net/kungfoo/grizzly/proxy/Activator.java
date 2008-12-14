package net.kungfoo.grizzly.proxy;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.Adapter;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;

public class Activator implements BundleActivator {
    /**
     * {@inheritDoc}
     */
    public void start(BundleContext bundleContext) throws Exception {
        setup();
        try {
            startSelector();
        } catch (Exception e) {
            SimpleHttpProxy.logger.log(Level.SEVERE, "Exception in Selector: ", e);
            SimpleHttpProxy.logger.throwing(SelectorThread.class.getCanonicalName(), "{init,start}Endpoint", e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext bundleContext) throws Exception {
        stopSelector();
    }

    public static void main(String[] args) throws IOException {
        setup();
        try {
            startSelector();
        } catch (Exception e) {
            SimpleHttpProxy.logger.log(Level.SEVERE, "Exception in Selector: ", e);
        } finally {
            stopSelector();
        }
    }

    private static SelectorThread selectorThread;

    private static void startSelector() throws IOException, InstantiationException {
        selectorThread.initEndpoint();
        selectorThread.startEndpoint();
    }

    private static Adapter setup() {
        selectorThread = new SelectorThread();
        selectorThread.setPort(8282);
        Adapter httpProxy = new SimpleHttpProxy();
        selectorThread.setAdapter(httpProxy);
        SimpleHttpProxy.logger.setLevel(Level.FINEST);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINEST);
        SimpleHttpProxy.logger.addHandler(consoleHandler);
        SimpleHttpProxy.logger.log(Level.FINE, "Setup done.");
        return httpProxy;
    }

    private static void stopSelector() {
        if (selectorThread.isRunning()) {
            selectorThread.stopEndpoint();
        }
    }
}
