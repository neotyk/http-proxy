package net.kungfoo.grizzly.proxy.impl.sample;

import com.sun.grizzly.tcp.Response;
import net.kungfoo.grizzly.proxy.impl.ReqRespHolder;
import static net.kungfoo.grizzly.proxy.impl.sample.ConnState.IDLE;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.nio.IOControl;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Proxy Processing Information.
 *
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
  private ReqRespHolder requestResponseHolder;

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
/*        if (this.clientIOControl != null) {
            try {
                this.clientIOControl.shutdown();
            } catch (IOException ignore) {
            }
        }*/
    if (this.originIOControl != null) {
      try {
        this.originIOControl.shutdown();
      } catch (IOException ignore) {
      }
    }
  }

  public void setRequestResponseHolder(ReqRespHolder requestResponseHolder) {
    this.requestResponseHolder = requestResponseHolder;
  }

  public ReqRespHolder getRequestResponseHolder() {
    return requestResponseHolder;
  }
}
