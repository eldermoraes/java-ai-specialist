package com.eldermoraes;

import com.eldermoraes.dto.ComiteEvent;
import com.eldermoraes.dto.DossieInput;
import com.eldermoraes.workflow.ComiteOrchestrator;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/comite")
public class ComiteWebsocket {

    private static final Logger LOG = Logger.getLogger(ComiteWebsocket.class);
    private static final int MAX_CHARS = 6000;

    @Inject
    ComiteOrchestrator orchestrator;

    @OnTextMessage
    public Multi<ComiteEvent> onMessage(DossieInput input) {
        return orchestrator.deliberarAsStream(truncate(input.dossie()));
    }

    @OnError
    public ComiteEvent onError(Throwable t) {
        LOG.error("Falha no fluxo do comitê", t);
        return ComiteEvent.error(t.getMessage());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
