package me.bakje.playtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.Style;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import java.lang.reflect.Type;

public class Playtime implements ModInitializer {
    // combination of https://github.com/Circuit-Overtime/myPlayTimeTracker, chatgpt and my dumb ass.

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PLAYTIME_FILE = Paths.get("playtime.json");
    private final Map<String, Long> joinTimeMap = new HashMap<>();
    static private Map<String, Long> playtimeData = new HashMap<>();

    @Override
    public void onInitialize() {
        ReadPlayerData();
        // Registering the player join event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();

            joinTimeMap.put(playerName, System.currentTimeMillis());
        });

        // Registering the player leave event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();

            if (joinTimeMap.containsKey(playerName)) {
                long joinTime = joinTimeMap.remove(playerName);
                long leaveTime = System.currentTimeMillis();
                long sessionPlaytime = leaveTime - joinTime;

                AddPlayTime(playerName, sessionPlaytime);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("playtime")
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        ServerPlayerEntity player = source.getPlayer();

                        if (player != null) {
                            String playerName = player.getName().getString();
                            long playtime = GetPlaytime(playerName);

                            long currentSessionPlaytime = 0L;
                            if (joinTimeMap.containsKey(playerName)) {
                                long joinTime = joinTimeMap.get(playerName);
                                currentSessionPlaytime = System.currentTimeMillis() - joinTime;
                            }

                            long totalPlaytime = playtime + currentSessionPlaytime;

                            if (totalPlaytime > 0) {
                                String formattedPlaytime = FormatPlaytime(totalPlaytime);
                                Text response = Text.literal("Your playtime is ")
                                        .append(Text.literal(formattedPlaytime).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFF00)))) // Yellow color for time
                                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))); // White color for text

                                source.sendFeedback(() -> response, false);
                            } else {
                                source.sendFeedback(() -> Text.literal("No playtime found.")
                                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000))), // Red color for error
                                        false);
                            }
                        } else {
                            source.sendFeedback(() -> Text.literal("This command can only be run by a player.")
                                            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))), // White color
                                    false); // Corrected
                        }
                        return 1;
                    })
            );
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
            WritePlayerData();
        });

    }

    private void AddPlayTime(String playerName, long playTime) {
        playtimeData.put(playerName, playtimeData.getOrDefault(playerName, 0L) + playTime);
    }

    private long GetPlaytime(String playerName) {
        return playtimeData.getOrDefault(playerName, 0L);
    }

    private void ReadPlayerData() {
        if (PLAYTIME_FILE.toFile().exists()) {
            try (Reader reader = new FileReader(PLAYTIME_FILE.toFile())) {
                Type type = new TypeToken<Map<String, Long>>() {}.getType();
                playtimeData = GSON.fromJson(reader, type);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void WritePlayerData() {
        try (Writer writer = new FileWriter(PLAYTIME_FILE.toFile())) {
            GSON.toJson(playtimeData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String FormatPlaytime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
}
