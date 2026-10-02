package com.werewolf.plugin;

import com.werewolf.game.GameManager;
import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.room.Room;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {

    private final WerewolfPlugin plugin;

    public ChatListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        GameManager gm = plugin.getGameManager();
        Room room = gm.getRoomForPlayer(player);
        if (room == null || room.getCurrentState() != GameState.PLAYING) return;
        if (!data.isInGame()) return;

        // 旁观者发言只有旁观者能收到
        if (!data.isAlive()) {
            event.getRecipients().removeIf(online -> {
                PlayerData pd = plugin.getPlayerDataManager().getPlayerData(online);
                if (pd == null || !pd.isInGame() || pd.isAlive()) return true;
                Room theirRoom = gm.getRoomForPlayer(online);
                return theirRoom != room;
            });
            event.setFormat("§7[旁观] " + player.getName() + ": " + event.getMessage());
        }
        // 活着的人发言 → 同房间所有人收到
        else {
            event.getRecipients().removeIf(online -> {
                Room theirRoom = gm.getRoomForPlayer(online);
                return theirRoom != room;
            });
        }
    }
}
