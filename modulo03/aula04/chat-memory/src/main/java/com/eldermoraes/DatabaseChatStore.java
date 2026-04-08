package com.eldermoraes;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class DatabaseChatStore implements ChatMemoryStore {

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ChatSessionEntity session = ChatSessionEntity.findById(memoryId.toString());
        if (session == null || session.messageJson == null) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(session.messageJson);
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ChatSessionEntity session = ChatSessionEntity.findById(memoryId.toString());
        if (session == null) {
            session = new ChatSessionEntity();
            session.id = memoryId.toString();
        }

        session.messageJson = ChatMessageSerializer.messagesToJson(messages);
        session.persist();
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        ChatSessionEntity.deleteById(memoryId.toString());
    }
}