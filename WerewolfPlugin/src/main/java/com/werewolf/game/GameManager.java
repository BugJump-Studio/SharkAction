package com.werewolf.game;

import com.werewolf.player.PlayerData;
import com.werewolf.plugin.WerewolfPlugin;
import com.werewolf.room.Room;
import com.werewolf.room.RoomManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GameManager {

    private final WerewolfPlugin plugin;
    private final RoomManager roomManager;

    public GameManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.roomManager = new RoomManager(plugin);
    }

    public RoomManager getRoomManager() {
        return roomManager;
    }

    public Room getRoomForPlayer(PlayerData player) {
        if (player == null || player.getBukkitPlayer() == null) return null;
        return roomManager.getRoomByPlayer(player.getBukkitPlayer());
    }

    public Room getRoomForPlayer(Player player) {
        if (player == null) return null;
        return roomManager.getRoomByPlayer(player);
    }

    // ==================== DELEGATED GAME FLOW ====================

    public void joinGame(PlayerData player) {
        // Legacy: should not be used directly anymore
        // Room-based join is handled by RoomManager.joinRoom
    }

    public void leaveGame(PlayerData player) {
        // 走 RoomManager：会清理 playerRooms 映射、解散空房间、移交房主
        if (player == null || player.getBukkitPlayer() == null) return;
        roomManager.leaveRoom(player.getBukkitPlayer());
    }

    public void startGame() {
        // Legacy: should not be used directly anymore
    }

    public void useSkill(PlayerData player, String skillId) {
        Room room = getRoomForPlayer(player);
        if (room != null) room.useSkill(player, skillId);
    }

    public void killPlayer(PlayerData victim, PlayerData killer) {
        Room room = getRoomForPlayer(victim);
        if (room != null) room.killPlayer(victim, killer);
    }

    // ==================== STATE QUERIES ====================

    public GameState getCurrentState() {
        return GameState.LOBBY;
    }

    public GameState getCurrentState(Player player) {
        Room room = getRoomForPlayer(player);
        return room != null ? room.getCurrentState() : GameState.LOBBY;
    }

    public int getPlayerCount() {
        return 0;
    }

    public int getPlayerCount(Room room) {
        return room != null ? room.getPlayerCount() : 0;
    }

    public List<PlayerData> getPlayers() {
        return new ArrayList<>();
    }

    public int getRemainingTime() {
        return 0;
    }

    public int getRemainingTime(Room room) {
        return room != null ? room.getRemainingTime() : 0;
    }

    // ==================== UTILITY (room-independent) ====================

    public void healPlayer(Player target, double amount) {
        if (target == null || !target.isOnline()) return;
        double max = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        target.setHealth(Math.min(target.getHealth() + amount, max));
    }

    public void addWizardEnergy(Player bp, int amount) {
        Room room = getRoomForPlayer(bp);
        if (room != null) room.addWizardEnergy(bp, amount);
    }

    public void broadcast(String message) {
        for (Room room : roomManager.getAllRooms()) {
            room.broadcast(message);
        }
    }

    public void broadcast(Room room, String message) {
        if (room != null) room.broadcast(message);
    }

    // ==================== ITEM MAKERS (static, room-independent) ====================

    public ItemStack makeCrowbar() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c撬棍");
        meta.setLore(Collections.singletonList("§7+攻击力 14"));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
            new org.bukkit.attribute.AttributeModifier("attack_damage", 14, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeStunGrenade(int amount) {
        return makeItem(Material.FIRE_CHARGE, "§e震撼弹", "§7不伤害 缓慢5 3s 反胃8s 黑暗3s", amount);
    }

    public ItemStack makeMedkit(int amount) {
        return makeItem(Material.GOLDEN_APPLE, "§a医疗包", "§7右键使用 4s恢复5心", amount);
    }

    public ItemStack makeAdrenaline(int amount) {
        return makeItem(Material.POTION, "§a肾上腺素", "§7右键使用 速度3八秒+恢复3心", amount);
    }

    public ItemStack makeItem(Material mat, String name, String lore, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); if (!lore.isEmpty()) meta.setLore(Collections.singletonList(lore)); item.setItemMeta(meta); }
        return item;
    }

    public ItemStack makeItem(Material mat, String name, String lore) {
        return makeItem(mat, name, lore, 1);
    }

    public void stopGame() {
        // Stop all rooms
        for (Room room : roomManager.getAllRooms()) {
            if (room.getCurrentState() != GameState.LOBBY) room.stopGame();
        }
    }

    // ==================== SHUTDOWN ====================

    public void shutdown() {
        for (Room room : roomManager.getAllRooms()) {
            room.shutdown();
        }
    }
}
