package com.eldermoraes;

import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

@WebSocket(path = "/ws/streaming")
public class StreamingResource {

    @Inject
    StreamingService streamingService;

    @OnTextMessage
    public Multi<String> chat(String message) {
        return streamingService.chat(message);
    }
}
