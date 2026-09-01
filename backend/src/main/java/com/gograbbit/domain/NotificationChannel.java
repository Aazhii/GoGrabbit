package com.gograbbit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "notification_channel")
public class NotificationChannel {

    public enum Type { EMAIL, TELEGRAM, DISCORD }

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private String target;

    @Column(nullable = false)
    private boolean active = true;

    protected NotificationChannel() {
    }

    public NotificationChannel(Type type, String target) {
        this.type = type;
        this.target = target;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public String getTarget() {
        return target;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
