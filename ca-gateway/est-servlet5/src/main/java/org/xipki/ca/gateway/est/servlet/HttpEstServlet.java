// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.gateway.est.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.audit.*;
import org.xipki.ca.gateway.GatewayUtil;
import org.xipki.ca.gateway.est.EstResponder;
import org.xipki.servlet5.HttpRequestMetadataRetrieverImpl;
import org.xipki.servlet5.ServletHelper;
import org.xipki.util.Args;
import org.xipki.util.IoUtil;
import org.xipki.util.http.RestResponse;

import java.io.IOException;

/**
 * EST servlet.
 *
 * @author Lijun Liao (xipki)
 * @since 6.0.0
 */

public class HttpEstServlet extends HttpServlet {

  private static final Logger LOG = LoggerFactory.getLogger(HttpEstServlet.class);

  private boolean logReqResp;

  private EstResponder responder;

  public void setLogReqResp(boolean logReqResp) {
    this.logReqResp = logReqResp;
  }

  public void setResponder(EstResponder responder) {
    this.responder = Args.notNull(responder, "responder");
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    service0(req, resp, false);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    service0(req, resp, true);
  }

  private void service0(HttpServletRequest req, HttpServletResponse resp, boolean viaPost)
      throws IOException {
    AuditService auditService = Audits.getAuditService();
    AuditEvent event = new AuditEvent();
    event.setApplicationName("est-gw");

    try {
      String path = req.getServletPath();
      byte[] requestBytes = viaPost ? IoUtil.readAllBytesAndClose(req.getInputStream()) : null;
      RestResponse restResp = responder.service(path, requestBytes, new HttpRequestMetadataRetrieverImpl(req), event);
      ServletHelper.fillResponse(restResp, resp);
      ServletHelper.logReqResp("EST Gateway", LOG, logReqResp, viaPost, req, requestBytes, restResp.getBody());

      if (event.getStatus() == null) {
        event.setStatus(AuditStatus.SUCCESSFUL);
      }
    } catch (RuntimeException ex) {
      event.setStatus(AuditStatus.FAILED);
      event.setLevel(AuditLevel.ERROR);
      LOG.error("RuntimeException thrown, this should not happen!", ex);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      event.finish();
      auditService.logEvent(event);
      GatewayUtil.logAuditEvent(LOG, event);
    }
  } // method service0

}