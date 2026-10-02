package com.werewolf.plugin;

import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.room.Room;
import com.werewolf.room.RoomManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class RoomMenuListener implements Listener {

    private final WerewolfPlugin plugin;
    private static final String MAIN_MENU_TITLE = "§6§l鲨鱼行动 - 主菜单";
    private static final String ROOM_MENU_TITLE_PREFIX = "§6§l房间: ";

    private final Set<UUID> waitingForRoomName = new HashSet<>();
    private final Set<UUID> waitingForKickTarget = new HashSet<>();

    public RoomMenuListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearWaiting(UUID uuid) {
        waitingForRoomName.remove(uuid);
        waitingForKickTarget.remove(uuid);
    }

    // 房间列表槽位：第一行 10-16，第二行 19-25（共14个）
    private static final int[] ROOM_SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};

    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, MAIN_MENU_TITLE);
        RoomManager rm = plugin.getRoomManager();

        ItemStack createBtn = makeItem(Material.EMERALD_BLOCK, "§a§l创建房间", "§7点击创建一个新房间");
        gui.setItem(4, createBtn);

        List<Room> lobbies = rm.getLobbyRooms();
        for (int i = 0; i < lobbies.size() && i < ROOM_SLOTS.length; i++) {
            gui.setItem(ROOM_SLOTS[i], makeRoomItem(lobbies.get(i)));
        }

        for (int i = 0; i < 9; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        player.openInventory(gui);
    }

    public void openRoomMenu(Player player) {
        RoomManager rm = plugin.getRoomManager();
        Room room = rm.getRoomByPlayer(player);
        if (room == null) { player.sendMessage("§c你不在任何房间中！"); return; }

        String title = ROOM_MENU_TITLE_PREFIX + room.getRoomName();
        Inventory gui = Bukkit.createInventory(null, 54, title);

        List<PlayerData> roomPlayers = room.getPlayers();
        for (int i = 0; i < roomPlayers.size() && i < 21; i++) {
            PlayerData pd = roomPlayers.get(i);
            gui.setItem(i, makePlayerHead(pd));
        }

        for (int i = 36; i < 45; i++) {
            gui.setItem(i, makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        boolean isOwner = player.getName().equalsIgnoreCase(room.getOwnerName());
        boolean canStart = room.getState() == GameState.LOBBY && roomPlayers.size() >= 2;

        if (isOwner) {
            gui.setItem(45, makeItem(Material.GREEN_WOOL, "§a开始游戏",
                    "§7开始房间内所有玩家的游戏" + (canStart ? "" : " (需要至少2人)")));
            gui.setItem(46, makeItem(Material.RED_WOOL, "§c解散房间",
                    "§7解散当前房间"));
            gui.setItem(47, makeItem(Material.ORANGE_WOOL, "§e踢出玩家",
                    "§7点击后在聊天框输入玩家名"));
        }
        gui.setItem(49, makeItem(Material.BARRIER, "§c离开房间",
                "§7离开当前房间"));
        gui.setItem(53, makeItem(Material.ARROW, "§7返回主菜单", ""));

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        int slot = event.getRawSlot();

        if (title.equals(MAIN_MENU_TITLE)) {
            event.setCancelled(true);
            handleMainMenuClick(player, slot);
        } else if (title.startsWith(ROOM_MENU_TITLE_PREFIX)) {
            event.setCancelled(true);
            handleRoomMenuClick(player, slot, title.substring(ROOM_MENU_TITLE_PREFIX.length()));
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        if (slot == 4) {
            waitingForRoomName.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§a请输入房间名称（在聊天框输入）：");
        } else if ((slot >= 10 && slot <= 16) || (slot >= 19 && slot <= 25)) {
            RoomManager rm = plugin.getRoomManager();
            List<Room> lobbies = rm.getLobbyRooms();
            int roomIndex = slot < 19 ? slot - 10 : slot - 12;
            if (roomIndex >= 0 && roomIndex < lobbies.size()) {
                Room room = lobbies.get(roomIndex);
                if (rm.joinRoom(room.getRoomId(), player)) {
                    player.sendMessage("§a已加入房间: §e" + room.getRoomName());
                    openRoomMenu(player);
                } else {
                    // 加入失败（已满/已开局等），刷新列表
                    openMainMenu(player);
                }
            }
        }
    }

    private void handleRoomMenuClick(Player player, int slot, String roomName) {
        RoomManager rm = plugin.getRoomManager();
        Room room = rm.getRoomByPlayer(player);
        if (room == null) { player.closeInventory(); return; }

        boolean isOwner = player.getName().equalsIgnoreCase(room.getOwnerName());

        if (slot == 49) {
            rm.leaveRoom(player);
            player.sendMessage("§a已离开房间！");
            openMainMenu(player);
        } else if (slot == 53) {
            openMainMenu(player);
        } else if (isOwner && slot == 45) {
            if (room.getPlayerCount() < 2) {
                player.sendMessage("§c需要至少2名玩家才能开始！");
                return;
            }
            room.startGame();
            player.closeInventory();
        } else if (isOwner && slot == 46) {
            // deleteRoom 会向房间内所有玩家广播“房间已解散”
            rm.deleteRoom(room.getRoomId());
            openMainMenu(player);
        } else if (isOwner && slot == 47) {
            waitingForKickTarget.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage("§e请输入要踢出的玩家名（在聊天框输入）：");
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (waitingForRoomName.contains(uuid)) {
            event.setCancelled(true);
            waitingForRoomName.remove(uuid);
            String roomName = event.getMessage().trim();
            if (roomName.isEmpty() || roomName.length() > 20) {
                player.sendMessage("§c房间名称有效长度: 1-20字符");
                return;
            }
            if (plugin.getRoomManager().getRoomByPlayer(player) != null) {
                player.sendMessage("§c你已在一个房间中，已自动离开旧房间。");
            }
            RoomManager rm = plugin.getRoomManager();
            Room room = rm.createRoom(roomName, player);
            player.sendMessage("§a房间已创建！房间名: §e" + roomName + " §7ID: §f" + room.getRoomId());
            openRoomMenu(player);
        } else if (waitingForKickTarget.contains(uuid)) {
            event.setCancelled(true);
            waitingForKickTarget.remove(uuid);
            String targetName = event.getMessage().trim();
            Player target = Bukkit.getPlayer(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§c玩家不在线！");
                return;
            }
            RoomManager rm = plugin.getRoomManager();
            Room room = rm.getRoomByPlayer(player);
            if (room == null) {
                player.sendMessage("§c你不在任何房间中！");
                return;
            }
            if (room.getOwnerName().equalsIgnoreCase(targetName)) {
                player.sendMessage("§c不能踢出房主！");
                return;
            }
            // 只能踢出同房间的玩家，防止跨房间误踢
            Room targetRoom = rm.getRoomByPlayer(target);
            if (targetRoom == null || !targetRoom.getRoomId().equals(room.getRoomId())) {
                player.sendMessage("§c该玩家不在你的房间中！");
                return;
            }
            rm.kickPlayer(target);
            player.sendMessage("§a已踢出玩家: §e" + targetName);
            target.sendMessage("§c你被房间房主踢出了！");
            openRoomMenu(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
    }

    private ItemStack makeRoomItem(Room room) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e" + room.getRoomName());
        meta.setLore(Arrays.asList(
                "§7ID: §f" + room.getRoomId(),
                "§7房主: §f" + room.getOwnerName(),
                "§7玩家: §a" + room.getPlayerCount() + "/14",
                "§7状态: §f" + room.getState().getDisplayName(),
                "",
                "§a点击加入"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makePlayerHead(PlayerData pd) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(pd.getUsername()));
            meta.setDisplayName("§e" + pd.getUsername());
            meta.setLore(Arrays.asList(
                    "§7身份: §f未分配",
                    "§7状态: §a等待中"
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack makeItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(Collections.singletonList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
