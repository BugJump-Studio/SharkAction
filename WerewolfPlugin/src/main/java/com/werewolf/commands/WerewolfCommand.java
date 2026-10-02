package com.werewolf.commands;

import com.werewolf.auth.AdminSession;
import com.werewolf.game.GameState;
import com.werewolf.player.PlayerData;
import com.werewolf.plugin.WerewolfPlugin;
import com.werewolf.room.Room;
import com.werewolf.role.Role;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WerewolfCommand implements CommandExecutor, TabCompleter {

    private final WerewolfPlugin plugin;

    public WerewolfCommand(WerewolfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                // 有房间 -> 只打开自己所在房间的菜单; 没有房间 -> 打开主菜单
                if (plugin.getRoomManager().getRoomByPlayer(p) != null)
                    plugin.getRoomMenuListener().openRoomMenu(p);
                else
                    plugin.getRoomMenuListener().openMainMenu(p);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
            case "join":
                handleJoin(sender);
                break;
            case "leave":
                handleLeave(sender);
                break;
            case "login":
                handleLogin(sender, args);
                break;
            case "logout":
                handleLogout(sender);
                break;
            case "start":
                handleStart(sender);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "players":
                handlePlayers(sender);
                break;
            case "skill":
                handleSkill(sender, args);
                break;
            case "admin":
                handleAdmin(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "register":
                handleRegister(sender, args);
                break;
            default:
                sender.sendMessage("§c未知指令。使用 /ww help 查看帮助。");
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6========== 鲨鱼行动指令帮助 ==========");
        sender.sendMessage("§e/ww §7- 打开房间主菜单");
        sender.sendMessage("§e/ww join §7- 浏览房间列表");
        sender.sendMessage("§e/ww leave §7- 离开当前房间");
        sender.sendMessage("§e/ww status §7- 查看当前房间状态");
        sender.sendMessage("§e/ww players §7- 查看房间玩家列表");
        sender.sendMessage("§e/ww skill <1|2|3|4> §7- 使用技能");
        sender.sendMessage("§e/ww login <密码> §7- 管理员登录");
        sender.sendMessage("§e/ww logout §7- 登出");

        if (sender.hasPermission("werewolf.admin")) {
            sender.sendMessage("§c/ww start §7- 开始游戏（房主用）");
            sender.sendMessage("§c/ww stop §7- 停止游戏");
            sender.sendMessage("§c/ww admin <add|remove|list> §7- 管理管理员");
            sender.sendMessage("§c/ww reload §7- 重载配置");
        }
        sender.sendMessage("§6============================================");
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("§c只有玩家可以执行此指令！"); return; }
        Player player = (Player) sender;
        Room currentRoom = plugin.getRoomManager().getRoomByPlayer(player);
        if (currentRoom != null) { player.sendMessage("§c你已经在一个房间中！请先 /ww leave"); return; }
        plugin.getRoomMenuListener().openMainMenu(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("§c只有玩家可以执行此指令！"); return; }
        Player player = (Player) sender;
        Room room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room == null) { player.sendMessage("§c你没有加入任何房间！"); return; }
        plugin.getRoomManager().leaveRoom(player);
        player.sendMessage("§a你已离开房间！");
    }

    private void handleLogin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以执行此指令！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /ww login <密码>");
            return;
        }

        Player player = (Player) sender;
        String username = player.getName();
        String password = args[1];

        if (!plugin.getAuthManager().isAdmin(username)) {
            player.sendMessage("§c你不是管理员！");
            return;
        }

        if (plugin.getAuthManager().isLoggedIn(player)) {
            player.sendMessage("§c你已经登录了！");
            return;
        }

        if (plugin.getAuthManager().login(username, password)) {
            player.sendMessage("§a登录成功！");
            AdminSession.PermissionLevel level = plugin.getAuthManager().getPermissionLevel(username);
            player.sendMessage("§e你的权限等级: " + level.getName());
        } else {
            player.sendMessage("§c密码错误！");
        }
    }

    private void handleLogout(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以执行此指令！");
            return;
        }

        Player player = (Player) sender;
        String username = player.getName();

        if (!plugin.getAuthManager().isLoggedIn(player)) {
            player.sendMessage("§c你还没有登录！");
            return;
        }

        plugin.getAuthManager().logout(username);
        player.sendMessage("§a已登出！");
    }

    private void handleStart(CommandSender sender) {
        if (!checkAdminPermission(sender)) return;
        if (!(sender instanceof Player)) { sender.sendMessage("§c只有玩家可以执行！"); return; }
        Player player = (Player) sender;
        Room room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room == null) { sender.sendMessage("§c你不在任何房间中！"); return; }
        if (!room.getOwnerName().equalsIgnoreCase(player.getName())) { sender.sendMessage("§c只有房主可以开始游戏！"); return; }
        if (room.getCurrentState() != GameState.LOBBY) { sender.sendMessage("§c游戏不在等待状态，无法开始！"); return; }
        int playerCount = room.getPlayerCount();
        int minPlayers = plugin.getConfigManager().getConfig().getInt("game.min-players", 2);
        if (playerCount < minPlayers) { sender.sendMessage("§c玩家数量不足！需要至少 " + minPlayers + " 名玩家。"); return; }
        room.startGame();
        sender.sendMessage("§a游戏开始！");
    }

    private void handleStop(CommandSender sender) {
        if (!checkAdminPermission(sender)) return;
        if (!(sender instanceof Player)) { sender.sendMessage("§c只有玩家可以执行！"); return; }
        Player player = (Player) sender;
        Room room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room == null) { sender.sendMessage("§c你不在任何房间中！"); return; }
        if (room.getCurrentState() == GameState.LOBBY) { sender.sendMessage("§c游戏尚未开始！"); return; }
        room.stopGame();
        sender.sendMessage("§a游戏已停止！");
    }

    private void handleStatus(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("§6当前没有游戏进行中。"); return; }
        Player player = (Player) sender;
        Room room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room == null) { sender.sendMessage("§c你不在任何房间中！"); return; }
        GameState state = room.getCurrentState();
        sender.sendMessage("§6========== 游戏状态 ==========");
        sender.sendMessage("§e房间: " + room.getRoomName());
        sender.sendMessage("§e当前阶段: " + state.getDisplayName());
        sender.sendMessage("§e玩家数量: " + room.getPlayerCount());
        if (state == GameState.PLAYING) {
            sender.sendMessage("§e剩余时间: §c" + formatTime(room.getRemainingTime()));
            int aliveCount = 0;
            for (PlayerData p : room.getPlayers()) if (p.isAlive()) aliveCount++;
            sender.sendMessage("§e存活玩家: " + aliveCount);
        }
        sender.sendMessage("§6==============================");
    }

    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private void handlePlayers(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage("§c只有玩家可以执行此指令！"); return; }
        Player player = (Player) sender;
        Room room = plugin.getRoomManager().getRoomByPlayer(player);
        if (room == null) { sender.sendMessage("§c你不在任何房间中！"); return; }

        List<PlayerData> players = room.getPlayers();
        boolean playing = room.getCurrentState() == GameState.PLAYING;
        sender.sendMessage("§6========== 房间玩家 (" + players.size() + ") ==========");
        for (PlayerData p : players) {
            String status = playing ? (p.isAlive() ? "§a[存活]" : "§c[死亡]") : "§7[等待中]";
            sender.sendMessage(status + " §e" + p.getUsername());
        }
        sender.sendMessage("§6==============================");
    }

    private void handleSkill(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以执行此指令！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /ww skill <1|2>");
            return;
        }

        Player player = (Player) sender;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        if (!playerData.isInGame()) {
            player.sendMessage("§c你没有加入游戏！");
            return;
        }

        String skillNum = args[1];
        if (!skillNum.equals("1") && !skillNum.equals("2")) {
            player.sendMessage("§c技能编号必须是 1 或 2");
            return;
        }

        Role role = playerData.getRole();
        if (role == null) {
            player.sendMessage("§c你没有身份！");
            return;
        }

        if (skillNum.equals("1")) {
            if (role.hasSkill1()) {
                player.sendMessage("§a使用了 §e" + role.getSkill1Name());
            } else {
                player.sendMessage("§c该身份没有一技能！");
            }
        } else {
            if (role.hasSkill2()) {
                player.sendMessage("§a使用了 §e" + role.getSkill2Name());
            } else {
                player.sendMessage("§c该身份没有二技能！");
            }
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("werewolf.admin")) {
            sender.sendMessage("§c你没有权限执行此指令！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /ww admin <add|remove|list> [用户名] [密码] [owner|admin]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 5) {
                    sender.sendMessage("§c用法: /ww admin add <用户名> <密码> <owner|admin>");
                    return;
                }
                String newAdmin = args[2];
                String password = args[3];
                AdminSession.PermissionLevel level = AdminSession.PermissionLevel.fromString(args[4]);

                if (level == null) {
                    sender.sendMessage("§c权限等级必须是 owner 或 admin！");
                    return;
                }

                plugin.getAuthManager().setAdmin(newAdmin, password, level);
                sender.sendMessage("§a已添加管理员: " + newAdmin + " (" + level.getName() + ")");
                break;

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /ww admin remove <用户名>");
                    return;
                }
                String removeAdmin = args[2];

                if (!plugin.getAuthManager().isAdmin(removeAdmin)) {
                    sender.sendMessage("§c该用户不是管理员！");
                    return;
                }

                plugin.getAuthManager().removeAdmin(removeAdmin);
                sender.sendMessage("§a已移除管理员: " + removeAdmin);
                break;

            case "list":
                sender.sendMessage("§6========== 管理员列表 ==========");
                for (String admin : plugin.getAuthManager().getAdmins()) {
                    AdminSession.PermissionLevel adminLevel = plugin.getAuthManager().getPermissionLevel(admin);
                    sender.sendMessage("§e" + admin + " §7(" + adminLevel.getName() + ")");
                }
                sender.sendMessage("§6================================");
                break;

            default:
                sender.sendMessage("§c未知操作。可用操作: add, remove, list");
                break;
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("werewolf.admin")) {
            sender.sendMessage("§c你没有权限执行此指令！");
            return;
        }

        plugin.getConfigManager().reloadConfigs();
        plugin.getRoleManager().loadRoles();
        sender.sendMessage("§a配置已重载！");
    }

    private void handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以执行此指令！");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /ww register <密码>");
            return;
        }

        Player player = (Player) sender;
        String username = player.getName();
        String password = args[1];

        // 检查是否是管理员
        if (!plugin.getAuthManager().isAdmin(username)) {
            player.sendMessage("§c你不是管理员，无法注册！");
            return;
        }

        // 检查是否已设置密码
        if (plugin.getAuthManager().hasPassword(username)) {
            player.sendMessage("§c你已经注册过了，请使用 /ww login 登录");
            return;
        }

        // 注册密码
        plugin.getAuthManager().setPassword(username, password);
        player.sendMessage("§a§l注册成功！");
        player.sendMessage("§e请使用 /ww login <密码> 登录");
    }

    private boolean checkAdminPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // 控制台有权限
        }

        Player player = (Player) sender;
        if (!plugin.getAuthManager().isLoggedIn(player) && !player.hasPermission("werewolf.admin")) {
            player.sendMessage("§c你没有权限执行此指令！请先登录。");
            return false;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("help", "join", "leave", "login", "logout",
                "status", "players", "skill"));

            if (sender.hasPermission("werewolf.admin") ||
                (sender instanceof Player && plugin.getAuthManager().isLoggedIn((Player) sender))) {
                completions.addAll(Arrays.asList("start", "stop", "admin", "reload"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "admin":
                    completions.addAll(Arrays.asList("add", "remove", "list"));
                    break;
                case "skill":
                    completions.addAll(Arrays.asList("1", "2"));
                    break;
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("add")) {
            completions.addAll(Arrays.asList("owner", "admin"));
        }

        return completions;
    }
}
