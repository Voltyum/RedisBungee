package com.imaginarycode.minecraft.redisbungee;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.moandjiezana.toml.Toml;
import lombok.Getter;
import redis.clients.jedis.JedisPool;

import java.net.InetAddress;
import java.util.List;

public class RedisBungeeConfiguration {
    @Getter
    private final JedisPool pool;
    @Getter
    private final String serverId;
    @Getter
    private final boolean registerBungeeCommands;
    @Getter
    private final List<InetAddress> exemptAddresses;

    public RedisBungeeConfiguration(JedisPool pool, Toml configuration) {
        this.pool = pool;
        this.serverId = configuration.getString("server-id");
        this.registerBungeeCommands = configuration.getBoolean("register-velocity-commands", true);

        List<String> stringified = configuration.getList("exempt-ip-addresses");
        ImmutableList.Builder<InetAddress> addressBuilder = ImmutableList.builder();

        for (String s : stringified) {
            addressBuilder.add(InetAddresses.forString(s));
        }

        this.exemptAddresses = addressBuilder.build();
    }
}
