package com.eldermoraes.workflow;

import com.eldermoraes.dto.TriageReport;
import com.eldermoraes.dto.TriageStep;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MedicalSupervisor {

    @Inject
    TriageAgent triageAgent;

    public Multi<TriageStep> triar(String sintomas) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTriagem(sintomas, emitter)));
    }

    private void runTriagem(String sintomas,
                            MultiEmitter<? super TriageStep> emitter) {
        try {
            emitter.emit(TriageStep.started());
            TriageReport report = triageAgent.triar(sintomas);
            emitter.emit(TriageStep.done(report));
            emitter.complete();
        } catch (Exception e) {
            emitter.fail(e);
        }
    }
}
