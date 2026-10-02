package com.werewolf.player;

import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final WerewolfPlugin plugin;
    private final Map<String, PlayerData> playerDataMap;

    public PlayerDataManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.playerDataMap = new ConcurrentHashMap<>();
        loadPlayerData();
    }

    public void loadPlayerData() {
        FileConfiguration config = plugin.getConfigManager().getPlayerDataConfig();

        if (config.contains("players")) {
            for (String username : config.getConfigurationSection("players").getKeys(false)) {
                String path = "players." + username;
                PlayerData data = new PlayerData(username);
                data.setCamp(config.getString(path + ".camp", ""));
                data.addWin();
                data.addLoss();

                // 这里简化处理，实际应该加载完整数据
                playerDataMap.put(username.toLowerCase(), data);
            }
        }
    }

    public void saveAll() {
        FileConfiguration config = plugin.getConfigManager().getPlayerDataConfig();

        for (Map.Entry<String, PlayerData> entry : playerDataMap.entrySet()) {
            String username = entry.getKey();
            PlayerData data = entry.getValue();
            String path = "players." + username;

            config.set(path + ".wins", data.getWins());
            config.set(path + ".losses", data.getLosses());
            config.set(path + ".games-played", data.getGamesPlayed());
        }

        plugin.getConfigManager().savePlayerDataConfig();
    }

    public PlayerData getPlayerData(String username) {
        String lowerUsername = username.toLowerCase();
        return playerDataMap.computeIfAbsent(lowerUsername, PlayerData::new);
    }

    public PlayerData getPlayerData(Player player) {
        return getPlayerData(player.getName());
    }

    public void removePlayerData(String username) {
        playerDataMap.remove(username.toLowerCase());
    }

    public Collection<PlayerData> getAllPlayerData() {
        return playerDataMap.values();
    }

    public List<PlayerData> getPlayersInGame() {
        List<PlayerData> inGame = new ArrayList<>();
        for (PlayerData data : playerDataMap.values()) {
            if (data.isInGame()) {
                inGame.add(data);
            }
        }
        return inGame;
    }

    public List<PlayerData> getAlivePlayers() {
        List<PlayerData> alive = new ArrayList<>();
        for (PlayerData data : playerDataMap.values()) {
            if (data.isInGame() && data.isAlive()) {
                alive.add(data);
            }
        }
        return alive;
    }

    public List<PlayerData> getPlayersByCamp(String camp) {
        List<PlayerData> players = new ArrayList<>();
        for (PlayerData data : playerDataMap.values()) {
            if (data.isInGame() && camp.equals(data.getCamp())) {
                players.add(data);
            }
        }
        return players;
    }

    public void clearGameData() {
        for (PlayerData data : playerDataMap.values()) {
            data.resetGameState();
        }
    }

    public void updateBukkitPlayer(Player player) {
        PlayerData data = getPlayerData(player.getName());
        data.setBukkitPlayer(player);
    }

    public void removeBukkitPlayer(Player player) {
        PlayerData data = playerDataMap.get(player.getName().toLowerCase());
        if (data != null) {
            data.setBukkitPlayer(null);
        }
    }
}
