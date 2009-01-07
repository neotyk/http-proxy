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

import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.Request;
import static net.kungfoo.grizzly.proxy.impl.ConnState.IDLE;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.nio.IOControl;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Proxy Processing Information.
 *
 * TODO: clean this one up.
 * @author Hubert Iwaniuk.
 */
public class ProxyProcessingInfo {

  public static final String ATTRIB = "proxy-processing-info";

  private final ByteBuffer inBuffer;
  private final ByteBuffer outBuffer;

  private HttpHost target;

  private IOControl originIOControl;

  private ConnState originState;
  private ConnState clientState;

  private HttpRequest request;
  private Response response;
  private Runnable completion;
  private Request originalRequest;

  public ProxyProcessingInfo() {
    super();
    this.originState = IDLE;
    this.clientState = IDLE;
    this.inBuffer = ByteBuffer.allocateDirect(1024);
    this.outBuffer = ByteBuffer.allocateDirect(1024);
  }

  public ByteBuffer getInBuffer() {
    return this.inBuffer;
  }

  public ByteBuffer getOutBuffer() {
    return this.outBuffer;
  }

  public HttpHost getTarget() {
    return this.target;
  }

  public void setTarget(final HttpHost target) {
    this.target = target;
  }

  public HttpRequest getRequest() {
    return this.request;
  }

  public void setRequest(final HttpRequest request) {
    this.request = request;
  }

  public Response getResponse() {
    return this.response;
  }

  public void setResponse(final Response response) {
    this.response = response;
  }

  public IOControl getOriginIOControl() {
    return this.originIOControl;
  }

  public void setOriginIOControl(final IOControl originIOControl) {
    this.originIOControl = originIOControl;
  }

  public ConnState getOriginState() {
    return this.originState;
  }

  public void setOriginState(final ConnState state) {
    this.originState = state;
  }

  public ConnState getClientState() {
    return this.clientState;
  }

  public void setClientState(final ConnState state) {
    this.clientState = state;
  }

  public void reset() {
    this.inBuffer.clear();
    this.outBuffer.clear();
    this.originState = IDLE;
    this.clientState = IDLE;
    this.request = null;
    this.response = null;
  }

  public void shutdown() {
    if (this.originIOControl != null) {
      try {
        this.originIOControl.shutdown();
      } catch (IOException ignore) {
      }
    }
  }

  public void setCompletion(Runnable completion) {
    this.completion = completion;
  }

  public Runnable getCompletion() {
    return completion;
  }

  public void setOriginalRequest(Request originalRequest) {
    this.originalRequest = originalRequest;
  }

  public Request getOriginalRequest() {
    return originalRequest;
  }
}
