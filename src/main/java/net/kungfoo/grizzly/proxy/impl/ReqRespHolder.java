/*
 * @(#)ReqRespHolder     Dec 30, 2008
 *
 * Copyright (c) 2006 TomTom International B.V. All rights reserved.
 * TomTom PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
package net.kungfoo.grizzly.proxy.impl;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;

/**
 * {@link Request} and {@link Response} holder.
 *
 * @author Hubert Iwaniuk.
 * @since Dec 30, 2008
 */
public class ReqRespHolder {
  public ReqRespHolder(Request request, Response response) {
    this.request = request;
    this.response = response;
  }

  public Request getRequest() {
    return request;
  }

  public Response getResponse() {
    return response;
  }

  private final Request request;
  private final Response response;
}
