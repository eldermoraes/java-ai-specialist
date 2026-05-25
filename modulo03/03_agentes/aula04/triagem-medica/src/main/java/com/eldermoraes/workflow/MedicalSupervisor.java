package com.eldermoraes.workflow;

import com.eldermoraes.ai.CardioAgent;
import com.eldermoraes.ai.ConsultationValidator;
import com.eldermoraes.ai.GiClinicaAgent;
import com.eldermoraes.ai.NeuroAgent;
import com.eldermoraes.ai.OrtopediaAgent;
import com.eldermoraes.ai.SpecialistRouter;
import com.eldermoraes.dto.SpecialistOpinion;
import com.eldermoraes.dto.Specialty;
import com.eldermoraes.dto.SupervisorReview;
import com.eldermoraes.dto.TriageReport;
import com.eldermoraes.dto.TriageStep;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MedicalSupervisor {

    private static final String DISCLAIMER = "Material didático: triagem inicial automatizada, "
            + "NÃO substitui avaliação médica presencial. Em caso de urgência, procure atendimento imediato.";

    @Inject
    SpecialistRouter router;

    @Inject
    CardioAgent cardio;

    @Inject
    NeuroAgent neuro;

    @Inject
    OrtopediaAgent ortopedia;

    @Inject
    GiClinicaAgent giClinica;

    @Inject
    ConsultationValidator validator;

    public Multi<TriageStep> triar(String sintomas) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTriagem(sintomas, emitter)));
    }

    private void runTriagem(String sintomas,
                            MultiEmitter<? super TriageStep> emitter) {
        try {
            emitter.emit(TriageStep.started());

            Specialty specialty = router.decidirEspecialidade(sintomas);
            emitter.emit(TriageStep.routed(specialty));

            SpecialistOpinion opinion = invokeSpecialist(specialty, sintomas);
            emitter.emit(TriageStep.specialistDone(opinion));

            emitter.emit(TriageStep.reviewing());
            SupervisorReview review = validator.revisar(specialty, sintomas, opinion);

            TriageReport report = new TriageReport(specialty, opinion, review, DISCLAIMER);
            emitter.emit(TriageStep.done(report));
            emitter.complete();
        } catch (Exception e) {
            emitter.fail(e);
        }
    }

    private SpecialistOpinion invokeSpecialist(Specialty specialty, String sintomas) {
        return switch (specialty) {
            case CARDIO -> cardio.avaliar(sintomas);
            case NEURO -> neuro.avaliar(sintomas);
            case ORTOPEDIA -> ortopedia.avaliar(sintomas);
            case GI_CLINICA -> giClinica.avaliar(sintomas);
        };
    }
}
