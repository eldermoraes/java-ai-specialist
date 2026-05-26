package com.eldermoraes.dto;

import java.util.List;

public record ProgressUpdate(
        String type,
        List<String> langs,
        List<Traducao> traducoes,
        String error) {

    public static ProgressUpdate started(List<String> langs) {
        return new ProgressUpdate("STARTED", langs, null, null);
    }

    public static ProgressUpdate allDone(List<Traducao> traducoes) {
        return new ProgressUpdate("ALL_DONE", null, traducoes, null);
    }

    public static ProgressUpdate error(String error) {
        return new ProgressUpdate("ERROR", null, null, error);
    }
}
