package com.eldermoraes.comprador.ws;

import com.eldermoraes.comprador.dto.EntradaCompra;
import com.eldermoraes.comprador.dto.NegociacaoEvent;
import com.eldermoraes.comprador.negociacao.NegociacaoCoordinator;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/negociacao")
public class CompradorWebSocket {

    private static final Logger LOG = Logger.getLogger(CompradorWebSocket.class);

    @Inject
    NegociacaoCoordinator coordinator;

    @OnTextMessage
    public Multi<NegociacaoEvent> onMessage(EntradaCompra entrada) {
        return coordinator.negociar(entrada);
    }

    @OnError
    public NegociacaoEvent onError(Throwable t) {
        LOG.error("Falha no fluxo de negociação", t);
        return NegociacaoEvent.error(t.getMessage());
    }
}
