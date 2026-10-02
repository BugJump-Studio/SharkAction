package com.werewolf.plugin;

import com.werewolf.game.GameManager;
import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.room.Room;
import com.werewolf.role.Role;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ItemUseListener implements Listener {

    private final WerewolfPlugin plugin;
    private final Set<UUID> medkitChannelling = new HashSet<>();

    public ItemUseListener(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        if (pd == null || !pd.isInGame() || !pd.isAlive()) return;
        GameManager gm = plugin.getGameManager();
        Room room = gm.getRoomForPlayer(player);
        if (room == null || room.getCurrentState() != GameState.PLAYING) return;
        // 时停: 被冻结的玩家无法使用物品
        if (room.isTimeFrozen(player)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;
        String name = meta.getDisplayName();

        // 震撼弹: 丢出雪球
        if (name.contains("震撼弹") && item.getType() == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            Snowball snowball = player.launchProjectile(Snowball.class);
            snowball.setCustomName("§e震撼弹");
            snowball.setCustomNameVisible(false);
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().removeItem(item);
            }
            return;
        }

        // 风弹: 直接扔原版雪球的话实体没有自定义名, onProjectileHit 会当成普通雪球丢弃
        if (name.contains("风弹") && item.getType() == Material.SNOWBALL) {
            event.setCancelled(true);
            Snowball windBall = player.launchProjectile(Snowball.class);
            windBall.setCustomName("§b风弹");
            windBall.setCustomNameVisible(false);
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().removeItem(item);
            }
            return;
        }

        // 医疗包: 4s读条，期间缓慢恢复3.5心
        if (name.contains("医疗包") && item.getType() == Material.GOLDEN_APPLE) {
            if (medkitChannelling.contains(player.getUniqueId())) return;
            event.setCancelled(true);
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().removeItem(item);
            medkitChannelling.add(player.getUniqueId());
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 80, 1, false, false));
            player.sendTitle("§a使用医疗包...", "§74s读条中", 0, 100, 0);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!pd.isAlive() || !player.isOnline()) { medkitChannelling.remove(player.getUniqueId()); cancel(); return; }
                    ticks++;
                    if (ticks % 20 == 0) {
                        int sec = ticks / 20;
                        player.sendTitle("§a使用医疗包...", "§7" + (4 - sec) + "s", 0, 25, 0);
                    }
                    // 缓慢恢复: 80ticks内分10次共7.0生命值(=3.5心)
                    if (ticks % 8 == 0) gm.healPlayer(player, 0.7);
                    if (ticks >= 80) {
                        medkitChannelling.remove(player.getUniqueId());
                        cancel();
                        player.sendMessage("§a医疗包使用成功！恢复3.5心血量！");
                        player.sendTitle("§a医疗完成", "", 5, 20, 5);
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
            return;
        }

        // 肾上腺素: 速度3 8s (不回血)
        if (name.contains("肾上腺素") && item.getType() == Material.POTION) {
            event.setCancelled(true);
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().removeItem(item);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, true, false));
            player.sendMessage("§a肾上腺素注射！速度3 8s");
            return;
        }

        // 商店银币(铁锭): 打开商店GUI
        // 余烬: 灯塔的余烬, 使用后速度6 3s, 结束时短暂僵直
        if (item.getType() == Material.BLAZE_POWDER && name.contains("余烬")) {
            event.setCancelled(true);
            if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
            else player.getInventory().removeItem(item);
            pd.removeTag("ember");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 5, true, false));
            player.sendTitle("§6余烬燃烧", "§7速度6 3s", 5, 30, 5);
            player.sendMessage("§6余烬已使用！速度6 3s，3s后短暂僵直");
            new BukkitRunnable() {
                @Override public void run() {
                    if (!pd.isAlive() || !player.isOnline()) return;
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 9, true, false));
                    player.sendMessage("§7余烬灭，短暂僵直！");
                }
            }.runTaskLater(plugin, 60L);
            return;
        }

        if (name.contains("商店银币") && item.getType() == Material.IRON_INGOT) {
            event.setCancelled(true);
            openShop(player);
            return;
        }
    }

    @EventHandler
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        Player player = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        if (player.equals(target)) return;
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        if (pd == null || !pd.isInGame() || !pd.isAlive()) return;
        GameManager gm = plugin.getGameManager();
        Room room = gm.getRoomForPlayer(player);
        if (room == null || room.getCurrentState() != GameState.PLAYING) return;
        if (room.isTimeFrozen(player)) return;
        // 炸弹客烫手山芋: 持有炸弹者右键 r=4 内玩家传递炸弹
        room.tryPassBomb(player, target);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball)) return;
        Snowball snowball = (Snowball) event.getEntity();
        if (snowball.getShooter() == null) return;
        if (!(snowball.getShooter() instanceof Player)) return;
        String shotName = snowball.getCustomName();
        if (shotName == null) return;

        // 风弹: 命中点 r=3 内所有生物被吹开
        if (shotName.contains("风弹")) {
            Location windLoc = snowball.getLocation();
            windLoc.getWorld().playSound(windLoc, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.6f);
            windLoc.getWorld().spawnParticle(Particle.CLOUD, windLoc, 20, 0.3, 0.3, 0.3, 0.05);
            for (Entity en : windLoc.getWorld().getNearbyEntities(windLoc, 3, 3, 3)) {
                org.bukkit.util.Vector dir = en.getLocation().toVector().subtract(windLoc.toVector());
                if (dir.lengthSquared() < 1.0E-6) dir = new org.bukkit.util.Vector(0, 1, 0);
                en.setVelocity(dir.normalize().multiply(1.2).setY(0.5));
            }
            return;
        }

        if (!shotName.contains("震撼弹")) return;

        Location hitLoc = snowball.getLocation();
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        hitLoc.getWorld().createExplosion(hitLoc, 0F);
        // 震撼弹不造成伤害，只对被炸到的玩家施加缓慢255 3s
        for (Entity entity : hitLoc.getWorld().getNearbyEntities(hitLoc, 3, 3, 3)) {
            if (entity instanceof Player) {
                Player p = (Player) entity;
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 160, 0, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false));
                p.sendMessage("§e被震撼弹命中！缓慢5 3s 反胃8s 黑暗3s");
            }
        }
    }

    private void openShop(Player player) {
        org.bukkit.inventory.Inventory shop = Bukkit.createInventory(null, 27, "§e商店");

        int emeralds = countItem(player, Material.EMERALD);
        int redstones = countItem(player, Material.REDSTONE);
        String bal = "§7绿:" + emeralds + " 红:" + redstones;

        shop.setItem(0, makeShopItem(Material.FIRE_CHARGE, "§e震撼弹×1", "§a10绿", bal));
        shop.setItem(1, makeShopItem(Material.POTION, "§a肾上腺素×1", "§a20绿", bal));
        shop.setItem(2, makeShopItem(Material.GOLDEN_APPLE, "§a医疗包×3", "§a20绿", bal));
        shop.setItem(3, makeShopItem(Material.LINGERING_POTION, "§a滞留型治疗药水", "§a30绿 §7生命恢复2 3s", bal));
        shop.setItem(4, makeShopItem(Material.LINGERING_POTION, "§c滞留型中毒药水", "§a20红 §7中毒2 3s", bal));
        shop.setItem(5, makeShopItem(Material.STICK, "§c撬棍", "§a20红 §7+14攻击力", bal));
        shop.setItem(6, makeShopItem(Material.NETHERITE_SWORD, "§c秒刀", "§c50红 §7锋利255 耐久1", bal));
        shop.setItem(7, makeShopItem(Material.SHIELD, "§b盾", "§a50绿 §7或 §c30红 §7耐久30", bal));
        shop.setItem(9, makeShopItem(Material.TOTEM_OF_UNDYING, "§6不死图腾", "§a200绿", bal));
        shop.setItem(10, makeShopItem(Material.SNOWBALL, "§b风弹×1", "§a30绿 §7或 §c15红", bal));
        shop.setItem(11, makeShopItem(Material.FISHING_ROD, "§b鱼竿", "§a50绿 §7或 §c30红", bal));
        shop.setItem(12, makeShopItem(Material.WRITABLE_BOOK, "§4死亡笔记", "§a150绿 §7或 §c90红", bal));

        player.openInventory(shop);
    }

    private boolean buy(Player player, int emeraldCost, int redstoneCost, Runnable grant) {
        int emeralds = countItem(player, Material.EMERALD);
        int redstones = countItem(player, Material.REDSTONE);
        if (emeraldCost > 0 && emeralds >= emeraldCost) {
            removeItem(player, Material.EMERALD, emeraldCost);
        } else if (redstoneCost > 0 && redstones >= redstoneCost) {
            removeItem(player, Material.REDSTONE, redstoneCost);
        } else {
            player.sendMessage("§c货币不足！");
            return false;
        }
        grant.run();
        player.sendMessage("§a购买成功！");
        return true;
    }

    private ItemStack makeLingeringEffect(PotionEffectType type, int amplifier, int ticks) {
        ItemStack item = new ItemStack(Material.LINGERING_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, ticks, amplifier), true);
        meta.setColor(type.getColor());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(Collections.singletonList(lore));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onShopClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains("商店")) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        if (pd == null || !pd.isInGame() || !pd.isAlive()) return;

        GameManager gm = plugin.getGameManager();

        switch (slot) {
            case 0: buy(player, 10, 0, () -> player.getInventory().addItem(gm.makeStunGrenade(1))); break;
            case 1: buy(player, 20, 0, () -> player.getInventory().addItem(gm.makeAdrenaline(1))); break;
            case 2: buy(player, 20, 0, () -> player.getInventory().addItem(gm.makeMedkit(3))); break;
            case 3: buy(player, 30, 0, () -> player.getInventory().addItem(makeLingeringEffect(PotionEffectType.REGENERATION, 1, 60))); break;
            case 4: buy(player, 0, 20, () -> player.getInventory().addItem(makeLingeringEffect(PotionEffectType.POISON, 1, 60))); break;
            case 5: buy(player, 0, 20, () -> player.getInventory().addItem(gm.makeCrowbar())); break;
            case 6: buy(player, 0, 50, () -> {
                ItemStack blade = new ItemStack(Material.NETHERITE_SWORD); ItemMeta bm = blade.getItemMeta();
                bm.addEnchant(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 255, true);
                bm.setDisplayName("§4秒刀"); blade.setItemMeta(bm); blade.setDurability((short) (Material.NETHERITE_SWORD.getMaxDurability() - 1));
                player.getInventory().addItem(blade);
            }); break;
            case 7: buy(player, 50, 30, () -> {
                ItemStack shield = new ItemStack(Material.SHIELD);
                shield.setDurability((short) (Material.SHIELD.getMaxDurability() - 30));
                player.getInventory().addItem(shield);
            }); break;
            case 9: buy(player, 200, 0, () -> player.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING))); break;
            case 10: buy(player, 30, 15, () -> player.getInventory().addItem(makeItem(Material.SNOWBALL, "§b风弹", "§7风灵之力"))); break;
            case 11: buy(player, 50, 30, () -> player.getInventory().addItem(new ItemStack(Material.FISHING_ROD))); break;
            case 12: buy(player, 150, 90, () -> player.getInventory().addItem(makeItem(Material.WRITABLE_BOOK, "§4死亡笔记", "§7写下一个活着的玩家名字"))); break;
        }
        player.updateInventory();
    }

    private int countItem(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private void removeItem(Player player, Material mat, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
    }

    private ItemStack makeShopItem(Material mat, String name, String line1, String line2) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(line1, line2));
        item.setItemMeta(meta);
        return item;
    }
}
