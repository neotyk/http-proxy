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

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import static net.kungfoo.grizzly.proxy.impl.HttpHeader.*;
import static net.kungfoo.grizzly.proxy.impl.HttpMethodName.OPTIONS;
import static net.kungfoo.grizzly.proxy.impl.HttpMethodName.TRACE;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.protocol.HTTP;

import java.io.IOException;
import java.net.InetSocketAddress;
import static java.text.MessageFormat.format;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple HTTP Proxy Adapter.
 * <p/>
 * Uses HTTPCommponents to proxy requests.
 *
 * @author Hubert Iwaniuk
 */
public class ProxyAdapter implements Adapter {

  private final ConnectingIOReactor connectingIOReactor;
  static final String CALLBACK_KEY = "proxy.callback";

  public ProxyAdapter(ConnectingIOReactor connectingIOReactor) {
    this.connectingIOReactor = connectingIOReactor;
  }

  /**
   * {@inheritDoc}
   */
  public void service(Request request, Response response) throws Exception {
    String uri = request.unparsedURI().toString();

    final MessageBytes method = request.method();
    logURIAndMethod(uri, method);

    if (maxForwards(request, response, method))
      return;

    String targetHost = request.serverName().toString();
    int targetPort = request.getServerPort();

    ProxyProcessingInfo proxyTask = new ProxyProcessingInfo();

    // TODO: think of it.
    synchronized (proxyTask) {

      // from connected

      // Initialize connection state
      proxyTask.setTarget(new HttpHost(targetHost, targetPort));
      proxyTask.setRequest(convert(method.getString(), uri, request));
      proxyTask.setOriginalRequest(request);
      Runnable completion = (Runnable) request.getAttribute(CALLBACK_KEY);
      proxyTask.setCompletion(completion);
      proxyTask.setResponse(response);

      InetSocketAddress address = new InetSocketAddress(targetHost, targetPort);

      if (!IOReactorStatus.ACTIVE.equals(connectingIOReactor.getStatus())) {
        System.err.println("Connecting reactor not running.");
        response.setStatus(500);
        response.setMessage("Internal Booo");
        // complete request.
        ExecutorService executorService = Executors.newFixedThreadPool(1, new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            return new Thread(r, "EmergencyService");  //To change body of implemented methods use File | Settings | File Templates.
          }
        });
        executorService.submit(completion);
        return;
      } else {
        connectingIOReactor.connect(address, null, proxyTask, null);
      }
      
      // from requestReceived
      try {
        System.out.println(request + " [client->proxy] >> " + request.unparsedURI().toString());

        // Update connection state
        proxyTask.setClientState(ConnState.REQUEST_RECEIVED);

        if (request.getContentLength() != 0) {
          proxyTask.setClientState(ConnState.REQUEST_BODY_DONE);
        }
        // See if the client expects a 100-Continue
        if (isExpectContinue(request)) {
          response.setStatus(HttpStatus.SC_CONTINUE);
          response.sendHeaders();
        }
      } catch (IOException ignore) {
        System.out.println("err " + ignore.getMessage());
      }
    }

    // handle "Via", TODO: should go after we have headers from target server.
    response.setHeader(
      Via.name(),
      request.protocol() + " antares");// TODO hostname, and Via from response

  }

  private static HttpRequest convert(String method, String uri, Request request) {
    HttpRequest req;
    final int len = request.getContentLength();
    if (len > 0) {
      req = new BasicHttpEntityEnclosingRequest(method, uri);
      final BasicHttpEntity httpEntity = new BasicHttpEntity();
      httpEntity.setContentLength(len);
//      httpEntity.setContent(((InternalInputBuffer) request.getInputBuffer()).getInputStream());
      ((BasicHttpEntityEnclosingRequest) req).setEntity(httpEntity);
    } else {
      req = new BasicHttpRequest(method, uri);
    }
    final MimeHeaders mimeHeaders = request.getMimeHeaders();
    final Enumeration names = mimeHeaders.names();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      req.addHeader(name, mimeHeaders.getHeader(name));
    }
    return req;
  }

  private static boolean isExpectContinue(Request request) {
    String expect = request.getHeader(HTTP.EXPECT_DIRECTIVE);
    return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect);

  }

  private static void logURIAndMethod(String uri, MessageBytes method) {
    if (logger.isLoggable(Level.FINE)) {
      logger.log(
        Level.FINE,
        format("Incomming request. URI: {0}, Method: {1}", uri, method));
    }
  }

  public void afterService(Request req, Response res) throws Exception {
    req.action(ActionCode.ACTION_POST_REQUEST, null);
  }

  /**
   * Process Max-Forwards header.
   * <p/>
   * As specified in HTTP Protocol: Max-Forwards (14.32)
   *
   * @param request  Request to analyze.
   * @param response Response to write to.
   * @param method   HTTP Method name, Max-Forwards works only on OPTIONS and TRACE methods.
   * @return <code>true</code> if Max-Forwards blocked from processing request (this ends processing of this reqeuest),
   *         else <code>false</code>.
   * @throws IOException If failed to send headers to client.
   */
  private static boolean maxForwards(Request request, Response response, MessageBytes method) throws IOException {
    final String met = method.toString();
    if (OPTIONS.equalsIgnoreCase(met) || TRACE.equalsIgnoreCase(met)) {
      String maxFrwds = request.getHeader(Max_Forwards.toString());
      if (maxFrwds != null && !maxFrwds.trim().isEmpty()) {
        // TODO: would be nice to check for NFE here
        int forwards = Integer.parseInt(maxFrwds);
        if (forwards == 0) {
          // last allowed
          // Exectation failed
          response.setStatus(417);
          response.setHeader(
            Expect.toString(), "Expected to have " +
            Max_Forwards + " > 0. Can't forward.");
          response.sendHeaders();
          return true;
        } else {
          // update Max-Forwards
          response.setHeader(
            Max_Forwards.toString(), String.valueOf(forwards - 1));
        }
      }
    }
    return false;
  }

  /**
   * logger
   */
  public static Logger logger = Logger.getLogger("httpproxy");
}
