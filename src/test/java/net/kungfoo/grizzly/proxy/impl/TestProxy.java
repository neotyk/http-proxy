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
import com.sun.grizzly.http.servlet.ServletAdapter;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;


/**
 * Set of simple tests of proxy.
 *
 * @author Hubert Iwaniuk
 */
public class TestProxy {
  @Test(timeOut = 500)
  public void testGET200() throws Exception {
    String fileName = "index.html";
    URL url = new URL("http", "localhost", PORT, "/" + fileName);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(PROXY);
    urlConnection.connect();
    Assert.assertTrue(urlConnection.usingProxy(), "Should be using proxy");
    File file = new File(WEB_ROOT, fileName);
    Assert.assertTrue(file.exists(), "No test file present " + file.getAbsolutePath());
    Assert.assertEquals(urlConnection.getResponseCode(), 200, "Expecting 200 status code");
    long fileLength = file.length();
    Assert.assertEquals(urlConnection.getContentLength(), fileLength, "Content length should be identical");
    InputStream is = urlConnection.getInputStream();
    int available = is.available();
    Assert.assertEquals(available, fileLength, "Body length should be identical");
    int read = is.read(new byte[available]);
    Assert.assertEquals(read, fileLength, "Should be able to read all body");
  }

  @Test(timeOut = 500)
  public void testGET404() throws IOException {
    String fileName = "whereAreYou";
    URL url = new URL("http", "localhost", PORT, "/" + fileName);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(PROXY);
    urlConnection.connect();
    Assert.assertTrue(urlConnection.usingProxy(), "Should be using proxy");
    File file = new File(WEB_ROOT, fileName);
    Assert.assertFalse(file.exists(), "Test file present " + file.getAbsolutePath());
    Assert.assertEquals(urlConnection.getResponseCode(), 404, "Expecting 404 status code");
  }

  @Test
  public void testPOST201() throws IOException {
    String location = "uploader/upload";
    URL url = new URL("http", "localhost", PORT, "/" + location);
    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection(PROXY);
    urlConnection.setRequestMethod("POST");
    urlConnection.setDoOutput(true);
    OutputStream outputStream = urlConnection.getOutputStream();
    outputStream.write(UPLOAD_MSG.getBytes());
    outputStream.flush();
    urlConnection.connect();
    Assert.assertTrue(urlConnection.usingProxy(), "Should be using proxy");
    Assert.assertEquals(urlConnection.getResponseCode(), 201, "Expecting 201 status code");
  }

  @AfterClass
  private void shutdown() throws Exception {
    proxyActivator.stop(null);
    server.stop();
  }

  @BeforeClass
  private void startup() throws Exception {
    server = new GrizzlyWebServer(PORT, WEB_ROOT);
    ServletAdapter servletAdapter = new ServletAdapter(WEB_ROOT);
    servletAdapter.setServletInstance(new HttpServlet() {
      @Override
      protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
        String postData = reader.readLine();
        System.out.println("got '" + postData + "'");
        if (UPLOAD_MSG.equals(postData)) {
          resp.setStatus(HttpServletResponse.SC_CREATED);
          resp.setHeader("Location", req.getRequestURI());
        } else {
          resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Need more input to compute");
        }
      }
    });
    servletAdapter.setHandleStaticResources(true);
    servletAdapter.setContextPath("/uploader");
    servletAdapter.setServletPath("/upload");
    server.addGrizzlyAdapter(servletAdapter, new String[]{"/uploader"});
    server.start();

    proxyActivator = new Activator();
    proxyActivator.start(null);
  }

  private static final String UPLOAD_MSG = "Uploaded message";
  private static final String WEB_ROOT = "src/test/resources/data/";
  private static final int PORT = 9123;
  private GrizzlyWebServer server;
  private Activator proxyActivator;
  private static final Proxy PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 8282));
}
