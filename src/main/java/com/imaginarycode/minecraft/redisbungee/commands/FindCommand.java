package com.imaginarycode.minecraft.redisbungee.commands;

import com.imaginarycode.minecraft.redisbungee.RedisBungee;
import com.imaginarycode.minecraft.redisbungee.utils.NyaUtils;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.AllArgsConstructor;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.UUID;

/**
 * This code has been created by
 * gatogamer#6666 A.K.A. gatogamer.
 * If you want to use my code, please
 * don't remove this messages and
 * give me the credits. Arigato! n.n
 */
@AllArgsConstructor
public class FindCommand implements Command {

    private static final TextComponent NO_PLAYER_SPECIFIED = TextComponent.of("You must specify a player name.").color(TextColor.RED);
    private static final TextComponent PLAYER_NOT_FOUND = TextComponent.of("No such player found.").color(TextColor.RED);
    private static final TextComponent NO_PERMISSION = TextComponent.of("You have no permissions to do that.").color(TextColor.RED);

    private final RedisBungee redisBungee;

    @Override
    public void execute(CommandSource commandSource, String @NonNull [] args) {
        if (!commandSource.hasPermission("redisbungee.command.find")) {
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
                RegisteredServer registeredServer = RedisBungee.getApi().getServerFor(uuid);
                if (registeredServer != null) {
                    TextComponent message = TextComponent.of(args[0] + " is at " + registeredServer.getServerInfo().getName() + ".").color(TextColor.GREEN);
                    commandSource.sendMessage(message);
                } else {
                    commandSource.sendMessage(PLAYER_NOT_FOUND);
                }
            } else {
                commandSource.sendMessage(NO_PLAYER_SPECIFIED);
            }
        });
    }
}
