package com.eldermoraes.dto;

import java.util.List;

public record ProgressUpdate(
        String type,
        String lang,
        Object payload,
        List<String> langs,
        String error,
        Long elapsedMs) {

    public static ProgressUpdate started(List<String> langs) {
        return new ProgressUpdate("STARTED", null, null, langs, null, null);
    }

    public static ProgressUpdate langDone(String lang, Traducao t, long elapsedMs) {
        return new ProgressUpdate("LANG_DONE", lang, t, null, null, elapsedMs);
    }

    public static ProgressUpdate langError(String lang, String error, long elapsedMs) {
        return new ProgressUpdate("LANG_ERROR", lang, null, null, error, elapsedMs);
    }

    public static ProgressUpdate allDone() {
        return new ProgressUpdate("ALL_DONE", null, null, null, null, null);
    }

    public static ProgressUpdate error(String error) {
        return new ProgressUpdate("ERROR", null, null, null, error, null);
    }
}
