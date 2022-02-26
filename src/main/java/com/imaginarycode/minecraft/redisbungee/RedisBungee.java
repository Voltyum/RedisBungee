package com.imaginarycode.minecraft.redisbungee;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.imaginarycode.minecraft.redisbungee.commands.*;
import com.imaginarycode.minecraft.redisbungee.config.NyaConfiguration;
import com.imaginarycode.minecraft.redisbungee.events.PubSubMessageEvent;
import com.imaginarycode.minecraft.redisbungee.listeners.ConnectionListener;
import com.imaginarycode.minecraft.redisbungee.util.IOUtil;
import com.imaginarycode.minecraft.redisbungee.util.LuaManager;
import com.imaginarycode.minecraft.redisbungee.util.uuid.NameFetcher;
import com.imaginarycode.minecraft.redisbungee.util.uuid.UUIDFetcher;
import com.imaginarycode.minecraft.redisbungee.util.uuid.UUIDTranslator;
import com.imaginarycode.minecraft.redisbungee.utils.NyaUtils;
import com.moandjiezana.toml.Toml;
import com.squareup.okhttp.Dispatcher;
import com.squareup.okhttp.OkHttpClient;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.LegacyChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.slf4j.Logger;
import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisConnectionException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This code has been created by Tux, and modified by gatogamer.
 */
@Plugin(id = "redisbungee", name = "RedisBungee", version = "1.0-SNAPSHOT", description = "Connecting multiple proxies using Redis", authors = {"Tux", "gatogamer_"})
@Getter
public class RedisBungee {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataFolder;

    private NyaConfiguration nyaConfiguration;

    @Getter
    private static Gson gson = new Gson();
    @Getter
    private static RedisBungeeAPI api;
    @Getter
    private static PubSubListener pubSubListener = null;
    @Getter
    private JedisPool pool;
    @Getter
    private UUIDTranslator uuidTranslator;
    @Getter()
    private static RedisBungeeConfiguration configuration;
    @Getter
    private DataManager dataManager;
    @Getter
    private static OkHttpClient httpClient;
    private volatile List<String> serverIds;
    private final AtomicInteger nagAboutServers = new AtomicInteger();
    private final AtomicInteger globalPlayerCount = new AtomicInteger();
    private ScheduledTask integrityCheck;
    private ScheduledTask heartbeatTask;
    private boolean usingLua;
    private LuaManager.Script serverToPlayersScript;
    private LuaManager.Script getPlayerCountScript;

    @Inject
    private ConnectionListener connectionListener;

    private static final Object SERVER_TO_PLAYERS_KEY = new Object();
    private final Cache<Object, Multimap<String, UUID>> serverToPlayersCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .build();

