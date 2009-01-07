/**
 * Copyright 2009 Hubert Iwaniuk <neotyk@kungfoo.pl>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.kungfoo.grizzly.proxy.impl;

import com.sun.grizzly.arp.*;
import com.sun.grizzly.http.DefaultProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.*;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

public class Activator implements BundleActivator {
  private static DefaultClientIOEventDispatch connectingEventDispatch;
  private static ConnectingIOReactor connectingIOReactor;

  public static void main(String[] args) throws IOReactorException {
    setup();
    try {
      startSelector();
      startReactor();
      selectorThread.join();
    } catch (Exception e) {
      ProxyAdapter.logger.log(Level.SEVERE, "Exception in Selector: ", e);
    } finally {
      stopSelector();
    }
  }

  /** {@inheritDoc} */
  public void start(BundleContext bundleContext) throws Exception {
    setup();
    try {
      startSelector();
      startReactor();
    } catch (Exception e) {
      ProxyAdapter.logger.log(Level.SEVERE, "Exception in Selector: ", e);
      ProxyAdapter.logger.throwing(SelectorThread.class.getCanonicalName(), "listen", e);
      throw e;
    }
  }

  private static void setup() throws IOReactorException {
    setupClient();

    selectorThread = new SelectorThread();
    selectorThread.setPort(8282);
    ProxyAdapter httpProxy = new ProxyAdapter(connectingIOReactor);
    DefaultAsyncHandler handler = new DefaultAsyncHandler();
    handler.addAsyncFilter(new AsyncFilter() {
      @Override public boolean doFilter(AsyncExecutor asyncExecutor) {
        final AsyncTask asyncTask = asyncExecutor.getAsyncTask();
        final AsyncHandler asyncHandler = asyncExecutor.getAsyncHandler();
        DefaultProcessorTask task = (DefaultProcessorTask) asyncExecutor.getProcessorTask();
        task.getRequest().setAttribute(ProxyAdapter.CALLBACK_KEY,
            new Runnable() {
              @Override public void run() {
                asyncHandler.handle(asyncTask);
              }
            });
        task.invokeAdapter();
        return false;
      }
    });
    selectorThread.setAsyncHandler(handler);
    selectorThread.setAdapter(httpProxy);
    selectorThread.setEnableAsyncExecution(true);
    selectorThread.setDisplayConfiguration(true);

    ProxyAdapter.logger.setLevel(Level.FINEST);
    ConsoleHandler consoleHandler = new ConsoleHandler();
    ProxyAdapter.logger.addHandler(consoleHandler);

    ProxyAdapter.logger.log(Level.FINE, "Setup done.");
  }

  private static void setupClient() throws IOReactorException {
    HttpParams params = new BasicHttpParams();
    params
      .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
      .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
      .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
      .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);

    connectingIOReactor = new DefaultConnectingIOReactor(1, params);

    BasicHttpProcessor originServerProc = new BasicHttpProcessor();
//    originServerProc.addInterceptor(new RequestContent());
    originServerProc.addInterceptor(new RequestTargetHost());
    originServerProc.addInterceptor(new RequestConnControl());
    originServerProc.addInterceptor(new RequestUserAgent());
    originServerProc.addInterceptor(new RequestExpectContinue());

    NHttpClientHandler connectingHandler = new ConnectingHandler(
      originServerProc,
      new DefaultConnectionReuseStrategy(),
      params);

    connectingEventDispatch = new DefaultClientIOEventDispatch(connectingHandler, params);
  }

  private static void startSelector() throws IOException, InstantiationException {
    selectorThread.listen();
  }

  private static void startReactor() {
    Thread t = new Thread(new Runnable() {
      @SuppressWarnings({"UseOfSystemOutOrSystemErr"})
      public void run() {
        try {
          connectingIOReactor.execute(connectingEventDispatch);
        } catch (InterruptedIOException ex) {
          System.err.println("Interrupted");
        } catch (IOException e) {
          System.err.println("I/O error: " + e.getMessage());
          e.printStackTrace(System.err);
        }
      }
    });
    t.start();
  }

  /** {@inheritDoc} */
  public void stop(BundleContext bundleContext) throws Exception {
    stopSelector();
  }

  private static void stopSelector() {
    if (selectorThread.isRunning()) {
      selectorThread.stopEndpoint();
    }
  }

  private static SelectorThread selectorThread;
}
