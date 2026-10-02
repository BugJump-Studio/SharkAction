package com.werewolf.player;

import com.werewolf.role.Role;
import org.bukkit.entity.Player;

import java.util.*;

public class PlayerData {

    private final String username;
    private Player bukkitPlayer;

    // 游戏状态
    private boolean inGame;
    private boolean isAlive;
    private Role role;
    private String camp;

    // 游戏数据
    private int wins;
    private int losses;
    private int gamesPlayed;

    // 当前游戏状态
    private Set<String> tags;
    private Map<String, Object> attributes;
    private int voteCount;
    private boolean hasVoted;
    private String votedFor;

    // 技能状态
    private Map<String, Boolean> skillUsed;
    private Map<String, Integer> skillCooldowns;

    public PlayerData(String username) {
        this.username = username.toLowerCase();
        this.inGame = false;
        this.isAlive = true;
        this.wins = 0;
        this.losses = 0;
        this.gamesPlayed = 0;
        this.tags = new HashSet<>();
        this.attributes = new HashMap<>();
        this.skillUsed = new HashMap<>();
        this.skillCooldowns = new HashMap<>();
        this.voteCount = 0;
        this.hasVoted = false;
        this.votedFor = null;
    }

    public String getUsername() {
        return username;
    }

    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }

    public void setBukkitPlayer(Player bukkitPlayer) {
        this.bukkitPlayer = bukkitPlayer;
    }

    public boolean isInGame() {
        return inGame;
    }

    public void setInGame(boolean inGame) {
        this.inGame = inGame;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
        if (role != null) {
            this.camp = role.getCamp();
        }
    }

    public String getCamp() {
        return camp;
    }

    public void setCamp(String camp) {
        this.camp = camp;
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        this.wins++;
        this.gamesPlayed++;
    }

    public int getLosses() {
        return losses;
    }

    public void addLoss() {
        this.losses++;
        this.gamesPlayed++;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void clearTags() {
        tags.clear();
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    public void addVote() {
        this.voteCount++;
    }

    public boolean hasVoted() {
        return hasVoted;
    }

    public void setHasVoted(boolean hasVoted) {
        this.hasVoted = hasVoted;
    }

    public String getVotedFor() {
        return votedFor;
    }

    public void setVotedFor(String votedFor) {
        this.votedFor = votedFor;
    }

    public boolean isSkillUsed(String skillName) {
        return skillUsed.getOrDefault(skillName, false);
    }

    public void setSkillUsed(String skillName, boolean used) {
        skillUsed.put(skillName, used);
    }

    public int getSkillCooldown(String skillName) {
        return skillCooldowns.getOrDefault(skillName, 0);
    }

    public void setSkillCooldown(String skillName, int cooldown) {
        skillCooldowns.put(skillName, cooldown);
    }

    public void resetGameState() {
        this.inGame = false;
        this.isAlive = true;
        this.role = null;
        this.camp = null;
        this.tags.clear();
        this.attributes.clear();
        this.skillUsed.clear();
        this.skillCooldowns.clear();
        this.voteCount = 0;
        this.hasVoted = false;
        this.votedFor = null;
    }

    public void sendMessage(String message) {
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            bukkitPlayer.sendMessage(message);
        }
    }

    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            bukkitPlayer.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public void sendActionBar(String message) {
        if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
            bukkitPlayer.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(message));
        }
    }
}
