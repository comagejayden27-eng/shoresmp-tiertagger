package com.comag.shoresmp.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


public class ShoreSMPTierTaggerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierConfig.load();
        System.out.println("ShoreSMP TierTagger loaded!");

        // /tiertagger <name> and /shoretier <name> (same thing). The argument is called "shorename" so it
        // can't clash with another mod that also has a /tiertagger command with an argument called "player".
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            for (String command : new String[]{"tiertagger", "shoretier"}) {
                dispatcher.register(ClientCommands.literal(command)
                        .then(ClientCommands.argument("shorename", StringArgumentType.word())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "shorename");
                                    var source = context.getSource();

                                    source.sendFeedback(TierData.gradient("[TierTagger] Searching..."));

                                    // Asks the bot's API in the background, then prints the answer in chat
                                    TierData.infoLinesAsync(name).whenComplete((lines, error) ->
                                            Minecraft.getInstance().execute(() -> {
                                                if (error != null || lines == null) {
                                                    source.sendFeedback(Component.literal("Could not reach the tier server."));
                                                    return;
                                                }
                                                for (Component line : lines) {
                                                    source.sendFeedback(line);
                                                }
                                            }));

                                    return 1;
                                })));
            }
        });
    }
}