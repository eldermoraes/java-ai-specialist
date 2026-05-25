package com.eldermoraes.ws;

import com.eldermoraes.dto.ApprovalDecision;
import com.eldermoraes.dto.GerenteEvent;
import com.eldermoraes.hitl.ApprovalService;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/gerente")
public class GerenteWebSocket {

    private static final Logger LOG = Logger.getLogger(GerenteWebSocket.class);

    @Inject
    ApprovalService approvalService;

    @OnOpen
    @RunOnVirtualThread
    public GerenteEvent onOpen() {
        return GerenteEvent.snapshot(approvalService.listarTodas());
    }

    @OnTextMessage
    @RunOnVirtualThread
    public void onDecision(ApprovalDecision decision) {
        LOG.infof("Decisão recebida: proposta=%d status=%s", decision.propostaId(), decision.status());
        approvalService.decidir(decision);
    }
}
