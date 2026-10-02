package com.werewolf.plugin;

import com.werewolf.game.GameManager;
import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.room.Room;
import com.werewolf.role.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class CombatListener implements Listener {

    private final WerewolfPlugin plugin;

    public CombatListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        GameManager gm = plugin.getGameManager();

        Room victimRoom = gm.getRoomForPlayer(victim);
        Room attackerRoom = gm.getRoomForPlayer(attacker);

        // Game not in progress - cancel all PVP
        if (victimRoom == null || victimRoom.getCurrentState() != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        PlayerData victimData = plugin.getPlayerDataManager().getPlayerData(victim);
        PlayerData attackerData = plugin.getPlayerDataManager().getPlayerData(attacker);

        // Null safety
        if (victimData == null || attackerData == null) { event.setCancelled(true); return; }

        // 时停: 被冻结的一方不能行动/攻击
        if (attackerRoom != null && attackerRoom.isTimeFrozen(attacker)) { event.setCancelled(true); return; }
        if (victimRoom != null && victimRoom.isTimeFrozen(victim)) { event.setCancelled(true); return; }

        // Dead/spectator players can't fight or be fought
        if (!victimData.isInGame() || !victimData.isAlive()) {
            event.setCancelled(true);
            return;
        }
        if (!attackerData.isInGame() || !attackerData.isAlive()) {
            event.setCancelled(true);
            return;
        }

        // Let vanilla damage + knockback work naturally
        // 巫师普攻恢复能量: 木剑造成伤害后恢复50能量 3sCD
        if (attackerData.getRole() != null && "WIZARD".equals(attackerData.getRole().getName())) {
            if (attacker.getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD) {
                Boolean wizardCd = (Boolean) attackerData.getAttribute("wizardAttackCd");
                if (wizardCd == null || !wizardCd) {
                    GameManager gm2 = plugin.getGameManager();
                    gm2.addWizardEnergy(attacker, 50);
                    attackerData.setAttribute("wizardAttackCd", true);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override public void run() { attackerData.setAttribute("wizardAttackCd", false); }
                    }.runTaskLater(plugin, 60L);
                }
            }
        }
    }

    @EventHandler
    public void onHerderZombieAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Zombie)) return;
        Zombie zombie = (Zombie) event.getDamager();
        if (!zombie.hasMetadata("herderZombie")) return;
        Player victim = (Player) event.getEntity();
        // 赶尸人的僵尸不能攻击主人 (元数据存的是主人UUID)
        if (victim.getUniqueId().toString().equals(zombie.getMetadata("herderZombie").get(0).asString())) {
            event.setCancelled(true);
            return;
        }
        // 赶尸人的僵尸不能攻击狼人阵营
        PlayerData victimData = plugin.getPlayerDataManager().getPlayerData(victim);
        if (victimData != null && "狼人阵营".equals(victimData.getCamp())) {
            event.setCancelled(true);
            if (zombie.getTarget() != null && zombie.getTarget().getUniqueId().equals(victim.getUniqueId())) {
                zombie.setTarget(null);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerData victimData = plugin.getPlayerDataManager().getPlayerData(victim);
        GameManager gm = plugin.getGameManager();

        if (victimData == null || !victimData.isInGame()) return;
        // NOTE: gm.getCurrentState() is a legacy stub that always returns LOBBY,
        // which caused this handler to bail out and never judge the death.
        // Use the victim's actual room state instead.
        Room room = gm.getRoomForPlayer(victim);
        if (room == null || room.getCurrentState() != GameState.PLAYING) return;
        if (!victimData.isAlive()) return;

        // If this death came from Room.dealDamage(), it left a "__lethal" marker.
        // Claim it so dealDamage() doesn't double-process the kill (e.g. Slime losing 2 lives).
        if (victimData.getAttribute("__lethal") != null) {
            victimData.removeAttribute("__lethal");
        }

        // 尸潮: 被尸潮僵尸杀死的玩家在原地刷一个顶着其头颅的僵尸
        Object lastDamagerRaw = victimData.getAttribute("__lastDamager");
        victimData.removeAttribute("__lastDamager");
        if (lastDamagerRaw != null) {
            try {
                Entity lastDamager = Bukkit.getEntity(UUID.fromString(lastDamagerRaw.toString()));
                if (lastDamager instanceof Zombie && ((Zombie) lastDamager).hasMetadata("hordeZombie")) {
                    room.spawnHordeHeadZombie(victim.getLocation(), victim.getName());
                }
            } catch (IllegalArgumentException ignored) { }
        }

        // Get killer
        Player killer = victim.getKiller();
        PlayerData killerData = null;
        if (killer != null) {
            killerData = plugin.getPlayerDataManager().getPlayerData(killer);
        }

        // Clear drops and death message
        event.getDrops().clear();
        event.setDeathMessage(null);
        event.setDroppedExp(0);

        // 守护之誓复活: 保留物品栏, 否则复活后会失去所有物资
        if (room.willGuardRevive(victimData)) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }

        // Handle the kill
        victimData.setAttribute("__deathEvent", Boolean.TRUE);
        gm.killPlayer(victimData, killerData);
        victimData.removeAttribute("__deathEvent");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data != null && data.isInGame()) {
            GameManager gm = plugin.getGameManager();
            Room room = gm.getRoomForPlayer(player);
            // 复活(守护之誓 / 史莱姆): 回到死亡地点并由 Room 恢复状态
            if (data.isAlive() && room != null) {
                org.bukkit.Location pending = room.consumePendingRevive(player.getUniqueId());
                if (pending != null) {
                    event.setRespawnLocation(pending);
                    Bukkit.getScheduler().runTask(plugin, () -> room.completeRevive(player));
                    return;
                }
            }
            // Respawn at world spawn as spectator
            event.setRespawnLocation(player.getWorld().getSpawnLocation());
            // A dead player must always end up observing; respawn can reset gamemode.
            if (!data.isAlive()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (data.isInGame() && !data.isAlive() && player.isOnline()) {
                        player.setGameMode(GameMode.SPECTATOR);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        GameManager gm = plugin.getGameManager();
        Room room = gm.getRoomForPlayer(player);

        if (data == null || !data.isInGame()) return;

        // During game: allow PVP + fire + projectile + magic + effect damage, cancel the rest
        if (room != null && room.getCurrentState() == GameState.PLAYING) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK &&
                cause != EntityDamageEvent.DamageCause.FIRE &&
                cause != EntityDamageEvent.DamageCause.FIRE_TICK &&
                cause != EntityDamageEvent.DamageCause.PROJECTILE &&
                cause != EntityDamageEvent.DamageCause.MAGIC &&
                cause != EntityDamageEvent.DamageCause.VOID &&
                cause != EntityDamageEvent.DamageCause.WITHER &&
                cause != EntityDamageEvent.DamageCause.POISON &&
                cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION &&
                cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                event.setCancelled(true);
            }
        } else {
            // Not playing: cancel all damage
            event.setCancelled(true);
        }

        // Dead players take no damage
        if (!data.isAlive()) {
            event.setCancelled(true);
            return;
        }

        // 记录最后一次有效伤害来源，用于尸潮"头颅僵尸"判定
        if (!event.isCancelled() && event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager != null) data.setAttribute("__lastDamager", damager.getUniqueId().toString());
        }
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (!data.isInGame()) return;
        GameManager gm = plugin.getGameManager();
        Room room = gm.getRoomForPlayer(player);
        if (room != null && room.getCurrentState() == GameState.PLAYING) {
            // 游戏中禁止饥饿回血，但允许Regeneration药水效果和Absorption效果
            EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
            if (reason == EntityRegainHealthEvent.RegainReason.REGEN ||
                reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.EATING) {
                event.setCancelled(true);
            }
        }
    }
}
