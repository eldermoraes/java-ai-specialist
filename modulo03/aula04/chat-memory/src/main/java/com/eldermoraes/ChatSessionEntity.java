package com.eldermoraes;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ChatSessionEntity extends PanacheEntityBase {

    @Id
    public String id;

    public String messageJson;
}
