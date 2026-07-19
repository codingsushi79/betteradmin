package com.betteradmin;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists mutes, freezes, and admin history to data.yml so state survives a server restart.
 */
final class PlayerDataStore {
    private final File file;

    PlayerDataStore(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    record LoadResult(Map<UUID, BetterAdminPlugin.MuteEntry> mutes, Map<UUID, Instant> frozen,
            List<AdminHistoryEntry> history) {
    }

    LoadResult load() {
        Map<UUID, BetterAdminPlugin.MuteEntry> mutes = new HashMap<>();
        Map<UUID, Instant> frozen = new HashMap<>();
        List<AdminHistoryEntry> history = new ArrayList<>();

        if (!file.exists()) {
            return new LoadResult(mutes, frozen, history);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (yaml.isConfigurationSection("mutes")) {
            for (String key : yaml.getConfigurationSection("mutes").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long expires = yaml.getLong("mutes." + key + ".expires");
                    String reason = yaml.getString("mutes." + key + ".reason", "");
                    mutes.put(uuid, new BetterAdminPlugin.MuteEntry(Instant.ofEpochMilli(expires), reason));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (yaml.isConfigurationSection("frozen")) {
            for (String key : yaml.getConfigurationSection("frozen").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    frozen.put(uuid, Instant.ofEpochMilli(yaml.getLong("frozen." + key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (Map<?, ?> entry : yaml.getMapList("history")) {
            try {
                Instant timestamp = Instant.ofEpochMilli(Long.parseLong(String.valueOf(entry.get("timestamp"))));
                AdminHistoryEntry historyEntry = new AdminHistoryEntry(
                        timestamp,
                        String.valueOf(entry.get("actor")),
                        String.valueOf(entry.get("action")),
                        String.valueOf(entry.get("target")),
                        entry.get("targetUuid") == null ? "" : String.valueOf(entry.get("targetUuid")),
                        entry.get("reason") == null ? "" : String.valueOf(entry.get("reason")),
                        entry.get("extra") == null ? "" : String.valueOf(entry.get("extra")));
                if (Boolean.parseBoolean(String.valueOf(entry.get("undone")))) {
                    historyEntry.markUndone();
                }
                history.add(historyEntry);
            } catch (RuntimeException ignored) {
            }
        }

        return new LoadResult(mutes, frozen, history);
    }

    void save(Map<UUID, BetterAdminPlugin.MuteEntry> mutes, Map<UUID, Instant> frozen,
            List<AdminHistoryEntry> history) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, BetterAdminPlugin.MuteEntry> entry : mutes.entrySet()) {
            String path = "mutes." + entry.getKey();
            yaml.set(path + ".expires", entry.getValue().expires().toEpochMilli());
            yaml.set(path + ".reason", entry.getValue().reason());
        }

        for (Map.Entry<UUID, Instant> entry : frozen.entrySet()) {
            yaml.set("frozen." + entry.getKey(), entry.getValue().toEpochMilli());
        }

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (AdminHistoryEntry entry : history) {
            Map<String, Object> map = new HashMap<>();
            map.put("timestamp", entry.timestamp().toEpochMilli());
            map.put("actor", entry.actor());
            map.put("action", entry.action());
            map.put("target", entry.target());
            map.put("targetUuid", entry.targetUuid());
            map.put("reason", entry.reason());
            map.put("extra", entry.extra());
            map.put("undone", entry.undone());
            historyList.add(map);
        }
        yaml.set("history", historyList);

        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to save BetterAdmin data.yml", e);
        }
    }
}
