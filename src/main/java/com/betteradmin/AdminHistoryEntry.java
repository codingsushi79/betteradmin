package com.betteradmin;

import java.time.Instant;

public final class AdminHistoryEntry {
    private final Instant timestamp;
    private final String actor;
    private final String action;
    private final String target;
    private final String targetUuid;
    private final String reason;
    private final String extra;
    private boolean undone;

    public AdminHistoryEntry(Instant timestamp, String actor, String action, String target, String targetUuid,
            String reason, String extra) {
        this.timestamp = timestamp;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.targetUuid = targetUuid == null ? "" : targetUuid;
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

    public String targetUuid() {
        return targetUuid;
    }

    public String reason() {
        return reason;
    }

    public String extra() {
        return extra;
    }

    public boolean undone() {
        return undone;
    }

    public void markUndone() {
        this.undone = true;
    }

    public boolean isUndoable() {
        if (undone || targetUuid.isEmpty()) {
            return false;
        }
        return switch (action) {
            case "ban", "tempban", "mute", "freeze" -> true;
            default -> false;
        };
    }
}
