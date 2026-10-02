package com.werewolf.auth;

public class AdminSession {

    private final String sessionId;
    private final String username;
    private final PermissionLevel permissionLevel;
    private long lastActivity;

    public AdminSession(String sessionId, String username, PermissionLevel permissionLevel) {
        this.sessionId = sessionId;
        this.username = username;
        this.permissionLevel = permissionLevel;
        this.lastActivity = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMillis) {
        return System.currentTimeMillis() - lastActivity > timeoutMillis;
    }

    public boolean canEditAllRoles() {
        return permissionLevel == PermissionLevel.OWNER;
    }

    public boolean canEditCustomRoles() {
        return permissionLevel == PermissionLevel.OWNER || permissionLevel == PermissionLevel.ADMIN;
    }

    public boolean canManageGame() {
        return permissionLevel == PermissionLevel.OWNER || permissionLevel == PermissionLevel.ADMIN;
    }

    public enum PermissionLevel {
        OWNER("owner"),
        ADMIN("admin");

        private final String name;

        PermissionLevel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static PermissionLevel fromString(String name) {
            for (PermissionLevel level : values()) {
                if (level.name.equalsIgnoreCase(name)) {
                    return level;
                }
            }
            return null;
        }
    }
}
