package net.kungfoo.grizzly.proxy;

import com.sun.grizzly.tcp.Adapter;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Parameters;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import static java.text.MessageFormat.format;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple HTTP Proxy.
 * <p/>
 * Uses HTTPCommons to proxy requests.
 * @author Hubert Iwaniuk
 */
public class SimpleHttpProxy implements Adapter {
  private static HttpClient httpClient = new HttpClient(
      new MultiThreadedHttpConnectionManager());
  private static final String TRACE = "TRACE";
  private static final String GET = "GET";
  private static final String DELETE = "DELETE";
  private static final String HEAD = "HEAD";
  private static final String OPTIONS = "OPTIONS";
  private static final String POST = "POST";
  private static final String PUT = "PUT";

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
        "Via",
        request.protocol() + " antares");// TODO hostname, and Via from response

    String targetHost = request.serverName().toString();
    int targetPort = request.getServerPort();

    httpClient.getHostConfiguration()
        .setHost(targetHost, targetPort, request.scheme().toString());

    HttpMethod httpMethod = createMethod(request);
    httpMethod.setFollowRedirects(false);
    int responseCode = httpClient.executeMethod(httpMethod);
    response.setStatus(responseCode);
    populateHeaders(httpMethod.getResponseHeaders(), response);
    transfer(
        httpMethod.getResponseBodyAsStream(), response.getOutputBuffer(),
        response);
  }

  public void afterService(Request req, Response res) throws Exception {
    req.recycle();
    res.recycle();
  }

  private static void transfer(
      InputStream responseBodyAsStream, OutputBuffer outputBuffer,
      Response response) throws IOException {
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
              Level.FINEST, MessageFormat
              .format(
              "Received Start\n{0}Received End", new String(bc.getBuffer())));
        }
      }
    } while (read != -1);
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

  /**
   * Create HTTP method request.
   *
   * @param request Original reqeuest to build target request from.
   *
   * @return Method ready to be executed.
   *
   * @throws URIException This really should not happen, since we've been called
   *                      with this URI.
   */
  private static HttpMethod createMethod(Request request) throws URIException {
    HttpMethod method = null;

    String uri = request.unparsedURI().toString();
    String methodName = request.method().toString();
    if (logger.isLoggable(Level.FINER)) {
      logger.log(
          Level.FINER, MessageFormat.format(
          "Creating reqeuest for method: {0}, URI: {1}", methodName, uri));
    }

    if (TRACE.equalsIgnoreCase(methodName)) {
      method = new TraceMethod(uri);
    } else if (GET.equalsIgnoreCase(methodName)) {
      method = new GetMethod(uri);
    } else if (DELETE.equalsIgnoreCase(methodName)) {
      method = new DeleteMethod(uri);
    } else if (HEAD.equalsIgnoreCase(methodName)) {
      method = new HeadMethod(uri);
    } else if (OPTIONS.equalsIgnoreCase(methodName)) {
      method = new OptionsMethod(uri);
    } else if (POST.equalsIgnoreCase(methodName)) {
      PostMethod postMethod = new PostMethod(uri);
      postMethod.setRequestBody(converParameters(request.getParameters()));
      method = postMethod;
    } else if (PUT.equalsIgnoreCase(methodName)) {
      method = new PutMethod(uri);
    } else if (ConnectMethod.NAME.equalsIgnoreCase(methodName)) {
      method = new ConnectMethod();
      method.setURI(new URI(uri, true));
    }
    //request.
    return method;
  }

  private static NameValuePair[] converParameters(Parameters parameters) {
    List<NameValuePair> result = new ArrayList<NameValuePair>(
        parameters.size());
    Enumeration parameterNames = parameters.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String name = (String) parameterNames.nextElement();
      result
          .add(new NameValuePair(name, parameters.getUndecodedParameter(name)));
    }
    return result.toArray(new NameValuePair[result.size()]);
  }

  private static boolean maxForwards(
      Request request, Response response, MessageBytes method)
      throws IOException {
    // Max-Forwards (14.32)
    if (OPTIONS.equalsIgnoreCase(method.toString()) || TRACE
        .equalsIgnoreCase(method.toString())) {
      String maxFrwds = request.getHeader(HttpHeader.Max_Forwards.toString());
      if (maxFrwds != null && !maxFrwds.trim().isEmpty()) {
        int forwards = Integer.parseInt(maxFrwds);
        if (forwards == 0) {
          // last allowed
          // Exectation failed
          response.setStatus(417);
          response.setHeader(
              HttpHeader.Expect.toString(), "Expected to have " + HttpHeader
              .Max_Forwards + " > 0. Can't forward.");
          response.sendHeaders();
          return true;
        } else {
          // update Max-Forwards
          response.setHeader(
              HttpHeader.Max_Forwards.toString(), String.valueOf(forwards - 1));
        }
      }
    }
    return false;
  }

  /** logger */
  public static Logger logger = Logger.getLogger("httpproxy");
}
