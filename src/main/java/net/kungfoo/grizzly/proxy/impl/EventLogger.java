package net.kungfoo.grizzly.proxy.impl;

import org.apache.http.nio.protocol.EventListener;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.HttpException;

import java.io.IOException;

/**
 * based on http components nio client.
 */
public class EventLogger implements EventListener {

  public void connectionOpen(final NHttpConnection conn) {
    System.out.println("Connection open: " + conn);
  }

  public void connectionTimeout(final NHttpConnection conn) {
    System.out.println("Connection timed out: " + conn);
  }

  public void connectionClosed(final NHttpConnection conn) {
    System.out.println("Connection closed: " + conn);
  }

  public void fatalIOException(final IOException ex, final NHttpConnection conn) {
    System.err.println("I/O error: " + ex.getMessage());
  }

  public void fatalProtocolException(final HttpException ex, final NHttpConnection conn) {
    System.err.println("HTTP error: " + ex.getMessage());
  }

}
