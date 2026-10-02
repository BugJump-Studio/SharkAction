package com.werewolf.plugin;

import com.werewolf.auth.AuthManager;
import com.werewolf.commands.WerewolfCommand;
import com.werewolf.config.ConfigManager;
import com.werewolf.game.GameManager;
import com.werewolf.player.PlayerDataManager;
import com.werewolf.role.RoleManager;
import com.werewolf.web.WebServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class WerewolfPlugin extends JavaPlugin {

    private static WerewolfPlugin instance;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private AuthManager authManager;
    private GameManager gameManager;
    private RoleManager roleManager;
    private WebServer webServer;
    private RoomMenuListener roomMenuListener;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=================================");
        getLogger().info("鲨鱼行动插件正在启动...");
        getLogger().info("=================================");

        try {
            this.configManager = new ConfigManager(this);
            this.configManager.loadConfigs();

            this.playerDataManager = new PlayerDataManager(this);

            this.authManager = new AuthManager(this);

            this.roleManager = new RoleManager(this);
            this.roleManager.loadRoles();

            this.gameManager = new GameManager(this);
            this.roomMenuListener = new RoomMenuListener(this);

            registerCommands();
            registerListeners();

            if (configManager.getConfig().getBoolean("web.enabled", true)) {
                this.webServer = new WebServer(this);
                this.webServer.start();
            }

            getLogger().info("=================================");
            getLogger().info("鲨鱼行动插件启动成功！");
            getLogger().info("=================================");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "插件启动失败！", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("鲨鱼行动插件正在关闭...");

        if (webServer != null) {
            webServer.stop();
        }

        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }

        if (gameManager != null) {
            gameManager.shutdown();
        }

        getLogger().info("鲨鱼行动插件已关闭！");
    }

    private void registerCommands() {
        WerewolfCommand werewolfCommand = new WerewolfCommand(this);
        getCommand("werewolf").setExecutor(werewolfCommand);
        getCommand("werewolf").setTabCompleter(werewolfCommand);
        getCommand("ww").setExecutor(werewolfCommand);
        getCommand("ww").setTabCompleter(werewolfCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new SkillItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemUseListener(this), this);
        getServer().getPluginManager().registerEvents(roomMenuListener, this);
    }

    public static WerewolfPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public RoleManager getRoleManager() {
        return roleManager;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public RoomMenuListener getRoomMenuListener() {
        return roomMenuListener;
    }

    public com.werewolf.room.RoomManager getRoomManager() {
        return gameManager.getRoomManager();
    }
}
