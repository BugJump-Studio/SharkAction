package com.werewolf.auth;

import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final WerewolfPlugin plugin;
    private final Map<String, AdminSession> sessions;
    private final long sessionTimeout;

    public SessionManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.sessions = new ConcurrentHashMap<>();
        this.sessionTimeout = plugin.getConfigManager().getConfig().getLong("session.timeout", 3) * 60 * 1000;

        // 启动会话清理任务
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredSessions();
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 每分钟清理一次
    }

    public AdminSession createSession(String username, AdminSession.PermissionLevel permissionLevel) {
        String sessionId = generateSessionId();
        AdminSession session = new AdminSession(sessionId, username, permissionLevel);
        sessions.put(sessionId, session);
        return session;
    }

    public AdminSession getSession(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        AdminSession session = sessions.get(sessionId);
        if (session != null) {
            if (session.isExpired(sessionTimeout)) {
                sessions.remove(sessionId);
                return null;
            }
            session.updateActivity();
        }
        return session;
    }

    public boolean isValidSession(String sessionId) {
        return getSession(sessionId) != null;
    }

    public void invalidateSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public void invalidateUserSessions(String username) {
        sessions.entrySet().removeIf(entry ->
            entry.getValue().getUsername().equalsIgnoreCase(username)
        );
    }

    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry ->
            entry.getValue().isExpired(sessionTimeout)
        );
    }

    public void invalidateAllSessions() {
        sessions.clear();
    }

    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public long getSessionTimeout() {
        return sessionTimeout;
    }

    public boolean hasSession(String username) {
        cleanupExpiredSessions();
        return sessions.values().stream()
            .anyMatch(session -> session.getUsername().equalsIgnoreCase(username));
    }
}
