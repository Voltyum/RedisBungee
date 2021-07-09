package com.imaginarycode.minecraft.redisbungee.listeners;

import com.imaginarycode.minecraft.redisbungee.DataManager;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.RedisUtil;
import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import com.imaginarycode.minecraft.redisbungee.util.RedisCallable;
import com.imaginarycode.minecraft.redisbungee.utils.NyaUtils;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import java.util.Optional;

/**
 * This code has been created by Tux, and modified by gatogamer.
 */
public class ConnectionListener {

    @Inject
    private ProxyServer proxyServer;

    @Inject
    private Logger logger;

    @Inject
    private RedisBungee redisBungee;

    private static final TextComponent ALREADY_LOGGED_IN = Component.text("You are already logged on to this server.").color(NamedTextColor.RED).append(Component.text("\n\nIt may help to try logging in again in a few minutes.\nIf this does not resolve your issue, please contact staff.").color(NamedTextColor.GRAY));
    private static final TextComponent ONLINE_MODE_RECONNECT = Component.text("Whoops! You need to reconnect.").color(NamedTextColor.RED).append(Component.text("\n\nWe found someone online using your username. They were kicked and you may reconnect.\nIf this does not work, please contact staff.").color(NamedTextColor.GRAY));

    @Subscribe
    public void on(LoginEvent event) {
        Player proxiedPlayer = event.getPlayer();

        if (proxyServer.getConfiguration().isOnlineMode()) {
            Optional<Player> optionalPlayer = proxyServer.getPlayer(proxiedPlayer.getUsername());

            optionalPlayer.ifPresent(player -> event.setResult(ResultedEvent.ComponentResult.denied(ONLINE_MODE_RECONNECT)));
        }

        new RedisCallable<Void>(redisBungee) {
            @Override
            protected Void call(Jedis jedis) {
                for (String s : redisBungee.getServerIds()) {
                    if (jedis.sismember("proxy:" + s + ":usersOnline", proxiedPlayer.getUniqueId().toString())) {
                        event.setResult(ResultedEvent.ComponentResult.denied(ALREADY_LOGGED_IN));
                        return null;
                    }

                }

                Pipeline pipeline = jedis.pipelined();
                redisBungee.getUuidTranslator().persistInfo(event.getPlayer().getUsername(), event.getPlayer().getUniqueId(), pipeline);
                RedisUtil.createPlayer(event.getPlayer(), pipeline, false);
                // We're not publishing, the API says we only publish at PostLoginEvent time.
                pipeline.sync();

                return null;
            }
        }.run();
    }

    @Subscribe
    public void on(PostLoginEvent event) {
        NyaUtils.run(new RedisCallable<Void>(redisBungee) {
            @Override
            protected Void call(Jedis jedis) {
                jedis.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                        event.getPlayer().getUniqueId(), DataManager.DataManagerMessage.Action.JOIN,
                        new DataManager.LoginPayload(event.getPlayer().getRemoteAddress().getAddress()))));
                return null;
            }
        });
    }

    @Subscribe
    public void on(DisconnectEvent event) {
        NyaUtils.run(new RedisCallable<Void>(redisBungee) {
            @Override
            protected Void call(Jedis jedis) {
                Pipeline pipeline = jedis.pipelined();
                RedisUtil.cleanUpPlayer(event.getPlayer().getUniqueId().toString(), pipeline);
                pipeline.sync();
                return null;
            }
        });
    }

    @Subscribe
    public void on(ServerConnectedEvent event) {
        RegisteredServer oldServer = event.getPreviousServer().orElse(null);

        String name = oldServer == null ? null : oldServer.getServerInfo().getName();

        NyaUtils.run(new RedisCallable<Void>(redisBungee) {
            @Override
            protected Void call(Jedis jedis) {
                jedis.hset("player:" + event.getPlayer().getUniqueId().toString(), "server", event.getServer().getServerInfo().getName());
                jedis.publish("redisbungee-data", RedisBungee.getGson().toJson(new DataManager.DataManagerMessage<>(
                        event.getPlayer().getUniqueId(), DataManager.DataManagerMessage.Action.SERVER_CHANGE,
                        new DataManager.ServerChangePayload(event.getServer().getServerInfo().getName(), name))));
                return null;
            }
        });
    }

    @Subscribe
    public void on(ProxyPingEvent event) {
        event.setPing(event.getPing().asBuilder().onlinePlayers(redisBungee.getCount()).build());
    }

    @Subscribe
    public void on(PubSubMessageEvent event) {
        if (event.getChannel().equals("redisbungee-allservers") || event.getChannel().equals("redisbungee-" + RedisBungee.getApi().getServerId())) {
            String message = event.getMessage();
            if (message.startsWith("/"))
                message = message.substring(1);
            logger.info("Invoking command via PubSub: /" + message);
            proxyServer.getCommandManager().executeAsync(proxyServer.getConsoleCommandSource(), message);
        }
    }

}