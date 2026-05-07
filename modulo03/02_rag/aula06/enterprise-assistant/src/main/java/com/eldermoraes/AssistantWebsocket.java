package com.eldermoraes;

import com.eldermoraes.ai.AssistantService;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import jakarta.inject.Inject;

@WebSocket(path = "/ws/assistant")
public class AssistantWebsocket {

    @Inject
    AssistantService assistant;

    @OnTextMessage
    public String onTextMessage(String message) {
        return assistant.chat(message);
    }
}
