package com.eldermoraes;

import com.eldermoraes.dto.ComunicadoInput;
import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.workflow.TraducaoOrchestrator;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/traducao")
public class TraducaoWebsocket {

    private static final Logger LOG = Logger.getLogger(TraducaoWebsocket.class);
    private static final int MAX_CHARS = 4000;

    @Inject
    TraducaoOrchestrator orchestrator;

    @OnTextMessage
    public Multi<ProgressUpdate> onMessage(ComunicadoInput input) {
        return orchestrator.traduzir(truncate(input.comunicado()));
    }

    @OnError
    public ProgressUpdate onError(Throwable t) {
        LOG.error("Falha na tradução paralela", t);
        return ProgressUpdate.error(t.getMessage());
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
