package com.imaginarycode.minecraft.redisbungee.commands;

import com.google.common.base.Joiner;
import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * This code has been created by Tux, and modified by gatogamer.
 */
public class SendToAllCommand implements SimpleCommand {

    private static final TextComponent NO_PERMISSION = Component.text("You have no permissions to do that.").color(NamedTextColor.RED);
    private static final TextComponent NO_COMMAND_SPECIFIED = Component.text("You must specify a command to be run.").color(NamedTextColor.RED);

    @Override
    public void execute(final SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        String[] args = invocation.arguments();

        if (!commandSource.hasPermission("redisbungee.command.sendtoall")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        if (args.length > 0) {
            String command = Joiner.on(" ").skipNulls().join(args);
            RedisBungee.getApi().sendProxyCommand(command);
            commandSource.sendMessage(Component.text("Sent the command /" + command + " to all proxies.").color(NamedTextColor.GREEN));
        } else {
            commandSource.sendMessage(NO_COMMAND_SPECIFIED);
        }
    }
}
