package com.imaginarycode.minecraft.redisbungee.listeners;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
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
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.inject.Inject;
import java.util.*;

/**
 * This code has been created by Tux, and modified by gatogamer and YoSoyVillaa.
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

    @Subscribe
    public void on(PluginMessageEvent event) {
        if (event.getIdentifier().getId().equals("redisbungee:toproxy") && event.getSource() instanceof ServerConnection) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            final byte[] data = Arrays.copyOf(event.getData(), event.getData().length);
            redisBungee.getProxyServer().getScheduler().buildTask(redisBungee, () -> {
                ByteArrayDataInput in = ByteStreams.newDataInput(data);
                String subChannel = in.readUTF();

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                String type;

                switch (subChannel) {
                    case "PlayerList":
                        out.writeUTF("PlayerList");
                        Set<UUID> original = Collections.emptySet();
                        type = in.readUTF();
                        if (type.equals("ALL")) {
                            out.writeUTF("ALL");
                            original = redisBungee.getPlayers();
                        } else {
                            try {
                                original = RedisBungee.getApi().getPlayersOnServer(type);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        Set<String> players = new HashSet<>();
                        for (UUID uuid : original)
                            players.add(redisBungee.getUuidTranslator().getNameFromUuid(uuid, false));
                        out.writeUTF(Joiner.on(',').join(players));
                        break;
                    case "PlayerCount":
                        out.writeUTF("PlayerCount");

                        type = in.readUTF();
                        if (type.equals("ALL")) {
                            out.writeUTF("ALL");

                            out.writeInt(redisBungee.getCount());
                        } else {

                            out.writeUTF(type);
                            try {
                                out.writeInt(RedisBungee.getApi().getPlayersOnServer(type).size());
                            } catch (IllegalArgumentException e) {
                                out.writeInt(0);
                            }
                        }
                        Optional<ServerConnection> server = ((ServerConnection) event.getSource()).getPlayer().getCurrentServer();
                        if (!server.isPresent()){
                            throw new IllegalStateException("No server to send data to");
                        }
                        server.get().sendPluginMessage(MinecraftChannelIdentifier.from("redisbungee:tospigot"), out.toByteArray());
                        break;
                    case "LastOnline":
                        String user = in.readUTF();
                        out.writeUTF("LastOnline");
                        out.writeUTF(user);
                        out.writeLong(RedisBungee.getApi().getLastOnline(Objects.requireNonNull(redisBungee.getUuidTranslator().getTranslatedUuid(user, true))));
                        break;
                    case "ServerPlayers":
                        String type1 = in.readUTF();
                        out.writeUTF("ServerPlayers");
                        Multimap<String, UUID> multimap = RedisBungee.getApi().getServerToPlayers();

                        boolean includesUsers;

                        switch (type1) {
                            case "COUNT":
                                includesUsers = false;
                                break;
                            case "PLAYERS":
                                includesUsers = true;
                                break;
                            default:
                                // TODO: Should I raise an error?
                                return;
                        }

                        out.writeUTF(type1);

                        if (includesUsers) {
                            Multimap<String, String> human = HashMultimap.create();
                            for (Map.Entry<String, UUID> entry : multimap.entries()) {
                                human.put(entry.getKey(), redisBungee.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
                            }
                            serializeMultimap(human, true, out);
                        } else {
                            serializeMultiset(multimap.keys(), out);
                        }
                        break;
                    case "Proxy":
                        out.writeUTF("Proxy");
                        out.writeUTF(RedisBungee.getConfiguration().getServerId());
                        break;
                    case "PlayerProxy":
                        String username = in.readUTF();
                        out.writeUTF("PlayerProxy");
                        out.writeUTF(username);
                        out.writeUTF(RedisBungee.getApi().getProxy(Objects.requireNonNull(redisBungee.getUuidTranslator().getTranslatedUuid(username, true))));
                        break;
                    default:
                }
            }).schedule();
        }

    }

    private void serializeMultiset(Multiset<String> collection, ByteArrayDataOutput output) {
        output.writeInt(collection.elementSet().size());
        for (Multiset.Entry<String> entry : collection.entrySet()) {
            output.writeUTF(entry.getElement());
            output.writeInt(entry.getCount());
        }
    }

    private void serializeMultimap(Multimap<String, String> collection, boolean includeNames, ByteArrayDataOutput output) {
        output.writeInt(collection.keySet().size());
        for (Map.Entry<String, Collection<String>> entry : collection.asMap().entrySet()) {
            output.writeUTF(entry.getKey());
            if (includeNames) {
                serializeCollection(entry.getValue(), output);
            } else {
                output.writeInt(entry.getValue().size());
            }
        }
    }

    private void serializeCollection(Collection<?> collection, ByteArrayDataOutput output) {
        output.writeInt(collection.size());
        for (Object o : collection) {
            output.writeUTF(o.toString());
        }
    }
}