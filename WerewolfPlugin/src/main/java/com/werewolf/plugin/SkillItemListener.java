package com.werewolf.plugin;

import com.werewolf.game.GameManager;
import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.room.Room;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SkillItemListener implements Listener {

    private final WerewolfPlugin plugin;

    public SkillItemListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.DIAMOND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        if (!playerData.isInGame() || !playerData.isAlive()) return;

        GameManager gameManager = plugin.getGameManager();
        Room room = gameManager.getRoomForPlayer(player);
        if (room == null || room.getCurrentState() != GameState.PLAYING) {
            player.sendMessage("§c游戏尚未开始！");
            return;
        }
        // 时停: 被冻结的玩家无法释放技能
        if (room.isTimeFrozen(player)) {
            player.sendMessage("§c你被时停了！");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;

        String displayName = meta.getDisplayName();

        // 炸弹传递: 手持钻石且准星对准附近玩家时, 优先传递炸弹而不是放技能
        if (room.hasBomb(player)) {
            Player bombTarget = findLookedPlayer(player, 4.5);
            if (bombTarget != null) {
                room.tryPassBomb(player, bombTarget);
                return;
            }
        }

        // 潜行右键: 不释放技能 (忍者潜行右键二技能 = 切换标记目标)
        if (player.isSneaking()) {
            com.werewolf.role.Role role = playerData.getRole();
            if (role != null && "NINJA".equals(role.getName()) && displayName.contains("二技能")) {
                room.switchNinjaTarget(playerData);
            }
            return;
        }

        if (displayName.contains("一技能")) {
            gameManager.useSkill(playerData, "skill1");
        } else if (displayName.contains("二技能")) {
            gameManager.useSkill(playerData, "skill2");
        } else if (displayName.contains("三技能")) {
            gameManager.useSkill(playerData, "skill3");
        } else if (displayName.contains("四技能")) {
            gameManager.useSkill(playerData, "skill4");
        }
    }

    // 准星方向上最近的存活玩家
    private Player findLookedPlayer(Player player, double dist) {
        org.bukkit.util.Vector look = player.getLocation().getDirection().normalize();
        Player best = null;
        double bestDot = 0.5;
        for (org.bukkit.entity.Entity e : player.getWorld().getNearbyEntities(player.getLocation(), dist, dist, dist)) {
            if (!(e instanceof Player)) continue;
            Player p = (Player) e;
            if (p.equals(player)) continue;
            PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p);
            if (pd == null || !pd.isInGame() || !pd.isAlive()) continue;
            org.bukkit.util.Vector v = p.getLocation().add(0, p.getEyeHeight() * 0.5, 0).toVector()
                    .subtract(player.getEyeLocation().toVector());
            double len = v.length();
            if (len < 0.001 || len > dist) continue;
            double dot = v.normalize().dot(look);
            if (dot > bestDot) { bestDot = dot; best = p; }
        }
        return best;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(event.getPlayer());
        if (!playerData.isInGame()) return;

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (dropped.getType() == Material.DIAMOND) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData((Player) event.getWhoClicked());
        if (!playerData.isInGame()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked != null && clicked.getType() == Material.DIAMOND) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getDisplayName() != null &&
                (meta.getDisplayName().contains("一技能") || meta.getDisplayName().contains("二技能") || meta.getDisplayName().contains("三技能") || meta.getDisplayName().contains("四技能"))) {
                event.setCancelled(true);
            }
        }
    }
}
