package com.imaginarycode.minecraft.redisbungee.commands;

import com.google.common.base.Joiner;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
public class SendToAllCommand implements Command {

    private static final TextComponent NO_PERMISSION = TextComponent.of("You have no permissions to do that.").color(NamedTextColor.RED);
    private static final TextComponent NO_COMMAND_SPECIFIED = TextComponent.of("You must specify a command to be run.").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSource commandSource, String @NonNull [] args) {
        if (!commandSource.hasPermission("redisbungee.command.sendtoall")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        if (args.length > 0) {
            String command = Joiner.on(" ").skipNulls().join(args);
            RedisBungee.getApi().sendProxyCommand(command);
            commandSource.sendMessage(TextComponent.of("Sent the command /" + command + " to all proxies.").color(NamedTextColor.GREEN));
        } else {
            commandSource.sendMessage(NO_COMMAND_SPECIFIED);
        }
    }
}
