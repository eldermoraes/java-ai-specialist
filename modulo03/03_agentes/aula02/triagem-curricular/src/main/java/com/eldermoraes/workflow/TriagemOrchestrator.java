package com.eldermoraes.workflow;

import com.eldermoraes.ai.CulturalFitAnalyzer;
import com.eldermoraes.ai.ExperienceAnalyzer;
import com.eldermoraes.ai.RedFlagsAnalyzer;
import com.eldermoraes.ai.ReportSynthesizer;
import com.eldermoraes.ai.SkillsAnalyzer;
import com.eldermoraes.dto.CulturalFitReport;
import com.eldermoraes.dto.ExperienceReport;
import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.dto.RedFlagsReport;
import com.eldermoraes.dto.SkillsReport;
import com.eldermoraes.dto.TriagemReport;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Function;
import java.util.function.Supplier;

@ApplicationScoped
public class TriagemOrchestrator {

    @Inject
    SkillsAnalyzer skillsAnalyzer;

    @Inject
    ExperienceAnalyzer experienceAnalyzer;

    @Inject
    CulturalFitAnalyzer culturalAnalyzer;

    @Inject
    RedFlagsAnalyzer redFlagsAnalyzer;

    @Inject
    ReportSynthesizer synthesizer;

    public Multi<ProgressUpdate> triagem(String vaga, String cv) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTriagem(vaga, cv, emitter)));
    }

    private void runTriagem(String vaga, String cv,
                            MultiEmitter<? super ProgressUpdate> emitter) {
        try {
            emitter.emit(ProgressUpdate.started());
            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Object>awaitAll())) {
                Subtask<SkillsReport> skillsTask = scope.fork(() ->
                        runWorker("skills", () -> skillsAnalyzer.analyze(vaga, cv),
                                SkillsReport::empty, emitter));
                Subtask<ExperienceReport> experienceTask = scope.fork(() ->
                        runWorker("experience", () -> experienceAnalyzer.analyze(vaga, cv),
                                ExperienceReport::empty, emitter));
                Subtask<CulturalFitReport> culturalTask = scope.fork(() ->
                        runWorker("cultural", () -> culturalAnalyzer.analyze(vaga, cv),
                                CulturalFitReport::empty, emitter));
                Subtask<RedFlagsReport> redFlagsTask = scope.fork(() ->
                        runWorker("redFlags", () -> redFlagsAnalyzer.analyze(vaga, cv),
                                RedFlagsReport::empty, emitter));

                scope.join();

                var skills = skillsTask.get();
                var experience = experienceTask.get();
                var cultural = culturalTask.get();
                var redFlags = redFlagsTask.get();

                emitter.emit(ProgressUpdate.synthesizing());
                var parcial = synthesizer.synthesize(vaga, skills, experience, cultural, redFlags);

                var report = new TriagemReport(
                        parcial.scoreFinal(),
                        parcial.recomendacao(),
                        parcial.justificativa(),
                        skills,
                        experience,
                        cultural,
                        redFlags);
                emitter.emit(ProgressUpdate.done(report));
                emitter.complete();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.fail(e);
        } catch (Exception e) {
            emitter.fail(e);
        }
    }

    private <T> T runWorker(String name, Supplier<T> work,
                            Function<String, T> fallback,
                            MultiEmitter<? super ProgressUpdate> emitter) {
        try {
            T result = work.get();
            emitter.emit(ProgressUpdate.workerDone(name, result));
            return result;
        } catch (Exception e) {
            T fb = fallback.apply(e.getMessage());
            emitter.emit(ProgressUpdate.workerDone(name, fb));
            return fb;
        }
    }
}
