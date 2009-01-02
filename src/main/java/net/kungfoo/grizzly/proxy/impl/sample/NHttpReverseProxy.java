/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package net.kungfoo.grizzly.proxy.impl.sample;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;

import org.apache.http.HttpHost;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultClientIOEventDispatch;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;

@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class NHttpReverseProxy {

  public static void main(String[] args) throws Exception {

    if (args.length < 1) {
      System.out.println("Usage: NHttpReverseProxy <hostname> [port]");
      System.exit(1);
    }
    String hostname = args[0];
    int port = 80;
    if (args.length > 1) {
      port = Integer.parseInt(args[1]);
    }

    // Target host
    HttpHost targetHost = new HttpHost(hostname, port);

    HttpParams params = new BasicHttpParams();
    params
      .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
      .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
      .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
      .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
      .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpComponents/1.1")
      .setParameter(CoreProtocolPNames.USER_AGENT, "HttpComponents/1.1");

    final ConnectingIOReactor connectingIOReactor = new DefaultConnectingIOReactor(
      1, params);

    final ListeningIOReactor listeningIOReactor = new DefaultListeningIOReactor(
      1, params);

    BasicHttpProcessor originServerProc = new BasicHttpProcessor();
    originServerProc.addInterceptor(new RequestContent());
    originServerProc.addInterceptor(new RequestTargetHost());
    originServerProc.addInterceptor(new RequestConnControl());
    originServerProc.addInterceptor(new RequestUserAgent());
    originServerProc.addInterceptor(new RequestExpectContinue());

    BasicHttpProcessor clientProxyProcessor = new BasicHttpProcessor();
    clientProxyProcessor.addInterceptor(new ResponseDate());
    clientProxyProcessor.addInterceptor(new ResponseServer());
    clientProxyProcessor.addInterceptor(new ResponseContent());
    clientProxyProcessor.addInterceptor(new ResponseConnControl());

    NHttpClientHandler connectingHandler = new ConnectingHandler(
      originServerProc,
      new DefaultConnectionReuseStrategy(),
      params);

    NHttpServiceHandler listeningHandler = new ListeningHandler(
      targetHost,
      connectingIOReactor,
      clientProxyProcessor,
      new DefaultHttpResponseFactory(),
      new DefaultConnectionReuseStrategy(),
      params);

    final IOEventDispatch connectingEventDispatch = new DefaultClientIOEventDispatch(
      connectingHandler, params);

    final IOEventDispatch listeningEventDispatch = new DefaultServerIOEventDispatch(
      listeningHandler, params);

    Thread t = new Thread(new Runnable() {

      public void run() {
        try {
          connectingIOReactor.execute(connectingEventDispatch);
        } catch (InterruptedIOException ex) {
          System.err.println("Interrupted");
        } catch (IOException e) {
          System.err.println("I/O error: " + e.getMessage());
        }
      }

    });
    t.start();

    try {
      listeningIOReactor.listen(new InetSocketAddress(8888));
      listeningIOReactor.execute(listeningEventDispatch);
    } catch (InterruptedIOException ex) {
      System.err.println("Interrupted");
    } catch (IOException e) {
      System.err.println("I/O error: " + e.getMessage());
    }
  }
}
