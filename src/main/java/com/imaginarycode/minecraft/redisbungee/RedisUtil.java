package com.imaginarycode.minecraft.redisbungee;

import com.google.common.annotations.VisibleForTesting;
import com.velocitypowered.api.proxy.Player;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@VisibleForTesting
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RedisUtil {
    public static void createPlayer(Player player, Pipeline pipeline, boolean fireEvent) {
        createPlayer2(player, pipeline, fireEvent);
        player.getCurrentServer().ifPresent(
                serverConnection -> pipeline.hset("player:" + player.getUniqueId().toString(), "server", serverConnection.getServerInfo().getName())
        );
    }

    protected static void createPlayer2(Player connection, Pipeline pipeline, boolean fireEvent) {
        Map<String, String> playerData = new HashMap<>(4);
        playerData.put("online", "0");
        playerData.put("ip", connection.getRemoteAddress().getAddress().getHostAddress());
        playerData.put("proxy", RedisBungee.getConfiguration().getServerId());

        pipeline.sadd("proxy:" + RedisBungee.getApi().getServerId() + ":usersOnline", connection.getUniqueId().toString());
        pipeline.hmset("player:" + connection.getUniqueId().toString(), playerData);

        if (fireEvent) {
            pipeline.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                    connection.getUniqueId(), DataManager.DataManagerMessage.Action.JOIN,
                    new DataManager.LoginPayload(connection.getRemoteAddress().getAddress()))));
        }
    }

    public static void cleanUpPlayer(String player, Jedis rsc) {
        rsc.srem("proxy:" + RedisBungee.getApi().getServerId() + ":usersOnline", player);
        rsc.hdel("player:" + player, "server",  "ip", "proxy");
        long timestamp = System.currentTimeMillis();
        rsc.hset("player:" + player, "online", String.valueOf(timestamp));
        rsc.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                UUID.fromString(player), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(timestamp))));
    }

    public static void cleanUpPlayer(String player, Pipeline rsc) {
        rsc.srem("proxy:" + RedisBungee.getApi().getServerId() + ":usersOnline", player);
        rsc.hdel("player:" + player, "server", "ip", "proxy");
        long timestamp = System.currentTimeMillis();
        rsc.hset("player:" + player, "online", String.valueOf(timestamp));
        rsc.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                UUID.fromString(player), DataManager.DataManagerMessage.Action.LEAVE,
                new DataManager.LogoutPayload(timestamp))));
    }

    public static boolean canUseLua(String redisVersion) {
        // Need to use >=2.6 to use Lua optimizations.
        String[] args = redisVersion.split("\\.");

        if (args.length < 2) {
            return false;
        }

        int major = Integer.parseInt(args[0]);
        int minor = Integer.parseInt(args[1]);

        return major >= 3 || (major == 2 && minor >= 6);
    }
}
