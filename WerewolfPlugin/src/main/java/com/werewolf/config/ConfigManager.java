package com.werewolf.config;

import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public class ConfigManager {

    private final WerewolfPlugin plugin;

    private FileConfiguration config;
    private FileConfiguration authConfig;
    private FileConfiguration playerDataConfig;

    private File configFile;
    private File authFile;
    private File playerDataFile;

    public ConfigManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // 确保插件数据文件夹存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // 加载主配置
        loadMainConfig();

        // 加载认证配置
        loadAuthConfig();

        // 加载玩家数据
        loadPlayerDataConfig();

        // 创建角色目录
        File rolesDir = new File(plugin.getDataFolder(), "roles");
        if (!rolesDir.exists()) {
            rolesDir.mkdirs();
        }

        // 始终保存内置角色文件（覆盖更新）
        saveBuiltinRoles(rolesDir);

        // 创建游戏历史目录
        File gamesDir = new File(plugin.getDataFolder(), "games");
        if (!gamesDir.exists()) {
            gamesDir.mkdirs();
        }
    }

    private void loadMainConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadAuthConfig() {
        authFile = new File(plugin.getDataFolder(), "auth.yml");

        if (!authFile.exists()) {
            plugin.saveResource("auth.yml", false);
        }

        authConfig = YamlConfiguration.loadConfiguration(authFile);
    }

    private void loadPlayerDataConfig() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");

        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "无法创建玩家数据文件！", e);
            }
        }

        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void saveBuiltinRoles(File rolesDir) {
        String[] roleFiles = {
            "white_wolf_king.yml", "wolf_witch.yml", "wolf_jackal.yml", "wretcher.yml",
            "corpse_herder.yml", "gambler.yml", "dio.yml", "killer.yml",
            "crimson_messenger.yml", "tide_singer.yml", "bomber.yml", "count.yml",
            "guard.yml", "knight.yml", "ninja.yml", "hacker.yml", "slime.yml",
            "buddhist_monk.yml", "monk.yml", "wind_spirit.yml", "water_divination.yml",
            "lantern_bearer.yml", "survivor.yml", "blade_soul.yml",
            "arsonist.yml", "time_duke.yml", "hamster.yml", "pelican.yml",
            "neutral_jackal.yml", "follower.yml", "wizard.yml"
        };

        for (String fileName : roleFiles) {
            File roleFile = new File(rolesDir, fileName);
            plugin.saveResource("roles/" + fileName, true);
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存主配置文件！", e);
        }
    }

    public void saveAuthConfig() {
        try {
            authConfig.save(authFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存认证配置文件！", e);
        }
    }

    public void savePlayerDataConfig() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存玩家数据文件！", e);
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getAuthConfig() {
        return authConfig;
    }

    public FileConfiguration getPlayerDataConfig() {
        return playerDataConfig;
    }

    public void reloadConfigs() {
        loadConfigs();
    }
}
