package com.betteradmin;

import java.time.Instant;

public final class AdminHistoryEntry {
    private final Instant timestamp;
    private final String actor;
    private final String action;
    private final String target;
    private final String reason;
    private final String extra;

    public AdminHistoryEntry(Instant timestamp, String actor, String action, String target, String reason, String extra) {
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.reason = reason;
        this.extra = extra;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String actor() {
        return actor;
    }

    public String action() {
        return action;
    }

    public String target() {
        return target;
    }

    public String reason() {
        return reason;
    }

    public String extra() {
        return extra;
    }
}
