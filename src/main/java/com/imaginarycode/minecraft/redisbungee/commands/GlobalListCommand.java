package com.imaginarycode.minecraft.redisbungee.commands;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

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
public class GlobalListCommand implements SimpleCommand {

    private static final TextComponent NO_PERMISSION = Component.text("You have no permissions to do that.").color(NamedTextColor.RED);

    private final RedisBungee redisBungee;

    @Override
    public void execute(final SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        String[] args = invocation.arguments();

        if (!commandSource.hasPermission("redisbungee.command.glist")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        int count = RedisBungee.getApi().getPlayerCount();
        TextComponent playersOnline = Component.text(playerPlural(count) + " currently online.").color(NamedTextColor.YELLOW);

        if (args.length > 0 && args[0].equals("showall")) {
            Multimap<String, UUID> serverToPlayers = RedisBungee.getApi().getServerToPlayers();
            Multimap<String, String> human = HashMultimap.create();
            for (Map.Entry<String, UUID> entry : serverToPlayers.entries()) {
                human.put(entry.getKey(), redisBungee.getUuidTranslator().getNameFromUuid(entry.getValue(), false));
            }
            for (String server : new TreeSet<>(serverToPlayers.keySet())) {
                TextComponent serverName = Component.text("[" + server + "] ").color(NamedTextColor.GREEN);
                TextComponent serverCount = Component.text("(" + human.get(server).size() + "): ").color(NamedTextColor.YELLOW);
                TextComponent serverPlayers = Component.text(Joiner.on(", ").join(human.get(server))).color(NamedTextColor.WHITE);

                commandSource.sendMessage(serverName.append(serverCount).append(serverPlayers));
            }
            commandSource.sendMessage(playersOnline);
        } else {
            commandSource.sendMessage(playersOnline);
            commandSource.sendMessage(Component.text("To see all players online, use /glist showall.").color(NamedTextColor.YELLOW));
        }

    }


    private static String playerPlural(int num) {
        return num == 1 ? num + " player is" : num + " players are";
    }
}
