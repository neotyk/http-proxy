package net.kungfoo.grizzly.proxy.impl;

import org.apache.http.nio.protocol.HttpRequestExecutionHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHttpRequest;

import java.util.concurrent.CountDownLatch;
import java.io.IOException;

/**
 * Based on http components nio client.
 */
public class MyHttpRequestExecutionHandler implements HttpRequestExecutionHandler {

  private final static String REQUEST_SENT = "request-sent";
  private final static String RESPONSE_RECEIVED = "response-received";

  private final CountDownLatch requestCount;

  public MyHttpRequestExecutionHandler(final CountDownLatch requestCount) {
    super();
    this.requestCount = requestCount;
  }

  public void initalizeContext(final HttpContext context, final Object attachment) {
    HttpHost targetHost = (HttpHost) attachment;
    context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
  }

  public void finalizeContext(final HttpContext context) {
    Object flag = context.getAttribute(RESPONSE_RECEIVED);
    if (flag == null) {
      // Signal completion of the request execution
      requestCount.countDown();
    }
  }

  public HttpRequest submitRequest(final HttpContext context) {
    HttpHost targetHost = (HttpHost) context.getAttribute(
        ExecutionContext.HTTP_TARGET_HOST);
    Object flag = context.getAttribute(REQUEST_SENT);
    if (flag == null) {
      // Stick some object into the context
      context.setAttribute(REQUEST_SENT, Boolean.TRUE);

      System.out.println("--------------");
      System.out.println("Sending request to " + targetHost);
      System.out.println("--------------");

      return new BasicHttpRequest("GET", "/");
    } else {
      // No new request to submit
      return null;
    }
  }

  public void handleResponse(final HttpResponse response, final HttpContext context) {
    HttpEntity entity = response.getEntity();
    try {
      String content = EntityUtils.toString(entity);

      System.out.println("--------------");
      System.out.println(response.getStatusLine());
      System.out.println("--------------");
      System.out.println("Document length: " + content.length());
      System.out.println("--------------");
    } catch (IOException ex) {
      System.err.println("I/O error: " + ex.getMessage());
    }

    context.setAttribute(RESPONSE_RECEIVED, Boolean.TRUE);

    // Signal completion of the request execution
    requestCount.countDown();
  }

}
