package com.werewolf.auth;

import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final WerewolfPlugin plugin;
    private final Map<String, String> adminPasswords;
    private final Map<String, AdminSession.PermissionLevel> adminPermissions;
    private final Set<String> loggedInPlayers;

    public AuthManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.adminPasswords = new ConcurrentHashMap<>();
        this.adminPermissions = new ConcurrentHashMap<>();
        this.loggedInPlayers = ConcurrentHashMap.newKeySet();
        loadAuthData();
    }

    private void loadAuthData() {
        FileConfiguration authConfig = plugin.getConfigManager().getAuthConfig();

        // 加载管理员密码
        if (authConfig.contains("admins")) {
            for (String username : authConfig.getConfigurationSection("admins").getKeys(false)) {
                String password = authConfig.getString("admins." + username);
                if (password != null && !password.isEmpty()) {
                    adminPasswords.put(username.toLowerCase(), password);
                }
            }
        }

        // 加载权限等级
        if (authConfig.contains("permissions.levels")) {
            for (String username : authConfig.getConfigurationSection("permissions.levels").getKeys(false)) {
                String level = authConfig.getString("permissions.levels." + username);
                AdminSession.PermissionLevel permissionLevel = AdminSession.PermissionLevel.fromString(level);
                if (permissionLevel != null) {
                    adminPermissions.put(username.toLowerCase(), permissionLevel);
                }
            }
        }
    }

    public boolean isAdmin(String username) {
        return adminPasswords.containsKey(username.toLowerCase());
    }

    public boolean verifyPassword(String username, String password) {
        String hashedPassword = adminPasswords.get(username.toLowerCase());
        if (hashedPassword == null) {
            return false;
        }
        return BCrypt.checkpw(password, hashedPassword);
    }

    public boolean login(String username, String password) {
        if (verifyPassword(username, password)) {
            loggedInPlayers.add(username.toLowerCase());
            return true;
        }
        return false;
    }

    public void logout(String username) {
        loggedInPlayers.remove(username.toLowerCase());
    }

    public boolean isLoggedIn(String username) {
        return loggedInPlayers.contains(username.toLowerCase());
    }

    public boolean isLoggedIn(Player player) {
        return isLoggedIn(player.getName());
    }

    public AdminSession.PermissionLevel getPermissionLevel(String username) {
        return adminPermissions.getOrDefault(username.toLowerCase(), AdminSession.PermissionLevel.ADMIN);
    }

    public void setAdmin(String username, String password, AdminSession.PermissionLevel level) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        adminPasswords.put(username.toLowerCase(), hashedPassword);
        adminPermissions.put(username.toLowerCase(), level);
        saveAuthData();
    }

    public void removeAdmin(String username) {
        adminPasswords.remove(username.toLowerCase());
        adminPermissions.remove(username.toLowerCase());
        loggedInPlayers.remove(username.toLowerCase());
        saveAuthData();
    }

    public void changePassword(String username, String newPassword) {
        if (isAdmin(username)) {
            String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            adminPasswords.put(username.toLowerCase(), hashedPassword);
            saveAuthData();
        }
    }

    private void saveAuthData() {
        FileConfiguration authConfig = plugin.getConfigManager().getAuthConfig();

        // 保存管理员密码
        for (Map.Entry<String, String> entry : adminPasswords.entrySet()) {
            authConfig.set("admins." + entry.getKey(), entry.getValue());
        }

        // 保存权限等级
        for (Map.Entry<String, AdminSession.PermissionLevel> entry : adminPermissions.entrySet()) {
            authConfig.set("permissions.levels." + entry.getKey(), entry.getValue().getName());
        }

        plugin.getConfigManager().saveAuthConfig();
    }

    public Set<String> getAdmins() {
        return new HashSet<>(adminPasswords.keySet());
    }

    public boolean hasPermission(String username, AdminSession.PermissionLevel requiredLevel) {
        AdminSession.PermissionLevel userLevel = getPermissionLevel(username);
        if (userLevel == AdminSession.PermissionLevel.OWNER) {
            return true;
        }
        return userLevel == requiredLevel;
    }

    /**
     * 检查管理员是否已设置密码（已注册）
     */
    public boolean hasPassword(String username) {
        String password = adminPasswords.get(username.toLowerCase());
        return password != null && !password.isEmpty();
    }

    /**
     * 设置管理员密码（用于注册）
     */
    public void setPassword(String username, String password) {
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        adminPasswords.put(username.toLowerCase(), hashedPassword);
        saveAuthData();
    }
}
