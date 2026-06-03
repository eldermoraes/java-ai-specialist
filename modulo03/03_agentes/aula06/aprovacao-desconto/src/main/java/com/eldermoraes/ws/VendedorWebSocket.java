package com.eldermoraes.ws;

import com.eldermoraes.dto.EntradaPedido;
import com.eldermoraes.dto.VendedorEvent;
import com.eldermoraes.workflow.DescontoWorkflow;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/vendedor/{vendedorId}")
public class VendedorWebSocket {

    private static final Logger LOG = Logger.getLogger(VendedorWebSocket.class);

    @Inject
    DescontoWorkflow workflow;

    @OnTextMessage
    public Multi<VendedorEvent> onMessage(EntradaPedido input, @PathParam("vendedorId") String vendedorId) {
        LOG.infof("Pedido recebido do vendedor %s", vendedorId);
        return workflow.processarPedido(vendedorId, input.descricao());
    }

    @OnError
    public VendedorEvent onError(Throwable t) {
        LOG.error("Falha no fluxo de aprovação", t);
        return VendedorEvent.error(t.getMessage());
    }
}
