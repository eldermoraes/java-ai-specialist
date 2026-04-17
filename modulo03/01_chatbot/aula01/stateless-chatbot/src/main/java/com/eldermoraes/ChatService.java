package com.eldermoraes;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ChatService {
    String chat(String message);
}
