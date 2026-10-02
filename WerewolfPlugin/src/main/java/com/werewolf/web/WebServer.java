package com.werewolf.web;

import com.google.gson.Gson;
import com.werewolf.plugin.WerewolfPlugin;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

public class WebServer {

    private final WerewolfPlugin plugin;
    private final Gson gson;
    private final int port;
    private final String host;

    public WebServer(WerewolfPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.port = plugin.getConfigManager().getConfig().getInt("web.port", 8080);
        this.host = plugin.getConfigManager().getConfig().getString("web.host", "0.0.0.0");
    }

    public void start() {
        Spark.ipAddress(host);
        Spark.port(port);

        // CORS
        before((req, res) -> {
            String origin = req.headers("Origin");
            if (origin != null) {
                res.header("Access-Control-Allow-Origin", origin);
            } else {
                String scheme = req.scheme() != null ? req.scheme() : "http";
                String host = req.host() != null ? req.host() : "localhost";
                res.header("Access-Control-Allow-Origin", scheme + "://" + host);
            }
            res.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            res.header("Vary", "Origin");
            res.header("Cache-Control", "no-cache, no-store, must-revalidate");
            res.header("Pragma", "no-cache");
            res.header("Expires", "0");
        });

        // OPTIONS
        options("/*", (req, res) -> {
            res.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            return "OK";
        });

        // 路由
        setupRoutes();

        plugin.getLogger().info("Web服务器已启动，监听端口: " + port);
    }

    public void stop() {
        try {
            Spark.stop();
        } catch (Exception e) {
            plugin.getLogger().info("Web服务器已关闭");
        }
    }

    private void setupRoutes() {
        path("/api", () -> {
            // 游戏状态
            get("/game/status", this::getGameStatus);

            // 玩家列表
            get("/game/players", this::getPlayers);
        });
    }

    private Object getGameStatus(Request req, Response res) {
        res.type("application/json");

        Map<String, Object> status = new HashMap<>();
        status.put("state", plugin.getGameManager().getCurrentState().name());
        status.put("stateDisplay", plugin.getGameManager().getCurrentState().getDisplayName());
        status.put("playerCount", plugin.getGameManager().getPlayerCount());
        status.put("remainingTime", plugin.getGameManager().getRemainingTime());

        return gson.toJson(Map.of("success", true, "status", status));
    }

    private Object getPlayers(Request req, Response res) {
        res.type("application/json");

        return gson.toJson(Map.of("success", true, "players", plugin.getPlayerDataManager().getPlayersInGame()));
    }
}
