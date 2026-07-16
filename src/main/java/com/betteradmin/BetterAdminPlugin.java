package com.betteradmin;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
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
    private final Map<UUID, PendingAction> pendingActions = new HashMap<>();
    private final Map<UUID, MuteEntry> mutedPlayers = new HashMap<>();
    private final Map<UUID, Instant> frozenPlayers = new HashMap<>();
    private final List<AdminHistoryEntry> historyEntries = new ArrayList<>();

    private NamespacedKey actionKey;
    private NamespacedKey targetKey;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        actionKey = new NamespacedKey(this, "betteradmin-action");
        targetKey = new NamespacedKey(this, "betteradmin-target");

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("adminpanel") != null) {
            getCommand("adminpanel").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(formatColor("messages.only-player"));
            return true;
        }
        if (!player.hasPermission(getPermission("use"))) {
            player.sendMessage(formatColor("messages.no-permission"));
            return true;
        }
        player.openInventory(createPlayerListMenu(player));
        return true;
    }

    private Inventory createPlayerListMenu(Player viewer) {
        Inventory inventory = Bukkit.createInventory(null, 54, Component.text("BetterAdmin Panel"));

        int slot = 10;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) {
                continue;
            }
            if (slot >= 53) {
                break;
            }
            inventory.setItem(slot, createMenuItem(Material.PLAYER_HEAD,
                    Component.text(target.getName()).color(NamedTextColor.GREEN),
                    Component.text("Click to manage this player").color(NamedTextColor.GRAY),
                    "manage", target.getName()));
            slot += 2;
            if (slot % 9 == 0) {
                slot++;
            }
        }

        inventory.setItem(45, createMenuItem(Material.COMPASS,
                Component.text("Refresh Menu").color(NamedTextColor.GOLD),
                Component.text("Click to refresh online players").color(NamedTextColor.GRAY),
                "refresh", ""));
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

        return inventory;
    }

    private Inventory createActionMenu(Player viewer, String targetName, boolean online) {
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
            inventory.setItem(20, createMenuItem(Material.SNOWBALL,
                    Component.text("Freeze Player").color(NamedTextColor.AQUA),
                    Component.text("Prevent movement until unfrozen").color(NamedTextColor.GRAY),
                    "freeze", targetName));
            inventory.setItem(22, createMenuItem(Material.LEATHER_BOOTS,
                    Component.text("Mute Player").color(NamedTextColor.BLUE),
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
        inventory.setItem(22, createMenuItem(Material.BARRIER,
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

    private Inventory createHistoryMenu(Player viewer) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("BetterAdmin History"));
        if (historyEntries.isEmpty()) {
            inventory.setItem(13, createMenuItem(Material.PAPER,
                    Component.text(getMessage("messages.no-history")).color(NamedTextColor.GRAY),
                    Component.text("No history entries yet.").color(NamedTextColor.DARK_GRAY),
                    "info", ""));
        } else {
            int slot = 10;
            for (AdminHistoryEntry entry : historyEntries) {
                if (slot >= 26) {
                    break;
                }
                Component name = Component.text(entry.timestamp().toString()).color(NamedTextColor.GRAY);
                Component lore = Component.text(entry.actor()).color(NamedTextColor.GREEN)
                        .append(Component.text(" -> ").color(NamedTextColor.GRAY))
                        .append(Component.text(entry.action()).color(NamedTextColor.YELLOW))
                        .append(Component.text(" ")).append(Component.text(entry.target()).color(NamedTextColor.AQUA));
                inventory.setItem(slot,
                        createMenuItem(Material.PAPER, name, lore, "info", ""));
                slot++;
                if ((slot % 9) == 0) {
                    slot++;
                }
            }
        }
        inventory.setItem(22, createMenuItem(Material.BARRIER,
                Component.text("Back").color(NamedTextColor.RED),
                Component.text("Return to the player list").color(NamedTextColor.GRAY),
                "back", ""));
        return inventory;
    }

    private ItemStack createMenuItem(Material material, Component name, Component lore, String action, String targetName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            meta.lore(List.of(lore));
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

    private void addHistory(Player actor, String action, String target, String reason, String extra) {
        historyEntries.add(0, new AdminHistoryEntry(Instant.now(), actor.getName(), action, target, reason, extra));
        if (historyEntries.size() > 21) {
            historyEntries.remove(historyEntries.size() - 1);
        }
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
        if (!title.contains("BetterAdmin") && !title.contains("Manage:") && !title.contains("History")
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
                player.openInventory(createActionMenu(player, target.getName(), true));
            }
            case "refresh" -> player.openInventory(createPlayerListMenu(player));
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
                player.openInventory(createHistoryMenu(player));
            }
            case "close" -> player.closeInventory();
            case "back" -> player.openInventory(createPlayerListMenu(player));
            case "kick", "ban", "tempban", "mute" -> {
                if (targetName.isEmpty()) {
                    return;
                }
                OfflinePlayer target = resolvePlayer(targetName);
                if (target == null || !target.isOnline()) {
                    if (action.equals("ban") || action.equals("tempban")) {
                        pendingActions.put(player.getUniqueId(), new PendingAction(action, targetName));
                        player.closeInventory();
                        player.sendMessage(formatColor("messages.reason-prompt"));
                        return;
                    }
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (action.equals("kick") && !player.hasPermission(getPermission("kick"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                if (action.equals("ban") && !player.hasPermission(getPermission("ban"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                if (action.equals("tempban") && !player.hasPermission(getPermission("tempban"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                if (action.equals("mute") && !player.hasPermission(getPermission("mute"))) {
                    player.sendMessage(formatColor("messages.no-permission"));
                    return;
                }
                pendingActions.put(player.getUniqueId(), new PendingAction(action, target.getUniqueId().toString()));
                player.closeInventory();
                if (action.equals("tempban")) {
                    player.sendMessage(formatColor("messages.tempban-prompt"));
                } else if (action.equals("mute")) {
                    player.sendMessage(formatColor("messages.mute-prompt"));
                } else {
                    player.sendMessage(formatColor("messages.reason-prompt"));
                }
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
                frozenPlayers.put(target.getUniqueId(), Instant.now());
                player.sendMessage(formatColor("messages.player-frozen", Map.of("target", target.getName())));
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
                target.setHealth(target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                target.setFoodLevel(20);
                target.setSaturation(20f);
                player.sendMessage(formatColor("messages.healed", Map.of("target", target.getName())));
                addHistory(player, "heal", target.getName(), "", "");
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
                addHistory(player, "feed", target.getName(), "", "");
            }
            case "info" -> {
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
                player.openInventory(createActionMenu(player, name, target.isOnline()));
                player.sendMessage(formatColor("messages.lookup-opened", Map.of("target", name)));
            }
            case "kick", "ban" -> {
                String reason = getReason(input);
                OfflinePlayer targetOffline = resolvePlayer(pending.targetName());
                if (targetOffline == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                if (pending.action().equals("kick")) {
                    if (!targetOffline.isOnline()) {
                        player.sendMessage(formatColor("messages.player-offline"));
                        return;
                    }
                    Player target = (Player) targetOffline;
                    target.kick(Component.text(reason));
                    player.sendMessage(formatColor("messages.kicked", Map.of("target", target.getName(), "reason", reason)));
                    addHistory(player, "kick", target.getName(), reason, "");
                } else {
                    String name = targetOffline.getName() != null ? targetOffline.getName() : pending.targetName();
                    Bukkit.getServer().getBanList(BanList.Type.NAME).addBan(name, reason, null, player.getName());
                    if (targetOffline.isOnline()) {
                        ((Player) targetOffline).kick(Component.text("Banned: ").append(Component.text(reason)));
                    }
                    player.sendMessage(formatColor("messages.banned", Map.of("target", name, "reason", reason)));
                    addHistory(player, "ban", name, reason, "");
                }
            }
            case "tempban" -> {
                String[] parts = input.split(" ", 2);
                Duration duration = parseDuration(parts[0]);
                if (duration == null) {
                    player.sendMessage(formatColor("messages.invalid-duration"));
                    return;
                }
                String reason = parts.length > 1 ? parts[1] : "Temporary ban by administration.";
                OfflinePlayer target = resolvePlayer(pending.targetName());
                if (target == null) {
                    player.sendMessage(formatColor("messages.player-offline"));
                    return;
                }
                Instant expiry = Instant.now().plus(duration);
                String name = target.getName() != null ? target.getName() : pending.targetName();
                Bukkit.getServer().getBanList(BanList.Type.NAME).addBan(name, reason, java.util.Date.from(expiry), player.getName());
                if (target.isOnline()) {
                    ((Player) target).kick(Component.text("Temporarily banned until ")
                            .append(Component.text(expiry.toString())).append(Component.text(": ")).append(Component.text(reason)));
                }
                player.sendMessage(formatColor("messages.tempbanned", Map.of(
                        "target", name,
                        "duration", duration.toString(),
                        "reason", reason)));
                addHistory(player, "tempban", name, reason, "expires=" + expiry);
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
                addHistory(player, "mute", target.getName(), reason, "expires=" + until);
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
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        pendingActions.remove(player.getUniqueId());
        frozenPlayers.remove(player.getUniqueId());
        mutedPlayers.remove(player.getUniqueId());
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

    private record MuteEntry(Instant expires, String reason) {
    }
}
