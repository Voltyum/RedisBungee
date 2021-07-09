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
public class ServerIdsCommand implements Command {

    private static final TextComponent NO_PERMISSION = TextComponent.of("You have no permissions to do that.").color(NamedTextColor.RED);

    @Override
    public void execute(CommandSource commandSource, String @NonNull [] strings) {
        if (!commandSource.hasPermission("redisbungee.command.serverids")) {
            commandSource.sendMessage(NO_PERMISSION);
            return;
        }

        commandSource.sendMessage(TextComponent.of("All server IDs: " + Joiner.on(", ").join(RedisBungee.getApi().getAllServers())).color(NamedTextColor.YELLOW));
    }
}
