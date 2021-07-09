package com.imaginarycode.minecraft.redisbungee.commands;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
public class ProxyCommand implements SimpleCommand {

    private static final TextComponent NO_PERMISSION = Component.text("You have no permissions to do that.").color(NamedTextColor.RED);

    @Override
    public void execute(final SimpleCommand.Invocation invocation) {
        CommandSource commandSource = invocation.source();
        String[] args = invocation.arguments();

        if (!commandSource.hasPermission("redisbungee.command.proxyid")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        commandSource.sendMessage(Component.text("You are currently at "+ RedisBungee.getApi().getServerId() +".").color(NamedTextColor.GREEN));
    }
}
