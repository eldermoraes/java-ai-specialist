package com.eldermoraes;

import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.dto.TriagemInput;
import com.eldermoraes.workflow.TriagemOrchestrator;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/triagem")
public class TriagemWebsocket {

    private static final Logger LOG = Logger.getLogger(TriagemWebsocket.class);
    private static final int MAX_CHARS = 6000;

    @Inject
    TriagemOrchestrator orchestrator;

    @OnTextMessage
    public Multi<ProgressUpdate> onMessage(TriagemInput input) {
        return orchestrator.triagem(truncate(input.vaga()), truncate(input.cv()));
    }

    @OnError
    public ProgressUpdate onError(Throwable t) {
        LOG.error("Falha no fluxo de triagem", t);
        return ProgressUpdate.error(t.getMessage());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
