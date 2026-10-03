package com.werewolf.room;

import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.plugin.WerewolfPlugin;
import com.werewolf.role.Role;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class Room {

    private final WerewolfPlugin plugin;
    private final String roomId;
    private final String roomName;
    private String ownerName;
    private GameState state;
    private final List<PlayerData> players;
    private int remainingTime;
    private BukkitTask gameTimer;
    private BukkitTask scoreboardTask;
    private BukkitTask passiveTickTask;
    private BukkitTask startCountdownTask;
    private BukkitTask battleCountdownTask;
    private BukkitTask pendingResetTask;
    private final Random random;

    private static final int GAME_DURATION = 500;

    private final Map<UUID, BukkitTask> activeBombs = new HashMap<>();
    private final Map<UUID, BukkitTask> hackerAuraTasks = new HashMap<>();
    private final Set<UUID> countFrenzyActive = new HashSet<>();
    private final Map<UUID, BukkitRunnable> countFrenzyTasks = new HashMap<>();
    private final Map<UUID, Integer> wizardEnergy = new HashMap<>();
    private final Map<UUID, Integer> slimeLives = new HashMap<>();
    private final Map<UUID, Integer> bladeSoulS1Stacks = new HashMap<>();
    private final Map<UUID, Integer> bladeSoulS2Stacks = new HashMap<>();
    private final Map<UUID, UUID> ninjaPlayerMarks = new HashMap<>();
    private final Map<UUID, Location> ninjaReturnMarks = new HashMap<>();
    private final Map<UUID, Location> hackerLocationMarks = new HashMap<>();
    private final Map<UUID, List<UUID>> pelicanStoredPlayers = new HashMap<>();
    private final Map<UUID, Integer> revivalCharges = new HashMap<>();
    private final Map<UUID, Location> pendingRevives = new HashMap<>();
    private final List<Zombie> herderZombies = new ArrayList<>();
    private final Map<UUID, Long> hitFeedbackAt = new HashMap<>();
    private boolean suppressCooldown = false;
    private String activeRandomEvent = null;
    private final List<BukkitTask> randomEventTasks = new ArrayList<>();
    private UUID cursedBladeHolder = null;
    private final List<ArmorStand> flyingKnives = new ArrayList<>();
    private final List<ArmorStand> dioStands = new ArrayList<>();
    private final List<Creeper> eventCreepers = new ArrayList<>();
    private final List<Zombie> eventZombies = new ArrayList<>();
    private final List<Bat> summonBats = new ArrayList<>();
    private final Map<UUID, Integer> timeStopFrozen = new HashMap<>();
    private final Map<UUID, BombState> bombStates = new HashMap<>();
    private final Map<UUID, TideBomb> tideBombs = new HashMap<>();
    private BukkitTask tideBombTask;
    private final Set<UUID> bomberArmed = new HashSet<>();
    private int timeStopGameSeconds = 0;
    private BukkitTask timeStopTask;

    public Room(WerewolfPlugin plugin, String roomId, String roomName, String ownerName) {
        this.plugin = plugin;
        this.roomId = roomId;
        this.roomName = roomName;
        this.ownerName = ownerName;
        this.state = GameState.LOBBY;
        this.players = new ArrayList<>();
        this.remainingTime = GAME_DURATION;
        this.random = new Random();
    }

    // ==================== GAME FLOW ====================

    public boolean joinGame(PlayerData player) {
        if (state != GameState.LOBBY) return false;
        int maxPlayers = plugin.getConfigManager().getConfig().getInt("game.max-players", 14);
        if (players.size() >= maxPlayers) return false;
        if (players.contains(player)) return true;
        players.add(player);
        player.setInGame(true);
        player.setAlive(true);
        broadcast("§e" + player.getUsername() + " §a加入了游戏！ (" + players.size() + "/" +
            maxPlayers + ")");
        return true;
    }

    public void leaveGame(PlayerData player) {
        if (!players.remove(player)) return;
        player.setInGame(false);
        player.resetGameState();
        broadcast("§e" + player.getUsername() + " §c离开了游戏！");
        if (state == GameState.STARTING) {
            int minPlayers = plugin.getConfigManager().getConfig().getInt("game.min-players", 2);
            if (players.size() < minPlayers) {
                cancelCountdownTasks();
                state = GameState.LOBBY;
                for (PlayerData p : players) { p.setRole(null); p.setCamp(null); }
                broadcast("§c人数不足，游戏开始已取消！");
            }
        } else if (state == GameState.PLAYING && !players.isEmpty()) {
            checkGameEnd();
        }
    }

    public void startGame() {
        if (state != GameState.LOBBY) return;
        int playerCount = players.size();
        int minPlayers = plugin.getConfigManager().getConfig().getInt("game.min-players", 2);
        if (playerCount < minPlayers) {
            broadcast("§c玩家数量不足！需要至少 " + minPlayers + " 名玩家。");
            return;
        }
        state = GameState.STARTING;
        assignRoles();
        startCountdownTask = new BukkitRunnable() {
            int countdown = 30;
            @Override
            public void run() {
                if (state != GameState.STARTING) { cancel(); startCountdownTask = null; return; }
                if (countdown > 0) {
                    for (PlayerData p : players) {
                        Player bp = p.getBukkitPlayer();
                        if (bp != null && bp.isOnline()) {
                            bp.sendTitle("§e" + countdown, "§7游戏即将开始", 0, 25, 0);
                            if (countdown <= 10) p.sendActionBar("§e§l倒计时: §c" + countdown + " §e秒");
                        }
                    }
                    countdown--;
                } else {
                    cancel();
                    startCountdownTask = null;
                    announceRoles();
                    startBattleCountdown();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startBattleCountdown() {
        battleCountdownTask = new BukkitRunnable() {
            int countdown = 3;
            @Override
            public void run() {
                if (state != GameState.STARTING) { cancel(); battleCountdownTask = null; return; }
                if (countdown > 0) {
                    for (PlayerData p : players) {
                        Player bp = p.getBukkitPlayer();
                        if (bp != null && bp.isOnline()) bp.sendTitle("§c§l" + countdown, "§7战斗即将开始！", 0, 25, 0);
                    }
                    countdown--;
                } else {
                    cancel();
                    battleCountdownTask = null;
                    startBattle();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void announceRoles() {
        for (PlayerData player : players) {
            Role role = player.getRole();
            if (role == null) continue;
            Player bp = player.getBukkitPlayer();
            if (bp == null || !bp.isOnline()) continue;
            bp.sendTitle(role.getDisplayName(), role.getDescription(), 10, 80, 20);
            bp.sendMessage("§6=========================================");
            bp.sendMessage("§e你的身份: " + role.getDisplayName());
            bp.sendMessage("§7" + role.getDescription());
            bp.sendMessage("");
            if (role.hasSkill1()) bp.sendMessage("§e技能1 §f- " + role.getSkill1Name());
            if (role.hasSkill2()) bp.sendMessage("§e技能2 §f- " + role.getSkill2Name());
            if (role.hasSkill3()) bp.sendMessage("§e技能3 §f- " + role.getSkill3Name());
            if (role.hasSkill4()) bp.sendMessage("§e技能4 §f- " + role.getSkill4Name());
            if (role.hasPassiveSkill()) bp.sendMessage("§e被动 §f- " + role.getPassiveSkillName());
            bp.sendMessage("");
            if (role.hasSkill1() || role.hasSkill2() || role.hasSkill3() || role.hasSkill4()) {
                bp.sendMessage("§7右键物品栏中的钻石释放技能");
            } else {
                bp.sendMessage("§7本身份没有主动技能，被动效果自动生效");
            }
            bp.sendMessage("§6=========================================");
        }
    }

    private void assignRoles() {
        int playerCount = players.size();
        int werewolfCount = getWerewolfCount(playerCount);
        int[] neutralCounts = getNeutralCounts(playerCount);
        int peacefulNeutralCount = neutralCounts[0];
        int dangerousNeutralCount = neutralCounts[1];
        int goodCount = playerCount - werewolfCount - peacefulNeutralCount - dangerousNeutralCount;

        List<Role> availableRoles = new ArrayList<>();
        List<Role> wolfRoles = new ArrayList<>(getWolfRoles()); Collections.shuffle(wolfRoles);
        for (int i = 0; i < werewolfCount && !wolfRoles.isEmpty(); i++) availableRoles.add(wolfRoles.remove(0));
        List<Role> peacefulRoles = new ArrayList<>(getPeacefulNeutralRoles()); Collections.shuffle(peacefulRoles);
        for (int i = 0; i < peacefulNeutralCount && !peacefulRoles.isEmpty(); i++) availableRoles.add(peacefulRoles.remove(0));
        List<Role> dangerousRoles = new ArrayList<>(getDangerousNeutralRoles()); Collections.shuffle(dangerousRoles);
        for (int i = 0; i < dangerousNeutralCount && !dangerousRoles.isEmpty(); i++) availableRoles.add(dangerousRoles.remove(0));
        List<Role> goodRoles = new ArrayList<>(getGoodRoles()); Collections.shuffle(goodRoles);
        // 预言家位(水占师/提灯人)一局只能有一个
        boolean seerTaken = false;
        for (int i = 0; i < goodCount && !goodRoles.isEmpty(); ) {
            Role r = goodRoles.remove(0);
            if (isSeerRole(r)) {
                if (seerTaken) continue;
                seerTaken = true;
            }
            availableRoles.add(r);
            i++;
        }
        Collections.shuffle(availableRoles);

        for (int i = 0; i < players.size() && i < availableRoles.size(); i++) {
            players.get(i).setRole(availableRoles.get(i));
        }
    }

    // 预言家位: 水占师 / 提灯人 二者只能出现一个
    private static boolean isSeerRole(Role role) {
        if (role == null) return false;
        return "WATER DIVINATION".equals(role.getName()) || "LANTERN BEARER".equals(role.getName());
    }

    private int getWerewolfCount(int pc) {        if (pc <= 4) return 1; if (pc <= 6) return 2; if (pc <= 10) return 3; return 4;
    }

    private int[] getNeutralCounts(int pc) {
        int p = 0, d = 0;
        if (pc >= 13) {
            double r = random.nextDouble();
            if (pc == 13) { if (r < 0.4) { d = 1; p = 1; } else { p = 2; } }
            else { if (r < 0.6) { d = 1; p = 1; } else { p = 2; } }
        } else if (pc >= 9) p = 1;
        return new int[]{p, d};
    }

    private List<Role> getWolfRoles() { return getRolesByNames(new String[]{"THE WHITE WOLF KING","WOLF WITCH","WOLF JACKAL","CORPSE HERDER","GAMBLER","DIO","KILLER","CRIMSON MESSENGER","TIDE SINGER","BOMBER","COUNT"}); }
    private List<Role> getGoodRoles() { return getRolesByNames(new String[]{"GUARD","KNIGHT","NINJA","HACKER","SLIME","BUDDHIST MONK","WIND SPIRIT","WATER DIVINATION","LANTERN BEARER","THE SURVIVOR","BLADE AND SOUL"}); }
    private List<Role> getPeacefulNeutralRoles() { return getRolesByNames(new String[]{"ARSONIST","TIME DUKE","HAMSTER","PELICAN"}); }
    private List<Role> getDangerousNeutralRoles() { return getRolesByNames(new String[]{"NEUTRAL JACKAL","WIZARD"}); }

    private List<Role> getRolesByNames(String[] names) {
        List<Role> roles = new ArrayList<>();
        for (String n : names) { Role r = plugin.getRoleManager().getRole(n); if (r != null) roles.add(r); }
        return roles;
    }

    // ==================== BATTLE INIT ====================

    private void startBattle() {
        state = GameState.PLAYING;
        remainingTime = GAME_DURATION;
        // 禁用自然回血
        for (PlayerData player : players) {
            Player bp = player.getBukkitPlayer();
            if (bp != null) bp.getWorld().setGameRuleValue("naturalRegeneration", "false");
        }
        for (PlayerData player : players) initializePlayerForBattle(player);
        for (PlayerData player : players) {
            Role role = player.getRole();
            if (role == null) continue;
            String rn = role.getName();
            Player bp = player.getBukkitPlayer();
            if (bp == null) continue;
            UUID uuid = bp.getUniqueId();
            if ("WIND SPIRIT".equals(rn)) { player.setAttribute("windIndex", 0); player.setAttribute("windTick", 0); }
            if ("SLIME".equals(rn)) slimeLives.put(uuid, 3);
            if ("WIZARD".equals(rn)) wizardEnergy.put(uuid, 200);
            if ("BLADE AND SOUL".equals(rn)) { bladeSoulS1Stacks.put(uuid, 4); bladeSoulS2Stacks.put(uuid, 4); }
            if ("CORPSE HERDER".equals(rn)) player.setAttribute("herderCharges", 2);
            if ("NINJA".equals(rn)) {
                player.setAttribute("ninjaCharges", 1);
                // 每50s获得一次充能
                new BukkitRunnable() {
                    @Override public void run() {
                        if (state != GameState.PLAYING || !player.isAlive()) { cancel(); return; }
                        Integer ch = (Integer) player.getAttribute("ninjaCharges");
                        if (ch == null) ch = 0;
                        player.setAttribute("ninjaCharges", ch + 1);
                        Player nbp = player.getBukkitPlayer();
                        if (nbp != null && nbp.isOnline()) nbp.sendMessage("§a忍者充能+1（当前: " + (ch + 1) + "）");
                    }
                }.runTaskTimer(plugin, 1000L, 1000L);
            }
            if ("CRIMSON MESSENGER".equals(rn)) {
                bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(32.0);
            }
            if ("WATER DIVINATION".equals(rn)) { player.setSkillCooldown("skill1", 30); }
            // 开局进CD
            if ("WOLF WITCH".equals(rn)) {
                player.setSkillCooldown("skill1", 35);
                player.setSkillCooldown("skill2", 50);
                player.setSkillCooldown("skill3", 40);
            }
            if ("DIO".equals(rn)) { player.setSkillCooldown("skill1", 80); }
            if ("BOMBER".equals(rn)) { bomberArmed.add(uuid); }
        }
        // 开局资源: 好人20绿(预言家60绿) / 狼人20绿+45红
        for (PlayerData player : players) {
            Player bp = player.getBukkitPlayer();
            if (bp == null || !bp.isOnline()) continue;
            String camp = player.getCamp();
            if ("狼人阵营".equals(camp)) {
                bp.getInventory().addItem(new ItemStack(Material.EMERALD, 20));
                bp.getInventory().addItem(new ItemStack(Material.REDSTONE, 45));
                bp.sendMessage("§c狼人开局资源：20绿宝石 + 45红宝石");
            } else if ("好人阵营".equals(camp)) {
                if (isSeerRole(player.getRole())) {
                    bp.getInventory().addItem(new ItemStack(Material.EMERALD, 60));
                    // 预言家位额外6黄心
                    bp.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 2, true, false));
                    bp.sendMessage("§6预言家开局资源：60绿宝石 + 6黄心");
                } else {
                    bp.getInventory().addItem(new ItemStack(Material.EMERALD, 20));
                    bp.sendMessage("§a好人开局资源：20绿宝石");
                }
            } else if (isNeutralCamp(camp)) {
                // 中立(和平/危险)开局20绿宝石
                bp.getInventory().addItem(new ItemStack(Material.EMERALD, 20));
                bp.sendMessage("§e中立开局资源：20绿宝石");
            }
        }
        broadcast("§c§l========== 战斗开始！==========");
        broadcast("§e所有玩家可以互相攻击！ 游戏时长: §c500秒");
        startGameTimer();
        startPassiveTick();
        startScoreboardUpdater();
        // 被动收入: 每2s所有玩家获得1绿宝石
        new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                for (PlayerData pd : players) {
                    if (!pd.isAlive()) continue;
                    Player p = pd.getBukkitPlayer();
                    if (p != null && p.isOnline()) p.getInventory().addItem(new ItemStack(Material.EMERALD, 1));
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
        // 剑灵【刺/扫】每7s补充1层, 上限4层
        new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                for (PlayerData pd : players) {
                    if (!pd.isAlive() || pd.getRole() == null || !"BLADE AND SOUL".equals(pd.getRole().getName())) continue;
                    Player bp = pd.getBukkitPlayer();
                    if (bp == null || !bp.isOnline()) continue;
                    UUID u = bp.getUniqueId();
                    bladeSoulS1Stacks.put(u, Math.min(bladeSoulS1Stacks.getOrDefault(u, 0) + 1, 4));
                    bladeSoulS2Stacks.put(u, Math.min(bladeSoulS2Stacks.getOrDefault(u, 0) + 1, 4));
                }
            }
        }.runTaskTimer(plugin, 140L, 140L);
        // 5%概率触发随机事件
        if (random.nextInt(100) < 5) {
            String[] events = {"THUNDERSTORM", "CURSED_BLADE", "GEM_NATION", "ZOMBIE_HORDE", "EARTHQUAKE", "HURRICANE", "SCORCHING_SUN", "CREEPER_CRISIS"};
            activeRandomEvent = events[random.nextInt(events.length)];
            broadcast("§c§l§k== 随机事件触发！ ==§r");
            switch (activeRandomEvent) {
                case "THUNDERSTORM": broadcast("§9§l夜间雷暴雨！注意避难！"); startThunderstormEvent(); break;
                case "CURSED_BLADE": broadcast("§5§l魔刀降世！地图上刺出一把魔剑！"); startCursedBladeEvent(); break;
                case "GEM_NATION": broadcast("§6§l宝石之国！每20s随机玩家获得宝石！"); startGemNationEvent(); break;
                case "ZOMBIE_HORDE": broadcast("§2§c尸潮！尸将不断涌发！"); startZombieHordeEvent(); break;
                case "EARTHQUAKE": broadcast("§8§l地震！建筑物会坍塌！"); startEarthquakeEvent(); break;
                case "HURRICANE": broadcast("§b§l飓风！风向每20s变化！"); startHurricaneEvent(); break;
                case "SCORCHING_SUN": broadcast("§6§l烈阳天！暴露在太阳下会受伤！"); startScorchingSunEvent(); break;
                case "CREEPER_CRISIS": broadcast("§2§l苦力怕大危机！苦力怕不断刷出！"); startCreeperCrisisEvent(); break;
            }
        }
    }

    private void initializePlayerForBattle(PlayerData player) {
        Player bp = player.getBukkitPlayer();
        if (bp == null || !bp.isOnline()) return;
        bp.setHealth(bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue());
        bp.setGameMode(GameMode.ADVENTURE);
        bp.setFoodLevel(19); bp.setSaturation(0f); bp.setExhaustion(20f);
        bp.getInventory().clear();
        bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
        Role role = player.getRole();
        if (role != null) {
            if (role.hasSkill1() && grantsSkillDiamond(role, 1)) bp.getInventory().setItem(0, makeItem(Material.DIAMOND, "§b一技能 - " + role.getSkill1Name(), "§7右键释放技能"));
            if (role.hasSkill2() && grantsSkillDiamond(role, 2)) bp.getInventory().setItem(1, makeItem(Material.DIAMOND, "§b二技能 - " + role.getSkill2Name(), "§7右键释放技能"));
            if (role.hasSkill3() && grantsSkillDiamond(role, 3)) bp.getInventory().setItem(2, makeItem(Material.DIAMOND, "§b三技能 - " + role.getSkill3Name(), "§7右键释放技能"));
            if (role.hasSkill4() && grantsSkillDiamond(role, 4)) bp.getInventory().setItem(3, makeItem(Material.DIAMOND, "§b四技能 - " + role.getSkill4Name(), "§7右键释放技能"));
            giveRoleItems(player, role);
        }
        player.setSkillCooldown("skill1", 0);
        player.setSkillCooldown("skill2", 0);
        player.setSkillCooldown("skill3", 0);
        player.setSkillCooldown("skill4", 0);
    }

    // 部分技能不发放技能钻石(自动触发/不需要手动释放)
    private static boolean grantsSkillDiamond(Role role, int skillNum) {
        if ("HACKER".equals(role.getName()) && skillNum == 1) return false;
        return true;
    }

    private void giveRoleItems(PlayerData player, Role role) {
        Player bp = player.getBukkitPlayer();
        if (bp == null) return;
        String rn = role.getName();
        // 特殊分配身份(豺狼/跟班): 开局无物资，仅靠被动与钻石技能
        if (!"NEUTRAL JACKAL".equals(rn) && !"FOLLOWER".equals(rn)) {
            // 默认初始物品: 短剑 + 弓(无限耐久3) + 箭1 + 震撼弹1 + 医疗包1 + 铁锭1
            giveDefaultItems(bp);
        }
        switch (rn) {
            case "WRETCHER":
                // 初始物资: 撬棍 + 震撼弹4 + 医疗包3
                removeDefaultItems(bp);
                bp.getInventory().addItem(makeCrowbar());
                for (int i = 0; i < 4; i++) bp.getInventory().addItem(makeStunGrenade(1));
                for (int i = 0; i < 3; i++) bp.getInventory().addItem(makeMedkit(1));
                break;
            case "THE SURVIVOR":
                removeDefaultItems(bp);
                ItemStack sSword = makeItem(Material.STONE_SWORD, "§c残破的剑", "");
                sSword.setDurability((short)(sSword.getType().getMaxDurability() / 2));
                bp.getInventory().addItem(sSword);
                ItemStack sBow = makeItem(Material.BOW, "§c残破的弓", "");
                ItemMeta sBowMeta = sBow.getItemMeta();
                sBowMeta.addEnchant(org.bukkit.enchantments.Enchantment.ARROW_INFINITE, 1, true);
                sBowMeta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 3, true);
                sBow.setItemMeta(sBowMeta);
                sBow.setDurability((short)(sBow.getType().getMaxDurability() / 2));
                bp.getInventory().addItem(sBow);
                bp.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                for (int i = 0; i < 3; i++) bp.getInventory().addItem(makeStunGrenade(1));
                bp.getInventory().addItem(makeAdrenaline(1));
                for (int i = 0; i < 5; i++) bp.getInventory().addItem(makeMedkit(1));
                break;
            case "WIZARD":
                removeDefaultItems(bp);
                ItemStack ws = makeItem(Material.WOODEN_SWORD, "§d法杖", "§7Knockback II");
                ItemMeta wsMeta = ws.getItemMeta();
                wsMeta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 2, true);
                ws.setItemMeta(wsMeta);
                bp.getInventory().addItem(ws);
                break;
            case "HAMSTER":
                removeDefaultItems(bp);
                bp.getInventory().addItem(makeShortSword());
                bp.getInventory().addItem(makeBow());
                bp.getInventory().addItem(new ItemStack(Material.ARROW, 1));
                for (int i = 0; i < 3; i++) bp.getInventory().addItem(makeStunGrenade(1));
                for (int i = 0; i < 2; i++) bp.getInventory().addItem(makeMedkit(1));
                break;
            case "NEUTRAL JACKAL":
            case "FOLLOWER":
                break;
            case "CRIMSON MESSENGER":
                // 血量上限32 同时补满血 + 开局6黄心
                bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(32.0);
                bp.setHealth(32.0);
                bp.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 2, true, false));
                break;
        }
    }

    private void removeDefaultItems(Player bp) {
        bp.getInventory().remove(Material.IRON_SWORD);
        bp.getInventory().remove(Material.BOW);
        bp.getInventory().remove(Material.ARROW);
        bp.getInventory().remove(Material.FIRE_CHARGE);
        bp.getInventory().remove(Material.GOLDEN_APPLE);
        bp.getInventory().remove(Material.IRON_INGOT);
    }

    private void giveDefaultItems(Player bp) {
        bp.getInventory().addItem(makeShortSword());
        bp.getInventory().addItem(makeBow());
        bp.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        bp.getInventory().addItem(makeStunGrenade(1));
        bp.getInventory().addItem(makeMedkit(1));
        bp.getInventory().addItem(makeItem(Material.IRON_INGOT, "§e商店银币", "§7右键打开商店"));
    }

    private ItemStack makeShortSword() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c短剑");
        meta.setLore(Collections.singletonList("§7+攻击力 8"));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
            new org.bukkit.attribute.AttributeModifier("attack_damage", 8, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeCrowbar() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c撬棍");
        meta.setLore(Collections.singletonList("§7+攻击力 14"));
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
            new org.bukkit.attribute.AttributeModifier("attack_damage", 14, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.ARROW_INFINITE, 1, true);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 3, true);
        meta.setDisplayName("§7弓");
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeStunGrenade(int amount) {
        return makeItem(Material.FIRE_CHARGE, "§e震撼弹", "§7不伤害 缓慢5 3s 反胃8s 黑暗3s", amount);
    }

    public ItemStack makeMedkit(int amount) {
        return makeItem(Material.GOLDEN_APPLE, "§a医疗包", "§7右键使用 4s读条恢复3.5心", amount);
    }

    public ItemStack makeAdrenaline(int amount) {
        return makeItem(Material.POTION, "§a肾上腺素", "§7右键使用 速度3八秒", amount);
    }

    private ItemStack makeItem(Material mat, String name, String lore) {
        return makeItem(mat, name, lore, 1);
    }

    private ItemStack makeItem(Material mat, String name, String lore, int amount) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); if (!lore.isEmpty()) meta.setLore(Collections.singletonList(lore)); item.setItemMeta(meta); }
        return item;
    }

    // ==================== TIMERS ====================

    private void startGameTimer() {
        gameTimer = new BukkitRunnable() {
            @Override public void run() {
                // 时停期间游戏时间冻结
                if (timeStopGameSeconds > 0) {
                    timeStopGameSeconds--;
                    if (timeStopGameSeconds == 0) broadcast("§5时间恢复流动！");
                    return;
                }
                remainingTime--;
                if (remainingTime == 60) broadcast("§c§l还剩60秒！");
                else if (remainingTime == 30) broadcast("§c§l还剩30秒！");
                else if (remainingTime == 10) broadcast("§c§l还剩10秒！");
                if (remainingTime <= 0) endGameWithDraw();
                else checkGameEnd();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startPassiveTick() {
        passiveTickTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                // 强制禁止自然回血 + 全程虚弱2
                for (PlayerData pd : players) {
                    if (!pd.isAlive()) continue;
                    Player bp = pd.getBukkitPlayer();
                    if (bp != null && bp.isOnline()) {
                        bp.setFoodLevel(19);
                        bp.setSaturation(0f);
                        bp.setExhaustion(20f);
                        bp.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, true, false));
                    }
                }
                tickPassives();
                tickCooldowns();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickCooldowns() {
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            for (String key : new String[]{"skill1", "skill2", "skill3", "skill4"}) {
                int cd = pd.getSkillCooldown(key);
                if (cd > 0) pd.setSkillCooldown(key, cd - 1);
            }
        }
    }

    private void startScoreboardUpdater() {
        scoreboardTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                for (PlayerData p : players) {
                    if (!p.isAlive()) continue;
                    Player bp = p.getBukkitPlayer();
                    if (bp == null || !bp.isOnline()) continue;
                    StringBuilder ab = new StringBuilder();
                    ab.append("§c剩余:").append(formatTime(remainingTime));
                    ab.append(" §a♥").append((int)bp.getHealth()).append("/").append((int)bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                    Role role = p.getRole();
                    boolean isNinja = role != null && "NINJA".equals(role.getName());
                    if (role != null && role.hasSkill1()) ab.append(" §eS1:").append(p.getSkillCooldown("skill1") > 0 ? "§c" + p.getSkillCooldown("skill1") + "s" : "§a就绪");
                    if (role != null && role.hasSkill2()) {
                        if (isNinja && ninjaPlayerMarks.containsKey(bp.getUniqueId())) ab.append(" §eS2:§a已标记");
                        else ab.append(" §eS2:").append(p.getSkillCooldown("skill2") > 0 ? "§c" + p.getSkillCooldown("skill2") + "s" : "§a就绪");
                    }
                    if (role != null && role.hasSkill3()) {
                        if (isNinja && ninjaReturnMarks.containsKey(bp.getUniqueId())) ab.append(" §eS3:§a已标记");
                        else ab.append(" §eS3:").append(p.getSkillCooldown("skill3") > 0 ? "§c" + p.getSkillCooldown("skill3") + "s" : "§a就绪");
                    }
                    if (role != null && role.hasSkill4()) ab.append(" §eS4:").append(p.getSkillCooldown("skill4") > 0 ? "§c" + p.getSkillCooldown("skill4") + "s" : "§a就绪");
                    if (role != null) {
                        if ("WIZARD".equals(role.getName())) ab.append(" §b能量:").append(wizardEnergy.getOrDefault(bp.getUniqueId(), 200));
                        if ("BLADE AND SOUL".equals(role.getName())) ab.append(" §e刺:").append(bladeSoulS1Stacks.getOrDefault(bp.getUniqueId(),0)).append(" §c扫:").append(bladeSoulS2Stacks.getOrDefault(bp.getUniqueId(),0));
                        if (isNinja) {
                            Object ch = p.getAttribute("ninjaCharges");
                            ab.append(" §a速:").append(ch == null ? 0 : ch);
                        }
                        // 狼人阵营: 队友列表
                        if ("狼人阵营".equals(p.getCamp())) {
                            String teammates = buildWolfTeammateList(p);
                            if (teammates != null) ab.append(" §c队友:").append(teammates);
                        }
                    }
                    p.sendActionBar(ab.toString());
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // 狼人阵营队友列表(排除自己, 只列存活队友)
    private String buildWolfTeammateList(PlayerData self) {
        StringBuilder sb = new StringBuilder();
        for (PlayerData other : players) {
            if (other == self || !other.isAlive()) continue;
            if (!"狼人阵营".equals(other.getCamp())) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline()) continue;
            if (sb.length() > 0) sb.append("§8, ");
            sb.append("§f").append(other.getUsername());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private String formatTime(int s) { return String.format("%d:%02d", s / 60, s % 60); }
    // ==================== PASSIVES ====================

    private void tickPassives() {
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            Role role = pd.getRole();
            if (role == null) continue;
            Player bp = pd.getBukkitPlayer();
            if (bp == null || !bp.isOnline()) continue;
        suppressCooldown = false;
        switch (role.getName()) {
                case "THE WHITE WOLF KING": tickWhiteWolfKingPassive(pd, bp); break;
                case "COUNT":
                    if (countFrenzyActive.contains(bp.getUniqueId())) applyFrenzyVisual(bp);
                    tickCountPassive(pd, bp);
                    break;
                case "HAMSTER": bp.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 40, 3, true, false)); break;
                case "MONK": bp.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 40, 3, true, false)); break;
                case "WIND SPIRIT": tickWindSpiritPassive(pd, bp); break;
                case "HACKER": tickHackerPassive(pd, bp); break;
                case "LANTERN BEARER": tickLanternBearerPassive(pd, bp); break;
                case "WIZARD": tickWizardPassive(bp); break;
                case "SLIME": tickSlimePassive(pd, bp); break;
                case "NEUTRAL JACKAL": tickInstakillPassive(pd, bp); break;
                case "FOLLOWER": tickInstakillPassive(pd, bp); break;
                case "TIME DUKE": tickTimeDukePassive(pd, bp); break;
            }
        }
        // 魔刀降世: 检查谁拿着魔刀
        if ("CURSED_BLADE".equals(activeRandomEvent)) {
            for (PlayerData pd2 : players) {
                if (!pd2.isAlive()) continue;
                Player bp2 = pd2.getBukkitPlayer();
                if (bp2 == null) continue;
                boolean hasCursedBlade = false;
                for (ItemStack item : bp2.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta() &&
                        item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("魔刀")) {
                        hasCursedBlade = true; break;
                    }
                }
                if (hasCursedBlade) {
                    if (cursedBladeHolder == null || !cursedBladeHolder.equals(bp2.getUniqueId())) {
                        // 新持有者 开始18s计时
                        cursedBladeHolder = bp2.getUniqueId();
                        bp2.sendMessage("§5§l魔剑已被拿起！18s后持有者将死亡！");
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (cursedBladeHolder == null) return;
                                Player holder = Bukkit.getPlayer(cursedBladeHolder);
                                if (holder != null && holder.isOnline()) {
                                    dealDamage(holder, 999.0, null);
                                    holder.sendTitle("§5§l魔刀召唤", "§7你死亡了", 10, 60, 10);
                                }
                                cursedBladeHolder = null;
                            }
                        }.runTaskLater(plugin, 360L);
                    }
                }
            }
        }
    }

    private void tickWhiteWolfKingPassive(PlayerData pd, Player bp) {
        if (isOnlyWolfAlive(pd)) {
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 40, 0, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, 0, true, false));
        }
    }

    private void tickCountPassive(PlayerData pd, Player bp) {
        UUID uuid = bp.getUniqueId();
        boolean frenzy = countFrenzyActive.contains(uuid);
        // 计数器显式初始化, 避免计数失效
        Integer cdObj = (Integer) pd.getAttribute("countPassiveCd");
        if (cdObj == null) cdObj = frenzy ? 6 : 10;
        int interval = frenzy ? 6 : 10;
        if (cdObj > 0) { pd.setAttribute("countPassiveCd", cdObj - 1); return; }
        // 正常: 1心真伤+自己回2心 CD10s / 狂热: 1.5心真伤+自己回2心 CD6s
        double damage = frenzy ? 3.0 : 2.0;
        int hit = 0;
        for (PlayerData other : players) {
            if (other == pd || !other.isAlive()) continue;
            // 正常状态无友伤，狂热形态有友伤
            if (!frenzy && "狼人阵营".equals(other.getCamp())) continue;
            Player ob = other.getBukkitPlayer();
            if (ob != null && ob.isOnline() && sameWorldAndNear(bp, ob, 5.0)) {
                dealDamage(ob, damage, bp);
                ob.sendTitle(frenzy ? "§5嗜血" : "§c吸血", "§7被伯爵吸血 1心", 2, 30, 6);
                ob.sendMessage(frenzy ? "§5嗜血！你掉了1.5心" : "§c吸血！你掉了1心");
                ob.playSound(ob.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.7f);
                hit++;
            }
        }
        healPlayer(bp, 4.0);
        bp.sendMessage("§5吸血！恢复2心" + (hit > 0 ? " §7吸取了 " + hit + " 名玩家" : " §7(范围内无目标)"));
        pd.setAttribute("countPassiveCd", interval);
    }

    private boolean sameWorldAndNear(Player a, Player b, double dist) {
        if (a.getWorld() != b.getWorld()) return false;
        return a.getLocation().distance(b.getLocation()) <= dist;
    }

    // 狂热形态红色特效(每秒由 tickPassives 重新施加, 避免特效丢失)
    private void applyFrenzyVisual(Player bp) {
        bp.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, false));
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12.0;
            Location p = bp.getLocation().add(Math.cos(angle) * 0.9, 0.5, Math.sin(angle) * 0.9);
            p.getWorld().spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0,
                new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.6f));
        }
        for (int i = 0; i < 6; i++) {
            bp.getLocation().getWorld().spawnParticle(Particle.FLAME,
                bp.getLocation().add(0, 1.0 + random.nextDouble(), 0), 3, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void stopFrenzyEffect(Player bp) {
        bp.removePotionEffect(PotionEffectType.GLOWING);
        bp.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        bp.removePotionEffect(PotionEffectType.SPEED);
        bp.removePotionEffect(PotionEffectType.JUMP);
    }

    private void tickTimeDukePassive(PlayerData pd, Player bp) {
        Integer batTick = (Integer) pd.getAttribute("timeDukeBatTick");
        if (batTick == null) batTick = 0;
        batTick++;
        if (batTick >= 10) {
            batTick = 0;
            // 每10s召唤时间蝙蝠
            Bat bat = (Bat) bp.getWorld().spawnEntity(bp.getLocation().add(random.nextDouble()*4-2, 2, random.nextDouble()*4-2), EntityType.BAT);
            summonBats.add(bat);
            bat.setCustomName("§d时间蝙蝠"); bat.setCustomNameVisible(true);
            // 蝙蝠每18s使周围r=6玩家时停5s
            new BukkitRunnable() {
                @Override public void run() {
                    if (!bat.isValid() || !pd.isAlive()) { cancel(); return; }
                    for (PlayerData other : players) {
                        if (!other.isAlive() || other == pd) continue;
                        Player ob = other.getBukkitPlayer();
                        if (ob != null && ob.isOnline() && bat.getLocation().distance(ob.getLocation()) <= 6.0) {
                            ob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 250, true, false));
                            ob.sendTitle("§d时间停止", "§7被时间蝙蝠击中", 5, 60, 5);
                        }
                    }
                }
            }.runTaskTimer(plugin, 360L, 360L);
            // 蝙蝠30s后消失
            new BukkitRunnable() { @Override public void run() { summonBats.remove(bat); if (bat.isValid()) bat.remove(); } }.runTaskLater(plugin, 600L);
        }
        pd.setAttribute("timeDukeBatTick", batTick);
    }

    private void tickWindSpiritPassive(PlayerData pd, Player bp) {
        Integer tick = (Integer) pd.getAttribute("windTick");
        if (tick == null) tick = 0;
        tick++;
        if (tick >= 20) {
            tick = 0;
            Integer idx = (Integer) pd.getAttribute("windIndex");
            if (idx == null) idx = 0;
            idx = (idx + 1) % 4;
            pd.setAttribute("windIndex", idx);
            // 每次切换时恢复2心
            healPlayer(bp, 4.0);
            // 被动【风之切换】: 切换时获得2个风弹
            bp.getInventory().addItem(makeWindBomb(2));
            bp.updateInventory();
            applyWindEffect(bp, idx);
            bp.sendMessage("§b风向切换：" + windName(idx) + " §7获得 2 个风弹");
        }
        // 持续被动效果
        Integer idx = (Integer) pd.getAttribute("windIndex");
        if (idx == null) idx = 0;
        switch (idx) {
            case 0: // 迅捷之风: 速度2
                bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false));
                break;
            case 1: // 跃进之风: 跳跃1
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, 0, true, false));
                break;
            case 2: // 治愈之风: 切换时恢复(已在上面处理)
                break;
            case 3: // 飘扬之风: 速度1
                bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false));
                break;
        }
        pd.setAttribute("windTick", tick);
    }

    private void tickWizardPassive(Player bp) {
        int energy = wizardEnergy.getOrDefault(bp.getUniqueId(), 200);
        if (energy < 250) {
            energy = Math.min(energy + 40, 500);
            wizardEnergy.put(bp.getUniqueId(), energy);
        }
    }

    private void tickSlimePassive(PlayerData pd, Player bp) {
        int lives = slimeLives.getOrDefault(bp.getUniqueId(), 3);
        switch (lives) {
            case 2:
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, 2, true, false));
                if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() > 14.0) bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(14.0);
                break;
            case 1:
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, 0, true, false));
                if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() > 10.0) bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(10.0);
                break;
            case 0:
                if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() > 6.0) bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(6.0);
                break;
            default:
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 40, 4, true, false));
                break;
        }
    }

    private void tickInstakillPassive(PlayerData pd, Player bp) {
        Integer tick = (Integer) pd.getAttribute("instakillTick");
        if (tick == null) tick = 0;
        tick++;
        if (tick >= 30) {
            tick = 0;
            // 秒刀 = 锋利255耐久1的下界合金剑
            ItemStack instakillBlade = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta meta = instakillBlade.getItemMeta();
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 255, true);
            meta.setUnbreakable(false);
            meta.setDisplayName("§5秒刀");
            instakillBlade.setItemMeta(meta);
            instakillBlade.setDurability((short) (Material.NETHERITE_SWORD.getMaxDurability() - 1));
            // 只替换上一把秒刀，不能清空背包（否则技能钻石和装备会丢失）
            ItemStack[] contents = bp.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (it == null || it.getType() != Material.NETHERITE_SWORD || !it.hasItemMeta()) continue;
                ItemMeta itMeta = it.getItemMeta();
                if (itMeta.hasDisplayName() && itMeta.getDisplayName().contains("秒刀")) bp.getInventory().setItem(i, null);
            }
            bp.getInventory().addItem(instakillBlade);
            bp.sendMessage("§5利刃已刺入！下一击将秒杀！");
        }
        pd.setAttribute("instakillTick", tick);
    }

    // ==================== RANDOM EVENTS ====================

    private void startThunderstormEvent() {
        for (PlayerData pd : players) {
            Player bp = pd.getBukkitPlayer();
            if (bp != null) { bp.getWorld().setTime(18000); bp.getWorld().setStorm(true); bp.getWorld().setThundering(true); }
        }
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                for (PlayerData pd : players) {
                    if (!pd.isAlive()) continue;
                    Player bp = pd.getBukkitPlayer();
                    if (bp == null || !bp.isOnline()) continue;
                    // 没有方块遮挡 = 头顶是天空
                    if (bp.getLocation().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                        bp.getWorld().strikeLightningEffect(bp.getLocation());
                        dealDamage(bp, 4.0, null);
                        bp.sendTitle("§9雷击", "", 2, 10, 5);
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 100L);
        randomEventTasks.add(task);
    }

    private void startCursedBladeEvent() {
        // 在随机位置生成锋利5下界合金剑
        PlayerData first = players.get(random.nextInt(players.size()));
        Player bp = first.getBukkitPlayer();
        if (bp == null) return;
        Location dropLoc = bp.getLocation().add(random.nextDouble()*20-10, 0, random.nextDouble()*20-10);
        dropLoc.setY(dropLoc.getWorld().getHighestBlockYAt(dropLoc) + 1);
        ItemStack blade = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = blade.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 5, true);
        meta.setDisplayName("§5§l魔刀");
        meta.setLore(Collections.singletonList("§c拿起后18s死亡"));
        blade.setItemMeta(meta);
        dropLoc.getWorld().dropItemNaturally(dropLoc, blade);
    }

    private void startGemNationEvent() {
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                List<PlayerData> alive = new ArrayList<>();
                for (PlayerData pd : players) if (pd.isAlive()) alive.add(pd);
                if (alive.isEmpty()) return;
                PlayerData target = alive.get(random.nextInt(alive.size()));
                Player tp = target.getBukkitPlayer();
                if (tp == null || !tp.isOnline()) return;
                if (random.nextBoolean()) {
                    tp.getInventory().addItem(new ItemStack(Material.EMERALD, 50));
                    tp.sendMessage("§6宝石之国！你获得了§a50绿宝石！");
                } else {
                    tp.getInventory().addItem(new ItemStack(Material.REDSTONE, 30));
                    tp.sendMessage("§6宝石之国！你获得了§c30红石！");
                }
            }
        }.runTaskTimer(plugin, 400L, 400L);
        randomEventTasks.add(task);
    }

    private void startZombieHordeEvent() {
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                int aliveCount = 0;
                for (PlayerData pd : players) if (pd.isAlive()) aliveCount++;
                if (aliveCount == 0) return;
                int spawnCount = aliveCount * 3;
                for (int i = 0; i < spawnCount; i++) {
                    PlayerData rp = players.get(random.nextInt(players.size()));
                    Player rbp = rp.getBukkitPlayer();
                    if (rbp == null) continue;
                    Location loc = rbp.getLocation().add(random.nextDouble()*40-20, 0, random.nextDouble()*40-20);
                    loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
                    Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                    zombie.setShouldBurnInDay(false);
                    zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, true, false));
                    zombie.setCustomName("§c尸潮"); zombie.setCustomNameVisible(true);
                    zombie.setMetadata("hordeZombie", new org.bukkit.metadata.FixedMetadataValue(plugin, "horde"));
                    zombie.setTarget(findNearestLivingPlayer(loc));
                    zombie.setRemoveWhenFarAway(false);
                    eventZombies.add(zombie);
                }
                eventZombies.removeIf(Zombie::isDead);
            }
        }.runTaskTimer(plugin, 200L, 200L);
        randomEventTasks.add(task);
    }

    private void startEarthquakeEvent() {
        BukkitTask task = new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                tick++;
                // 每10s开始抖动5s
                if (tick % 10 == 0) {
                    broadcast("§8§l地震来了！");
                    for (PlayerData pd : players) {
                        if (!pd.isAlive()) continue;
                        Player bp = pd.getBukkitPlayer();
                        if (bp == null || !bp.isOnline()) continue;
                        // 开始抖动时检查头顶建筑
                        earthquakeCollapse(pd);
                    }
                    // 抖动5s (10 ticks)
                    new BukkitRunnable() {
                        int shakeTicks = 0;
                        @Override public void run() {
                            if (shakeTicks >= 10 || state != GameState.PLAYING) {
                                // 结束抖动时再次检查头顶建筑
                                if (shakeTicks >= 10 && state == GameState.PLAYING) {
                                    for (PlayerData pd2 : players) {
                                        if (pd2.isAlive()) earthquakeCollapse(pd2);
                                    }
                                }
                                cancel(); return;
                            }
                            for (PlayerData pd : players) {
                                if (!pd.isAlive()) continue;
                                Player bp = pd.getBukkitPlayer();
                                if (bp != null && bp.isOnline()) {
                                    bp.setVelocity(new org.bukkit.util.Vector(
                                        (random.nextDouble()-0.5)*0.5,
                                        0.1,
                                        (random.nextDouble()-0.5)*0.5));
                                }
                            }
                            shakeTicks++;
                        }
                    }.runTaskTimer(plugin, 0L, 10L);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        randomEventTasks.add(task);
    }

    private void cleanupRandomEvents() {
        randomEventTasks.forEach(t -> t.cancel());
        randomEventTasks.clear();
        activeRandomEvent = null;
        cursedBladeHolder = null;
        for (Creeper creeper : eventCreepers) if (creeper != null) creeper.remove();
        eventCreepers.clear();
        for (Zombie zombie : eventZombies) if (zombie != null) zombie.remove();
        eventZombies.clear();
    }

    private void startHurricaneEvent() {
        String[] directions = {"EAST", "WEST", "NORTH", "SOUTH"};
        String[] dirNames = {"§e东风", "§e西风", "§e北风", "§e南风"};
        BukkitTask task = new BukkitRunnable() {
            int dirIndex = random.nextInt(4);
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                // 每20s换风向
                dirIndex = (dirIndex + 1) % 4;
                broadcast("§b飓风方向变更: " + dirNames[dirIndex]);
                // 获取风向向量
                Vector windVec = getWindVector(directions[dirIndex]);
                for (PlayerData pd : players) {
                    if (!pd.isAlive()) continue;
                    Player bp = pd.getBukkitPlayer();
                    if (bp == null || !bp.isOnline()) continue;
                    // 玩家朝向
                    Vector look = bp.getLocation().getDirection().setY(0).normalize();
                    double dot = look.dot(windVec);
                    // dot > 0.5 = 同向, < -0.5 = 反向, 其他 = 不同
                    if (dot > 0.5) {
                        bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 420, 1, true, false));
                        bp.sendMessage("§b飓风助力！速度+2");
                    } else {
                        bp.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 420, 1, true, false));
                        bp.sendMessage("§c飓风阻挡！缓慢+2");
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 400L);
        randomEventTasks.add(task);
    }

    private Vector getWindVector(String dir) {
        switch (dir) {
            case "EAST": return new Vector(1, 0, 0);
            case "WEST": return new Vector(-1, 0, 0);
            case "NORTH": return new Vector(0, 0, -1);
            case "SOUTH": return new Vector(0, 0, 1);
            default: return new Vector(1, 0, 0);
        }
    }

    private void startScorchingSunEvent() {
        // 中午+烈日: 暴露玩家缓慢1, 10s后反胃, 15s后每秒扣1心
        for (PlayerData pd : players) {
            Player bp = pd.getBukkitPlayer();
            if (bp != null) { bp.getWorld().setTime(6000); bp.getWorld().setStorm(false); bp.getWorld().setThundering(false); }
        }
        BukkitTask task = new BukkitRunnable() {
            final Map<UUID, Integer> sunExposure = new HashMap<>();
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                for (PlayerData pd : players) {
                    if (!pd.isAlive()) continue;
                    Player bp = pd.getBukkitPlayer();
                    if (bp == null || !bp.isOnline()) continue;
                    boolean exposed = bp.getLocation().add(0, 1, 0).getBlock().getType() == Material.AIR;
                    UUID uuid = bp.getUniqueId();
                    if (exposed) {
                        int ticks = sunExposure.getOrDefault(uuid, 0) + 1;
                        sunExposure.put(uuid, ticks);
                        bp.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0, true, false));
                        if (ticks >= 10) bp.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 40, 0, true, false));
                        if (ticks >= 15) dealDamage(bp, 2.0, null);
                    } else {
                        sunExposure.put(uuid, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        randomEventTasks.add(task);
    }

    private void startCreeperCrisisEvent() {
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cancel(); return; }
                int aliveCount = 0;
                for (PlayerData pd : players) if (pd.isAlive()) aliveCount++;
                if (aliveCount == 0) return;
                int spawnCount = aliveCount * 2;
                for (int i = 0; i < spawnCount; i++) {
                    PlayerData rp = players.get(random.nextInt(players.size()));
                    Player rbp = rp.getBukkitPlayer();
                    if (rbp == null) continue;
                    Location loc = rbp.getLocation().add(random.nextDouble()*40-20, 0, random.nextDouble()*40-20);
                    loc.setY(loc.getWorld().getHighestBlockYAt(loc) + 1);
                    Creeper creeper = (Creeper) loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
                    creeper.setPowered(false);
                    creeper.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, true, false));
                    creeper.setTarget(findNearestLivingPlayer(loc));
                    creeper.setRemoveWhenFarAway(false);
                    eventCreepers.add(creeper);
                }
                eventCreepers.removeIf(Creeper::isDead);
            }
        }.runTaskTimer(plugin, 100L, 100L);
        randomEventTasks.add(task);
    }

    // ==================== KILL & DEATH HANDLING ====================

    public void killPlayer(PlayerData victim, PlayerData killer) {
        if (!victim.isAlive()) return;
        UUID victimUUID = victim.getBukkitPlayer() != null ? victim.getBukkitPlayer().getUniqueId() : null;
        // 检查复活次数
        if (victimUUID != null) {
            int charges = revivalCharges.getOrDefault(victimUUID, 0);
            if (charges > 0) {
                revivalCharges.put(victimUUID, charges - 1);
                Player vp = victim.getBukkitPlayer();
                if (vp != null && vp.isOnline()) {
                    if (victim.getAttribute("__deathEvent") != null) {
                        // 死亡事件中复活: 交给 PlayerRespawnEvent 处理,
                        // 否则在 PlayerDeathEvent 里直接回血会卡死客户端(无敌分身)
                        pendingRevives.put(victimUUID, vp.getLocation().clone());
                    } else {
                        double max = vp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
                        vp.setHealth(max);
                        vp.setGameMode(GameMode.ADVENTURE);
                        vp.sendTitle("§a复活了", "§7剩余复活次数: " + (charges - 1), 10, 60, 10);
                    }
                    vp.sendMessage("§a你被守护的守护之誓救活了！剩余复活次数: " + (charges - 1));
                }
                broadcast("§c" + victim.getUsername() + " §7被守护之誓复活！");
                return;
            }
        }
        victim.setAlive(false);
        Role victimRole = victim.getRole();
        handleDeathPassives(victim, killer);
        // 猩红信使被动【狼之心】: 场上每死一个玩家恢复3心
        for (PlayerData pd2 : players) {
            if (!pd2.isAlive() || pd2 == victim) continue;
            Role pdRole = pd2.getRole();
            if (pdRole != null && "CRIMSON MESSENGER".equals(pdRole.getName())) {
                Player pb2 = pd2.getBukkitPlayer();
                if (pb2 != null && pb2.isOnline()) {
                    healPlayer(pb2, 6.0);
                    pb2.sendMessage("§c狼之心！恢复了3心血量！");
                }
            }
        }
        if (killer != null) {
            broadcast("§c" + victim.getUsername() + " §7被 §e" + killer.getUsername() + " §7击杀！");
            // 击杀奖励
            Player kp = killer.getBukkitPlayer();
            if (kp != null && kp.isOnline()) {
                String killerCamp = killer.getCamp();
                String victimCamp = victim.getCamp();
                boolean victimIsDangerNeutral = victimRole != null && "危险中立".equals(victimCamp);
                boolean victimIsPeacefulNeutral = victimRole != null && "和平中立".equals(victimCamp);
                if ("好人阵营".equals(killerCamp)) {
                    if ("狼人阵营".equals(victimCamp)) { kp.getInventory().addItem(new ItemStack(Material.EMERALD, 50)); kp.sendMessage("§a击杀狼人！获得50绿宝石"); }
                    else if (victimIsPeacefulNeutral) { kp.getInventory().addItem(new ItemStack(Material.EMERALD, 40)); kp.sendMessage("§a击杀和平中立！获得40绿宝石"); }
                    else if (victimIsDangerNeutral) { kp.getInventory().addItem(new ItemStack(Material.EMERALD, 60)); kp.sendMessage("§a击杀危险中立！获得60绿宝石"); }
                } else if ("狼人阵营".equals(killerCamp)) {
                    if ("好人阵营".equals(victimCamp)) {
                        if (victimRole != null && ("WATER DIVINATION".equals(victimRole.getName()) || "LANTERN BEARER".equals(victimRole.getName()))) {
                            kp.getInventory().addItem(new ItemStack(Material.REDSTONE, 60));
                            kp.sendMessage("§c狼之爱！击杀了预言家！获得60红宝石");
                        } else {
                            kp.getInventory().addItem(new ItemStack(Material.REDSTONE, 45));
                            kp.sendMessage("§c狼之爱！获得45红宝石");
                        }
                    }
                    else if (isNeutralCamp(victimCamp)) { kp.getInventory().addItem(new ItemStack(Material.REDSTONE, 50)); kp.sendMessage("§c获得50红宝石"); }
                } else {
                    // 中立击杀
                    if ("好人阵营".equals(victimCamp)) { kp.getInventory().addItem(new ItemStack(Material.EMERALD, 50)); kp.sendMessage("§a获得50绿宝石"); }
                    else if ("狼人阵营".equals(victimCamp)) { kp.getInventory().addItem(new ItemStack(Material.REDSTONE, 40)); kp.sendMessage("§c获得40红宝石"); }
                    else if (isNeutralCamp(victimCamp) && killer != victim) { kp.getInventory().addItem(new ItemStack(Material.EMERALD, 30)); kp.getInventory().addItem(new ItemStack(Material.REDSTONE, 15)); kp.sendMessage("§a获得30绿宝石+15红宝石"); }
                }
            }
        } else {
            broadcast("§c" + victim.getUsername() + " §7死亡了！");
        }
        Player vp = victim.getBukkitPlayer();
        if (vp != null && vp.isOnline()) {
            vp.setGameMode(GameMode.SPECTATOR);
            vp.sendTitle("§c你死了", "§7可以观察剩余玩家", 10, 70, 20);
            vp.getActivePotionEffects().forEach(e -> vp.removePotionEffect(e.getType()));
        }
        if (victimRole != null) {
            if ("PELICAN".equals(victimRole.getName())) releasePelicanStoredPlayers(victim);
            if ("NEUTRAL JACKAL".equals(victimRole.getName())) promoteFollowerToJackal();
            if ("SLIME".equals(victimRole.getName())) { handleSlimeDeath(victim); return; }
        }
        if (vp != null) {
            UUID uuid = vp.getUniqueId();
            activeBombs.remove(uuid); hackerAuraTasks.remove(uuid);
            ninjaReturnMarks.remove(uuid); ninjaPlayerMarks.remove(uuid);
            hackerLocationMarks.remove(uuid);
        }
        checkGameEnd();
    }

    private void handleDeathPassives(PlayerData victim, PlayerData killer) {
        Role role = victim.getRole();
        if (role == null) return;
        Player vp = victim.getBukkitPlayer();
        switch (role.getName()) {
            case "WOLF WITCH":
                for (PlayerData other : players) {
                    if (other == victim || !other.isAlive() || "狼人阵营".equals(other.getCamp())) continue;
                    Player ob = other.getBukkitPlayer();
                    if (ob != null && ob.isOnline()) { dealDamage(ob, 6.0, null); ob.sendTitle("§5狼之诅咒", "§7亡语", 10, 40, 10); }
                }
                break;
            case "CRIMSON MESSENGER":
                // 亡语: 死亡时所有非狼人扣3心 (已处理在下面的通用循环中)
                break;
            case "CORPSE HERDER":
                PlayerData herder = findFirstPlayerByRole("CORPSE HERDER");
                if (herder != null) {
                    Integer ch = (Integer) herder.getAttribute("herderCharges");
                    if (ch == null) ch = 2;
                    herder.setAttribute("herderCharges", ch + 1);
                    Player hb = herder.getBukkitPlayer();
                    if (hb != null && hb.isOnline()) hb.sendMessage("§a赶尸次数 +1！当前: " + (ch + 1));
                }
                break;
        }
        // 巫师被动: 每死一人恢复300能量
        for (PlayerData pd2 : players) {
            if (!pd2.isAlive() || pd2 == victim) continue;
            Role pdRole = pd2.getRole();
            if (pdRole != null && "WIZARD".equals(pdRole.getName())) {
                Player pb2 = pd2.getBukkitPlayer();
                if (pb2 != null && pb2.isOnline()) {
                    UUID wuuid = pb2.getUniqueId();
                    int e = wizardEnergy.getOrDefault(wuuid, 200);
                    wizardEnergy.put(wuuid, Math.min(e + 300, 500));
                    pb2.sendMessage("§5能量恢复+300！当前: " + Math.min(e + 300, 500));
                }
            }
        }
    }

    private void handleSlimeDeath(PlayerData victim) {
        Player bp = victim.getBukkitPlayer();
        if (bp == null) return;
        int lives = slimeLives.getOrDefault(bp.getUniqueId(), 3) - 1;
        // 三次复活: lives 2=第一条(仅有武器) 1=第二条(仅有小刀) 0=第三条(无物资) -1=彻底消亡
        if (lives >= 0) {
            slimeLives.put(bp.getUniqueId(), lives);
            victim.setAlive(true);
            broadcast("§a" + victim.getUsername() + " §7还有 " + lives + " 条命！");
            if (victim.getAttribute("__deathEvent") != null) {
                // 死亡事件中: 交给 PlayerRespawnEvent 恢复, 避免客户端卡死(无敌分身)
                pendingRevives.put(bp.getUniqueId(), bp.getLocation().clone());
                return;
            }
            bp.setGameMode(GameMode.ADVENTURE);
            double maxHp = lives == 2 ? 14.0 : lives == 1 ? 10.0 : 6.0;
            bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
            bp.setHealth(maxHp);
            bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
            bp.getInventory().clear();
            if (lives == 2) {
                // 第一条: 仅有武器 跳跃3 血量上限7心
                bp.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, 2, true, false));
            } else if (lives == 1) {
                // 第二条: 仅有小刀 跳跃1 血量上限5心
                bp.getInventory().addItem(makeItem(Material.STONE_SWORD, "§c小刀", ""));
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, 0, true, false));
            } else {
                // 第三条: 无物资 血量上限3心 不可拾取物资
                bp.sendTitle("§c§l最后一条", "§7没有任何物品，且无法拾取", 10, 60, 10);
            }
            bp.sendTitle("§a生命力丧失", "§7剩余 " + lives + " 条命", 10, 40, 10);
        } else {
            // 史莱姆彻底消亡 - 手动处理避免递归
            victim.setAlive(false);
            bp.setGameMode(GameMode.SPECTATOR);
            bp.sendTitle("§c你死了", "§7彻底消灭", 10, 70, 20);
            broadcast("§c" + victim.getUsername() + " §7已被彻底消灭！");
            bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
            checkGameEnd();
        }
    }

    // 第三条命(无物资形态)不可拾取任何物品
    // 按剩余命数应用史莱姆形态(血量上限/血量/物资/药水)
    private void applySlimeLifeState(Player bp, int lives) {
        bp.setGameMode(GameMode.ADVENTURE);
        double maxHp = lives == 2 ? 14.0 : lives == 1 ? 10.0 : 6.0;
        bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHp);
        bp.setHealth(maxHp);
        bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
        bp.getInventory().clear();
        if (lives == 2) {
            bp.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, 2, true, false));
        } else if (lives == 1) {
            bp.getInventory().addItem(makeItem(Material.STONE_SWORD, "§c小刀", ""));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, 0, true, false));
        }
        bp.updateInventory();
    }

    // ==================== REVIVE (respawn-based) ====================

    // 守护之誓复活: 复活时保留物品栏
    public boolean willGuardRevive(PlayerData victim) {
        if (victim == null || !victim.isAlive()) return false;
        Player vp = victim.getBukkitPlayer();
        if (vp == null) return false;
        return revivalCharges.getOrDefault(vp.getUniqueId(), 0) > 0;
    }

    public boolean willRevive(PlayerData victim) {
        if (victim == null || !victim.isAlive()) return false;
        Player vp = victim.getBukkitPlayer();
        if (vp == null) return false;
        if (revivalCharges.getOrDefault(vp.getUniqueId(), 0) > 0) return true;
        Role r = victim.getRole();
        return r != null && "SLIME".equals(r.getName()) && slimeLives.getOrDefault(vp.getUniqueId(), 3) - 1 >= 0;
    }

    public Location consumePendingRevive(UUID uuid) {
        return pendingRevives.remove(uuid);
    }

    public void completeRevive(Player p) {
        if (state != GameState.PLAYING || p == null || !p.isOnline()) return;
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p);
        if (pd == null || !pd.isAlive()) return;
        p.setGameMode(GameMode.ADVENTURE);
        Role role = pd.getRole();
        if (role != null && "SLIME".equals(role.getName())) {
            applySlimeLifeState(p, slimeLives.getOrDefault(p.getUniqueId(), 3));
        } else {
            double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
            p.setHealth(max);
            p.sendTitle("§a复活了", "§7守护之誓将你救活", 10, 60, 10);
        }
        p.updateInventory();
        p.sendMessage("§a你已复活！");
    }

    public boolean isItemPickupLocked(Player player) {
        if (player == null) return false;
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player);
        if (pd == null || pd.getRole() == null || !"SLIME".equals(pd.getRole().getName())) return false;
        return slimeLives.getOrDefault(player.getUniqueId(), 3) == 0;
    }

    private void releasePelicanStoredPlayers(PlayerData pelican) {
        List<UUID> stored = pelicanStoredPlayers.get(pelican.getBukkitPlayer().getUniqueId());
        if (stored == null) return;
        for (UUID uuid : stored) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.teleport(p.getWorld().getSpawnLocation());
                p.sendTitle("§a你被释放了", "", 10, 40, 10);
            }
        }
        pelicanStoredPlayers.remove(pelican.getBukkitPlayer().getUniqueId());
    }

    private void promoteFollowerToJackal() {
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            Role r = pd.getRole();
            if (r != null && "FOLLOWER".equals(r.getName())) {
                Role jackal = plugin.getRoleManager().getRole("NEUTRAL JACKAL");
                if (jackal != null) {
                pd.setRole(jackal);
                Player bp = pd.getBukkitPlayer();
                if (bp != null && bp.isOnline()) bp.sendTitle("§c你升级了", "§7你现在是中立爪牙", 10, 60, 10);
                // 新豺狼获得【招募】钻石
                initializePlayerForBattle(pd);
                }
                break;
            }
        }
    }

    // ==================== WIN CONDITIONS ====================

    private boolean checkGameEnd() {
        int aliveWerewolves = 0, aliveGood = 0, aliveDangerousNeutral = 0, alivePeacefulNeutral = 0;
        for (PlayerData p : players) {
            if (p.isAlive()) {
                String c = p.getCamp();
                if ("狼人阵营".equals(c)) aliveWerewolves++;
                else if ("危险中立".equals(c)) aliveDangerousNeutral++;
                else if ("和平中立".equals(c)) alivePeacefulNeutral++;
                else aliveGood++;
            }
        }
        // Dangerous neutral alive: game can't end normally
        if (aliveDangerousNeutral > 0) {
            if (aliveGood == 0 && aliveWerewolves == 0 && alivePeacefulNeutral == 0) {
                endGame("危险中立", "其他阵营全灭"); return true;
            }
            return false;
        }
        // Wolves all dead -> good wins
        if (aliveWerewolves == 0) { endGame("好人阵营", "狼人全灭"); return true; }
        // Good all dead -> wolves win
        if (aliveGood == 0) { endGame("狼人阵营", "好人全灭"); return true; }
        return false;
    }

    private void endGameWithDraw() { endGame("平局", "时间到"); }

    private void endGame(String winnerCamp, String reason) {
        state = GameState.END;
        cancelGameTasks();
        // 恢复天气
        for (PlayerData p : players) {
            Player bp = p.getBukkitPlayer();
            if (bp != null) {
                bp.getWorld().setStorm(false);
                bp.getWorld().setThundering(false);
                bp.getWorld().setTime(6000);
            }
        }
        // 恢复自然回血
        for (PlayerData p : players) {
            Player bp = p.getBukkitPlayer();
            if (bp != null) bp.getWorld().setGameRuleValue("naturalRegeneration", "true");
        }

        broadcast("§6§l========== 游戏结束 ==========");
        broadcast("§e结束原因: §f" + reason);
        if ("平局".equals(winnerCamp)) broadcast("§e§l结果: 平局！");
        else broadcast("§e§l获胜阵营: " + ("狼人阵营".equals(winnerCamp) ? "§c" : "§a") + winnerCamp);

        broadcast("§6§l========== 身份揭晓 ==========");
        for (PlayerData p : players) {
            String status = p.isAlive() ? "§a[存活]" : "§c[死亡]";
            String r = p.getRole() != null ? p.getRole().getDisplayName() : "未知";
            String c = p.getCamp() != null ? p.getCamp() : "未知";
            broadcast(status + " §e" + p.getUsername() + " §7- " + r + " (" + c + ")");
        }
        for (PlayerData p : players) {
            if (!"平局".equals(winnerCamp)) {
                if (winnerCamp.equals(p.getCamp())) p.addWin(); else p.addLoss();
            }
            Player bp = p.getBukkitPlayer();
            if (bp != null && bp.isOnline()) {
                bp.getInventory().clear();
                bp.setGameMode(GameMode.ADVENTURE);
                bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
                if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() != 20.0) bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
            }
        }
        clearAllSkillData();
        openRoomMenusForAll();
        pendingResetTask = new BukkitRunnable() {
            @Override public void run() { pendingResetTask = null; resetGame(); }
        }.runTaskLater(plugin, 200L);
    }

    // 游戏结束(以及重置回大厅)后自动打开每位玩家所在房间的菜单
    private void openRoomMenusForAll() {
        for (PlayerData p : players) {
            Player bp = p.getBukkitPlayer();
            if (bp == null || !bp.isOnline()) continue;
            openRoomMenuFor(bp);
        }
    }

    private void openRoomMenuFor(Player player) {
        if (player == null || !player.isOnline()) return;
        // 确保拿到的是该玩家自己所在的房间菜单
        if (plugin.getRoomManager().getRoomByPlayer(player) != this) return;
        plugin.getRoomMenuListener().openRoomMenu(player);
    }

    public void stopGame() {
        cancelGameTasks();
        broadcast("§c游戏被强制结束！");
        for (PlayerData p : players) {
            Player bp = p.getBukkitPlayer();
            if (bp != null && bp.isOnline()) bp.getInventory().clear();
        }
        clearAllSkillData();
        resetGame();
    }

    private void clearAllSkillData() {
        activeBombs.clear(); hackerAuraTasks.clear();
        wizardEnergy.clear(); slimeLives.clear();
        bladeSoulS1Stacks.clear(); bladeSoulS2Stacks.clear();
        ninjaPlayerMarks.clear(); ninjaReturnMarks.clear();
        hackerLocationMarks.clear(); pelicanStoredPlayers.clear(); revivalCharges.clear();
        for (BukkitRunnable fz : countFrenzyTasks.values()) { try { fz.cancel(); } catch (Throwable ignored) { } }
        countFrenzyTasks.clear();
        countFrenzyActive.clear();
        for (ArmorStand knife : flyingKnives) knife.remove();
        flyingKnives.clear();
    }

    private void resetGame() {
        state = GameState.LOBBY;
        remainingTime = GAME_DURATION;
        broadcast("§a游戏已重置，可以开始新游戏！");
        // 只重置本房间的玩家，保留他们在房间内以便开始下一局
        for (PlayerData p : players) {
            restorePlayerState(p);
            p.resetGameState();
            p.setInGame(true);
        }
        // 重置回大厅后刷新房间菜单（此时可显示"开始游戏"）
        openRoomMenusForAll();
    }

    private void restorePlayerState(PlayerData p) {
        Player bp = p.getBukkitPlayer();
        if (bp == null || !bp.isOnline()) return;
        bp.setGameMode(GameMode.ADVENTURE);
        if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            if (bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() != 20.0) {
                bp.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
            }
            bp.setHealth(20.0);
        }
        bp.getActivePotionEffects().forEach(e -> bp.removePotionEffect(e.getType()));
        bp.setFoodLevel(20);
        bp.setSaturation(20f);
    }

    private void cancelCountdownTasks() {
        if (startCountdownTask != null) { startCountdownTask.cancel(); startCountdownTask = null; }
        if (battleCountdownTask != null) { battleCountdownTask.cancel(); battleCountdownTask = null; }
    }

    private void cancelGameTasks() {
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
        if (scoreboardTask != null) { scoreboardTask.cancel(); scoreboardTask = null; }
        if (passiveTickTask != null) { passiveTickTask.cancel(); passiveTickTask = null; }
        cancelCountdownTasks();
        if (pendingResetTask != null) { pendingResetTask.cancel(); pendingResetTask = null; }
        activeBombs.values().forEach(BukkitTask::cancel); activeBombs.clear();
        bombStates.clear(); bomberArmed.clear();
        hackerAuraTasks.values().forEach(BukkitTask::cancel); hackerAuraTasks.clear();
        for (ArmorStand knife : flyingKnives) knife.remove();
        flyingKnives.clear();
        for (ArmorStand stand : dioStands) if (!stand.isDead()) stand.remove();
        dioStands.clear();
        if (timeStopTask != null) { timeStopTask.cancel(); timeStopTask = null; }
        unfreezeAll();
        for (BukkitRunnable fz : countFrenzyTasks.values()) { try { fz.cancel(); } catch (Throwable ignored) { } }
        countFrenzyTasks.clear();
        countFrenzyActive.clear();
        if (tideBombTask != null) { tideBombTask.cancel(); tideBombTask = null; }
        cleanupTideBombs();
        pendingRevives.clear();
        hitFeedbackAt.clear();
        herderZombies.clear();
        summonBats.clear();
        cleanupLeftoverEntities();
        cleanupRandomEvents();
    }

    // 游戏结束/重置时清理遗留实体: 赶尸人僵尸、时间/伯爵蝙蝠、箭矢、标记盔甲座等
    private void cleanupLeftoverEntities() {
        for (Zombie z : new ArrayList<>(herderZombies)) { if (z.isValid()) z.remove(); }
        herderZombies.clear();
        for (Bat b : new ArrayList<>(summonBats)) { if (b.isValid()) b.remove(); }
        summonBats.clear();
        for (ArmorStand knife : flyingKnives) { if (knife.isValid()) knife.remove(); }
        flyingKnives.clear();
        for (ArmorStand stand : dioStands) { if (stand.isValid()) stand.remove(); }
        dioStands.clear();
        Set<World> worlds = new HashSet<>();
        for (PlayerData pd : players) {
            Player bp = pd.getBukkitPlayer();
            if (bp != null) worlds.add(bp.getWorld());
        }
        if (worlds.isEmpty()) worlds.addAll(Bukkit.getWorlds());
        for (World w : worlds) {
            for (Arrow a : w.getEntitiesByClass(Arrow.class)) a.remove();
            for (Bat b : w.getEntitiesByClass(Bat.class)) b.remove();
            for (ArmorStand s : w.getEntitiesByClass(ArmorStand.class)) {
                if (s.isMarker() || (s.isCustomNameVisible() && s.getCustomName() != null)) s.remove();
            }
        }
    }
    // ==================== SKILL DISPATCHER ====================

    private static boolean isOneTimeSkill(String roleName, String skillId) {
        // 剑灵四技能【剑灵】一次性, 用完即消耗钻石且不再进入冷却
        if ("BLADE AND SOUL".equals(roleName)) return "skill4".equals(skillId);
        if (!"skill1".equals(skillId)) return false;
        return "WOLF JACKAL".equals(roleName) || "BUDDHIST MONK".equals(roleName)
            || "NEUTRAL JACKAL".equals(roleName) || "THE WHITE WOLF KING".equals(roleName);
    }

    private static String oneTimeKey(String roleName, String skillId) {
        return roleName + ":" + skillId;
    }

    private void consumeSkillDiamond(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer();
        if (bp != null) {
            int slot = "skill1".equals(skillId) ? 0 : "skill2".equals(skillId) ? 1 : "skill3".equals(skillId) ? 2 : 3;
            if (slot >= 0 && slot < 9) {
                ItemStack item = bp.getInventory().getItem(slot);
                if (item != null && item.getType() == Material.DIAMOND) bp.getInventory().setItem(slot, null);
            }
        }
        pd.setSkillCooldown(skillId, 0);
    }

    public void useSkill(PlayerData player, String skillId) {
        if (state != GameState.PLAYING || !player.isAlive()) return;
        Role role = player.getRole();
        if (role == null) return;
        Player bp = player.getBukkitPlayer();
        if (bp == null || !bp.isOnline()) return;
        int cd = player.getSkillCooldown(skillId);
        if (cd > 0) { player.sendMessage("§c技能冷却中！剩余 " + cd + " 秒"); return; }
        // 转换身份类技能: 一次性使用，用完即无钻石且无CD
        if (isOneTimeSkill(role.getName(), skillId) && player.isSkillUsed(oneTimeKey(role.getName(), skillId))) {
            if ("BLADE AND SOUL".equals(role.getName())) player.sendMessage("§c【剑灵】是一次性技能，已使用完毕！");
            else player.sendMessage("§c该转换技能只能使用一次，已消耗！");
            return;
        }
        // 忍者瞬身/归: 两段式一次性技能，完成传送后消耗
        if ("NINJA".equals(role.getName())
            && ("skill2".equals(skillId) || "skill3".equals(skillId))
            && player.isSkillUsed(oneTimeKey(role.getName(), skillId))) {
            player.sendMessage("§c该技能已使用完毕，无法再次使用！");
            return;
        }

        suppressCooldown = false;
        switch (role.getName()) {
            case "THE WHITE WOLF KING": skillWhiteWolfKing(player, skillId); break;
            case "WOLF WITCH": skillWolfWitch(player, skillId); break;
            case "WOLF JACKAL": skillWolfJackal(player, skillId); break;
            case "CORPSE HERDER": skillCorpseHerder(player, skillId); break;
            case "GAMBLER": skillGambler(player, skillId); break;
            case "DIO": skillDiO(player, skillId); break;
            case "KILLER": skillKiller(player, skillId); break;
            case "CRIMSON MESSENGER": skillCrimsonMessenger(player, skillId); break;
            case "TIDE SINGER": skillTideSinger(player, skillId); break;
            case "BOMBER": skillBomber(player, skillId); break;
            case "COUNT": skillCount(player, skillId); break;
            case "GUARD": skillGuard(player, skillId); break;
            case "KNIGHT": skillKnight(player, skillId); break;
            case "NINJA": skillNinja(player, skillId); break;
            case "HACKER": skillHacker(player, skillId); break;
            case "BUDDHIST MONK": skillBuddhistMonk(player, skillId); break;
            case "WIND SPIRIT": skillWindSpirit(player, skillId); break;
            case "WATER DIVINATION": skillWaterDivination(player, skillId); break;
            case "LANTERN BEARER": skillLanternBearer(player, skillId); break;
            case "THE SURVIVOR": skillTheSurvivor(player, skillId); break;
            case "BLADE AND SOUL": skillBladeAndSoul(player, skillId); break;
            case "ARSONIST": skillArsonist(player, skillId); break;
            case "TIME DUKE": skillTimeDuke(player, skillId); break;
            case "PELICAN": skillPelican(player, skillId); break;
            case "NEUTRAL JACKAL": skillNeutralJackal(player, skillId); break;
            case "FOLLOWER": skillFollower(player, skillId); break;
            case "WIZARD": skillWizardSkill(player, skillId); break;
        }
        // 技能执行后设置冷却 (未命中目标/资源不足时 suppressCooldown = true, 不进入CD)
        int cooldownTime = getSkillCooldownTime(player, skillId);
        if (cooldownTime > 0 && !suppressCooldown) {
            setCooldown(player, skillId, cooldownTime);
        }
        suppressCooldown = false;
    }

    // ==================== WOLF SKILLS ====================

    private void skillWhiteWolfKing(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        // 自爆是一次性技能
        String oneTime = oneTimeKey(pd.getRole().getName(), skillId);
        if (pd.isSkillUsed(oneTime)) {
            bp.sendMessage("§c【白狼王自爆】只能使用一次！");
            suppressCooldown = true;
            return;
        }
        pd.setSkillUsed(oneTime, true);
        Location loc = bp.getLocation();
        // Kill all non-wolves in r=8
        for (PlayerData other : players) {
            if (other == pd || !other.isAlive()) continue;
            if (!"狼人阵营".equals(other.getCamp())) {
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && ob.getWorld() == loc.getWorld() && loc.distance(ob.getLocation()) <= 8.0) {
                    killPlayer(other, pd);
                    ob.sendTitle("§c白狼王自爆", "", 10, 40, 10);
                }
            }
        }
        // All players lose 1 heart + slowness255 1s + nausea 3s
        for (PlayerData all : players) {
            Player ab = all.getBukkitPlayer();
            if (ab != null && ab.isOnline()) {
                dealDamage(ab, 2.0, null);
                ab.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 250, true, false));
                ab.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 60, 0, true, false));
            }
        }
        // 一次性技能：消耗技能钻石
        consumeSkillDiamond(pd, skillId);
        // 身份暴露：全服广播谁是白狼王（不是自杀）
        bp.sendTitle("§c白狼王自爆", "§7身份暴露", 10, 60, 10);
        Bukkit.broadcastMessage("§c§l白狼王自爆了！§e白狼王身份暴露：§f" + pd.getUsername() + " §7就是白狼王！");
        checkGameEnd();
    }

    private void skillWolfWitch(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive() || "狼人阵营".equals(other.getCamp())) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline()) {
                    ob.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1, true, false));
                    ob.sendTitle("§5瘟疫浪潮", "§7中毒中...", 10, 30, 10);
                }
            }
            bp.sendMessage("§5瘟疫浪潮已释放！");
            setCooldown(pd, skillId, 35);
        } else if ("skill2".equals(skillId)) {
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive() || "狼人阵营".equals(other.getCamp())) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline()) {
                    ob.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0, true, false));
                    ob.sendTitle("§5蚀骨之毒", "§7净零中...", 10, 30, 10);
                }
            }
            bp.sendMessage("§5蚀骨之毒已释放！");
            setCooldown(pd, skillId, 50);
        } else if ("skill3".equals(skillId)) {
            // 狼族之愈: 所有狼人10s恢复3心 CD40s
            for (PlayerData other : players) {
                if (!other.isAlive() || !"狼人阵营".equals(other.getCamp())) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline()) {
                    ob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1, true, false));
                    ob.sendTitle("§c狼族之愈", "§7恢复中...", 10, 30, 10);
                }
            }
            bp.sendMessage("§c狼族之愈已释放！");
            setCooldown(pd, skillId, 40);
        }
    }

    private void skillWolfJackal(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        PlayerData nearest = findNearestPlayer(pd, 50.0, p -> !"狼人阵营".equals(p.getCamp()));
        if (nearest == null) { bp.sendMessage("§c附近没有可转化的玩家！"); suppressCooldown = true; return; }
        Role wretcher = plugin.getRoleManager().getRole("WRETCHER");
        if (wretcher == null) { suppressCooldown = true; return; }
        nearest.setRole(wretcher);
        Player nb = nearest.getBukkitPlayer();
        if (nb != null && nb.isOnline()) {
            nb.sendTitle("§c你被转化了", "§7你现在是" + wretcher.getDisplayName(), 10, 60, 10);
            nb.sendMessage("§c你被豺狼转化了，你现在是狼人阵营的狼！");
        }
        initializePlayerForBattle(nearest);
        bp.sendMessage("§a成功转化了 " + nearest.getUsername() + "！");
        pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
        consumeSkillDiamond(pd, skillId);
    }

    private void skillCorpseHerder(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        Integer charges = (Integer) pd.getAttribute("herderCharges");
        if (charges == null) charges = 2;
        if (charges <= 0) { bp.sendMessage("§c没有赶尸次数了！"); suppressCooldown = true; return; }
        Zombie zombie = (Zombie) bp.getWorld().spawnEntity(bp.getLocation(), EntityType.ZOMBIE);
        zombie.setCustomName("§c催尸"); zombie.setCustomNameVisible(true);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 3, true, false));
        zombie.setShouldBurnInDay(false);
        zombie.setTarget(findNearestEnemyForMob(bp));
        // 原版"寻找可攻击目标"AI不认阵营, 会把狼人阵营玩家也列为目标;
        // 由 CombatListener 的 EntityTargetLivingEntityEvent 拦截 (兜底), 这里再按阵营刷新目标
        zombie.setMetadata("herderZombie", new org.bukkit.metadata.FixedMetadataValue(plugin, bp.getUniqueId().toString()));
        zombie.setRemoveWhenFarAway(true);
        herderZombies.add(zombie);
        new BukkitRunnable() {
            @Override public void run() {
                if (!zombie.isValid() || !herderZombies.contains(zombie)) { cancel(); return; }
                if (state != GameState.PLAYING || !pd.isAlive()) { cancel(); return; }
                Player owner = pd.getBukkitPlayer();
                if (owner == null || !owner.isOnline()) { zombie.setTarget(null); return; }
                Player target = findNearestEnemyForMob(owner);
                if (target != null) zombie.setTarget(target);
                else if (zombie.getTarget() != null) zombie.setTarget(null);
            }
        }.runTaskTimer(plugin, 20L, 20L);
        pd.setAttribute("herderCharges", charges - 1);
        bp.sendMessage("§a赶尸！催尸已生成！剩余次数: " + (charges - 1));
    }

    private void skillGambler(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        PlayerData nearest = findNearestPlayer(pd, 10.0, PlayerData::isAlive);
        if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
        Player nb = nearest.getBukkitPlayer(); if (nb == null || !nb.isOnline()) { suppressCooldown = true; return; }
        // 生死赌局: 胜者获得35绿宝石, 败者立刻死亡
        boolean gamblerWins = random.nextBoolean();
        PlayerData winner = gamblerWins ? pd : nearest;
        PlayerData loser = gamblerWins ? nearest : pd;
        Player wb = winner.getBukkitPlayer();
        if (wb != null && wb.isOnline()) wb.getInventory().addItem(new ItemStack(Material.EMERALD, 35));
        broadcast("§e" + winner.getUsername() + " §7在赌局中赢了 §e" + loser.getUsername() + " §7！获得35绿宝石");
        Player lb = loser.getBukkitPlayer();
        if (lb != null && lb.isOnline()) lb.sendTitle("§c赌局输了", "§7你死了", 10, 40, 10);
        killPlayer(loser, winner);
    }

    private void skillDiO(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            // 时停7s: 仅自己可活动, 包含游戏时间
            int ticks = 140;
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob == null || !ob.isOnline()) continue;
                timeStopFrozen.put(ob.getUniqueId(), ticks);
                ob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks + 20, 255, true, false));
                ob.sendTitle("§5时间停止", "§7你被停止了！", 5, 40, 5);
            }
            timeStopGameSeconds = 7;
            ensureTimeStopTask();
            bp.sendTitle("§5§l时间停止", "§7仅你可行动，游戏时间同步冻结 7s", 5, 40, 10);
            bp.sendMessage("§5时停已释放！7s！");
            setCooldown(pd, skillId, 80);
        } else if ("skill2".equals(skillId)) {
            // 替身: 面前5格, 持续5s, 每0.5s对周围(r=5)所有玩家造成0.5心伤害
            Vector dir = bp.getLocation().getDirection().normalize().multiply(5);
            Location standLoc = bp.getLocation().add(dir);
            ArmorStand stand = bp.getWorld().spawn(standLoc, ArmorStand.class);
            stand.setMarker(true);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCustomName("§5§l替身");
            stand.setCustomNameVisible(true);
            dioStands.add(stand);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 100 || !pd.isAlive() || state != GameState.PLAYING || !bp.isOnline()) {
                        dioStands.remove(stand);
                        if (!stand.isDead()) stand.remove();
                        cancel(); return;
                    }
                    for (PlayerData other : players) {
                        if (other == pd || !other.isAlive()) continue;
                        Player ob = other.getBukkitPlayer();
                        if (ob != null && ob.isOnline() && stand.getLocation().distance(ob.getLocation()) <= 5.0) {
                            dealDamage(ob, 1.0, bp);
                        }
                    }
                    stand.getWorld().spawnParticle(Particle.REDSTONE, stand.getLocation().add(0, 1.2, 0), 4, 0.25, 0.25, 0.25);
                    ticks += 10;
                }
            }.runTaskTimer(plugin, 0L, 10L);
            bp.sendMessage("§5替身已召唤，持续5s！");
            setCooldown(pd, skillId, 12);
        }
    }

    // ==================== DIO 时停 ====================

    public boolean isTimeFrozen(Player player) {
        return player != null && timeStopFrozen.containsKey(player.getUniqueId());
    }

    private void ensureTimeStopTask() {
        if (timeStopTask != null) return;
        timeStopTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) {
                    timeStopTask = null;
                    unfreezeAll();
                    cancel(); return;
                }
                tickTimeStop();
                if (timeStopFrozen.isEmpty()) { timeStopTask = null; cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickTimeStop() {
        Iterator<Map.Entry<UUID, Integer>> it = timeStopFrozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (left <= 0 || p == null || !p.isOnline()) {
                if (p != null && p.isOnline()) p.removePotionEffect(PotionEffectType.SLOW);
                it.remove();
                continue;
            }
            entry.setValue(left);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 255, true, false));
            p.setVelocity(new Vector(0, p.getVelocity().getY(), 0));
            p.setFoodLevel(19);
            p.setSaturation(0f);
            p.setExhaustion(20f);
        }
    }

    private void unfreezeAll() {
        for (UUID uid : timeStopFrozen.keySet()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.isOnline()) p.removePotionEffect(PotionEffectType.SLOW);
        }
        timeStopFrozen.clear();
        timeStopGameSeconds = 0;
    }

    private void skillKiller(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            // 飞刀: 水平直线飞行(无视重力/不反弹/不落地), 判定范围一格, 7心伤害+缓慢255一秒
            Vector dir = bp.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0E-6) { dir.setX(0); dir.setY(0); dir.setZ(1); }
            dir.normalize();
            final Vector step = dir.clone().multiply(1.4);
            Location start = bp.getLocation().clone().add(0, 1.0, 0);
            final double flightY = start.getY();
            ArmorStand knife = start.getWorld().spawn(start, ArmorStand.class);
            knife.setMarker(true);
            knife.setGravity(false);
            knife.setInvulnerable(true);
            knife.setSilent(true);
            knife.setVisible(false);
            flyingKnives.add(knife);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 60 || !bp.isOnline() || !pd.isAlive() || state != GameState.PLAYING) {
                        flyingKnives.remove(knife); knife.remove(); cancel(); return;
                    }
                    Location next = knife.getLocation().add(step);
                    next.setY(flightY);
                    knife.teleport(next);
                    next.getWorld().spawnParticle(Particle.CRIT, next, 2, 0.05, 0.05, 0.05, 0.0);
                    for (Entity e : next.getNearbyEntities(1.0, 1.0, 1.0)) {
                        if (!(e instanceof Player) || e == bp) continue;
                        Player t = (Player) e;
                        dealDamage(t, 14.0, bp);
                        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 250, true, false));
                        t.sendTitle("§c飞刀命中", "", 5, 20, 5);
                        flyingKnives.remove(knife); knife.remove(); cancel();
                        return;
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
            bp.sendMessage("§c飞刀已投出！");
            setCooldown(pd, skillId, 12);
        } else if ("skill2".equals(skillId)) {
            Vector dir = bp.getLocation().getDirection().normalize().multiply(5);
            Location dest = bp.getLocation().add(dir);
            dest.setY(bp.getWorld().getHighestBlockYAt(dest) + 1);
            bp.teleport(dest);
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, true, false));
            bp.sendMessage("§a捷径！");
            setCooldown(pd, skillId, 30);
        }
    }

    private void skillCrimsonMessenger(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            if (bp.getHealth() <= 7.0) { bp.sendMessage("§c血量不足！"); suppressCooldown = true; return; }
            dealDamage(bp, 7.0, null);
            bp.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 200, 1, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2, true, false));
            bp.sendMessage("§c狼之血！");
            setCooldown(pd, skillId, 20);
        } else if ("skill2".equals(skillId)) {
            if (bp.getHealth() <= 6.0) { bp.sendMessage("§c血量不足！"); suppressCooldown = true; return; }
            PlayerData nearest = findNearestPlayer(pd, 50.0, PlayerData::isAlive);
            if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
            dealDamage(bp, 6.0, null);
            Player nb = nearest.getBukkitPlayer();
            if (nb != null && nb.isOnline()) {
                nb.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 400, 1, true, false));
                nb.sendTitle("§c狼之骨", "§7剧毒！", 10, 40, 10);
            }
            bp.sendMessage("§a狼之骨！对 " + nearest.getUsername() + " 施加剧毒！");
            setCooldown(pd, skillId, 25);
        } else if ("skill3".equals(skillId)) {
            // 狼之魂: 扣自己6.5心 最近玩家凋零20s扣8心 CD30s
            if (bp.getHealth() <= 13.0) { bp.sendMessage("§c血量不足！"); suppressCooldown = true; return; }
            PlayerData nearest = findNearestPlayer(pd, 50.0, PlayerData::isAlive);
            if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
            dealDamage(bp, 13.0, null);
            Player nb = nearest.getBukkitPlayer();
            if (nb != null && nb.isOnline()) {
                nb.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 400, 0, true, false));
                nb.sendTitle("§c狼之魂", "§7净零中...", 10, 40, 10);
            }
            bp.sendMessage("§c狼之魂！对 " + nearest.getUsername() + " 施加净零！");
            setCooldown(pd, skillId, 30);
        }
    }

    // 潮汐咏者: 原地布置炸弹 → 自动吸附 r=10 内最近的非狼人玩家 → 再次使用引爆
    private static final class TideBomb {
        private final UUID owner;
        private Location loc;
        private UUID target;
        private TideBomb(UUID owner, Location loc) { this.owner = owner; this.loc = loc; }
    }

    private void skillTideSinger(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        TideBomb bomb = tideBombs.get(uuid);
        if (bomb == null) {
            // 第一段: 在原地布置炸弹
            tideBombs.put(uuid, new TideBomb(uuid, bp.getLocation().clone()));
            ensureTideBombTask();
            bp.sendTitle("§b潮汐炸弹", "§7已布置，正在吸附...", 5, 40, 10);
            bp.sendMessage("§b潮汐炸弹已在原地布置！正在吸附 r=10 内最近的非狼人玩家，吸附后再次使用引爆！");
            suppressCooldown = true;
            return;
        }
        if (bomb.target == null) {
            bp.sendMessage("§c炸弹尚未吸附到目标！请等待。");
            suppressCooldown = true;
            return;
        }
        // 第二段: 引爆
        tideBombs.remove(uuid);
        Location loc = bomb.loc.clone();
        PlayerData attached = null;
        for (PlayerData p : players) {
            Player pb = p.getBukkitPlayer();
            if (pb != null && bomb.target.equals(pb.getUniqueId())) { attached = p; break; }
        }
        // 溅射: 炸弹周围 r=10 内所有存活玩家(含狼人、含自己), 被吸附者除外
        int splash = 0;
        for (PlayerData other : players) {
            if (!other.isAlive() || other == attached) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline() || ob.getWorld() != loc.getWorld()) continue;
            if (ob.getLocation().distance(loc) > 10.0) continue;
            dealDamage(ob, 9.0, bp);
            ob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4, true, false));
            splash++;
        }
        if (attached != null) {
            Player tb = attached.getBukkitPlayer();
            if (tb != null && tb.isOnline()) {
                dealDamage(tb, 14.0, bp);
                tb.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 4, true, false));
                tb.sendTitle("§c潮汐爆炸", "§7你被吸附的炸弹命中 7心", 10, 30, 10);
            }
        }
        loc.getWorld().createExplosion(loc, 0F);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0, 0, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        broadcast("§b" + pd.getUsername() + " §7引爆了潮汐炸弹！溅射 " + splash + " 人");
        setCooldown(pd, skillId, 20);
    }

    private void ensureTideBombTask() {
        if (tideBombTask != null) return;
        tideBombTask = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) { cleanupTideBombs(); cancel(); tideBombTask = null; return; }
                Iterator<Map.Entry<UUID, TideBomb>> it = tideBombs.entrySet().iterator();
                while (it.hasNext()) {
                    TideBomb b = it.next().getValue();
                    Player owner = Bukkit.getPlayer(b.owner);
                    if (owner == null || !owner.isOnline()) { it.remove(); continue; }
                    if (b.target != null) {
                        // 已吸附: 炸弹跟随目标
                        PlayerData tp = null;
                        for (PlayerData p : players) {
                            Player pb = p.getBukkitPlayer();
                            if (pb != null && b.target.equals(pb.getUniqueId())) { tp = p; break; }
                        }
                        Player tpb = tp != null ? tp.getBukkitPlayer() : null;
                        if (tpb == null || !tpb.isOnline() || !tp.isAlive()) { it.remove(); continue; }
                        b.loc = tpb.getLocation().clone();
                        b.loc.getWorld().spawnParticle(Particle.CLOUD, b.loc.clone().add(0, 1.0, 0), 4, 0.2, 0.2, 0.2, 0);
                        continue;
                    }
                    // 寻找 r=10 内最近的非狼人存活玩家
                    PlayerData nearest = null;
                    double nd = Double.MAX_VALUE;
                    for (PlayerData p : players) {
                        if (!p.isAlive() || "狼人阵营".equals(p.getCamp())) continue;
                        Player pb = p.getBukkitPlayer();
                        if (pb == null || !pb.isOnline() || pb.getWorld() != b.loc.getWorld()) continue;
                        double d = pb.getLocation().distance(b.loc);
                        if (d <= 10.0 && d < nd) { nd = d; nearest = p; }
                    }
                    if (nearest == null) {
                        b.loc.getWorld().spawnParticle(Particle.CLOUD, b.loc, 2, 0.1, 0.1, 0.1, 0);
                        continue;
                    }
                    Player np = nearest.getBukkitPlayer();
                    Vector dir = np.getLocation().add(0, 1, 0).toVector().subtract(b.loc.toVector());
                    double dist = dir.length();
                    if (dist <= 1.6) {
                        b.target = nearest.getBukkitPlayer().getUniqueId();
                        owner.sendMessage("§b潮汐炸弹已吸附到 " + nearest.getUsername() + "！再次使用引爆！");
                        np.sendMessage("§c一颗潮汐炸弹吸附在了你身上！");
                        np.sendTitle("§c潮汐炸弹", "§7吸附在你身上", 5, 40, 10);
                        continue;
                    }
                    b.loc = b.loc.add(dir.normalize().multiply(Math.min(1.5, dist)));
                    b.loc.getWorld().spawnParticle(Particle.CRIT, b.loc, 4, 0.1, 0.1, 0.1, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupTideBombs() {
        tideBombs.clear();
    }


    // 炸弹客: 烫手山芋(单体伤害, 15s后可传递给r=4内的其他玩家)
    private static final class BombState {
        private UUID owner;
        private UUID holder;
        private int seconds;
        private BombState(UUID owner, UUID holder, int seconds) {
            this.owner = owner; this.holder = holder; this.seconds = seconds;
        }
    }

    private void skillBomber(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        if (bombStates.containsKey(uuid)) {
            bp.sendMessage("§c炸弹已在点中！右键对旁边(r=4)的玩家可以传递");
            suppressCooldown = true;
            return;
        }
        if (!bomberArmed.contains(uuid)) { bp.sendMessage("§c炸弹已用完，爆炸后5s重新发放！"); suppressCooldown = true; return; }
        bomberArmed.remove(uuid);
        lightBomb(uuid, uuid, 15);
        bp.sendMessage("§c§l炸弹已点燃！15秒后爆炸（仅爆炸持有者）！");
    }

    private void lightBomb(UUID owner, UUID holder, int seconds) {
        final BombState st = new BombState(owner, holder, seconds);
        bombStates.put(owner, st);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (state != GameState.PLAYING) {
                    bombStates.remove(st.owner); cancel(); return;
                }
                Player h = Bukkit.getPlayer(st.holder);
                if (h == null || !h.isOnline() || !isAlive(h)) {
                    // 持有者掉线或死亡: 炸弹回到炸弹客身上
                    Player o = Bukkit.getPlayer(st.owner);
                    if (o != null && o.isOnline() && isAlive(o)) {
                        st.holder = st.owner;
                        o.sendMessage("§c你回收了炸弹！");
                    }
                    return;
                }
                st.seconds--;
                if (st.seconds <= 0) { explodeBomb(st); cancel(); return; }
                h.sendTitle("§c" + st.seconds, "§7炸弹...", 0, 25, 0);
            }
        }.runTaskTimer(plugin, 0L, 20L);
        activeBombs.put(owner, task);
    }

    private void explodeBomb(BombState st) {
        Player holder = Bukkit.getPlayer(st.holder);
        bombStates.remove(st.owner);
        activeBombs.remove(st.owner);
        if (holder == null || !holder.isOnline() || !isAlive(holder)) return;
        Location loc = holder.getLocation();
        // 单体伤害: 只炸持有者, 15心, 非真伤
        dealDamage(holder, 30.0, null);
        holder.getWorld().createExplosion(loc, 0F);
        holder.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 1, 0, 0, 0);
        broadcast("§c§l炸弹爆炸！");
        final UUID owner = st.owner;
        new BukkitRunnable() {
            @Override public void run() {
                bombStates.remove(owner);
                Player o = Bukkit.getPlayer(owner);
                if (o != null && o.isOnline() && isAlive(o)) {
                    bomberArmed.add(owner);
                    o.sendMessage("§c炸弹已重新发放（未点燃）！");
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    // 持有炸弹者右键 r=4 内的玩家即可传递
    // 该玩家当前是否持有炸弹
    public boolean hasBomb(Player p) {
        if (p == null || p.getUniqueId() == null) return false;
        for (BombState st : bombStates.values()) {
            if (p.getUniqueId().equals(st.holder)) return true;
        }
        return false;
    }

    public void tryPassBomb(Player passer, Player target) {
        if (passer == null || target == null || passer.equals(target)) return;
        for (Map.Entry<UUID, BombState> entry : bombStates.entrySet()) {
            BombState st = entry.getValue();
            if (!st.holder.equals(passer.getUniqueId())) continue;
            if (passer.getLocation().distance(target.getLocation()) > 4.0) {
                passer.sendMessage("§c目标超出4格，无法传递！");
                return;
            }
            st.holder = target.getUniqueId();
            passer.sendMessage("§c炸弹已传给 " + target.getName() + "！");
            target.sendTitle("§c你接到了炸弹！", "§7右键对旁边(r=4)玩家可传递", 5, 40, 10);
            return;
        }
    }

    private void skillCount(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        boolean frenzy = countFrenzyActive.contains(uuid);
        if ("skill1".equals(skillId)) {
            double dmg = frenzy ? 3.0 : 2.0;
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 5.0) {
                    dealDamage(ob, dmg, bp); dealDamage(ob, dmg, bp); dealDamage(ob, dmg, bp);
                    if (frenzy) {
                        ob.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, false));
                    } else {
                        ob.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, true, false));
                    }
                    ob.sendTitle("§c撕咬", "", 5, 20, 5);
                }
            }
            healPlayer(bp, frenzy ? 6.0 : 4.0);
            bp.sendMessage("§c撕咬！");
            setCooldown(pd, skillId, 10);
        } else if ("skill2".equals(skillId)) {
            // 狂热之血: 技能2 = 一键切换形态(进入/退出) CD3s, 切换功能只由这一个技能负责
            BukkitRunnable oldTask = countFrenzyTasks.remove(uuid);
            if (oldTask != null) oldTask.cancel();
            if (frenzy) {
                countFrenzyActive.remove(uuid);
                stopFrenzyEffect(bp);
                pd.setAttribute("countPassiveCd", 10);
                bp.sendMessage("§c狂热结束！已退出狂热形态。");
            } else {
                countFrenzyActive.add(uuid);
                // 力量1 速度2 跳跃1
                bp.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999, 0, true, false));
                bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, true, false));
                bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, 0, true, false));
                applyFrenzyVisual(bp);
                pd.setAttribute("countPassiveCd", 6);
                bp.sendTitle("§c§l狂热之血", "§7已进入狂热形态", 5, 40, 10);
                bp.sendMessage("§c§l狂热之血！形态已切换（再次使用技能2退出）");
                BukkitRunnable frenzyTask = new BukkitRunnable() {
                    @Override public void run() {
                        if (!countFrenzyActive.contains(uuid) || !pd.isAlive() || state != GameState.PLAYING) { cancel(); return; }
                        // 每3s扣自己3.5心
                        dealDamage(bp, 7.0, null);
                    }
                };
                frenzyTask.runTaskTimer(plugin, 60L, 60L);
                countFrenzyTasks.put(uuid, frenzyTask);
            }
        } else if ("skill3".equals(skillId)) {
            // 蝙蝠唤取: 仅狂热形态可用, 隐身3s + 召唤20只蝙蝠 CD20s
            if (!frenzy) {
                bp.sendMessage("§c【蝙蝠唤取】需要先进入狂热形态（技能2）！");
                suppressCooldown = true;
                return;
            }
            bp.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, true, false));
            for (int i = 0; i < 20; i++) {
                Bat bat = (Bat) bp.getWorld().spawnEntity(bp.getLocation().add(random.nextDouble()*4-2, random.nextDouble()*2, random.nextDouble()*4-2), EntityType.BAT);
                bat.setCustomName("§5蝙蝠"); bat.setCustomNameVisible(false);
                summonBats.add(bat);
                new BukkitRunnable() { @Override public void run() { summonBats.remove(bat); if (bat.isValid()) bat.remove(); } }.runTaskLater(plugin, 200L);
            }
            bp.sendTitle("§5蝙蝠唤取", "§7隐身3s", 5, 40, 10);
            bp.sendMessage("§c蝙蝠唤取！隐身3s");
            setCooldown(pd, skillId, 20);
        }
    }
    // ==================== GOOD SKILLS ====================

    private void skillGuard(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
        PlayerData nearest = findNearestPlayer(pd, 8.0, p -> p.isAlive() && p != pd);
            if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
            Player nb = nearest.getBukkitPlayer(); if (nb == null || !nb.isOnline()) { suppressCooldown = true; return; }
            // 给守卫自己盾牌
            bp.getInventory().addItem(new ItemStack(Material.SHIELD));
            // 给最近玩家+1复活次数
            int charges = revivalCharges.getOrDefault(nb.getUniqueId(), 0) + 1;
            revivalCharges.put(nb.getUniqueId(), charges);
            nb.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 1, true, false));
            nb.sendTitle("§a守护之誓", "§7你被守护了，获得1次复活", 10, 40, 10);
            nb.sendMessage("§a你被守卫守护了，获得一次复活机会（当前: " + charges + "）");
            bp.sendMessage("§a已守护 " + nearest.getUsername() + "，获得1次复活！");
            setCooldown(pd, skillId, 30);
        } else if ("skill2".equals(skillId)) {
            // 众志成城: 一次性, r=6 内所有存活玩家(含自己)获得6黄心
            String key = oneTimeKey(pd.getRole().getName(), skillId);
            if (pd.isSkillUsed(key)) {
                bp.sendMessage("§c【众志成城】只能使用一次！");
                suppressCooldown = true;
                return;
            }
            pd.setSkillUsed(key, true);
            consumeSkillDiamond(pd, skillId);
            int shielded = 0;
            for (PlayerData other : players) {
                if (!other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob == null || !ob.isOnline()) continue;
                if (other != pd && !sameWorldAndNear(bp, ob, 6.0)) continue;
                ob.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 2, true, false));
                ob.sendTitle("§a众志成城", "§7你获得6黄心", 10, 40, 10);
                shielded++;
            }
            bp.sendMessage("§a众志成城！r=6 内 " + shielded + " 名玩家获得6黄心（一次性）");
        }
    }

    private void skillKnight(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        PlayerData nearest = findNearestPlayer(pd, 50.0, PlayerData::isAlive);
        if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
        Player nb = nearest.getBukkitPlayer(); if (nb == null || !nb.isOnline()) { suppressCooldown = true; return; }
        String targetCamp = nearest.getCamp();
        if ("好人阵营".equals(targetCamp)) {
            killPlayer(pd, null);
            bp.sendTitle("§c决斗失败", "§7对方是好人", 10, 40, 10);
            broadcast("§c" + pd.getUsername() + " §7决斗了好人，牺牲了！");
            checkGameEnd();
        } else if ("狼人阵营".equals(targetCamp)) {
            dealDamage(nb, 18.0, bp);
            nb.sendTitle("§c决斗失败", "§7身份被揭露！", 10, 60, 10);
            broadcast("§c§l" + nearest.getUsername() + " 的身份是 " + nearest.getRole().getDisplayName() + "！");
        } else {
            dealDamage(nb, 12.0, bp);
            nb.sendTitle("§c决斗", "§7你受伤了", 10, 40, 10);
        }
        bp.sendMessage("§a决斗发动！");
        setCooldown(pd, skillId, 40);
    }

    private void skillNinja(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        if ("skill1".equals(skillId)) {
            // 速: 储存类, 初始1次, 每50s充能1次, 可叠加, 无CD
            Integer charges = (Integer) pd.getAttribute("ninjaCharges");
            if (charges == null) charges = 0;
            if (charges <= 0) { bp.sendMessage("§c没有【速】次数了！"); suppressCooldown = true; return; }
            pd.setAttribute("ninjaCharges", charges - 1);
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 9, true, false));
            bp.sendMessage("§a速！极速！剩余: " + (charges - 1));
        } else if ("skill2".equals(skillId)) {
            // 瞬身: 一次性两段式. 第一次标记目标(无距离限制), 第二次直接传送到已标记的目标
            if (ninjaPlayerMarks.containsKey(uuid)) {
                UUID markedId = ninjaPlayerMarks.get(uuid);
                PlayerData marked = null;
                for (PlayerData other : players) {
                    Player ob = other.getBukkitPlayer();
                    if (ob != null && ob.getUniqueId().equals(markedId)) { marked = other; break; }
                }
                Player target = marked != null ? marked.getBukkitPlayer() : null;
                if (marked == null || !marked.isAlive() || target == null || !target.isOnline() || target.getWorld() != bp.getWorld()) {
                    bp.sendMessage("§c瞬身目标已失效，请重新标记！");
                    ninjaPlayerMarks.remove(uuid);
                    suppressCooldown = true;
                    return;
                }
                bp.teleport(target.getLocation());
                bp.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, true, false));
                bp.sendMessage("§a瞬身到 " + target.getName() + "！");
                ninjaPlayerMarks.remove(uuid);
                pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
                consumeSkillDiamond(pd, skillId);
            } else {
                PlayerData nearest = findNearestPlayer(pd, 1.0E9, other -> other.isAlive() && other != pd);
                if (nearest == null) { bp.sendMessage("§c场上没有可标记的玩家！"); suppressCooldown = true; return; }
                Player nb = nearest.getBukkitPlayer(); if (nb == null || !nb.isOnline()) { suppressCooldown = true; return; }
                ninjaPlayerMarks.put(uuid, nb.getUniqueId());
                bp.sendMessage("§a已标记 " + nearest.getUsername() + "！蹲下右键[二技能]可切换目标，再次使用即可瞬身");
            }
        } else if ("skill3".equals(skillId)) {
            // 归: 一次性两段式. 标记当前位置 -> 传送回
            Location ret = ninjaReturnMarks.get(uuid);
            if (ret == null) {
                ninjaReturnMarks.put(uuid, bp.getLocation().clone());
                bp.sendMessage("§a已标记位置！再次使用【归】传送回该位置");
            } else {
                bp.teleport(ret);
                ninjaReturnMarks.remove(uuid);
                bp.sendMessage("§a已返回！");
                pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
                consumeSkillDiamond(pd, skillId);
            }
        }
    }

    // 潜行右键二技能: 在同世界存活玩家间切换瞬身目标
    public void switchNinjaTarget(PlayerData pd) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        java.util.List<PlayerData> alive = new java.util.ArrayList<>();
        for (PlayerData other : players) {
            if (other == pd || !other.isAlive()) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline()) continue;
            if (ob.getWorld() != bp.getWorld()) continue;
            alive.add(other);
        }
        if (alive.isEmpty()) { bp.sendMessage("§c同世界没有可标记的玩家！"); return; }
        UUID current = ninjaPlayerMarks.get(uuid);
        int idx = -1;
        if (current != null) {
            for (int i = 0; i < alive.size(); i++) {
                Player p = alive.get(i).getBukkitPlayer();
                if (p != null && p.getUniqueId().equals(current)) { idx = i; break; }
            }
        }
        PlayerData next = alive.get((idx + 1) % alive.size());
        Player nb = next.getBukkitPlayer();
        if (nb == null) return;
        ninjaPlayerMarks.put(uuid, nb.getUniqueId());
        bp.sendMessage("§a瞬身目标已切换为 " + next.getUsername());
    }

    private void skillHacker(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        if ("skill1".equals(skillId)) {
            if (bp.getInventory().getItemInMainHand().getType() != Material.IRON_SWORD) {
                bp.sendMessage("§c请手持铁剑才能开启杀戮光环！");
                suppressCooldown = true;
                return;
            }
            if (hackerAuraTasks.containsKey(uuid)) {
                hackerAuraTasks.get(uuid).cancel(); hackerAuraTasks.remove(uuid);
                bp.sendMessage("§c杀戮光环已关闭！");
            } else {
                startHackerAura(pd, bp);
                bp.sendMessage("§a杀戮光环已开启！");
            }
        } else if ("skill2".equals(skillId)) {
            Location marked = hackerLocationMarks.get(uuid);
            if (marked == null) {
                hackerLocationMarks.put(uuid, bp.getLocation().clone());
                bp.sendMessage("§a已标记位置！");
            } else {
                bp.teleport(marked); hackerLocationMarks.remove(uuid);
                bp.sendMessage("§a已返回！");
            }
        }
    }

    // 杀戮光环 4s: 持有铁剑时由 tickHackerPassive 自动开启, 释放者同时获得凋零I 4s
    private void startHackerAura(PlayerData pd, Player bp) {
        UUID uuid = bp.getUniqueId();
        if (hackerAuraTasks.containsKey(uuid)) return;
        bp.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0, true, false));
        BukkitTask task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 80 || !pd.isAlive() || state != GameState.PLAYING) {
                    hackerAuraTasks.remove(uuid); cancel();
                    return;
                }
                if (!bp.isOnline()) { hackerAuraTasks.remove(uuid); cancel(); return; }
                ticks++;
                for (Entity e : bp.getNearbyEntities(10.0, 10.0, 10.0)) {
                    if (e instanceof Player) {
                        Player ob = (Player) e;
                        PlayerData od = plugin.getPlayerDataManager().getPlayerData(ob);
                        if (od != null && od.isAlive() && od != pd) {
                            // 复用原版普攻逻辑: 走 EntityDamageByEntityEvent, 由 CombatListener 放行
                            ob.damage(1.0, bp);
                            showHitFeedback(ob, 1.0, bp);
                        }
                    } else if (!e.isDead() && e instanceof LivingEntity) {
                        ((LivingEntity) e).damage(2.0, bp);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);
        hackerAuraTasks.put(uuid, task);
    }

    // 手持铁剑时自动开启/关闭杀戮光环
    private void tickHackerPassive(PlayerData pd, Player bp) {
        UUID uuid = bp.getUniqueId();
        boolean holdingIronSword = bp.getInventory().getItemInMainHand().getType() == Material.IRON_SWORD;
        BukkitTask running = hackerAuraTasks.get(uuid);
        if (holdingIronSword) {
            if (running == null && pd.getSkillCooldown("skill1") <= 0) {
                startHackerAura(pd, bp);
                pd.setSkillCooldown("skill1", 8);
                bp.sendMessage("§a手持铁剑：杀戮光环自动开启！");
            }
        } else if (running != null) {
            running.cancel();
            hackerAuraTasks.remove(uuid);
            bp.sendMessage("§e放下铁剑，杀戮光环已关闭！");
        }
    }

    // 【余烬视野】: 提灯人可看到其他持有余烬玩家的阵营构成(排除自己)
    private void tickLanternBearerPassive(PlayerData pd, Player bp) {
        Integer t = (Integer) pd.getAttribute("lanternScanTick");
        t = t == null ? 0 : t + 1;
        if (t < 40) { pd.setAttribute("lanternScanTick", t); return; }
        pd.setAttribute("lanternScanTick", 0);
        StringBuilder sb = new StringBuilder();
        for (PlayerData other : players) {
            if (other == pd || !other.isAlive()) continue;
            if (!other.hasTag("ember")) continue;
            String camp = other.getCamp();
            if (sb.length() > 0) sb.append(" ");
            sb.append(other.getUsername()).append("(").append(camp == null ? "?" : camp).append(")");
        }
        if (sb.length() == 0) { pd.sendActionBar("§6[余烬视野]§7当前无人持有余烬"); return; }
        pd.sendActionBar("§6[余烬视野]§f" + sb);
    }

    private void skillBuddhistMonk(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        PlayerData nearest = findNearestPlayer(pd, 8.0, p -> p.isAlive() && p != pd
            && !p.hasTag("converted")
            && (p.getRole() == null || !"MONK".equals(p.getRole().getName())));
        if (nearest == null) { bp.sendMessage("§c附近没有未明身份的玩家！"); suppressCooldown = true; return; }
        Role monk = plugin.getRoleManager().getRole("MONK");
        if (monk == null) { bp.sendMessage("§c武僧身份未加载！"); suppressCooldown = true; return; }
        nearest.setRole(monk);
        nearest.addTag("converted");
        Player nb = nearest.getBukkitPlayer();
        if (nb != null && nb.isOnline()) {
            nb.sendTitle("§a劝善成功", "§7你现在是武僧", 10, 60, 10);
            nb.sendMessage("§a你被佛僧劝善了，你现在是正义阵营！");
        }
        initializePlayerForBattle(nearest);
        bp.sendMessage("§a劝善成功！转化了 " + nearest.getUsername());
        pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
        consumeSkillDiamond(pd, skillId);
    }

    private void skillWindSpirit(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        Integer idxObj = (Integer) pd.getAttribute("windIndex");
        int idx = idxObj == null ? 0 : idxObj;
        // 只施加当前风向对应的一种增益 (迅捷/跃进/治愈/飘扬)
        int count = 0;
        for (PlayerData other : players) {
            if (!other.isAlive()) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline()) continue;
            if (other != pd && !sameWorldAndNear(bp, ob, 6.0)) continue;
            applyWindEffect(ob, idx);
            ob.sendTitle("§a风起之时", "§7" + windName(idx), 5, 30, 5);
            if (other != pd) count++;
        }
        bp.sendMessage("§a风起之时（" + windName(idx) + "）！影响了 " + count + " 名玩家！");
        setCooldown(pd, skillId, 30);
    }

    private String windName(int idx) {
        switch (idx) {
            case 0: return "迅捷之风";
            case 1: return "跃进之风";
            case 2: return "治愈之风";
            default: return "飘扬之风";
        }
    }

    // 施加当前风向对应的效果
    private void applyWindEffect(Player p, int idx) {
        switch (idx) {
            case 0: p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 4, true, false)); break;
            case 1: p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 200, 4, true, false)); break;
            case 2: p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, false)); healPlayer(p, 6.0); break;
            default:
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 2, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0, true, false));
                break;
        }
    }

    public ItemStack makeWindBomb(int amount) {
        return makeItem(Material.SNOWBALL, "§b风弹", "§7风灵之力", amount);
    }

    private void skillWaterDivination(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        int roll = random.nextInt(100) + 1;
        int waterBlocks = 0;
        Location loc = bp.getLocation();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (loc.clone().add(x, 0, z).getBlock().getType() == Material.WATER) waterBlocks++;
        }
        int total = roll + waterBlocks * 5;
        // 水占结果仅自己可见
        bp.sendMessage("§b水占术！掷骰: " + roll + " + 水加成: " + (waterBlocks * 5) + " = " + total);
        if (total >= 145) {
            // 水占师胜利
            bp.sendMessage("§b§l水占术预言实现！水占师获胜！");
            endGame(pd.getCamp(), "水占师预言成功");
        } else if (total >= 131) {
            // 获得全部好人狼人中立id
            bp.sendMessage("§b§l真理现于水占！全部身份暴露！");
            for (PlayerData other : players) {
                if (other.isAlive() && other.getRole() != null) {
                    bp.sendMessage("§b" + other.getUsername() + " = " + other.getRole().getDisplayName() + " [" + other.getCamp() + "]");
                }
            }
        } else if (total >= 100) {
            // 获得一个好人+狼人+中立id
            boolean foundGood = false, foundWolf = false, foundNeutral = false;
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                String c = other.getCamp();
                if ("好人阵营".equals(c) && !foundGood) { foundGood = true; bp.sendMessage("§b水占术好人: " + other.getUsername() + " [" + other.getRole().getDisplayName() + "]"); }
                else if ("狼人阵营".equals(c) && !foundWolf) { foundWolf = true; bp.sendMessage("§b水占术狼人: " + other.getUsername() + " [" + other.getRole().getDisplayName() + "]"); }
                else if (isNeutralCamp(c) && !foundNeutral) { foundNeutral = true; bp.sendMessage("§b水占术中立: " + other.getUsername() + " [" + other.getRole().getDisplayName() + "]"); }
            }
        } else if (total >= 76) {
            // 获得一个狼人id
            for (PlayerData other : players) {
                if (other != pd && other.isAlive() && "狼人阵营".equals(other.getCamp())) {
                    bp.sendMessage("§b水占术狼人: " + other.getUsername() + " [" + other.getRole().getDisplayName() + "]"); break;
                }
            }
        } else if (total >= 36) {
            // 获得一个好人id
            for (PlayerData other : players) {
                if (other != pd && other.isAlive() && "好人阵营".equals(other.getCamp())) {
                    bp.sendMessage("§b水占术好人: " + other.getUsername() + " [" + other.getRole().getDisplayName() + "]"); break;
                }
            }
        } else {
            bp.sendMessage("§b水面浑浊不清，无效果。");
        }
        setCooldown(pd, skillId, 60);
    }

    private static boolean isNeutralCamp(String camp) {
        return "和平中立".equals(camp) || "危险中立".equals(camp);
    }

    private void skillLanternBearer(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        int count = 0;
        for (PlayerData other : players) {
            if (!other.isAlive()) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline()) continue;
            if (other != pd && !sameWorldAndNear(bp, ob, 6.0)) continue;
            if (other.hasTag("ember")) continue;
            other.addTag("ember");
            ob.getInventory().addItem(makeEmber());
            ob.updateInventory();
            ob.sendTitle("§6灯塔", "§7获得余烬，右键使用", 5, 60, 5);
            count++;
        }
        bp.sendMessage("§6灯塔！余烬传递给 " + count + " 名玩家！");
        setCooldown(pd, skillId, 30);
    }

    public ItemStack makeEmber() {
        return makeItem(Material.BLAZE_POWDER, "§6[余烬]", "§7右键使用 速度6 3s后短暂僵直");
    }

    private void skillTheSurvivor(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            // 血清注射: 一次性
            String key = oneTimeKey(pd.getRole().getName(), skillId);
            if (pd.isSkillUsed(key)) { bp.sendMessage("§c【血清注射】只能使用一次！"); suppressCooldown = true; return; }
            pd.setSkillUsed(key, true);
            consumeSkillDiamond(pd, skillId);
            healPlayer(bp, 6.0);
            // 缓慢15s恢复5心 + 力量3 速度3 跳跃2 持续15s
            bp.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 300, 1, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 300, 2, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 300, 1, true, false));
            bp.sendMessage("§a血清注射！恢复3心 + 15s内慢恢复5心 + 力量3/速度3/跳跃2（一次性）");
        } else if ("skill2".equals(skillId)) {
            // 废土生存法则: 10s后判定，15%什么都没有
            bp.sendMessage("§a挖掘中... 10s后出现结果");
            new BukkitRunnable() {
                @Override public void run() {
                    if (!pd.isAlive()) return;
                    setCooldown(pd, "skill2", 30);
                    int roll = random.nextInt(1000);
                    if (roll < 150) { bp.sendMessage("§7什么都没搜到。"); return; }
                    if (roll < 180) { bp.getInventory().addItem(makeItem(Material.GOLDEN_APPLE, "§e医疗包", "")); bp.sendMessage("§a找到1个医疗包！"); return; }
                    if (roll < 230) { for(int i=0;i<3;i++) bp.getInventory().addItem(makeItem(Material.GOLDEN_APPLE, "§e医疗包", "")); bp.sendMessage("§a找到3个医疗包！"); return; }
                    if (roll < 250) { for(int i=0;i<6;i++) bp.getInventory().addItem(makeItem(Material.GOLDEN_APPLE, "§e医疗包", "")); bp.sendMessage("§a找到6个医疗包！"); return; }
                    if (roll < 280) { bp.getInventory().addItem(makeItem(Material.FIRE_CHARGE, "§e震撼弹", "")); bp.sendMessage("§a找到1个震撼弹！"); return; }
                    if (roll < 330) { for(int i=0;i<3;i++) bp.getInventory().addItem(makeItem(Material.FIRE_CHARGE, "§e震撼弹", "")); bp.sendMessage("§a找到3个震撼弹！"); return; }
                    if (roll < 350) { for(int i=0;i<8;i++) bp.getInventory().addItem(makeItem(Material.FIRE_CHARGE, "§e震撼弹", "")); bp.sendMessage("§a找到8个震撼弹！"); return; }
                    if (roll < 380) { bp.getInventory().addItem(makeItem(Material.POTION, "§a肾上腺素", "")); bp.sendMessage("§a找到1个肾上腺素！"); return; }
                    if (roll < 430) { for(int i=0;i<3;i++) bp.getInventory().addItem(makeItem(Material.POTION, "§a肾上腺素", "")); bp.sendMessage("§a找到3个肾上腺素！"); return; }
                    if (roll < 450) { bp.getInventory().addItem(makeItem(Material.POTION, "§b夜视仪", "")); bp.sendMessage("§a找到3个夜视仪！"); return; }
                    if (roll < 500) { ItemStack shield = new ItemStack(Material.SHIELD); shield.setDurability((short)(shield.getType().getMaxDurability() * 7 / 10)); bp.getInventory().addItem(shield); bp.sendMessage("§a找到一个破损盾牌！"); return; }
                    if (roll < 550) { ItemStack is = new ItemStack(Material.IRON_SWORD); is.setDurability((short)(is.getType().getMaxDurability() * 7 / 10)); bp.getInventory().addItem(is); bp.sendMessage("§a找到一把破损铁剑！"); return; }
                    if (roll < 600) { bp.getInventory().addItem(new ItemStack(Material.FISHING_ROD)); bp.sendMessage("§a找到一把鱼竿！"); return; }
                    if (roll < 650) { bp.getInventory().addItem(new ItemStack(Material.EMERALD, 30)); bp.sendMessage("§a找到30个绿宝石！"); return; }
                    if (roll < 700) { bp.getInventory().addItem(new ItemStack(Material.REDSTONE, 15)); bp.sendMessage("§a找到15个红宝石！"); return; }
                    if (roll < 750) { bp.getInventory().addItem(makeItem(Material.POTION, "§a瞬间治疗", "")); bp.sendMessage("§a找到瞬间治疗药水！"); return; }
                    if (roll < 780) { bp.getInventory().addItem(makeItem(Material.SPLASH_POTION, "§a投掷治疗", "")); bp.sendMessage("§a找到投掷治疗药水！"); return; }
                    if (roll < 800) { bp.getInventory().addItem(makeItem(Material.SPLASH_POTION, "§c投掷伤害", "")); bp.sendMessage("§a找到投掷伤害药水！"); return; }
                    if (roll < 850) { ItemStack is = new ItemStack(Material.IRON_SWORD); ItemMeta im = is.getItemMeta(); im.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 1, true); is.setItemMeta(im); is.setDurability((short)(is.getType().getMaxDurability() - 15)); bp.getInventory().addItem(is); bp.sendMessage("§a找到火焰附加铁剑！"); return; }
                    if (roll < 900) { ItemStack arrow = new ItemStack(Material.ARROW, 3); bp.getInventory().addItem(arrow); bp.sendMessage("§a找到3根中毒箭！"); return; }
                    if (roll < 950) { for(int i=0;i<6;i++) bp.getInventory().addItem(makeItem(Material.SNOWBALL, "§b风弹", "")); bp.sendMessage("§a找到6个风弹！"); return; }
                    if (roll < 980) { ItemStack cb = new ItemStack(Material.CROSSBOW); ItemMeta cbm = cb.getItemMeta(); cbm.addEnchant(org.bukkit.enchantments.Enchantment.MULTISHOT, 1, true); cb.setItemMeta(cbm); cb.setDurability((short)(cb.getType().getMaxDurability() - 10)); bp.getInventory().addItem(cb); bp.getInventory().addItem(new ItemStack(Material.FIREWORK_ROCKET)); bp.sendMessage("§a找到多重射击弓+烟花！"); return; }
                    if (roll < 990) { bp.getInventory().addItem(makeItem(Material.POTION, "§c力量1", "")); bp.sendMessage("§a找到力量药水！"); return; }
                    if (roll < 995) { bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2, true, false)); bp.sendMessage("§a速度+3（60s）！"); return; }
                    if (roll < 998) { bp.getInventory().addItem(new ItemStack(Material.TOTEM_OF_UNDYING)); bp.sendMessage("§a找到不死图腾！"); return; }
                    // 0.3% 秒刀
                    ItemStack blade = new ItemStack(Material.NETHERITE_SWORD); ItemMeta bm = blade.getItemMeta(); bm.addEnchant(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 255, true); bm.setDisplayName("§4秒刀"); blade.setItemMeta(bm); blade.setDurability((short) (Material.NETHERITE_SWORD.getMaxDurability() - 1)); bp.getInventory().addItem(blade);
                    bp.sendMessage("§c§l找到秒刀！！！");
                }
            }.runTaskLater(plugin, 200L);
        }
        setCooldown(pd, skillId, 20);
    }

    private void skillBladeAndSoul(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        if ("skill1".equals(skillId)) {
            Integer stacks = bladeSoulS1Stacks.get(uuid);
            if (stacks == null || stacks <= 0) { bp.sendMessage("§c刺击次数用尽！"); suppressCooldown = true; return; }
            bladeSoulS1Stacks.put(uuid, stacks - 1);
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 6.0) {
                    dealDamage(ob, 1.0, bp);
                    ob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 2, true, false));
                }
            }
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 2, true, false));
            bp.sendMessage("§e直线刺击！剩余: " + (stacks - 1));
        } else if ("skill2".equals(skillId)) {
            Integer stacks = bladeSoulS2Stacks.get(uuid);
            if (stacks == null || stacks <= 0) { bp.sendMessage("§c扫荡次数用尽！"); suppressCooldown = true; return; }
            bladeSoulS2Stacks.put(uuid, stacks - 1);
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 6.0) {
                    dealDamage(ob, 2.0, bp);
                }
            }
            bp.sendMessage("§c横向扫荡！剩余: " + (stacks - 1));
        } else if ("skill3".equals(skillId)) {
            // 瞬时防御: 缓慢3 1s + 抗性4 1s + 回3心 CD20s
            bp.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20, 2, true, false));
            bp.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20, 3, true, false));
            healPlayer(bp, 6.0);
            bp.sendMessage("§e瞬时防御！");
            setCooldown(pd, skillId, 20);
        } else if ("skill4".equals(skillId)) {
            // 剑灵: 消耗技能1和技能2各4层 对周围r=8每0.1s造成0.5心真伤持续1.4s
            Integer s1 = bladeSoulS1Stacks.getOrDefault(uuid, 0);
            Integer s2 = bladeSoulS2Stacks.getOrDefault(uuid, 0);
            if (s1 < 4 || s2 < 4) { bp.sendMessage("§c刺击和扫荡各需要4层！当前刺:" + s1 + " 扫:" + s2); suppressCooldown = true; return; }
            bladeSoulS1Stacks.put(uuid, s1 - 4);
            bladeSoulS2Stacks.put(uuid, s2 - 4);
            bp.sendMessage("§e§l剑灵！");
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 14 || !pd.isAlive()) { cancel(); return; }
                    for (PlayerData other : players) {
                        if (other == pd || !other.isAlive()) continue;
                        Player ob = other.getBukkitPlayer();
                        if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 8.0) {
                            dealDamage(ob, 1.0, bp);
                            org.bukkit.util.Vector push = ob.getLocation().toVector().subtract(bp.getLocation().toVector());
                            if (push.lengthSquared() < 1.0E-6) push = new org.bukkit.util.Vector(0, 1, 0);
                            ob.setVelocity(push.normalize().multiply(0.45).setY(0.25));
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 2L);
            // 一次性技能: 消耗技能钻石, 不进入冷却
            pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
            consumeSkillDiamond(pd, skillId);
            bp.sendMessage("§7【剑灵】为一次性技能, 已使用完毕。");
        }
    }

    // ==================== PEACEFUL NEUTRAL SKILLS ====================

    private void skillArsonist(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            int count = 0;
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 5.0) {
                    other.addTag("oiled");
                    ob.sendTitle("§4上油", "§7你被洒了油", 10, 30, 10);
                    count++;
                }
            }
            bp.sendMessage("§4上油！" + count + " 名玩家被标记！");
        } else if ("skill2".equals(skillId)) {
            int count = 0;
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive() || !other.hasTag("oiled")) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline()) {
                    dealDamage(ob, 10.0, bp);
                    ob.setFireTicks(60);
                    ob.sendTitle("§4着火了！", "", 5, 20, 5);
                    other.removeTag("oiled");
                    count++;
                }
            }
            bp.sendMessage("§4纵火！" + count + " 名玩家被点燃！");
        }
        setCooldown(pd, skillId, 20);
    }

    private void skillTimeDuke(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            for (PlayerData other : players) {
                if (other == pd || !other.isAlive()) continue;
                Player ob = other.getBukkitPlayer();
                if (ob != null && ob.isOnline() && bp.getLocation().distance(ob.getLocation()) <= 8.0) {
                    ob.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 200, 250, true, false));
                    ob.sendTitle("§5时间凝滞", "§7无法移动", 5, 150, 10);
                }
            }
            bp.sendMessage("§5时间凝滞！");
            setCooldown(pd, skillId, 18);
        } else if ("skill2".equals(skillId)) {
            if (pd.isSkillUsed("timeDukeRestore")) {
                // 第二次使用: 回流恢复
                @SuppressWarnings("unchecked")
                Map<UUID, Location> savedLocs = (Map<UUID, Location>) pd.getAttribute("timeRestoreLocs");
                @SuppressWarnings("unchecked")
                Map<UUID, Double> savedHP = (Map<UUID, Double>) pd.getAttribute("timeRestoreHP");
                if (savedLocs == null || savedHP == null) { bp.sendMessage("§c没有已保存状态！"); suppressCooldown = true; return; }
                for (PlayerData other : players) {
                    if (!other.isAlive()) continue;
                    Player ob = other.getBukkitPlayer();
                    if (ob != null && ob.isOnline()) {
                        Location loc = savedLocs.get(ob.getUniqueId());
                        Double hp = savedHP.get(ob.getUniqueId());
                        if (loc != null) ob.teleport(loc);
                        if (hp != null) ob.setHealth(hp);
                    }
                }
                pd.setSkillUsed("timeDukeRestore", false);
                bp.sendMessage("§5时间回流！已恢复到保存状态！");
                setCooldown(pd, skillId, 18);
            } else {
                // 第一次使用: 保存状态
                Map<UUID, Location> savedLocs = new HashMap<>();
                Map<UUID, Double> savedHP = new HashMap<>();
                for (PlayerData other : players) {
                    if (!other.isAlive()) continue;
                    Player ob = other.getBukkitPlayer();
                    if (ob != null && ob.isOnline()) {
                        savedLocs.put(ob.getUniqueId(), ob.getLocation().clone());
                        savedHP.put(ob.getUniqueId(), ob.getHealth());
                    }
                }
                pd.setAttribute("timeRestoreLocs", savedLocs);
                pd.setAttribute("timeRestoreHP", savedHP);
                pd.setSkillUsed("timeDukeRestore", true);
                bp.sendMessage("§5时间已保存！再次使用以回流！");
            }
        }
    }

    private void skillPelican(PlayerData pd, String skillId) {
        if (!"skill1".equals(skillId)) return;
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        PlayerData nearest = findNearestPlayer(pd, 3.0, PlayerData::isAlive);
        if (nearest == null) { bp.sendMessage("§c附近没有玩家（r=3）！"); suppressCooldown = true; return; }
        Player nb = nearest.getBukkitPlayer(); if (nb == null || !nb.isOnline()) { suppressCooldown = true; return; }
        Location cageLoc = new Location(bp.getWorld(), 0, 100, 0);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = 0; y <= 3; y++) {
            if (x == -2 || x == 2 || z == -2 || z == 2 || y == 0 || y == 3)
                cageLoc.clone().add(x, y, z).getBlock().setType(Material.BEDROCK);
        }
        cageLoc.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        cageLoc.clone().add(0, 2, 0).getBlock().setType(Material.AIR);
        nb.teleport(cageLoc.clone().add(0, 1, 0));
        nb.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 999999, 4, true, false));
        pelicanStoredPlayers.computeIfAbsent(bp.getUniqueId(), k -> new ArrayList<>()).add(nb.getUniqueId());
        nb.sendTitle("§c你被鹈鹕吞了", "§7等待鹈鹕死亡", 10, 60, 10);
        bp.sendMessage("§a同事入口即化！已吞下 " + nearest.getUsername());
        setCooldown(pd, skillId, 30);
    }

    // ==================== DANGEROUS NEUTRAL SKILLS ====================

    private void skillNeutralJackal(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        if ("skill1".equals(skillId)) {
            PlayerData nearest = findNearestPlayer(pd, 50.0, PlayerData::isAlive);
            if (nearest == null) { bp.sendMessage("§c附近没有玩家！"); suppressCooldown = true; return; }
            Role follower = plugin.getRoleManager().getRole("FOLLOWER");
            if (follower == null) { suppressCooldown = true; return; }
        nearest.setRole(follower);
        Player nb = nearest.getBukkitPlayer();
        if (nb != null && nb.isOnline()) {
            nb.sendTitle("§5你被招募了", "§7你现在是追随者", 10, 60, 10);
            nb.sendMessage("§5你被豺狼招募了，你现在是危险中立阵营！");
        }
        initializePlayerForBattle(nearest);
        bp.sendMessage("§5招募了 " + nearest.getUsername() + "！");
        pd.setSkillUsed(oneTimeKey(pd.getRole().getName(), skillId), true);
        consumeSkillDiamond(pd, skillId);
    }
    }

    private void skillFollower(PlayerData pd, String skillId) {
        // 跟班无主动技能
    }

    private void skillWizardSkill(PlayerData pd, String skillId) {
        Player bp = pd.getBukkitPlayer(); if (bp == null) return;
        UUID uuid = bp.getUniqueId();
        int energy = wizardEnergy.getOrDefault(uuid, 200);
        if ("skill1".equals(skillId)) {
            if (energy < 80) { bp.sendMessage("§c能量不足！需要80，当前: " + energy); suppressCooldown = true; return; }
            wizardEnergy.put(uuid, energy - 80);
            Vector dir = bp.getLocation().getDirection().normalize().multiply(5);
            Location target = bp.getLocation().add(dir);
            target.setY(bp.getWorld().getHighestBlockYAt(target));
            final Location strikeLoc = target.clone();
            new BukkitRunnable() {
                int count = 0;
                @Override public void run() {
                    if (count >= 3) { cancel(); return; }
                    strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
                    for (Entity e : strikeLoc.getWorld().getNearbyEntities(strikeLoc, 2, 2, 2)) {
                        if (e instanceof Player && e != bp) {
                            Player t = (Player) e;
                            if (isAlive(t)) dealDamage(t, 12.0, bp);
                        }
                    }
                    count++;
                }
            }.runTaskTimer(plugin, 0L, 5L);
            bp.sendMessage("§5落雷！");
            setCooldown(pd, skillId, 5);
        } else if ("skill2".equals(skillId)) {
            if (energy < 120) { bp.sendMessage("§c能量不足！需要120，当前: " + energy); suppressCooldown = true; return; }
            wizardEnergy.put(uuid, energy - 120);
            Vector dir = bp.getLocation().getDirection().normalize();
            for (int i = 5; i <= 9; i++) {
                Location target = bp.getLocation().add(dir.clone().multiply(i));
                target.setY(bp.getWorld().getHighestBlockYAt(target));
                final Location strikeLoc = target.clone();
                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
                for (Entity e : strikeLoc.getWorld().getNearbyEntities(strikeLoc, 2, 2, 2)) {
                    if (e instanceof Player && e != bp) {
                        Player t = (Player) e;
                        if (isAlive(t)) dealDamage(t, 12.0, bp);
                    }
                }
            }
            bp.sendMessage("§5直线雷击！");
            setCooldown(pd, skillId, 5);
        } else if ("skill3".equals(skillId)) {
            if (energy < 120) { bp.sendMessage("§c能量不足！需要120，当前: " + energy); suppressCooldown = true; return; }
            wizardEnergy.put(uuid, energy - 120);
            Vector dir = bp.getLocation().getDirection().normalize().multiply(5);
            Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            for (int i = -2; i <= 2; i++) {
                Location target = bp.getLocation().add(dir.clone().add(right.clone().multiply(i)));
                target.setY(bp.getWorld().getHighestBlockYAt(target));
                final Location strikeLoc = target.clone();
                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
                for (Entity e : strikeLoc.getWorld().getNearbyEntities(strikeLoc, 2, 2, 2)) {
                    if (e instanceof Player && e != bp) {
                        Player t = (Player) e;
                        if (isAlive(t)) dealDamage(t, 12.0, bp);
                    }
                }
            }
            bp.sendMessage("§5环形雷击！");
            setCooldown(pd, skillId, 5);
        }
    }

    // ==================== UTILITY METHODS ====================

    private void dealDamage(Player target, double amount, Player source) {
        if (target == null || !target.isOnline()) return;
        PlayerData td = plugin.getPlayerDataManager().getPlayerData(target);
        if (td == null || !td.isAlive()) return;
        double hp = target.getHealth();
        double newHp = hp - amount;
        // 先触发原版受击红闪/音效, 再按真伤扣血 (实际扣血以下面的 setHealth 为准)
        triggerHurtFlash(target, td, source);
        showHitFeedback(target, amount, source);
        if (newHp <= 0) {
            // setHealth(0) does not fire PlayerDeathEvent, so a vanilla death is never
            // reported here. Leave a marker: if it somehow does fire, CombatListener
            // claims the marker and judges the kill; otherwise we judge it right here.
            td.setAttribute("__lethal", Boolean.TRUE);
            target.setHealth(0);
            if (td.getAttribute("__lethal") != null) {
                td.removeAttribute("__lethal");
                PlayerData killerData = source != null ? plugin.getPlayerDataManager().getPlayerData(source) : null;
                killPlayer(td, killerData);
            }
        } else {
            target.setHealth(newHp);
        }
    }

    // 原版受击红闪: 用一次极小伤害触发 hurt 动画与受击音效。
    // 真伤仍由 dealDamage 的 setHealth 计算, 这次伤害不参与扣血结果。
    // "__flash" 标记让 CombatListener 放行 (否则 CUSTOM/ENTITY_ATTACK 会被白名单或技能逻辑干扰)。
    //
    // 本次 damage 会把 NMS 的无敌帧字段(Entity#invulnerableTime)写成 20, 等于白送被击者 1s 无敌,
    // 所以 damage 前先把该字段读出来清零(确保红闪一定触发), damage 后再还原成原值。
    // 红闪是靠 damage 期间广播给客户端的受击包驱动的, 客户端自己有一份副本, 还原服务端字段不影响红闪。
    private static java.lang.reflect.Field[] hurtTickFields;
    private static boolean hurtTickResolved;
    private static boolean hurtTickWarned;

    private java.lang.reflect.Field[] hurtTickFields(Player target) {
        if (hurtTickResolved) return hurtTickFields;
        hurtTickResolved = true;
        try {
            java.util.List<java.lang.reflect.Field> found = new java.util.ArrayList<>();
            Object nms = target.getClass().getMethod("getHandle").invoke(target);
            for (String want : new String[]{"invulnerableTime", "hurtTime"}) {
                for (Class<?> c = nms.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        java.lang.reflect.Field f = c.getDeclaredField(want);
                        f.setAccessible(true);
                        found.add(f);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            if (!found.isEmpty()) hurtTickFields = found.toArray(new java.lang.reflect.Field[0]);
        } catch (Throwable ignored) {
        }
        if (hurtTickFields == null && !hurtTickWarned) {
            hurtTickWarned = true;
            plugin.getLogger().warning("[SharkAction] 定位无敌帧字段失败, 受击红闪会给被击者短暂无敌帧");
        }
        return hurtTickFields;
    }

    private void triggerHurtFlash(Player target, PlayerData td, Player source) {
        if (target == null || !target.isOnline() || td == null) return;
        java.lang.reflect.Field[] immune = hurtTickFields(target);
        Integer[] prev = null;
        if (immune != null) {
            prev = new Integer[immune.length];
            for (int i = 0; i < immune.length; i++) {
                try { prev[i] = immune[i].getInt(target); } catch (Throwable ignored) { prev[i] = null; }
            }
            for (int i = 0; i < immune.length; i++) {
                if (prev[i] == null) continue;
                try { immune[i].setInt(target, 0); } catch (Throwable ignored) { }
            }
        }
        td.setAttribute("__flash", Boolean.TRUE);
        try {
            if (source != null && source.isOnline() && source != target) target.damage(0.0001, source);
            else target.damage(0.0001);
        } catch (Throwable ignored) {
        } finally {
            td.removeAttribute("__flash");
            if (prev != null) {
                for (int i = 0; i < immune.length; i++) {
                    if (prev[i] == null) continue;
                    try { immune[i].setInt(target, prev[i]); } catch (Throwable ignored) { }
                }
            }
        }
    }

    // 受击反馈: 动作栏(不会被技能紧接着发的标题覆盖) + 命中音效, 双方都提示 (每目标最多每秒一次)
    private void showHitFeedback(Player target, double amount, Player source) {
        long now = System.currentTimeMillis();
        Long last = hitFeedbackAt.get(target.getUniqueId());
        if (last != null && now - last < 1000L) return;
        hitFeedbackAt.put(target.getUniqueId(), now);
        int hearts = Math.max(1, (int) Math.round(amount / 2.0));
        PlayerData td = plugin.getPlayerDataManager().getPlayerData(target);
        if (td != null) {
            td.sendActionBar("§c受到伤害 §7-" + hearts + "心" + (source != null ? " §8来自 §f" + source.getName() : ""));
        }
        if (source != null && source.isOnline() && source != target) {
            source.playSound(source.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.2f);
            PlayerData sd = plugin.getPlayerDataManager().getPlayerData(source);
            if (sd != null) sd.sendActionBar("§e命中 §f" + target.getName() + " §7-" + hearts + "心");
        }
    }

    public void healPlayer(Player target, double amount) {
        if (target == null || !target.isOnline()) return;
        double max = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        target.setHealth(Math.min(target.getHealth() + amount, max));
    }

    private boolean isAlive(Player p) {
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(p);
        return pd != null && pd.isAlive();
    }

    private PlayerData findNearestPlayer(PlayerData from, double maxDist, java.util.function.Predicate<PlayerData> filter) {
        Player bp = from.getBukkitPlayer();
        if (bp == null) return null;
        PlayerData nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerData other : players) {
            if (other == from || !other.isAlive()) continue;
            if (!filter.test(other)) continue;
            Player ob = other.getBukkitPlayer();
            if (ob == null || !ob.isOnline()) continue;
            if (ob.getWorld() != bp.getWorld()) continue;
            double dist = bp.getLocation().distance(ob.getLocation());
            if (dist < nearestDist && dist <= maxDist) { nearestDist = dist; nearest = other; }
        }
        return nearest;
    }

    private List<PlayerData> findPlayersInRange(Location loc, double radius) {
        List<PlayerData> result = new ArrayList<>();
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            Player bp = pd.getBukkitPlayer();
            if (bp != null && bp.isOnline() && bp.getLocation().distance(loc) <= radius) result.add(pd);
        }
        return result;
    }

    private boolean hasBuildingOverhead(Player bp) {
        for (int y = 1; y <= 4; y++) {
            if (bp.getLocation().add(0, y, 0).getBlock().getType() != Material.AIR) return true;
        }
        return false;
    }

    private void earthquakeCollapse(PlayerData pd) {
        Player bp = pd.getBukkitPlayer();
        if (bp == null || !bp.isOnline()) return;
        if (hasBuildingOverhead(bp)) {
            dealDamage(bp, 4.0, null);
            bp.sendTitle("§8建筑坍塌", "§7你被摧毁2心！", 2, 20, 5);
        } else {
            bp.sendTitle("§8地震", "", 2, 10, 5);
        }
    }

    private Player findNearestLivingPlayer(Location loc) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            Player bp = pd.getBukkitPlayer();
            if (bp == null || !bp.isOnline()) continue;
            double dist = loc.distance(bp.getLocation());
            if (dist < nearestDist) { nearestDist = dist; nearest = bp; }
        }
        return nearest;
    }

    public void spawnHordeHeadZombie(Location loc, String victimName) {
        if (loc == null) return;
        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        zombie.setCustomName("§c" + victimName + " 的尸魃");
        zombie.setCustomNameVisible(true);
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 1, true, false));
        zombie.setShouldBurnInDay(false);
        zombie.getEquipment().setHelmet(makePlayerHead(victimName));
        zombie.setTarget(findNearestLivingPlayer(loc));
        zombie.setRemoveWhenFarAway(false);
        eventZombies.add(zombie);
    }

    private ItemStack makePlayerHead(String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (name == null) return head;
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        Player online = Bukkit.getPlayerExact(name);
        meta.setOwningPlayer(online != null ? online : Bukkit.getOfflinePlayer(name));
        head.setItemMeta(meta);
        return head;
    }

    private Player findNearestEnemyForMob(Player owner) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (PlayerData pd : players) {
            if (!pd.isAlive()) continue;
            Player bp = pd.getBukkitPlayer();
            if (bp == null || !bp.isOnline() || bp == owner) continue;
            if (bp.getWorld() != owner.getWorld()) continue;
            // 赶尸人的僵尸不攻击狼人阵营
            if ("狼人阵营".equals(pd.getCamp())) continue;
            double dist = owner.getLocation().distance(bp.getLocation());
            if (dist < nearestDist) { nearestDist = dist; nearest = bp; }
        }
        return nearest;
    }

    private PlayerData findFirstPlayerByRole(String roleName) {
        for (PlayerData pd : players) {
            if (pd.isAlive() && pd.getRole() != null && roleName.equals(pd.getRole().getName())) return pd;
        }
        return null;
    }

    private boolean isOnlyWolfAlive(PlayerData wolf) {
        for (PlayerData pd : players) {
            if (pd.isAlive() && "狼人阵营".equals(pd.getCamp()) && pd != wolf) return false;
        }
        return true;
    }

    private void setCooldown(PlayerData pd, String skillId, int seconds) {
        pd.setSkillCooldown(skillId, seconds);
    }

    private int getSkillCooldownTime(PlayerData pd, String skillId) {
        Role role = pd.getRole();
        if (role == null) return 10;
        String rn = role.getName();
        switch (rn) {
            case "WOLF WITCH": return "skill1".equals(skillId) ? 35 : "skill2".equals(skillId) ? 50 : 40;
            case "DIO": return "skill1".equals(skillId) ? 80 : 12;
            case "KILLER": return "skill1".equals(skillId) ? 12 : 30;
            case "CRIMSON MESSENGER": return "skill1".equals(skillId) ? 20 : "skill2".equals(skillId) ? 25 : 30;
            case "TIDE SINGER": return 20;
            case "BOMBER": return 30;
            case "COUNT": {
                if ("skill1".equals(skillId)) return 10;
                if ("skill3".equals(skillId)) return 20;
                // 技能2 = 切换形态
                return 3;
            }
            case "GAMBLER": return 0;
            case "GUARD": return 30;
        case "KNIGHT": return 40;
        case "NINJA": return 0;
        case "CORPSE HERDER": return 0;
        case "BLADE AND SOUL": return "skill3".equals(skillId) ? 20 : 0;
            case "WIZARD": return 5;
            case "ARSONIST": return 20;
            case "TIME DUKE": return 18;
            case "FOLLOWER": return 30;
            case "WIND SPIRIT": return 30;
        case "WATER DIVINATION": return 60;
        case "LANTERN BEARER": return 30;
        case "THE SURVIVOR": return 20;
        case "PELICAN": return 30;
        case "WOLF JACKAL":
        case "BUDDHIST MONK":
        case "NEUTRAL JACKAL": return 0;
            default: return 10;
        }
    }

    // ==================== GETTERS ====================

    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public String getOwnerName() { return ownerName; }
    public GameState getState() { return state; }

    public void transferOwner(PlayerData newOwner) {
        Player bp = newOwner.getBukkitPlayer();
        String newName = bp != null ? bp.getName() : newOwner.getUsername();
        this.ownerName = newName;
        newOwner.sendMessage("§6你成为了新房主！");
        broadcast("§e" + newName + " §6成为了新房主！");
    }

    public GameState getCurrentState() { return state; }
    public void addWizardEnergy(Player bp, int amount) {
        UUID uuid = bp.getUniqueId();
        int e = wizardEnergy.getOrDefault(uuid, 200);
        wizardEnergy.put(uuid, Math.min(e + amount, 500));
    }
    public int getPlayerCount() { return players.size(); }
    public List<PlayerData> getPlayers() { return new ArrayList<>(players); }
    public int getRemainingTime() { return remainingTime; }

    public void broadcast(String message) {
        for (PlayerData p : players) p.sendMessage(message);
        plugin.getLogger().info(message);
    }

    public void shutdown() {
        cancelGameTasks();
        state = GameState.LOBBY;
        remainingTime = GAME_DURATION;
        // 解散时不广播“已重置”，调用方（deleteRoom）已广播“房间已解散”
        for (PlayerData p : players) {
            restorePlayerState(p);
            p.resetGameState();
        }
        players.clear();
    }
}
