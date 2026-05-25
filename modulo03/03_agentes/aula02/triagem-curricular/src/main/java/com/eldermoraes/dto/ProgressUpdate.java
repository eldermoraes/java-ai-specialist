package com.eldermoraes.dto;

public record ProgressUpdate(String type, String worker, Object payload, String error) {
    public static ProgressUpdate started() {
        return new ProgressUpdate("STARTED", null, null, null);
    }

    public static ProgressUpdate workerDone(String worker, Object payload) {
        return new ProgressUpdate("WORKER_DONE", worker, payload, null);
    }

    public static ProgressUpdate synthesizing() {
        return new ProgressUpdate("SYNTHESIZING", null, null, null);
    }

    public static ProgressUpdate done(TriagemReport report) {
        return new ProgressUpdate("DONE", null, report, null);
    }

    public static ProgressUpdate error(String message) {
        return new ProgressUpdate("ERROR", null, null, message);
    }
}
