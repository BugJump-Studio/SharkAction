package com.werewolf.plugin;

import com.werewolf.room.Room;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final WerewolfPlugin plugin;

    public PlayerListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();
        plugin.getPlayerDataManager().updateBukkitPlayer(player);

        // 检查是否是管理员
        if (plugin.getAuthManager().isAdmin(username)) {
            // 检查是否已注册密码
            if (!plugin.getAuthManager().hasPassword(username)) {
                // 未注册，提醒注册
                player.sendMessage("");
                player.sendMessage("§6§l╔══════════════════════════════════════╗");
                player.sendMessage("§6§l║       §c§l⚠ 管理员账号未注册 ⚠       §6§l║");
                player.sendMessage("§6§l╠══════════════════════════════════════╣");
                player.sendMessage("§6§l║  §e你已被设为鲨鱼行动插件管理员       §6§l║");
                player.sendMessage("§6§l║  §e但尚未注册密码                     §6§l║");
                player.sendMessage("§6§l║                                      §6§l║");
                player.sendMessage("§6§l║  §a请使用指令注册:                   §6§l║");
                player.sendMessage("§6§l║  §b/ww register <密码>               §6§l║");
                player.sendMessage("§6§l║                                      §6§l║");
                player.sendMessage("§6§l║  §7注册后即可使用 /ww login 登录     §6§l║");
                player.sendMessage("§6§l╚══════════════════════════════════════╝");
                player.sendMessage("");
            } else {
                // 已注册，提醒登录
                player.sendMessage("§6[鲨鱼行动] §e你是管理员，请使用 §c/ww login <密码> §e登录管理后台");
            }
        }
    }

    @EventHandler
    public void onPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        Room room = plugin.getGameManager().getRoomForPlayer(player);
        if (room == null) return;
        if (room.isItemPickupLocked(player)) {
            event.setCancelled(true);
            if (event.getItem().getItemStack() != null && event.getItem().getItemStack().getAmount() > 1) {
                player.sendMessage("§c你无法拾取物品（第三条命）");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 通过 RoomManager 离开：清理 playerRooms 映射、必要时解散空房间/移交房主
        if (plugin.getRoomManager().getRoomByPlayer(player) != null) {
            plugin.getRoomManager().leaveRoom(player);
        }
        plugin.getRoomMenuListener().clearWaiting(player.getUniqueId());

        plugin.getPlayerDataManager().removeBukkitPlayer(player);
    }
}