    @Inject
    public RedisBungee(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataFolder) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Getter
    private ListeningExecutorService executorService;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        executorService = MoreExecutors.listeningDecorator(new ThreadPoolExecutor(24, 24, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100)));

        try {
            loadConfig();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load/save config", e);
        } catch (JedisConnectionException e) {
            throw new RuntimeException("Unable to connect to your Redis server!", e);
        }
        if (pool != null) {
            try (Jedis tmpRsc = pool.getResource()) {
                // This is more portable than INFO <section>
                String info = tmpRsc.info();
                for (String s : info.split("\r\n")) {
                    if (s.startsWith("redis_version:")) {
                        String version = s.split(":")[1];
                        if (!(usingLua = RedisUtil.canUseLua(version))) {
                            logger.warn("Your version of Redis (" + version + ") is not at least version 2.6. RedisBungee requires a newer version of Redis.");
                            throw new RuntimeException("Unsupported Redis version detected");
                        } else {
                            LuaManager manager = new LuaManager(this);
                            serverToPlayersScript = manager.createScript(IOUtil.readInputStreamAsString(getResourceAsStream("lua/server_to_players.lua")));
                            getPlayerCountScript = manager.createScript(IOUtil.readInputStreamAsString(getResourceAsStream("lua/get_player_count.lua")));
                        }
                        break;
                    }
                }

                tmpRsc.hset("heartbeats", configuration.getServerId(), tmpRsc.time().get(0));

                long uuidCacheSize = tmpRsc.hlen("uuid-cache");
                if (uuidCacheSize > 750000) {
                    logger.info("Looks like you have a really big UUID cache! Run https://www.spigotmc.org/resources/redisbungeecleaner.8505/ as soon as possible.");
                }
            }
            serverIds = getCurrentServerIds(true, false);
            uuidTranslator = new UUIDTranslator(this);
            heartbeatTask = makeThisRunAsync(() -> {
                try (Jedis rsc = pool.getResource()) {
                    long redisTime = getRedisTime(rsc.time());
                    rsc.hset("heartbeats", configuration.getServerId(), String.valueOf(redisTime));
                } catch (JedisConnectionException e) {
                    // Redis server has disappeared!
                    logger.error("Unable to update heartbeat - did your Redis server go away?", e);
                    return;
                }
                try {
                    serverIds = getCurrentServerIds(true, false);
                    globalPlayerCount.set(getCurrentCount());
                } catch (Throwable e) {
                    logger.error("Unable to update data - did your Redis server go away?", e);
                }
            }, 0L, 3L, TimeUnit.SECONDS);
            dataManager = new DataManager(this);
            if (configuration.isRegisterBungeeCommands()) {
                proxyServer.getCommandManager().register("find", new FindCommand(this), "rfind");
                proxyServer.getCommandManager().register("glist", new GlobalListCommand(this), "globallist");
                proxyServer.getCommandManager().register("ip", new IpCommand(this), "playerip", "rip", "rplayerip");
            }
            proxyServer.getCommandManager().register("lastseen", new LastSeenCommand(this), "rlastseen");
            proxyServer.getCommandManager().register("serverid", new ProxyCommand(), "rserverid", "proxy");
            proxyServer.getCommandManager().register("plist", new ProxyListCommand(this), "rplist", "proxylist");
            proxyServer.getCommandManager().register("sendtoall", new SendToAllCommand(), "rsendtoall");
            proxyServer.getCommandManager().register("serverids", new ServerIdsCommand(), "rserverids");

            api = new RedisBungeeAPI(this);
            proxyServer.getEventManager().register(this, connectionListener);
            proxyServer.getEventManager().register(this, dataManager);
            pubSubListener = new PubSubListener();
            NyaUtils.run(pubSubListener);
            integrityCheck = makeThisRunAsync(() -> {
                try (Jedis tmpRsc = pool.getResource()) {
                    Set<String> players = getLocalPlayersAsUuidStrings();
                    Set<String> playersInRedis = tmpRsc.smembers("proxy:" + configuration.getServerId() + ":usersOnline");
                    List<String> lagged = getCurrentServerIds(false, true);

                    // Clean up lagged players.
                    for (String s : lagged) {
                        Set<String> laggedPlayers = tmpRsc.smembers("proxy:" + s + ":usersOnline");
                        tmpRsc.del("proxy:" + s + ":usersOnline");
                        if (!laggedPlayers.isEmpty()) {
                            logger.info("Cleaning up lagged proxy " + s + " (" + laggedPlayers.size() + " players)...");
                            for (String laggedPlayer : laggedPlayers) {
                                RedisUtil.cleanUpPlayer(laggedPlayer, tmpRsc);
                            }
                        }
                    }

                    Set<String> absentLocally = new HashSet<>(playersInRedis);
                    absentLocally.removeAll(players);
                    Set<String> absentInRedis = new HashSet<>(players);
                    absentInRedis.removeAll(playersInRedis);

                    for (String member : absentLocally) {
                        boolean found = false;
                        for (String proxyId : getServerIds()) {
                            if (proxyId.equals(configuration.getServerId())) continue;
                            if (tmpRsc.sismember("proxy:" + proxyId + ":usersOnline", member)) {
                                // Just clean up the set.
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            RedisUtil.cleanUpPlayer(member, tmpRsc);
                            logger.warn("Player found in set that was not found locally and globally: " + member);
                        } else {
                            tmpRsc.srem("proxy:" + configuration.getServerId() + ":usersOnline", member);
                            logger.warn("Player found in set that was not found locally, but is on another proxy: " + member);
                        }
                    }

                    Pipeline pipeline = tmpRsc.pipelined();

                    for (String player : absentInRedis) {
                        // Player not online according to Redis but not BungeeCord.
                        logger.warn("Player " + player + " is on the proxy but not in Redis.");

                        Optional<Player> optionalPlayer = proxyServer.getPlayer(UUID.fromString(player));

                        optionalPlayer.ifPresent(proxiedPlayer -> RedisUtil.createPlayer(proxiedPlayer, pipeline, true));

                    }

                    pipeline.sync();
                } catch (Throwable e) {
                    logger.warn("Unable to fix up stored player data", e);
                }

            }, 0, 1, TimeUnit.MINUTES);
        }

        proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.from("redisbungee:toproxy"));
        proxyServer.getChannelRegistrar().register(MinecraftChannelIdentifier.create("redisbungee", "tospigot"));
    }

    @Subscribe
    public void onProxyShutdowning(ProxyShutdownEvent event) {
        if (pool != null) {
            // Poison the PubSub listener
            pubSubListener.poison();
            integrityCheck.cancel();
            heartbeatTask.cancel();
            proxyServer.getEventManager().unregisterListeners(this);

            try (Jedis tmpRsc = pool.getResource()) {
                tmpRsc.hdel("heartbeats", configuration.getServerId());
                if (tmpRsc.scard("proxy:" + configuration.getServerId() + ":usersOnline") > 0) {
                    Set<String> players = tmpRsc.smembers("proxy:" + configuration.getServerId() + ":usersOnline");
                    for (String member : players)
                        RedisUtil.cleanUpPlayer(member, tmpRsc);
                }
            }

            pool.close();
        }
    }

    private List<String> getCurrentServerIds(boolean nag, boolean lagged) {
        try (Jedis jedis = pool.getResource()) {
            long time = getRedisTime(jedis.time());
            int nagTime = 0;
            if (nag) {
                nagTime = nagAboutServers.decrementAndGet();
                if (nagTime <= 0) {
                    nagAboutServers.set(10);
                }
            }
            ImmutableList.Builder<String> servers = ImmutableList.builder();
            Map<String, String> heartbeats = jedis.hgetAll("heartbeats");
            for (Map.Entry<String, String> entry : heartbeats.entrySet()) {
                try {
                    long stamp = Long.parseLong(entry.getValue());
                    if (lagged ? time >= stamp + 30 : time <= stamp + 30)
                        servers.add(entry.getKey());
                    else if (nag && nagTime <= 0) {
                        logger.warn(entry.getKey() + " is " + (time - stamp) + " seconds behind! (Time not synchronized or server down?)");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return servers.build();
        } catch (JedisConnectionException e) {
            logger.warn("Unable to fetch server IDs", e);
            return Collections.singletonList(configuration.getServerId());
        }
    }

    public Set<UUID> getPlayersOnProxy(String server) {
        Preconditions.checkArgument(getServerIds().contains(server), server + " is not a valid proxy ID");
        try (Jedis jedis = pool.getResource()) {
            Set<String> users = jedis.smembers("proxy:" + server + ":usersOnline");
            ImmutableSet.Builder<UUID> builder = ImmutableSet.builder();
            for (String user : users) {
                builder.add(UUID.fromString(user));
            }
            return builder.build();
        }
    }

    final Multimap<String, UUID> serversToPlayers() {
        try {
            return serverToPlayersCache.get(SERVER_TO_PLAYERS_KEY, new Callable<Multimap<String, UUID>>() {
                @Override
                public Multimap<String, UUID> call() throws Exception {
                    Collection<String> data = (Collection<String>) serverToPlayersScript.eval(ImmutableList.<String>of(), getServerIds());

                    ImmutableMultimap.Builder<String, UUID> builder = ImmutableMultimap.builder();
                    String key = null;
                    for (String s : data) {
                        if (key == null) {
                            key = s;
                            continue;
                        }

                        builder.put(key, UUID.fromString(s));
                        key = null;
                    }

                    return builder.build();
                }
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private long getRedisTime(List<String> timeRes) {
        return Long.parseLong(timeRes.get(0));
    }

    public final List<String> getServerIds() {
        return serverIds;
    }

    public final Set<UUID> getPlayers() {
        ImmutableSet.Builder<UUID> setBuilder = ImmutableSet.builder();
        if (pool != null) {
            try (Jedis rsc = pool.getResource()) {
                List<String> keys = new ArrayList<>();
                for (String i : getServerIds()) {
                    keys.add("proxy:" + i + ":usersOnline");
                }
                if (!keys.isEmpty()) {
                    Set<String> users = rsc.sunion(keys.toArray(new String[keys.size()]));
                    if (users != null && !users.isEmpty()) {
                        for (String user : users) {
                            try {
                                setBuilder = setBuilder.add(UUID.fromString(user));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                }
            } catch (JedisConnectionException e) {
                // Redis server has disappeared!
                logger.error("Unable to get connection from pool - did your Redis server go away?", e);
                throw new RuntimeException("Unable to get all players online", e);
            }
        }
        return setBuilder.build();
    }

    public final int getCount() {
        return globalPlayerCount.get();
    }


    final int getCurrentCount() {
        Long count = (Long) getPlayerCountScript.eval(ImmutableList.<String>of(), ImmutableList.<String>of());
        return count.intValue();
    }

    private Set<String> getLocalPlayersAsUuidStrings() {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (Player player : proxyServer.getAllPlayers()) {
            builder.add(player.getUniqueId().toString());
        }
        return builder.build();
    }


    final void sendProxyCommand(@NonNull String proxyId, @NonNull String command) {
        checkArgument(getServerIds().contains(proxyId) || proxyId.equals("allservers"), "proxyId is invalid");
        sendChannelMessage("redisbungee-" + proxyId, command);
    }

    final void sendChannelMessage(String channel, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        } catch (JedisConnectionException e) {
            // Redis server has disappeared!
            logger.warn("Unable to get connection from pool - did your Redis server go away?", e);
            throw new RuntimeException("Unable to publish channel message", e);
        }
    }


    private void loadConfig() throws IOException, JedisConnectionException {
        if (!dataFolder.toFile().exists()) {
            dataFolder.toFile().mkdir();
        }

        nyaConfiguration = new NyaConfiguration(proxyServer, logger, "config", dataFolder.toFile());

        Toml toml = nyaConfiguration.getToml();

        final String redisServer = toml.getString("redis-server", "localhost");
        final int redisPort = Math.toIntExact(toml.getLong("redis-port", 6379L));
        String redisPassword = toml.getString("redis-password", "");
        String serverId = toml.getString("server-id");

        if (redisPassword != null && (redisPassword.isEmpty() || redisPassword.equals("none"))) {
            redisPassword = "";
        }

        // Configuration sanity checks.
        if (serverId == null || serverId.isEmpty()) {
            throw new RuntimeException("server-id is not specified in the configuration or is empty");
        }

        if (redisServer != null && !redisServer.isEmpty()) {
            final String finalRedisPassword = redisPassword;
            FutureTask<JedisPool> task = new FutureTask<>(() -> {
                // Create the pool...
                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(Math.toIntExact(toml.getLong("max-redis-connections", 8L)));
                return new JedisPool(config, redisServer, redisPort, 0, finalRedisPassword);
            });

            NyaUtils.run(task);

            try {
                pool = task.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Unable to create Redis pool", e);
            }

            // Test the connection
            try (Jedis rsc = pool.getResource()) {
                rsc.ping();
                // If that worked, now we can check for an existing, alive Bungee:
                File crashFile = new File(dataFolder.toFile(), "restarted_from_crash.txt");
                if (crashFile.exists()) {
                    crashFile.delete();
                } else if (rsc.hexists("heartbeats", serverId)) {
                    try {
                        long value = Long.parseLong(rsc.hget("heartbeats", serverId));
                        long redisTime = getRedisTime(rsc.time());
                        if (redisTime < value + 20) {
                            logger.warn("You have launched a possible impostor BungeeCord instance. Another instance is already running.");
                            logger.warn("For data consistency reasons, RedisBungee will now disable itself.");
                            logger.warn("If this instance is coming up from a crash, create a file in your RedisBungee plugins directory with the name 'restarted_from_crash.txt' and RedisBungee will not perform this check.");
                            throw new RuntimeException("Possible impostor instance!");
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }

                FutureTask<Void> task2 = new FutureTask<>(() -> {
                    httpClient = new OkHttpClient();
                    Dispatcher dispatcher = new Dispatcher(getExecutorService());
                    httpClient.setDispatcher(dispatcher);
                    NameFetcher.setHttpClient(httpClient);
                    UUIDFetcher.setHttpClient(httpClient);
                    RedisBungee.configuration = new RedisBungeeConfiguration(RedisBungee.this.getPool(), toml);
                    return null;
                });

                NyaUtils.run(task2);

                try {
                    task2.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException("Unable to create HTTP client", e);
                }

                logger.info("Successfully connected to Redis.");
            } catch (JedisConnectionException e) {
                pool.destroy();
                pool = null;
                throw e;
            }
        } else {
            throw new RuntimeException("No redis server specified!");
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    class PubSubListener implements Runnable {
        private JedisPubSubHandler jpsh;

        private Set<String> addedChannels = new HashSet<>();

        @Override
        public void run() {
            boolean broken = false;
            try (Jedis rsc = pool.getResource()) {
                try {
                    jpsh = new JedisPubSubHandler();
                    addedChannels.add("redisbungee-" + configuration.getServerId());
                    addedChannels.add("redisbungee-allservers");
                    addedChannels.add("redisbungee-data");
                    rsc.subscribe(jpsh, addedChannels.toArray(new String[0]));
                } catch (Exception e) {
                    // FIXME: Extremely ugly hack
                    // Attempt to unsubscribe this instance and try again.
                    logger.info("PubSub error, attempting to recover.", e);
                    try {
                        jpsh.unsubscribe();
                    } catch (Exception e1) {
                        /* This may fail with
                        - java.net.SocketException: Broken pipe
                        - redis.clients.jedis.exceptions.JedisConnectionException: JedisPubSub was not subscribed to a Jedis instance
                        */
                    }
                    broken = true;
                }
            } catch (JedisConnectionException e) {
                logger.info("PubSub error, attempting to recover in 5 secs.");
                proxyServer.getScheduler().buildTask(RedisBungee.this, PubSubListener.this).repeat(5, TimeUnit.SECONDS).schedule();
            }

            if (broken) {
                run();
            }
        }

        public void addChannel(String... channel) {
            addedChannels.addAll(Arrays.asList(channel));
            jpsh.subscribe(channel);
        }

        public void removeChannel(String... channel) {
            addedChannels.removeAll(Arrays.asList(channel));
            jpsh.unsubscribe(channel);
        }

        public void poison() {
            addedChannels.clear();
            jpsh.unsubscribe();
        }
    }

    private class JedisPubSubHandler extends JedisPubSub {
        @Override
        public void onMessage(final String s, final String s2) {
            if (s2.trim().length() == 0) return;
            NyaUtils.run(() -> proxyServer.getEventManager().fire(new PubSubMessageEvent(s, s2)));
        }
    }

    public final InputStream getResourceAsStream(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    public ScheduledTask makeThisRunAsync(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
        return proxyServer.getScheduler().buildTask(this, () -> {
            executorService.submit(runnable);
        }).delay(delay, timeUnit).repeat(period, timeUnit).schedule();
    }

}