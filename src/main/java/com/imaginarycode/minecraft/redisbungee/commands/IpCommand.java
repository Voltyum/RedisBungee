package com.imaginarycode.minecraft.redisbungee.commands;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.utils.NyaUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.net.InetAddress;
import java.util.UUID;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
@AllArgsConstructor
public class IpCommand implements SimpleCommand {

    private static final TextComponent NO_PERMISSION = Component.text("You have no permissions to do that.").color(NamedTextColor.RED);

    private static final TextComponent NO_PLAYER_SPECIFIED = Component.text("You must specify a player name.").color(NamedTextColor.RED);
    private static final TextComponent PLAYER_NOT_FOUND = Component.text("No such player found.").color(NamedTextColor.RED);

    private final RedisBungee redisBungee;
    @Override
    public void execute(final SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        String[] args = invocation.arguments();

        if (!commandSource.hasPermission("redisbungee.command.ip")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        NyaUtils.run(() -> {
            if (args.length > 0) {
                UUID uuid = redisBungee.getUuidTranslator().getTranslatedUuid(args[0], true);
                if (uuid == null) {
                    commandSource.sendMessage(PLAYER_NOT_FOUND);
                    return;
                }
                InetAddress ia = RedisBungee.getApi().getPlayerIp(uuid);
                if (ia != null) {
                    commandSource.sendMessage(Component.text(args[0] + " is connected from "+ia.toString()+".").color(NamedTextColor.GREEN));
                } else {
                    commandSource.sendMessage(PLAYER_NOT_FOUND);
                }
            } else {
                commandSource.sendMessage(NO_PLAYER_SPECIFIED);
            }
        });
    }
}
