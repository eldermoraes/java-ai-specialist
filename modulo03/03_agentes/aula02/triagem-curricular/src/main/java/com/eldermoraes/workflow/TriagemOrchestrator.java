package com.eldermoraes.workflow;

import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.dto.TriagemReport;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TriagemOrchestrator {

    @Inject
    TriagemAgent triagemAgent;

    public Multi<ProgressUpdate> triagem(String vaga, String cv) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTriagem(vaga, cv, emitter)));
    }

    private void runTriagem(String vaga, String cv,
                            MultiEmitter<? super ProgressUpdate> emitter) {
        try {
            emitter.emit(ProgressUpdate.started());
            TriagemReport result = triagemAgent.triagem(vaga, cv);
            emitter.emit(ProgressUpdate.done(result));
            emitter.complete();
        } catch (Exception e) {
            emitter.fail(e);
        }
    }
}
