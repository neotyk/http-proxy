package net.kungfoo.grizzly.proxy.impl;

import org.apache.http.nio.protocol.NHttpRequestExecutionHandler;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntityTemplate;
import org.apache.http.nio.entity.ContentListener;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpHost;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHttpRequest;

import java.io.IOException;

/**
 * NIO Http Request Execution Handler.
 *
 * @author Hubert Iwaniuk
 */
public class MyNHttpRequestExecutionHandler implements NHttpRequestExecutionHandler {

  private final static String REQUEST_SENT = "request-sent";
  private final static String RESPONSE_RECEIVED = "response-received";

  @Override
  public void initalizeContext(HttpContext context, Object attachment) {
    HttpHost targetHost = (HttpHost) attachment;
    context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
  }

  @Override
  public void finalizeContext(HttpContext context) {
    Object flag = context.getAttribute(RESPONSE_RECEIVED);
    if (flag == null) {
      // Signal completion of the request execution
      // TODO: requestCount.countDown();
    }
  }

  @Override
  public HttpRequest submitRequest(HttpContext context) {
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

  @Override
  public void handleResponse(HttpResponse response, HttpContext context) throws IOException {
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
    // TODO: requestCount.countDown();
  }

  @Override
  public ConsumingNHttpEntity responseEntity(HttpResponse response, HttpContext context) throws IOException {
    return new ConsumingNHttpEntityTemplate(response.getEntity(), new ContentListener() {
      @Override
      public void contentAvailable(ContentDecoder decoder, IOControl ioctrl) throws IOException {
        //To change body of implemented methods use File | Settings | File Templates.
      }

      @Override
      public void finished() {
        //To change body of implemented methods use File | Settings | File Templates.
      }
    }) {

    };
  }
}
