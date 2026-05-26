package com.eldermoraes.workflow;

import com.eldermoraes.dto.Idioma;
import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.dto.Traducao;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TraducaoOrchestrator {

    private static final Logger LOG = Logger.getLogger(TraducaoOrchestrator.class);

    @Inject
    TraducaoAgent traducaoAgent;

    public Multi<ProgressUpdate> traduzir(String comunicado) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTraducao(comunicado, emitter)));
    }

    private void runTraducao(String comunicado,
                             MultiEmitter<? super ProgressUpdate> emitter) {
        try {
            List<Idioma> idiomas = Idioma.alvos();
            emitter.emit(ProgressUpdate.started(idiomas.stream().map(Idioma::codigo).toList()));
            List<Traducao> traducoes = traducaoAgent.traduzir(idiomas, comunicado);
            emitter.emit(ProgressUpdate.allDone(traducoes));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha na tradução paralela", e);
            emitter.fail(e);
        }
    }
}
