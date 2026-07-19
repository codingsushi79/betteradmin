package com.betteradmin;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BetterAdminPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int PLAYER_PAGE_SIZE = 16;
    private static final int HISTORY_PAGE_SIZE = 7;

    private final Map<UUID, PendingAction> pendingActions = new HashMap<>();
    private final Map<UUID, PendingConfirmation> pendingConfirmations = new HashMap<>();
    private final Map<UUID, MuteEntry> mutedPlayers = new HashMap<>();
    private final Map<UUID, Instant> frozenPlayers = new HashMap<>();
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, Integer> playerListPage = new HashMap<>();
    private final Map<UUID, Integer> historyPage = new HashMap<>();
    private final List<AdminHistoryEntry> historyEntries = new ArrayList<>();

    private NamespacedKey actionKey;
    private NamespacedKey targetKey;
    private FileConfiguration config;
    private PlayerDataStore dataStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        actionKey = new NamespacedKey(this, "betteradmin-action");
        targetKey = new NamespacedKey(this, "betteradmin-target");

        dataStore = new PlayerDataStore(this);
        PlayerDataStore.LoadResult loaded = dataStore.load();
        mutedPlayers.putAll(loaded.mutes());
        frozenPlayers.putAll(loaded.frozen());
        historyEntries.addAll(loaded.history());

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("adminpanel") != null) {
            getCommand("adminpanel").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        persist();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(getPermission("reload"))) {
                sender.sendMessage(formatColor("messages.no-permission"));
                return true;
            }
            reloadConfig();
            config = getConfig();
            sender.sendMessage(formatColor("messages.config-reloaded"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(formatColor("messages.only-player"));
            return true;
        }
        if (!player.hasPermission(getPermission("use"))) {
            player.sendMessage(formatColor("messages.no-permission"));
            return true;
        }
        player.openInventory(createPlayerListMenu(player, 0));
        return true;
    }

    private Inventory createPlayerListMenu(Player viewer, int page) {
        List<Player> others = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(viewer)) {
                others.add(target);
            }
        }
        int totalPages = Math.max(1, (int) Math.ceil(others.size() / (double) PLAYER_PAGE_SIZE));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        playerListPage.put(viewer.getUniqueId(), clampedPage);

        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("BetterAdmin Panel"));

        int start = clampedPage * PLAYER_PAGE_SIZE;
        int end = Math.min(start + PLAYER_PAGE_SIZE, others.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            Player target = others.get(i);
            inventory.setItem(slot, createMenuItem(Material.PLAYER_HEAD,
                    Component.text(target.getName()).color(NamedTextColor.GREEN),
                    Component.text("Click to manage this player").color(NamedTextColor.GRAY),
                    "manage", target.getName()));
            slot += 2;
            if (slot % 9 == 0) {
                slot++;
            }
        }

        if (clampedPage > 0) {
            inventory.setItem(45, createMenuItem(Material.ARROW,
                    Component.text("Previous Page").color(NamedTextColor.YELLOW),
                    Component.text("Page " + clampedPage + " / " + totalPages).color(NamedTextColor.GRAY),
                    "player-prev", ""));
        }
        boolean vanished = vanishedPlayers.contains(viewer.getUniqueId());
        inventory.setItem(46, createMenuItem(vanished ? Material.ENDER_EYE : Material.GLASS,
                vanished ? Component.text("Vanish: ON").color(NamedTextColor.LIGHT_PURPLE)
                        : Component.text("Vanish: OFF").color(NamedTextColor.GRAY),
                Component.text("Click to toggle your visibility").color(NamedTextColor.GRAY),
                "vanish", ""));
        inventory.setItem(47, createMenuItem(Material.BOOK,
                Component.text("Lookup Player").color(NamedTextColor.AQUA),
                Component.text("Search by name or UUID").color(NamedTextColor.GRAY),
                "lookup", ""));
        inventory.setItem(49, createMenuItem(Material.PAPER,
                Component.text("View History").color(NamedTextColor.YELLOW),
                Component.text("Inspect recent admin actions").color(NamedTextColor.GRAY),
                "history", ""));
        inventory.setItem(51, createMenuItem(Material.BARRIER,
                Component.text("Close Panel").color(NamedTextColor.RED),
                Component.text("Close the admin panel").color(NamedTextColor.GRAY),
                "close", ""));
        if (clampedPage < totalPages - 1) {
            inventory.setItem(53, createMenuItem(Material.ARROW,
                    Component.text("Next Page").color(NamedTextColor.YELLOW),
                    Component.text("Page " + (clampedPage + 2) + " / " + totalPages).color(NamedTextColor.GRAY),
                    "player-next", ""));
        }

        return inventory;
    }

    private Inventory createActionMenu(Player viewer, String targetName, boolean online, UUID targetUuid) {
        Inventory inventory = Bukkit.createInventory(null, 27,
                Component.text("Manage: ").append(Component.text(targetName).color(NamedTextColor.AQUA)));
        if (online) {
            inventory.setItem(10, createMenuItem(Material.REDSTONE_TORCH,
                    Component.text("Kick Player").color(NamedTextColor.RED),
                    Component.text("Kick with a custom reason").color(NamedTextColor.GRAY),
                    "kick", targetName));
            inventory.setItem(12, createMenuItem(Material.LEVER,
                    Component.text("Ban Player").color(NamedTextColor.DARK_RED),
                    Component.text("Ban with a custom reason").color(NamedTextColor.GRAY),
                    "ban", targetName));
            inventory.setItem(14, createMenuItem(Material.RED_BED,
                    Component.text("Temp Ban").color(NamedTextColor.DARK_PURPLE),
                    Component.text("Temporary ban with duration").color(NamedTextColor.GRAY),
                    "tempban", targetName));
            inventory.setItem(16, createMenuItem(Material.CHEST,
                    Component.text("View Inventory").color(NamedTextColor.GOLD),
                    Component.text("Inspect their inventory").color(NamedTextColor.GRAY),
                    "view-inv", targetName));
            inventory.setItem(18, createMenuItem(Material.ENDER_CHEST,
                    Component.text("View Ender Chest").color(NamedTextColor.LIGHT_PURPLE),
                    Component.text("Inspect their ender chest").color(NamedTextColor.GRAY),
                    "view-ender", targetName));
            boolean frozen = targetUuid != null && frozenPlayers.containsKey(targetUuid);
            inventory.setItem(20, createMenuItem(frozen ? Material.PACKED_ICE : Material.SNOWBALL,
                    frozen ? Component.text("Unfreeze Player").color(NamedTextColor.AQUA)
                            : Component.text("Freeze Player").color(NamedTextColor.AQUA),
                    frozen ? Component.text("Allow movement again").color(NamedTextColor.GRAY)
                            : Component.text("Prevent movement until unfrozen").color(NamedTextColor.GRAY),
                    "freeze", targetName));
            boolean muted = targetUuid != null && mutedPlayers.containsKey(targetUuid);
            inventory.setItem(22, createMenuItem(Material.LEATHER_BOOTS,
                    Component.text(muted ? "Muted" : "Mute Player").color(NamedTextColor.BLUE),
                    Component.text("Mute chat for this player").color(NamedTextColor.GRAY),
                    "mute", targetName));
            inventory.setItem(24, createMenuItem(Material.FEATHER,
                    Component.text("Unmute Player").color(NamedTextColor.YELLOW),
                    Component.text("Allow chat again").color(NamedTextColor.GRAY),
                    "unmute", targetName));
            inventory.setItem(26, createMenuItem(Material.GOLDEN_APPLE,
                    Component.text("Heal Player").color(NamedTextColor.LIGHT_PURPLE),
                    Component.text("Restore health and saturation").color(NamedTextColor.GRAY),
                    "heal", targetName));
            inventory.setItem(8, createMenuItem(Material.COOKED_BEEF,
                    Component.text("Feed Player").color(NamedTextColor.GREEN),
                    Component.text("Fill their hunger bar").color(NamedTextColor.GRAY),
                    "feed", targetName));
            inventory.setItem(2, createMenuItem(Material.ENDER_PEARL,
                    Component.text("Teleport to Player").color(NamedTextColor.LIGHT_PURPLE),
                    Component.text("Teleport to this player").color(NamedTextColor.GRAY),
                    "teleport", targetName));
            inventory.setItem(4, createMenuItem(Material.OAK_SIGN,
                    Component.text("Player Info").color(NamedTextColor.WHITE),
                    Component.text("See health, food, gamemode, ping").color(NamedTextColor.GRAY),
                    "info", targetName));
        } else {
            inventory.setItem(10, createMenuItem(Material.LEVER,
                    Component.text("Ban Player").color(NamedTextColor.DARK_RED),
                    Component.text("Ban an offline player").color(NamedTextColor.GRAY),
                    "ban", targetName));
            inventory.setItem(12, createMenuItem(Material.RED_BED,
                    Component.text("Temp Ban").color(NamedTextColor.DARK_PURPLE),
                    Component.text("Temporary ban while offline").color(NamedTextColor.GRAY),
                    "tempban", targetName));
            inventory.setItem(14, createMenuItem(Material.OAK_SIGN,
                    Component.text("View Info").color(NamedTextColor.WHITE),
                    Component.text("See basic stored player info").color(NamedTextColor.GRAY),
                    "info", targetName));
        }
        inventory.setItem(0, createMenuItem(Material.BARRIER,
                Component.text("Back").color(NamedTextColor.RED),
                Component.text("Return to the player list").color(NamedTextColor.GRAY),
                "back", ""));
        return inventory;
    }

    private Inventory createInventoryViewer(Player target) {
        Inventory viewerInv = Bukkit.createInventory(null, 54,
                Component.text(target.getName()).append(Component.text("'s Inventory")).color(NamedTextColor.GOLD));

        ItemStack[] armor = target.getInventory().getArmorContents();
        if (armor != null) {
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null) {
                    viewerInv.setItem(9 + i, armor[i].clone());
                }
            }
        }
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < contents.length && i < 36; i++) {
            if (contents[i] != null) {
                viewerInv.setItem(i, contents[i].clone());
            }
        }
        viewerInv.setItem(45, createMenuItem(Material.OAK_SIGN,
                Component.text("Player: ").color(NamedTextColor.GRAY)
                        .append(Component.text(target.getName()).color(NamedTextColor.AQUA)),
                Component.text("Viewing inventory snapshot").color(NamedTextColor.GRAY),
                "info", ""));
        viewerInv.setItem(53, createMenuItem(Material.BARRIER,
                Component.text("Close").color(NamedTextColor.RED),
                Component.text("Close the inventory viewer").color(NamedTextColor.GRAY),
                "close", ""));
        return viewerInv;
    }

    private Inventory createEnderChestViewer(Player target) {
        Inventory viewerInv = Bukkit.createInventory(null, 54,
                Component.text(target.getName()).append(Component.text("'s Ender Chest")).color(NamedTextColor.LIGHT_PURPLE));
        ItemStack[] contents = target.getEnderChest().getContents();
        for (int i = 0; i < contents.length && i < 54; i++) {
            if (contents[i] != null) {
                viewerInv.setItem(i, contents[i].clone());
            }
        }
        viewerInv.setItem(52, createMenuItem(Material.OAK_SIGN,
                Component.text("Player: ").color(NamedTextColor.GRAY)
                        .append(Component.text(target.getName()).color(NamedTextColor.AQUA)),
                Component.text("Viewing ender chest snapshot").color(NamedTextColor.GRAY),
                "info", ""));
        viewerInv.setItem(53, createMenuItem(Material.BARRIER,
                Component.text("Close").color(NamedTextColor.RED),
                Component.text("Close the ender chest viewer").color(NamedTextColor.GRAY),
                "close", ""));
        return viewerInv;
    }

    private Inventory createHistoryMenu(Player viewer, int page) {
        int totalPages = Math.max(1, (int) Math.ceil(historyEntries.size() / (double) HISTORY_PAGE_SIZE));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        historyPage.put(viewer.getUniqueId(), clampedPage);

        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("BetterAdmin History"));

        if (historyEntries.isEmpty()) {
            inventory.setItem(13, createMenuItem(Material.PAPER,
                    Component.text(getMessage("messages.no-history")).color(NamedTextColor.GRAY),
                    Component.text("No history entries yet.").color(NamedTextColor.DARK_GRAY),
                    "info", ""));
        } else {
            int start = clampedPage * HISTORY_PAGE_SIZE;
            int end = Math.min(start + HISTORY_PAGE_SIZE, historyEntries.size());
            for (int i = start; i < end; i++) {
                AdminHistoryEntry entry = historyEntries.get(i);
                int entrySlot = 10 + (i - start);

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(DateTimeFormatter.ISO_INSTANT.format(entry.timestamp()))
                        .color(NamedTextColor.DARK_GRAY));
                lore.add(entry.actor() != null
                        ? Component.text(entry.actor()).color(NamedTextColor.GREEN)
                                .append(Component.text(" -> ").color(NamedTextColor.GRAY))
                                .append(Component.text(entry.target()).color(NamedTextColor.AQUA))
                        : Component.text(entry.target()).color(NamedTextColor.AQUA));
                if (!entry.reason().isEmpty()) {
                    lore.add(Component.text("Reason: " + entry.reason()).color(NamedTextColor.GRAY));
                }
                if (entry.undone()) {
                    lore.add(Component.text("Reverted").color(NamedTextColor.RED));
                }

                inventory.setItem(entrySlot, createMenuItem(Material.PAPER,
                        Component.text(entry.action().toUpperCase()).color(NamedTextColor.YELLOW),
                        lore, "info", ""));

                if (entry.isUndoable()) {
                    inventory.setItem(entrySlot + 9, createMenuItem(Material.ORANGE_DYE,
                            Component.text("Undo").color(NamedTextColor.GOLD),
                            Component.text("Reverse this action").color(NamedTextColor.GRAY),
                            "undo-history", String.valueOf(i)));
                }
            }
        }

        if (clampedPage > 0) {
            inventory.setItem(45, createMenuItem(Material.ARROW,
                    Component.text("Previous Page").color(NamedTextColor.YELLOW),
                    Component.text("Page " + clampedPage + " / " + totalPages).color(NamedTextColor.GRAY),
                    "history-prev", ""));
        }
        inventory.setItem(49, createMenuItem(Material.BARRIER,
                Component.text("Back").color(NamedTextColor.RED),
                Component.text("Return to the player list").color(NamedTextColor.GRAY),
                "back", ""));
        if (clampedPage < totalPages - 1) {
            inventory.setItem(53, createMenuItem(Material.ARROW,
                    Component.text("Next Page").color(NamedTextColor.YELLOW),
                    Component.text("Page " + (clampedPage + 2) + " / " + totalPages).color(NamedTextColor.GRAY),
                    "history-next", ""));
        }
        return inventory;
    }

    private Inventory createConfirmMenu(Component summary) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("BetterAdmin Confirm"));
        inventory.setItem(13, createMenuItem(Material.PAPER,
                Component.text("Confirm this action?").color(NamedTextColor.GOLD),
                summary, "info", ""));
        inventory.setItem(11, createMenuItem(Material.LIME_WOOL,
                Component.text("Confirm").color(NamedTextColor.GREEN),
                Component.text("Yes, proceed").color(NamedTextColor.GRAY),
                "confirm", ""));
        inventory.setItem(15, createMenuItem(Material.RED_WOOL,
                Component.text("Cancel").color(NamedTextColor.RED),
                Component.text("No, go back").color(NamedTextColor.GRAY),
                "cancel-confirm", ""));
        return inventory;
    }

    private void openConfirmation(Player player, Component summary, Runnable onConfirm) {
        pendingConfirmations.put(player.getUniqueId(), new PendingConfirmation(onConfirm));
        player.openInventory(createConfirmMenu(summary));
    }

    private ItemStack createMenuItem(Material material, Component name, Component lore, String action, String targetName) {
        return createMenuItem(material, name, List.of(lore), action, targetName);
    }

    private ItemStack createMenuItem(Material material, Component name, List<Component> lore, String action, String targetName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, targetName);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getPermission(String node) {
        return config.getString("permissions." + node, "betteradmin." + node);
    }

    private String getMessage(String path) {
        return config.getString(path, "Missing message: " + path);
    }

    private Component formatColor(String path) {
        return Component.text(getMessage(path)).color(NamedTextColor.WHITE);
    }

    private Component formatColor(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return Component.text(message).color(NamedTextColor.WHITE);
    }

    private String getAction(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
            return "";
        }
        return meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String getTarget(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(targetKey, PersistentDataType.STRING)) {
            return "";
        }
        return meta.getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
    }

    private void addHistory(Player actor, String action, String target, String targetUuid, String reason, String extra) {
        historyEntries.add(0, new AdminHistoryEntry(Instant.now(), actor.getName(), action, target, targetUuid, reason, extra));
        if (historyEntries.size() > 21) {
            historyEntries.remove(historyEntries.size() - 1);
        }
        persist();
    }

    private void persist() {
        if (dataStore != null) {
            dataStore.save(mutedPlayers, frozenPlayers, historyEntries);
        }
    }

    private void undoHistoryEntry(Player admin, AdminHistoryEntry entry) {
        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(entry.targetUuid());
        } catch (IllegalArgumentException ex) {
            return;
        }
        switch (entry.action()) {
            case "ban", "tempban" ->
                    Bukkit.getBanList(io.papermc.paper.ban.BanListType.PROFILE).pardon(Bukkit.createProfile(targetUuid));
            case "mute" -> mutedPlayers.remove(targetUuid);
            case "freeze" -> frozenPlayers.remove(targetUuid);
            default -> {
                return;
            }
        }
        entry.markUndone();
        admin.sendMessage(formatColor("messages.undo-success",
                Map.of("target", entry.target(), "action", entry.action())));
        addHistory(admin, "undo-" + entry.action(), entry.target(), entry.targetUuid(), "", "");
    }

    private void toggleVanish(Player player) {
        UUID uuid = player.getUniqueId();
        boolean nowVanished = !vanishedPlayers.contains(uuid);
        if (nowVanished) {
            vanishedPlayers.add(uuid);
        } else {
            vanishedPlayers.remove(uuid);
        }
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (nowVanished) {
                other.hidePlayer(this, player);
            } else {
                other.showPlayer(this, player);
            }
        }
        player.sendMessage(nowVanished ? formatColor("messages.vanish-on") : formatColor("messages.vanish-off"));
    }

    private void executeBan(Player admin, OfflinePlayer targetOffline, String name, UUID targetUuid, String reason) {
        targetOffline.ban(reason, (Instant) null, admin.getName());
        if (targetOffline.isOnline()) {
            ((Player) targetOffline).kick(Component.text("Banned: ").append(Component.text(reason)));
        }
        admin.sendMessage(formatColor("messages.banned", Map.of("target", name, "reason", reason)));
        addHistory(admin, "ban", name, targetUuid.toString(), reason, "");
    }

    private void executeTempban(Player admin, OfflinePlayer targetOffline, String name, UUID targetUuid,
            String reason, Duration duration, Instant expiry) {
        targetOffline.ban(reason, expiry, admin.getName());
        if (targetOffline.isOnline()) {
            ((Player) targetOffline).kick(Component.text("Temporarily banned until ")
                    .append(Component.text(expiry.toString())).append(Component.text(": ")).append(Component.text(reason)));
        }
        admin.sendMessage(formatColor("messages.tempbanned", Map.of(
                "target", name,
                "duration", duration.toString(),
                "reason", reason)));
        addHistory(admin, "tempban", name, targetUuid.toString(), reason, "expires=" + expiry);
    }

    private Duration parseDuration(String token) {
        long amount;
        try {
            if (token.endsWith("d")) {
                amount = Long.parseLong(token.substring(0, token.length() - 1));
                return Duration.ofDays(amount);
            }
            if (token.endsWith("h")) {
                amount = Long.parseLong(token.substring(0, token.length() - 1));
                return Duration.ofHours(amount);
            }
            if (token.endsWith("m")) {
                amount = Long.parseLong(token.substring(0, token.length() - 1));
                return Duration.ofMinutes(amount);
            }
            if (token.endsWith("s")) {
                amount = Long.parseLong(token.substring(0, token.length() - 1));
                return Duration.ofSeconds(amount);
            }
            amount = Long.parseLong(token);
            return Duration.ofMinutes(amount);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private OfflinePlayer resolvePlayer(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        OfflinePlayer onlinePlayer = Bukkit.getPlayerExact(query);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        try {
            UUID uuid = UUID.fromString(query);
            return Bukkit.getOfflinePlayer(uuid);
        } catch (IllegalArgumentException ignored) {
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(query);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            return offlinePlayer;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }
        String title = event.getView().title().toString();
        if (!title.contains("BetterAdmin") && !title.contains("Manage:")
                && !title.contains("Inventory") && !title.contains("Ender Chest")) {
            return;
        }
        event.setCancelled(true);
        String action = getAction(event.getCurrentItem());
        String targetName = getTarget(event.getCurrentItem());
        if (action.isEmpty()) {
            return;
        }

        switch (action) {
            case "manage" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                OfflinePlayer target = resolvePlayer(targetName);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    player.closeInventory();
                    return;
                }
                player.openInventory(createActionMenu(player, target.getName(), true, target.getUniqueId()));
            }
            case "refresh" -> player.openInventory(
                    createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0)));
            case "player-prev" -> player.openInventory(
                    createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0) - 1));
            case "player-next" -> player.openInventory(
                    createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0) + 1));
            case "vanish" -> {
                if (!player.hasPermission(getPermission("vanish"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                toggleVanish(player);
                player.openInventory(
                        createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0)));
            }
            case "lookup" -> {
                if (!player.hasPermission(getPermission("lookup"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                pendingActions.put(player.getUniqueId(), new PendingAction("lookup", ""));
                player.closeInventory();
                player.sendMessage(formatColor("messages.lookup-prompt"));
            }
            case "history" -> {
                if (!player.hasPermission(getPermission("history"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                player.openInventory(createHistoryMenu(player, 0));
            }
            case "history-prev" -> player.openInventory(
                    createHistoryMenu(player, historyPage.getOrDefault(player.getUniqueId(), 0) - 1));
            case "history-next" -> player.openInventory(
                    createHistoryMenu(player, historyPage.getOrDefault(player.getUniqueId(), 0) + 1));
            case "undo-history" -> {
                if (!player.hasPermission(getPermission("undo"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(targetName);
                } catch (NumberFormatException ex) {
                    return;
                }
                if (index < 0 || index >= historyEntries.size()) {
                    return;
                }
                AdminHistoryEntry entry = historyEntries.get(index);
                if (!entry.isUndoable()) {
                    return;
                }
                undoHistoryEntry(player, entry);
                player.openInventory(createHistoryMenu(player, historyPage.getOrDefault(player.getUniqueId(), 0)));
            }
            case "close" -> player.closeInventory();
            case "back" -> player.openInventory(
                    createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0)));
            case "confirm" -> {
                PendingConfirmation pending = pendingConfirmations.remove(player.getUniqueId());
                player.closeInventory();
                if (pending != null) {
                    pending.onConfirm().run();
                }
            }
            case "cancel-confirm" -> {
                pendingConfirmations.remove(player.getUniqueId());
                player.sendMessage(formatColor("messages.action-cancelled"));
                player.openInventory(
                        createPlayerListMenu(player, playerListPage.getOrDefault(player.getUniqueId(), 0)));
            }
            case "kick" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                if (!player.hasPermission(getPermission("kick"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                OfflinePlayer target = resolvePlayer(targetName);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                pendingActions.put(player.getUniqueId(), new PendingAction("kick", target.getUniqueId().toString()));
                player.closeInventory();
                player.sendMessage(formatColor("messages.reason-prompt"));
            }
            case "ban", "tempban" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                if (!player.hasPermission(getPermission(action))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                OfflinePlayer target = resolvePlayer(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                pendingActions.put(player.getUniqueId(), new PendingAction(action, target.getUniqueId().toString()));
                player.closeInventory();
                player.sendMessage(formatColor(action.equals("tempban") ? "messages.tempban-prompt" : "messages.reason-prompt"));
            }
            case "mute" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                if (!player.hasPermission(getPermission("mute"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                OfflinePlayer target = resolvePlayer(targetName);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (mutedPlayers.containsKey(target.getUniqueId())) {
                    player.sendMessage(formatColor("messages.already-muted", Map.of("target", target.getName())));
                    return;
                }
                pendingActions.put(player.getUniqueId(), new PendingAction("mute", target.getUniqueId().toString()));
                player.closeInventory();
                player.sendMessage(formatColor("messages.mute-prompt"));
            }
            case "view-inv" -> {
                if (!player.hasPermission(getPermission("viewinventory"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.not-online-for-inv"));
                    return;
                }
                player.openInventory(createInventoryViewer(target));
            }
            case "view-ender" -> {
                if (!player.hasPermission(getPermission("viewenderchest"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.not-online-for-inv"));
                    return;
                }
                player.openInventory(createEnderChestViewer(target));
            }
            case "teleport" -> {
                if (!player.hasPermission(getPermission("teleport"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                player.teleportAsync(target.getLocation()).thenRun(() -> player.sendMessage(formatColor("messages.teleported",
                        Map.of("target", target.getName()))));
            }
            case "freeze" -> {
                if (!player.hasPermission(getPermission("freeze"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (frozenPlayers.remove(target.getUniqueId()) != null) {
                    player.sendMessage(formatColor("messages.player-unfrozen", Map.of("target", target.getName())));
                    addHistory(player, "unfreeze", target.getName(), target.getUniqueId().toString(), "", "");
                } else {
                    frozenPlayers.put(target.getUniqueId(), Instant.now());
                    player.sendMessage(formatColor("messages.player-frozen", Map.of("target", target.getName())));
                    addHistory(player, "freeze", target.getName(), target.getUniqueId().toString(), "", "");
                }
                player.openInventory(createActionMenu(player, target.getName(), true, target.getUniqueId()));
            }
            case "unmute" -> {
                if (!player.hasPermission(getPermission("unmute"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (mutedPlayers.remove(target.getUniqueId()) == null) {
                    player.sendMessage(formatColor("messages.already-unmuted", Map.of("target", target.getName())));
                    return;
                }
                player.sendMessage(formatColor("messages.unmuted", Map.of("target", target.getName())));
                addHistory(player, "unmute", target.getName(), target.getUniqueId().toString(), "", "");
            }
            case "heal" -> {
                if (!player.hasPermission(getPermission("heal"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                target.setFoodLevel(20);
                target.setSaturation(20f);
                player.sendMessage(formatColor("messages.healed", Map.of("target", target.getName())));
                addHistory(player, "heal", target.getName(), target.getUniqueId().toString(), "", "");
            }
            case "feed" -> {
                if (!player.hasPermission(getPermission("feed"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(targetName);
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                target.setFoodLevel(20);
                target.setSaturation(20f);
                player.sendMessage(formatColor("messages.fed", Map.of("target", target.getName())));
                addHistory(player, "feed", target.getName(), target.getUniqueId().toString(), "", "");
            }
            case "info" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                OfflinePlayer offlineTarget = resolvePlayer(targetName);
                if (offlineTarget == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (offlineTarget.isOnline()) {
                    Player onlineTarget = (Player) offlineTarget;
                    player.sendMessage(formatColor("messages.info-player", Map.of(
                            "target", onlineTarget.getName(),
                            "health", String.valueOf((int) onlineTarget.getHealth()),
                            "food", String.valueOf(onlineTarget.getFoodLevel()),
                            "gamemode", onlineTarget.getGameMode().name().toLowerCase(),
                            "ping", String.valueOf(onlineTarget.getPing())
                    )));
                } else {
                    player.sendMessage(formatColor("messages.lookup-result-offline", Map.of("target", offlineTarget.getName() == null ? targetName : offlineTarget.getName())));
                }
            }
            default -> {
                // no-op
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (mutedPlayers.containsKey(player.getUniqueId())) {
            MuteEntry mute = mutedPlayers.get(player.getUniqueId());
            if (mute.expires().isAfter(Instant.now())) {
                event.setCancelled(true);
                player.sendMessage(formatColor("messages.mute-chat-block", Map.of(
                        "reason", mute.reason(),
                        "until", DateTimeFormatter.ISO_INSTANT.format(mute.expires())
                )));
                return;
            }
            mutedPlayers.remove(player.getUniqueId());
            persist();
        }

        PendingAction pending = pendingActions.get(player.getUniqueId());
        if (pending == null) {
            return;
        }

        event.setCancelled(true);
        pendingActions.remove(player.getUniqueId());
        String input = event.getMessage();

        switch (pending.action()) {
            case "lookup" -> {
                OfflinePlayer target = resolvePlayer(input);
                if (target == null) {
                    player.sendMessage(formatColor("messages.lookup-failed"));
                    return;
                }
                String name = target.getName() != null ? target.getName() : input;
                player.openInventory(createActionMenu(player, name, target.isOnline(), target.getUniqueId()));
                player.sendMessage(formatColor("messages.lookup-opened", Map.of("target", name)));
            }
            case "kick" -> {
                String reason = getReason(input);
                OfflinePlayer targetOffline = resolvePlayer(pending.targetName());
                if (targetOffline == null || !targetOffline.isOnline()) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                Player target = (Player) targetOffline;
                target.kick(Component.text(reason));
                player.sendMessage(formatColor("messages.kicked", Map.of("target", target.getName(), "reason", reason)));
                addHistory(player, "kick", target.getName(), target.getUniqueId().toString(), reason, "");
            }
            case "ban" -> {
                String reason = getReason(input);
                OfflinePlayer targetOffline = resolvePlayer(pending.targetName());
                if (targetOffline == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                String name = targetOffline.getName() != null ? targetOffline.getName() : pending.targetName();
                UUID targetUuid = targetOffline.getUniqueId();
                Component summary = Component.text("Ban ").color(NamedTextColor.GRAY)
                        .append(Component.text(name).color(NamedTextColor.AQUA))
                        .append(Component.text(" — " + reason).color(NamedTextColor.GRAY));
                openConfirmation(player, summary, () -> executeBan(player, targetOffline, name, targetUuid, reason));
            }
            case "tempban" -> {
                String[] parts = input.split(" ", 2);
                Duration duration = parseDuration(parts[0]);
                if (duration == null) {
                    player.sendMessage(formatColor("messages.invalid-duration"));
                    return;
                }
                String reason = parts.length > 1 ? parts[1] : "Temporary ban by administration.";
                OfflinePlayer targetOffline = resolvePlayer(pending.targetName());
                if (targetOffline == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                String name = targetOffline.getName() != null ? targetOffline.getName() : pending.targetName();
                UUID targetUuid = targetOffline.getUniqueId();
                Instant expiry = Instant.now().plus(duration);
                Component summary = Component.text("Tempban ").color(NamedTextColor.GRAY)
                        .append(Component.text(name).color(NamedTextColor.AQUA))
                        .append(Component.text(" for " + duration + " — " + reason).color(NamedTextColor.GRAY));
                openConfirmation(player, summary,
                        () -> executeTempban(player, targetOffline, name, targetUuid, reason, duration, expiry));
            }
            case "mute" -> {
                String[] parts = input.split(" ", 2);
                Duration duration = parseDuration(parts[0]);
                if (duration == null) {
                    player.sendMessage(formatColor("messages.invalid-duration"));
                    return;
                }
                String reason = parts.length > 1 ? parts[1] : "Muted by administration.";
                Player target = Bukkit.getPlayer(UUID.fromString(pending.targetName()));
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                Instant until = Instant.now().plus(duration);
                mutedPlayers.put(target.getUniqueId(), new MuteEntry(until, reason));
                player.sendMessage(formatColor("messages.muted", Map.of(
                        "target", target.getName(),
                        "duration", duration.toString())));
                addHistory(player, "mute", target.getName(), target.getUniqueId().toString(), reason, "expires=" + until);
            }
            default -> {
                player.sendMessage(Component.text("Unknown pending action.").color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!frozenPlayers.containsKey(player.getUniqueId())) {
            return;
        }
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!mutedPlayers.containsKey(player.getUniqueId())) {
            return;
        }
        MuteEntry mute = mutedPlayers.get(player.getUniqueId());
        if (mute.expires().isAfter(Instant.now())) {
            event.setCancelled(true);
            player.sendMessage(formatColor("messages.mute-chat-block", Map.of(
                    "reason", mute.reason(),
                    "until", DateTimeFormatter.ISO_INSTANT.format(mute.expires())
            )));
        } else {
            mutedPlayers.remove(player.getUniqueId());
            persist();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID vanishedId : vanishedPlayers) {
            Player vanishedPlayer = Bukkit.getPlayer(vanishedId);
            if (vanishedPlayer != null && !vanishedPlayer.equals(joined)) {
                joined.hidePlayer(this, vanishedPlayer);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pendingActions.remove(player.getUniqueId());
        pendingConfirmations.remove(player.getUniqueId());
        vanishedPlayers.remove(player.getUniqueId());
        playerListPage.remove(player.getUniqueId());
        historyPage.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            pendingActions.remove(player.getUniqueId());
        }
    }

    private String getReason(String input) {
        if (input.equalsIgnoreCase("default")) {
            return "Action performed by server administration.";
        }
        return input;
    }

    private record PendingAction(String action, String targetName) {
    }

    private record PendingConfirmation(Runnable onConfirm) {
    }

    record MuteEntry(Instant expires, String reason) {
    }
}
