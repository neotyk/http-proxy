package net.kungfoo.grizzly.proxy.impl;

import com.sun.grizzly.http.SelectorThread;
import net.kungfoo.grizzly.proxy.SimpleHttpProxy;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Activator implements BundleActivator {
  public static void main(String[] args) throws IOException {
    setup();
    try {
      startReactor();
      startSelector();
    } catch (Exception e) {
      SimpleHttpProxy.logger.log(Level.SEVERE, "Exception in Selector: ", e);
    } finally {
      stopSelector();
      stopReactor();
    }
  }

  /** {@inheritDoc} */
  public void start(BundleContext bundleContext) throws Exception {
    setup();
    try {
      startReactor();
    } catch (IOReactorException e) {
      SimpleHttpProxy.logger.log(Level.SEVERE, "Exception in reactor: ", e);
      SimpleHttpProxy.logger.throwing(SelectorThread.class.getCanonicalName(), "Reactor setup", e);
      throw e;
    }
    try {
      startSelector();
    } catch (Exception e) {
      SimpleHttpProxy.logger.log(Level.SEVERE, "Exception in Selector: ", e);
      SimpleHttpProxy.logger.throwing(SelectorThread.class.getCanonicalName(), "listen", e);
      throw e;
    }
  }

  private static void startReactor() throws IOReactorException {
    HttpParams params = new BasicHttpParams();
    params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
        .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000)
        .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
        .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
        .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
        .setParameter(CoreProtocolPNames.USER_AGENT, "BaseHttpProxy/0.1");
    ioReactor = new DefaultConnectingIOReactor(2, params);
    httpProxy.setConnectingIOReactor(ioReactor);
  }

  private static void startSelector() throws IOException, InstantiationException {
    selectorThread.listen();
  }

  /** {@inheritDoc} */
  public void stop(BundleContext bundleContext) throws Exception {
    stopSelector();
    stopReactor();
  }

  private static void stopSelector() {
    if (selectorThread.isRunning()) {
      selectorThread.stopEndpoint();
    }
  }

  private static void stopReactor() throws IOException {
    System.out.println("Shutting down I/O reactor");
    ioReactor.shutdown();
    System.out.println("Done");
  }

  private static void setup() {
    selectorThread = new SelectorThread();
    selectorThread.setPort(8282);
    httpProxy = new SimpleHttpProxy();
    selectorThread.setAdapter(httpProxy);
    SimpleHttpProxy.logger.setLevel(Level.FINEST);
    ConsoleHandler consoleHandler = new ConsoleHandler();
    SimpleHttpProxy.logger.addHandler(consoleHandler);
    SimpleHttpProxy.logger.log(Level.FINE, "Setup done.");
  }

  private static SimpleHttpProxy httpProxy;
  private static SelectorThread selectorThread;
  private static ConnectingIOReactor ioReactor;
}
