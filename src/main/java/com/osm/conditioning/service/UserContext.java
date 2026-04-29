package com.osm.conditioning.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class UserContext {
    private final UUID id;
    private final String login;
    private final String displayName;
    private final Map<String, Object> snapshot;

    public UserContext(UUID id, String login, String displayName, Map<String, Object> snapshot) {
        this.id = id;
        this.login = login;
        this.displayName = displayName;
        this.snapshot = snapshot;
    }

    public UUID id() {
        return id;
    }

    public String login() {
        return login;
    }

    public String displayName() {
        return displayName;
    }

    public Map<String, Object> snapshot() {
        return snapshot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (UserContext) obj;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.login, that.login) &&
                Objects.equals(this.displayName, that.displayName) &&
                Objects.equals(this.snapshot, that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, login, displayName, snapshot);
    }

    @Override
    public String toString() {
        return "UserContext[" +
                "id=" + id + ", " +
                "login=" + login + ", " +
                "displayName=" + displayName + ", " +
                "snapshot=" + snapshot + ']';
    }


}

