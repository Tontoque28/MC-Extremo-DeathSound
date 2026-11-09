package com.tontoque28.customdeathsound;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class CustomDeathSound implements ModInitializer {

    public static final String MODID = "customdeathsound";

    public static final ResourceLocation DEATH_SOUND_ID = new ResourceLocation(MODID, "death_sound");

    public static final SoundEvent DEATH_SOUND = new SoundEvent(DEATH_SOUND_ID);

    @Override
    public void onInitialize() {
        Registry.register(Registry.SOUND_EVENT, DEATH_SOUND_ID, DEATH_SOUND);

        Config.getCustomSoundsFolder();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> registerCommands(dispatcher));
    }

    public static void onPlayerDeath(ServerPlayer player) {
        try {
            MinecraftServer server = player.getServer();
            if (server != null) {
                List<ServerPlayer> playersSafe = new ArrayList<>(server.getPlayerList().getPlayers());

                for (ServerPlayer p : playersSafe) {
                    if (p != null) {
                        p.playNotifySound(DEATH_SOUND, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomDeathSound] Error playing death sound: " + e.getMessage());
        }
    }

    private static boolean rejectOnDedicatedServer(CommandSourceStack source) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            source.sendFailure(new TextComponent("[CustomDeathSound] Este comando solo esta disponible en el cliente."));
            return true;
        }
        return false;
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("customdeathsound")
                .then(Commands.literal("test")
                        .executes(context -> {
                            if (rejectOnDedicatedServer(context.getSource())) return 0;
                            CustomSoundPlayer.playLocal();
                            return 1;
                        }))
                .then(Commands.literal("setsound")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> {
                                    if (rejectOnDedicatedServer(context.getSource())) return 0;
                                    String path = StringArgumentType.getString(context, "path");
                                    Config.setCustomSoundPath(path);
                                    context.getSource().sendSuccess(
                                            new TextComponent("[CustomDeathSound] Sonido personalizado configurado: " + path), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            List<String> files = listCustomSoundFiles();
                            if (files.isEmpty()) {
                                context.getSource().sendSuccess(new TextComponent(
                                        "[CustomDeathSound] No hay archivos .wav en config/customdeathsound/custom/. "
                                                + "Coloca uno ahí y vuelve a intentar."), false);
                            } else {
                                context.getSource().sendSuccess(new TextComponent(
                                        "[CustomDeathSound] Archivos disponibles: " + String.join(", ", files)
                                                + "  -> usa /customdeathsound select <archivo>"), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("select")
                        .then(Commands.argument("file", StringArgumentType.string())
                                .suggests((context, builder) -> suggestCustomSoundFiles(builder))
                                .executes(context -> {
                                    if (rejectOnDedicatedServer(context.getSource())) return 0;
                                    String file = StringArgumentType.getString(context, "file");
                                    Path target = Config.getCustomSoundsFolder().resolve(file);
                                    if (!Files.exists(target)) {
                                        context.getSource().sendFailure(new TextComponent(
                                                "[CustomDeathSound] No se encontró \"" + file
                                                        + "\" en config/customdeathsound/custom/. Usa /customdeathsound list para ver los disponibles."));
                                        return 0;
                                    }
                                    String relative = "config/customdeathsound/custom/" + file;
                                    Config.setCustomSoundPath(relative);
                                    context.getSource().sendSuccess(
                                            new TextComponent("[CustomDeathSound] Sonido personalizado seleccionado: " + file), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .executes(context -> {
                            if (rejectOnDedicatedServer(context.getSource())) return 0;
                            Config.setCustomSoundPath("");
                            context.getSource().sendSuccess(
                                    new TextComponent("[CustomDeathSound] Sonido restaurado al predeterminado."), false);
                            return 1;
                        }))
        );
    }

    private static List<String> listCustomSoundFiles() {
        List<String> result = new ArrayList<>();
        Path folder = Config.getCustomSoundsFolder();
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.toLowerCase().endsWith(".wav"))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException ignored) {
        }
        return result;
    }

    private static CompletableFuture<Suggestions> suggestCustomSoundFiles(SuggestionsBuilder builder) {
        for (String name : listCustomSoundFiles()) {
            if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }
}
