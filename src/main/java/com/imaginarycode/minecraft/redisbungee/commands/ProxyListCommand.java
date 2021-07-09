package com.imaginarycode.minecraft.redisbungee.commands;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import lombok.RequiredArgsConstructor;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
@RequiredArgsConstructor
public class ProxyListCommand implements Command {

    private static final TextComponent NO_PERMISSION = TextComponent.of("You have no permissions to do that.").color(TextColor.RED);

    private final RedisBungee redisBungee;

    @Override
    public void execute(CommandSource commandSource, String @NonNull [] args) {
        if (!commandSource.hasPermission("redisbungee.command.proxyid")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        String proxy = args.length >= 1 ? args[0] : RedisBungee.getApi().getServerId();
        if (!redisBungee.getServerIds().contains(proxy)) {
            commandSource.sendMessage(TextComponent.of(proxy + " is not a valid proxy. See /serverids for valid proxies.").color(TextColor.RED));
            return;
        }
        Set<UUID> players = RedisBungee.getApi().getPlayersOnProxy(proxy);

        TextComponent playersOnline = TextComponent.of(playerPlural(players.size()) + " currently on proxy " + proxy + ".").color(TextColor.YELLOW);

        if (args.length >= 2 && args[1].equals("showall")) {
            Multimap<String, UUID> serverToPlayers = RedisBungee.getApi().getServerToPlayers();
            Multimap<String, String> human = HashMultimap.create();
            for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                if (players.contains(entry.getValue())) {
                    human.put(entry.getKey(), redisBungee.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
                }
            }
            for (String server : new TreeSet<>(human.keySet())) {
                TextComponent serverName = TextComponent.of("[" + server + "] ").color(TextColor.RED);
                TextComponent serverCount = TextComponent.of("(" + human.get(server).size() + "): ").color(TextColor.YELLOW);
                TextComponent serverPlayers = TextComponent.of(Joiner.on(", ").join(human.get(server))).color(TextColor.WHITE);

                commandSource.sendMessage(serverName.append(serverCount).append(serverPlayers));
            }
            commandSource.sendMessage(playersOnline);
        } else {
            commandSource.sendMessage(playersOnline);
            commandSource.sendMessage(TextComponent.of("To see all players online, use /plist " + proxy + " showall.").color(TextColor.YELLOW));
        }
    }

    private static String playerPlural(int num) {
        return num == 1 ? num + " player is" : num + " players are";
    }
}
