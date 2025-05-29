package de.maxhenkel.radio.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.maxhenkel.radio.Radio;
import de.maxhenkel.radio.radio.RadioData;
import de.maxhenkel.voicechat.api.Player;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class RadioCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess cra, CommandManager.RegistrationEnvironment environment) {
        LiteralArgumentBuilder<ServerCommandSource> literalBuilder = CommandManager.literal("radio")
                .requires((commandSource) -> commandSource.hasPermissionLevel(Radio.SERVER_CONFIG.commandPermissionLevel.get()));

        literalBuilder.then(
            CommandManager.literal("create")
                    .then(
                            CommandManager.argument("station_name", StringArgumentType.string())
                                    .executes(RadioCommands::runWithoutRange)
                                    .then(
                                        CommandManager.argument("sound_radius", FloatArgumentType.floatArg(0.0f))
                                        .executes(RadioCommands::runWithRange)
                                    )
                        )

        );

        dispatcher.register(literalBuilder);
    }


    private static int runWithRange(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String stationName = StringArgumentType.getString(context, "station_name");
        float soundRadius = FloatArgumentType.getFloat(context, "sound_radius");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        return runCommand(stationName, player, soundRadius);
    }

    private static int runWithoutRange(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        String stationName = StringArgumentType.getString(context, "station_name");
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

        return RadioCommands.runCommand(stationName, player, -1.0f);
    }


    private static int runCommand(String stationName, ServerPlayerEntity player, float range) {
        RadioData radioData = new RadioData(UUID.randomUUID(), stationName, false, range);
        player.getInventory().insertStack(radioData.toItemWithNoId());
        return 1;
    }

}
