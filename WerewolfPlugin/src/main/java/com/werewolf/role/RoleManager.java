package com.werewolf.role;

import com.werewolf.plugin.WerewolfPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class RoleManager {

    private final WerewolfPlugin plugin;
    private final Map<String, Role> roles;

    public RoleManager(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.roles = new HashMap<>();
    }

    public void loadRoles() {
        roles.clear();

        File rolesDir = new File(plugin.getDataFolder(), "roles");
        if (!rolesDir.exists() || !rolesDir.isDirectory()) {
            plugin.getLogger().warning("角色目录不存在！");
            return;
        }

        File[] roleFiles = rolesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (roleFiles == null) {
            return;
        }

        for (File roleFile : roleFiles) {
            try {
                Role role = loadRoleFromFile(roleFile);
                if (role != null) {
                    roles.put(role.getName().toLowerCase(), role);
                    plugin.getLogger().info("已加载角色: " + role.getName() + " (" + role.getDisplayName() + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "加载角色文件失败: " + roleFile.getName(), e);
            }
        }

        plugin.getLogger().info("共加载了 " + roles.size() + " 个角色");
    }

    private Role loadRoleFromFile(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name");
        if (name == null || name.isEmpty()) {
            plugin.getLogger().warning("角色文件缺少名称: " + file.getName());
            return null;
        }

        Role role = new Role(name);
        role.setDisplayName(config.getString("display-name", name));
        role.setCamp(config.getString("camp", "好人阵营"));
        role.setDescription(config.getString("description", ""));
        role.setSkill1Name(config.getString("skill1-name", ""));
        role.setSkill2Name(config.getString("skill2-name", ""));
        role.setSkill3Name(config.getString("skill3-name", ""));
        role.setSkill4Name(config.getString("skill4-name", ""));
        role.setPassiveSkillName(config.getString("passive-name", ""));

        plugin.getLogger().info("角色 " + name + " - 技能1: [" + role.getSkill1Name() + "] 技能2: [" + role.getSkill2Name() + "] 被动: [" + role.getPassiveSkillName() + "]");

        return role;
    }

    public Role getRole(String name) {
        return roles.get(name.toLowerCase());
    }

    public Collection<Role> getAllRoles() {
        return new ArrayList<>(roles.values());
    }

    public boolean roleExists(String name) {
        return roles.containsKey(name.toLowerCase());
    }
}
