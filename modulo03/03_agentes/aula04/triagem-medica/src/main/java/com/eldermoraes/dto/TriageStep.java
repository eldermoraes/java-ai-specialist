package com.eldermoraes.dto;

public record TriageStep(String type, Object payload, String error) {
    public static TriageStep started() {
        return new TriageStep("STARTED", null, null);
    }

    public static TriageStep routed(Specialty specialty) {
        return new TriageStep("ROUTED", specialty, null);
    }

    public static TriageStep specialistDone(SpecialistOpinion opinion) {
        return new TriageStep("SPECIALIST_DONE", opinion, null);
    }

    public static TriageStep reviewing() {
        return new TriageStep("REVIEWING", null, null);
    }

    public static TriageStep done(TriageReport report) {
        return new TriageStep("DONE", report, null);
    }

    public static TriageStep error(String message) {
        return new TriageStep("ERROR", null, message);
    }
}
