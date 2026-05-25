package com.eldermoraes;

import com.eldermoraes.dto.SymptomsInput;
import com.eldermoraes.dto.TriageStep;
import com.eldermoraes.workflow.MedicalSupervisor;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/ws/triagem")
public class TriageWebsocket {

    private static final Logger LOG = Logger.getLogger(TriageWebsocket.class);

    @Inject
    MedicalSupervisor supervisor;

    @OnTextMessage
    public Multi<TriageStep> onMessage(SymptomsInput input) {
        return supervisor.triar(input.sintomas());
    }

    @OnError
    public TriageStep onError(Throwable t) {
        LOG.error("Falha na triagem médica", t);
        return TriageStep.error(t.getMessage());
    }
}
