package com.eldermoraes;

import com.eldermoraes.ai.C3POService;
import com.eldermoraes.ai.StarWarsPersonService;
import com.eldermoraes.rest.StarWarsAPIService;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@WebSocket(path = "/ws/c3po/{personId}")
public class C3POWebsocket {

    @Inject
    C3POService c3poService;

    @Inject
    StarWarsPersonService starWarsPersonService;

    @Inject
    @RestClient
    StarWarsAPIService starWarsAPIService;

    @Inject
    WebSocketConnection connection;

    @OnOpen
    @Blocking
    public Multi<String> onOpen() {
        String personId = connection.pathParam("personId");
        String personJson = starWarsAPIService.getPerson(personId);
        Person person = starWarsPersonService.chat(personJson);

        return c3poService.chat("O que você acha desse personagem: " + person);
    }

    @OnTextMessage
    public Multi<String> onTextMessage(String message) {
        return c3poService.chat(message);
    }
}
