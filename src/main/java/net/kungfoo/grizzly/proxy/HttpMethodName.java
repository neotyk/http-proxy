package net.kungfoo.grizzly.proxy;

/**
 * HTTP Method names.
 *
 * @author Hubert Iwaniuk
 */
public enum HttpMethodName {
    TRACE,
    GET,
    DELETE,
    HEAD,
    OPTIONS,
    POST,
    PUT,
    CONNECT;

    public boolean equalsIgnoreCase(String methodName) {
        return this.name().equalsIgnoreCase(methodName);
    }
}
