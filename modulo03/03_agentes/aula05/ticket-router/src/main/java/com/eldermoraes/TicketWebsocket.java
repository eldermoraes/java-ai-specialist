package com.eldermoraes;

import com.eldermoraes.dto.TicketEvent;
import com.eldermoraes.dto.TicketInput;
import com.eldermoraes.workflow.TicketRouter;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/tickets")
public class TicketWebsocket {

    private static final Logger LOG = Logger.getLogger(TicketWebsocket.class);

    @Inject
    TicketRouter router;

    @OnTextMessage
    public Multi<TicketEvent> onMessage(TicketInput input) {
        return router.routeAsStream(input.texto() == null ? "" : input.texto());
    }

    @OnError
    public TicketEvent onError(Throwable t) {
        LOG.error("Falha processando ticket", t);
        return TicketEvent.error(t.getMessage());
    }
}
