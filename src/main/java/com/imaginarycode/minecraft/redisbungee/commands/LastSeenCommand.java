package com.imaginarycode.minecraft.redisbungee.commands;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.utils.NyaUtils;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import lombok.AllArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.SimpleDateFormat;
import java.util.UUID;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
@AllArgsConstructor
public class LastSeenCommand implements SimpleCommand {

    private static final TextComponent NO_PERMISSION = Component.text("You have no permissions to do that.").color(NamedTextColor.RED);

    private static final TextComponent NO_PLAYER_SPECIFIED = Component.text("You must specify a player name.").color(NamedTextColor.RED);
    private static final TextComponent PLAYER_NOT_FOUND = Component.text("No such player found.").color(NamedTextColor.RED);

    private final RedisBungee redisBungee;

    @Override
    public void execute(final SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        String[] args = invocation.arguments();

        if (!commandSource.hasPermission("redisbungee.command.lastseen")) {
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
                long secs = RedisBungee.getApi().getLastOnline(uuid);
                TextComponent message;
                if (secs == 0) {
                    message = Component.text(args[0] + " is currently online.").color(NamedTextColor.GREEN);
                } else if (secs != -1) {
                    message = Component.text(args[0] + " was last online on "+new SimpleDateFormat().format(secs)+".").color(NamedTextColor.GREEN);
                } else {
                    message = Component.text(args[0] + " has never been online.").color(NamedTextColor.GREEN);
                }
                commandSource.sendMessage(message);
            } else {
                commandSource.sendMessage(NO_PLAYER_SPECIFIED);
            }
        });
    }
}
