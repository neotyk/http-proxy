package net.kungfoo.grizzly.proxy.impl;

import org.apache.http.nio.protocol.NHttpRequestExecutionHandler;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntityTemplate;
import org.apache.http.nio.entity.ContentListener;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.*;
import org.apache.http.util.EntityUtils;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.http.Cookies;
import com.sun.grizzly.util.http.ServerCookie;

/**
 * NIO Http Request Execution Handler.
 *
 * @author Hubert Iwaniuk
 */
public class MyNHttpRequestExecutionHandler implements NHttpRequestExecutionHandler {

  private final static String REQUEST_SENT = "request-sent";
  private final static String RESPONSE_RECEIVED = "response-received";
  private final static String REQUEST_RESPONSE_HOLDER = "request-response-holder";

  @Override
  public void initalizeContext(HttpContext context, Object attachment) {
    final ReqRespHolder holder = (ReqRespHolder) attachment;
    HttpHost targetHost = new HttpHost(holder.getRequest().serverName().toString());
    context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, targetHost);
    context.setAttribute(REQUEST_RESPONSE_HOLDER, holder);
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
    ReqRespHolder holder = (ReqRespHolder) context.getAttribute(REQUEST_RESPONSE_HOLDER);
    Object flag = context.getAttribute(REQUEST_SENT);
    if (flag == null) {
      // Stick some object into the context
      context.setAttribute(REQUEST_SENT, Boolean.TRUE);

      System.out.println("--------------");
      System.out.println("Sending request to " + targetHost);
      System.out.println("--------------");

      final Request request = holder.getRequest();
      final BasicHttpRequest httpRequest = new BasicHttpRequest(request.method().toString(), request.unparsedURI().toString());
      httpRequest.setHeaders(convertHeaders(request.getMimeHeaders(), request.getCookies()));
      return httpRequest;
    } else {
      // No new request to submit
      return null;
    }
  }

  private static Header[] convertHeaders(MimeHeaders mimeHeaders, Cookies cookies) {
    final Enumeration enumeration = mimeHeaders.names();
    List<Header> list = new ArrayList<Header>(12);
    while (enumeration.hasMoreElements()) {
      String name = (String) enumeration.nextElement();
      //noinspection ObjectAllocationInLoop
      list.add(new BasicHeader(name, mimeHeaders.getValue(name).toString()));
    }
    if (cookies != null) {
      StringBuilder builder = new StringBuilder(128);
      final int count = cookies.getCookieCount();
      if (count > 0) {
      for (int i=0; i < count; i++) {
        final ServerCookie cookie = cookies.getCookie(i);
        builder.append(cookie.getName().toString()).append('=').append(cookie.getValue().toString());
        if (i < count -1){
          builder.append("; ");
        }
      }
      list.add(new BasicHeader("cookie", builder.toString()));
      }
    }
    return list.toArray(new Header[list.size()]);
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
