package com.werewolf.game;

public enum GameState {
    LOBBY("等待中", "等待玩家加入"),
    STARTING("即将开始", "游戏即将开始"),
    PLAYING("战斗中", "自由对战模式"),
    END("结束", "游戏结束");

    private final String displayName;
    private final String description;

    GameState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
