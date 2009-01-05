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

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;


/**
 * Set of simple tests of proxy.
 *
 * @author Hubert Iwaniuk
 */
public class TestProxy {
  @Test(timeOut = 500, invocationCount = 100, threadPoolSize = 5)
  public void test200() throws Exception {
    String fileName = "index.html";
    URL url = new URL("http", "localhost", PORT, "/" + fileName);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(PROXY);
    urlConnection.connect();
    Assert.assertTrue(urlConnection.usingProxy(), "Should be using proxy");
    File file = new File(WEB_ROOT, fileName);
    Assert.assertTrue(file.exists(), "No test file present " + file.getAbsolutePath());
    Assert.assertEquals(200, urlConnection.getResponseCode(), "Expecting 200 status code");
    long fileLength = file.length();
    Assert.assertEquals(fileLength, urlConnection.getContentLength(), "Content length should be identical");
    InputStream is = urlConnection.getInputStream();
    int available = is.available();
    Assert.assertEquals(fileLength, available, "Body length should be identical");
    int read = is.read(new byte[available]);
    Assert.assertEquals(fileLength, read, "Should be able to read all body");
  }

  @Test(timeOut = 500, invocationCount = 100, threadPoolSize = 5)
  public void test404() throws IOException {
    String fileName = "whereAreYou";
    URL url = new URL("http", "localhost", PORT, "/" + fileName);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(PROXY);
    urlConnection.connect();
    Assert.assertTrue(urlConnection.usingProxy(), "Should be using proxy");
    File file = new File(WEB_ROOT, fileName);
    Assert.assertFalse(file.exists(), "Test file present " + file.getAbsolutePath());
    Assert.assertEquals(404, urlConnection.getResponseCode(), "Expecting 404 status code");
  }

  @AfterClass
  private void shutdown() throws Exception {
    proxyActivator.stop(null);
    server.stop();
  }

  @BeforeClass
  private void startup() throws Exception {
    this.server = new GrizzlyWebServer(PORT, WEB_ROOT);
    this.server.start();

    proxyActivator = new Activator();
    proxyActivator.start(null);
  }

  private static final String WEB_ROOT = "src/test/resources/data/";
  private static final int PORT = 9123;
  private GrizzlyWebServer server;
  private Activator proxyActivator;
  private static final Proxy PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8282));
}
