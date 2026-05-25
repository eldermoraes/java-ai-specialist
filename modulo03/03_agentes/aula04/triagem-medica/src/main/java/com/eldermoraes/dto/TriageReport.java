package com.eldermoraes.dto;

public record TriageReport(
        Specialty specialty,
        SpecialistOpinion opinion,
        SupervisorReview review,
        String disclaimer) {
}
