package com.eldermoraes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ChatSessionEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(columnDefinition = "TEXT")
    public String messageJson;
}
