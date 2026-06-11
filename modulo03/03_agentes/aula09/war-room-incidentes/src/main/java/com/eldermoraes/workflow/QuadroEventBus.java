package com.eldermoraes.workflow;

import com.eldermoraes.dto.WarRoomEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Roteia eventos do quadro (emitidos pelo ObservablePlanner) para o emitter
 * Multi da investigação correta, usando o warRoomId gravado no AgenticScope.
 */
@ApplicationScoped
public class QuadroEventBus {

    private final ConcurrentMap<String, Consumer<WarRoomEvent>> assinantes = new ConcurrentHashMap<>();

    public void registrar(String warRoomId, Consumer<WarRoomEvent> consumer) {
        assinantes.put(warRoomId, consumer);
    }

    public void remover(String warRoomId) {
        assinantes.remove(warRoomId);
    }

    public void publicar(String warRoomId, WarRoomEvent evento) {
        Consumer<WarRoomEvent> consumer = assinantes.get(warRoomId);
        if (consumer != null) {
            consumer.accept(evento);
        }
    }
}
