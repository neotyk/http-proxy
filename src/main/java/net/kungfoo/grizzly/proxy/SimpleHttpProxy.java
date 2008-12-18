package net.kungfoo.grizzly.proxy;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import net.kungfoo.grizzly.proxy.impl.EventLogger;
import static net.kungfoo.grizzly.proxy.impl.HttpHeader.*;
import static net.kungfoo.grizzly.proxy.impl.HttpMethodName.OPTIONS;
import static net.kungfoo.grizzly.proxy.impl.HttpMethodName.TRACE;
import net.kungfoo.grizzly.proxy.impl.MyHttpRequestExecutionHandler;
import net.kungfoo.grizzly.proxy.impl.MySessionRequestCallback;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.protocol.BufferingHttpClientHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import static java.text.MessageFormat.format;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple HTTP Proxy.
 * <p/>
 * Uses HTTPCommons to proxy requests.
 *
 * @author Hubert Iwaniuk
 */
public class SimpleHttpProxy implements Adapter {

  private HttpParams params;

  public SimpleHttpProxy() {
    params = new BasicHttpParams();
    params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
        .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 10000)
        .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
        .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
        .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
        .setParameter(CoreProtocolPNames.USER_AGENT, "BaseHttpProxy/0.1");
  }

  public void service(Request request, Response response) throws Exception {
    String uri = request.requestURI().toString();

    final MessageBytes method = request.method();
    if (logger.isLoggable(Level.FINE)) {
      logger.log(
          Level.FINE,
          format("Incomming request. URI: {0}, Method: {1}", uri, method));
    }
    if (maxForwards(request, response, method))
      return;

    // handle "Via"
    response.setHeader(
        Via.name(),
        request.protocol() + " antares");// TODO hostname, and Via from response

    String targetHost = request.serverName().toString();
    int targetPort = request.getServerPort();



    // code pasted
    final ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(2, params);

    BasicHttpProcessor httpproc = new BasicHttpProcessor();
    httpproc.addInterceptor(new RequestContent());
    httpproc.addInterceptor(new RequestTargetHost());
    httpproc.addInterceptor(new RequestConnControl());
    httpproc.addInterceptor(new RequestUserAgent());
    httpproc.addInterceptor(new RequestExpectContinue());

    // We are going to use this object to synchronize between the
    // I/O event and main threads
    CountDownLatch requestCount = new CountDownLatch(3);

    BufferingHttpClientHandler handler = new BufferingHttpClientHandler(
            httpproc,
            new MyHttpRequestExecutionHandler(requestCount),
            new DefaultConnectionReuseStrategy(),
            params);

    handler.setEventListener(new EventLogger());

    final IOEventDispatch ioEventDispatch = new DefaultClientIOEventDispatch(handler, params);

    Thread t = new Thread(new Runnable() {

        public void run() {
            try {
                ioReactor.execute(ioEventDispatch);
            } catch (InterruptedIOException ex) {
                System.err.println("Interrupted");
            } catch (IOException e) {
                System.err.println("I/O error: " + e.getMessage());
            }
            System.out.println("Shutdown");
        }

    });
    t.start();

    // TODO: this could use tcp selector
    SessionRequest[] reqs = new SessionRequest[1];
    reqs[0] = ioReactor.connect(
            new InetSocketAddress(targetHost, targetPort),
            null,
            new HttpHost("www.yahoo.com"),
            new MySessionRequestCallback(requestCount));
    reqs[1] = ioReactor.connect(
            new InetSocketAddress("www.google.com", 80),
            null,
            new HttpHost("www.google.ch"),
            new MySessionRequestCallback(requestCount));
    reqs[2] = ioReactor.connect(
            new InetSocketAddress("www.apache.org", 80),
            null,
            new HttpHost("www.apache.org"),
            new MySessionRequestCallback(requestCount));

    // Block until all connections signal
    // completion of the request execution
    requestCount.await();

    System.out.println("Shutting down I/O reactor");

    ioReactor.shutdown();

    System.out.println("Done");

  }

  public void afterService(Request req, Response res) throws Exception {
    req.recycle();
    res.recycle();
  }

  private static void transfer(
      InputStream responseBodyAsStream, OutputBuffer outputBuffer,
      Response response) throws IOException {
    if (responseBodyAsStream != null) {
      byte[] buf = new byte[8192];
      int read;
      do {
        ByteChunk bc = new ByteChunk();
        read = responseBodyAsStream.read(buf);
        if (read != -1) {
          bc.append(buf, 0, read);
          outputBuffer.doWrite(bc, response);
          if (logger.isLoggable(Level.FINEST)) {
            logger.log(
                Level.FINEST,
                MessageFormat.format(
                    "Received Start\n{0}\nReceived End",
                    new String(bc.getBuffer())));
          }
        }
      } while (read != -1);
      responseBodyAsStream.close();
    }
    response.finish();
  }

  private void populateHeaders(Header[] responseHeaders, Response response) {
    if (logger.isLoggable(Level.FINEST)) {
      logger.entering(
          this.getClass().getName(), "populateHeaders",
          new Object[]{responseHeaders, response});
    }
    for (Header responseHeader : responseHeaders) {
      if (logger.isLoggable(Level.FINEST)) {
        logger.log(
            Level.FINEST, MessageFormat.format(
            "Populating header name: {0}, value: {1}", responseHeader.getName(),
            responseHeader.getValue()));
      }
      response.setHeader(responseHeader.getName(), responseHeader.getValue());
    }
  }

  private static boolean maxForwards(
      Request request, Response response, MessageBytes method)
      throws IOException {
    // Max-Forwards (14.32)
    if (OPTIONS.equalsIgnoreCase(method.toString())
        || TRACE.equalsIgnoreCase(method.toString())) {
      String maxFrwds = request.getHeader(Max_Forwards.toString());
      if (maxFrwds != null && !maxFrwds.trim().isEmpty()) {
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
