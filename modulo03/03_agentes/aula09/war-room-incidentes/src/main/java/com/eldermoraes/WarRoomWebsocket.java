package com.eldermoraes;

import com.eldermoraes.dto.Incidente;
import com.eldermoraes.dto.WarRoomEvent;
import com.eldermoraes.workflow.WarRoomOrchestrator;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/warroom")
public class WarRoomWebsocket {

    private static final Logger LOG = Logger.getLogger(WarRoomWebsocket.class);
    private static final int MAX_CHARS = 6000;

    @Inject
    WarRoomOrchestrator orchestrator;

    @OnTextMessage
    public Multi<WarRoomEvent> onMessage(Incidente incidente) {
        return orchestrator.investigarAsStream(new Incidente(
                truncate(incidente.sintoma()),
                truncate(incidente.logs()),
                truncate(incidente.metricas()),
                truncate(incidente.bancoDados())));
    }

    @OnError
    public WarRoomEvent onError(Throwable t) {
        LOG.error("Falha no fluxo da war-room", t);
        return WarRoomEvent.error(t.getMessage());
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_CHARS ? s : s.substring(0, MAX_CHARS);
    }
}
