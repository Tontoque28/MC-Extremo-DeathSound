package com.tontoque28.customdeathsound;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Mod(CustomDeathSound.MODID)
public class CustomDeathSound {

    public static final String MODID = "customdeathsound";

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final RegistryObject<SoundEvent> DEATH_SOUND = SOUNDS.register("death_sound",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(MODID, "death_sound")));

    public CustomDeathSound(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        SOUNDS.register(modBus);

        ModConfigEvent.Loading.getBus(modBus).addListener(CustomDeathSound::onConfigLoading);
        ModConfigEvent.Reloading.getBus(modBus).addListener(CustomDeathSound::onConfigReloading);
        context.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        RegisterCommandsEvent.BUS.addListener(CustomDeathSound::onCommandRegister);
        LivingDeathEvent.BUS.addListener(event -> {
            onLivingDeath(event);
            return false;
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.register();
        }

        Config.getCustomSoundsFolder();
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            Config.refreshCache();
        }
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            Config.refreshCache();
        }
    }

    private static boolean rejectOnDedicatedServer(CommandSourceStack source) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            source.sendFailure(Component.literal("[CustomDeathSound] Este comando solo esta disponible en el cliente."));
            return true;
        }
        return false;
    }

    private static void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("customdeathsound")
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
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("[CustomDeathSound] Sonido personalizado configurado: " + path), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            List<String> files = listCustomSoundFiles();
                            if (files.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "[CustomDeathSound] No hay archivos .wav en config/customdeathsound/custom/. "
                                                + "Coloca uno ahí y vuelve a intentar."), false);
                            } else {
                                context.getSource().sendSuccess(() -> Component.literal(
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
                                        context.getSource().sendFailure(Component.literal(
                                                "[CustomDeathSound] No se encontró \"" + file
                                                        + "\" en config/customdeathsound/custom/. Usa /customdeathsound list para ver los disponibles."));
                                        return 0;
                                    }
                                    String relative = "config/customdeathsound/custom/" + file;
                                    Config.setCustomSoundPath(relative);
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("[CustomDeathSound] Sonido personalizado seleccionado: " + file), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .executes(context -> {
                            if (rejectOnDedicatedServer(context.getSource())) return 0;
                            Config.setCustomSoundPath("");
                            context.getSource().sendSuccess(() ->
                                    Component.literal("[CustomDeathSound] Sonido restaurado al predeterminado."), false);
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

    private static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity() instanceof Player player) {
                if (!player.level().isClientSide()) {
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        List<ServerPlayer> playersSafe = new ArrayList<>(server.getPlayerList().getPlayers());

                        for (ServerPlayer p : playersSafe) {
                            if (p != null) {
                                p.connection.send(new ClientboundSoundPacket(
                                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(DEATH_SOUND.get()),
                                        SoundSource.PLAYERS, p.getX(), p.getY(), p.getZ(), 1.0F, 1.0F,
                                        p.level().getRandom().nextLong()));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomDeathSound] Error playing death sound: " + e.getMessage());
        }
    }

    public static class ClientEvents {

        private static void register() {
            ScreenEvent.Init.Post.BUS.addListener(ClientEvents::onScreenInit);
            ClientChatReceivedEvent.BUS.addListener(event -> {
                onChatReceived(event);
                return false;
            });
        }

        private static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof DeathScreen deathScreen) {
                CustomSoundPlayer.playLocal();

                try {
                    Field targetField = null;

                    for (Field f : DeathScreen.class.getDeclaredFields()) {
                        if (f.getType() == Component.class) {
                            f.setAccessible(true);
                            Object val = f.get(deathScreen);
                            if (val != null) {
                                targetField = f;
                                break;
                            }
                        }
                    }

                    if (targetField != null) {
                        Component causeOfDeath = (Component) targetField.get(deathScreen);
                        if (causeOfDeath != null) {
                            String rawText = causeOfDeath.getString()
                                    .replace("☠", "")
                                    .trim();

                            Component hardcoreDeathText = Component.literal("☠ " + rawText + " ☠")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                            targetField.set(deathScreen, hardcoreDeathText);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[CustomDeathSound] Visual tweak failed safely: " + e.getMessage());
                }
            }
        }

        private static void onChatReceived(ClientChatReceivedEvent event) {
            try {
                Component msg = event.getMessage();
                if (msg == null) return;

                boolean isDeathMessage = false;

                if (msg.getContents() instanceof TranslatableContents translatable) {
                    if (translatable.getKey().startsWith("death.")) {
                        isDeathMessage = true;
                    }
                }

                if (!isDeathMessage) {
                    String text = msg.getString();
                    if (text.contains("slain by") ||
                            text.contains("shot by") ||
                            text.contains("blown up by") ||
                            (text.contains("died") && !text.contains("died of")) ||
                            text.contains("fell from a high place") ||
                            text.contains("drowned") ||
                            text.contains("experienced kinetic energy") ||
                            text.contains("hit the ground too hard")) {

                        if (!text.contains(":")) {
                            isDeathMessage = true;
                        }
                    }
                }

                if (isDeathMessage) {
                    String rawText = msg.getString()
                            .replace("☠", "")
                            .trim();

                    Component hardcoreMessage = Component.literal("☠ " + rawText + " ☠")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                    event.setMessage(hardcoreMessage);
                }
            } catch (Exception e) {
            }
        }
    }
}
