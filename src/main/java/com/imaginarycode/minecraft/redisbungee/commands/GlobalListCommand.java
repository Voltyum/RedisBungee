package com.imaginarycode.minecraft.redisbungee.commands;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Map;
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
public class GlobalListCommand implements Command {

    private static final TextComponent NO_PERMISSION = TextComponent.of("You have no permissions to do that.").color(NamedTextColor.RED);

    private final RedisBungee redisBungee;

    @Override
    public void execute(CommandSource commandSource, String @NonNull [] args) {
        if (!commandSource.hasPermission("redisbungee.command.glist")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        int count = RedisBungee.getApi().getPlayerCount();
        TextComponent playersOnline = TextComponent.of(playerPlural(count) + " currently online.").color(NamedTextColor.YELLOW);

        if (args.length > 0 && args[0].equals("showall")) {
            Multimap<String, UUID> serverToPlayers = RedisBungee.getApi().getServerToPlayers();
            Multimap<String, String> human = HashMultimap.create();
            for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                human.put(entry.getKey(), redisBungee.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
            }
            for (String server : new TreeSet<>(serverToPlayers.keySet())) {
                TextComponent serverName = TextComponent.of("[" + server + "] ").color(NamedTextColor.GREEN);
                TextComponent serverCount = TextComponent.of("(" + human.get(server).size() + "): ").color(NamedTextColor.YELLOW);
                TextComponent serverPlayers = TextComponent.of(Joiner.on(", ").join(human.get(server))).color(NamedTextColor.WHITE);

                commandSource.sendMessage(serverName.append(serverCount).append(serverPlayers));
            }
            commandSource.sendMessage(playersOnline);
        } else {
            commandSource.sendMessage(playersOnline);
            commandSource.sendMessage(TextComponent.of("To see all players online, use /glist showall.").color(NamedTextColor.YELLOW));
        }

    }


    private static String playerPlural(int num) {
        return num == 1 ? num + " player is" : num + " players are";
    }
}
