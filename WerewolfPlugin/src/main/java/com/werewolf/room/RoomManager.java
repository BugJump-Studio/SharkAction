package com.werewolf.room;

import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRooms = new ConcurrentHashMap<>();
    private final WerewolfPlugin plugin;
    private int nextRoomNumber = 1;

    public RoomManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    public Room createRoom(String roomName, Player owner) {
        // 先离开旧房间，防止旧房间留下“幽灵玩家”
        leaveRoom(owner);
        String roomId = generateRoomId();
        Room room = new Room(plugin, roomId, roomName, owner.getName());
        rooms.put(roomId, room);
        playerRooms.put(owner.getUniqueId(), roomId);
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(owner);
        room.joinGame(pd);
        return room;
    }

    public void deleteRoom(String roomId) {
        Room room = rooms.remove(roomId);
        if (room != null) {
            // 无论玩家是否在线，都清掉所有指向该房间的映射
            playerRooms.values().removeIf(roomId::equals);
            room.broadcast("§c房间已解散！");
            room.shutdown();
        }
    }

    public boolean joinRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        if (room == null) { player.sendMessage("§c房间不存在！"); return false; }
        Room current = getRoomByPlayer(player);
        if (current == room) return true;
        if (room.getState() != GameState.LOBBY) {
            player.sendMessage("§c该房间游戏已开始，无法加入！");
            return false;
        }
        int maxPlayers = plugin.getConfigManager().getConfig().getInt("game.max-players", 14);
        if (room.getPlayerCount() >= maxPlayers) {
            player.sendMessage("§c该房间已满（" + maxPlayers + "人）！");
            return false;
        }
        leaveRoom(player);
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        if (!room.joinGame(pd)) {
            player.sendMessage("§c加入房间失败！");
            return false;
        }
        playerRooms.put(player.getUniqueId(), roomId);
        return true;
    }

    public void leaveRoom(Player player) {
        String roomId = playerRooms.remove(player.getUniqueId());
        if (roomId == null) return;
        Room room = rooms.get(roomId);
        if (room == null) return;
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        boolean ownerLeft = player.getName().equalsIgnoreCase(room.getOwnerName());
        room.leaveGame(pd);
        cleanupRoom(room, ownerLeft);
    }

    public void kickPlayer(Player target) {
        String roomId = playerRooms.remove(target.getUniqueId());
        if (roomId == null) return;
        Room room = rooms.get(roomId);
        if (room == null) return;
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(target);
        room.leaveGame(pd);
        // 踢人者必须是房主，所以房主不会被踢走，无需移交
        cleanupRoom(room, false);
    }

    /**
     * 房间人员变动后的统一清理：
     * - 没人了 -> 自动解散
     * - 房主离开但还有人 -> 移交房主
     */
    private void cleanupRoom(Room room, boolean ownerLeft) {
        if (room.getPlayerCount() <= 0) {
            deleteRoom(room.getRoomId());
        } else if (ownerLeft) {
            List<PlayerData> remaining = room.getPlayers();
            if (!remaining.isEmpty()) room.transferOwner(remaining.get(0));
        }
    }

    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public Room getRoomByPlayer(Player player) {
        String roomId = playerRooms.get(player.getUniqueId());
        return roomId != null ? rooms.get(roomId) : null;
    }

    public Room getRoomByPlayerName(String playerName) {
        for (Room room : rooms.values()) {
            for (PlayerData pd : room.getPlayers()) {
                if (pd.getUsername().equalsIgnoreCase(playerName)) return room;
            }
        }
        return null;
    }

    public Collection<Room> getAllRooms() {
        return rooms.values();
    }

    public List<Room> getLobbyRooms() {
        List<Room> result = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.getState() == GameState.LOBBY) result.add(room);
        }
        return result;
    }

    private synchronized String generateRoomId() {
        return String.format("%03d", nextRoomNumber++);
    }
}
