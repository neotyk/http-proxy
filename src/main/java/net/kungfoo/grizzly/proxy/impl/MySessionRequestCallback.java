package net.kungfoo.grizzly.proxy.impl;

import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.nio.reactor.SessionRequest;

import java.util.concurrent.CountDownLatch;

/**
 * based on http componenets nio client.
 */
public class MySessionRequestCallback implements SessionRequestCallback {

  private final CountDownLatch requestCount;

  public MySessionRequestCallback(final CountDownLatch requestCount) {
    super();
    this.requestCount = requestCount;
  }

  public void cancelled(final SessionRequest request) {
    this.requestCount.countDown();
  }

  public void completed(final SessionRequest request) {
    this.requestCount.countDown();
  }

  public void failed(final SessionRequest request) {
    this.requestCount.countDown();
  }

  public void timeout(final SessionRequest request) {
    this.requestCount.countDown();
  }

}
