package net.kungfoo.grizzly.proxy;

import com.sun.grizzly.tcp.*;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.*;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.logging.Level;
import static java.text.MessageFormat.format;
import java.io.IOException;

/**
 * Simple HTTP Proxy.
 *
 * @author Hubert Iwaniuk
 */
public class HttpProxy implements Adapter {

    public void service(Request request, Response response) throws Exception {
        String uri = request.requestURI().toString();

        final MessageBytes method = request.method();
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, format("Incomming request. URI: {0}, Method: {1}", uri, method));
        }
        if (maxForwards(request, response, method)) return;

        String targetHost = request.serverName().toString();
/*
        String host = request.getHeader("Host");
        if (host == null || host.trim().isEmpty()) {
            // read from Proxy header
            logger.log(Level.INFO, "No Host provided. Where it should be routed to?");
            response.setStatus(417);
            response.setHeader(Expect, "\"Host\" header expected. Don't know where request should be routed to.");
            response.sendHeaders();
            return;
        } else {
            targetHost = host;
        }
*/
        int targetPort = request.getServerPort();
        
        // set host for target request
        InputBuffer inputBuffer = request.getInputBuffer();

        OutputBuffer outputBuffer = response.getOutputBuffer();

        Controller controller = new Controller();
        // new TCPSelectorHandler(true) means the Selector will be used only
        // for client operation (OP_READ, OP_WRITE, OP_CONNECT).
        TCPSelectorHandler tcpSelectorHandler = new TCPSelectorHandler(true);
        controller.setSelectorHandler(tcpSelectorHandler);
        TCPConnectorHandler tcpConnectorHandler = new TCPConnectorHandler();
        tcpConnectorHandler.connect(new InetSocketAddress(targetHost, targetPort), new CallbackHandler() {
            public void onConnect(IOEvent ioEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public void onRead(IOEvent ioEvent) {

            }

            public void onWrite(IOEvent ioEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }
        }, tcpSelectorHandler);

        // handle "Via"
        response.setHeader("Via", request.protocol() + " antares");// TODO hostname, and Via from response


        if ("GET".equalsIgnoreCase(method.toString())) {
            response.setStatus(HttpURLConnection.HTTP_OK);
            byte[] bytes = "Here is my response text".getBytes();

            ByteChunk chunk = new ByteChunk();
            response.setContentLength(bytes.length);
            response.setContentType("text/plain");
            chunk.append(bytes, 0, bytes.length);
            OutputBuffer buffer = response.getOutputBuffer();
            buffer.doWrite(chunk, response);
            response.finish();
        }
    }

    private boolean maxForwards(Request request, Response response, MessageBytes method) throws IOException {
        // Max-Forwards (14.32)
        if ("OPTIONS".equalsIgnoreCase(method.toString()) ||
                "TRACE".equalsIgnoreCase(method.toString())) {
            String maxFrwds = request.getHeader(HttpHeader.Max_Forwards.toString());
            if (maxFrwds != null && !maxFrwds.trim().isEmpty()) {
                int forwards = Integer.parseInt(maxFrwds);
                if (forwards == 0) {
                    // last allowed
                    // Exectation failed
                    response.setStatus(417);
                    response.setHeader(HttpHeader.Expect.toString(), "Expected to have " + HttpHeader.Max_Forwards + " > 0. Can't forward.");
                    response.sendHeaders();
                    return true;
                } else {
                    // update Max-Forwards
                    response.setHeader(HttpHeader.Max_Forwards.toString(), String.valueOf(forwards - 1));
                }
            }
        }
        return false;
    }

    public void afterService(Request req, Response res) throws Exception {
        req.recycle();
        res.recycle();
    }

    /**
     * logger
     */
    public Logger logger = LoggerUtils.getLogger();
}
