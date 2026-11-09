package com.tontoque28.customdeathsound;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

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

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> DEATH_SOUND = SOUNDS.register("death_sound",
            () -> new SoundEvent(new ResourceLocation(MODID, "death_sound")));

    public CustomDeathSound() {
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(this);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onLoadConfig);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        Config.getCustomSoundsFolder();
    }

    private void onLoadConfig(final ModConfig.ModConfigEvent event) {
        if (event.getConfig().getSpec() == Config.CLIENT_SPEC) {
            Config.refreshCache();
        }
    }

    private static boolean rejectOnDedicatedServer(CommandSource source) {
        if (FMLEnvironment.dist.isDedicatedServer()) {
            source.sendFailure(new StringTextComponent("[CustomDeathSound] Este comando solo esta disponible en el cliente."));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
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
                                    context.getSource().sendSuccess(
                                            new StringTextComponent("[CustomDeathSound] Sonido personalizado configurado: " + path), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("list")
                        .executes(context -> {
                            List<String> files = listCustomSoundFiles();
                            if (files.isEmpty()) {
                                context.getSource().sendSuccess(new StringTextComponent(
                                        "[CustomDeathSound] No hay archivos .wav en config/customdeathsound/custom/. "
                                                + "Coloca uno ahí y vuelve a intentar."), false);
                            } else {
                                context.getSource().sendSuccess(new StringTextComponent(
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
                                        context.getSource().sendFailure(new StringTextComponent(
                                                "[CustomDeathSound] No se encontró \"" + file
                                                        + "\" en config/customdeathsound/custom/. Usa /customdeathsound list para ver los disponibles."));
                                        return 0;
                                    }
                                    String relative = "config/customdeathsound/custom/" + file;
                                    Config.setCustomSoundPath(relative);
                                    context.getSource().sendSuccess(
                                            new StringTextComponent("[CustomDeathSound] Sonido personalizado seleccionado: " + file), false);
                                    CustomSoundPlayer.playLocal();
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .executes(context -> {
                            if (rejectOnDedicatedServer(context.getSource())) return 0;
                            Config.setCustomSoundPath("");
                            context.getSource().sendSuccess(
                                    new StringTextComponent("[CustomDeathSound] Sonido restaurado al predeterminado."), false);
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

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity() instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity) event.getEntity();
                if (!player.level.isClientSide) {
                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        List<ServerPlayerEntity> playersSafe = new ArrayList<>(server.getPlayerList().getPlayers());

                        for (ServerPlayerEntity p : playersSafe) {
                            if (p != null) {
                                p.playNotifySound(DEATH_SOUND.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomDeathSound] Error playing death sound: " + e.getMessage());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
            if (event.getGui() instanceof DeathScreen) {
                DeathScreen deathScreen = (DeathScreen) event.getGui();
                CustomSoundPlayer.playLocal();

                try {
                    Field targetField = null;

                    for (Field f : DeathScreen.class.getDeclaredFields()) {
                        if (f.getType() == ITextComponent.class) {
                            f.setAccessible(true);
                            Object val = f.get(deathScreen);
                            if (val != null) {
                                targetField = f;
                                break;
                            }
                        }
                    }

                    if (targetField != null) {
                        ITextComponent causeOfDeath = (ITextComponent) targetField.get(deathScreen);
                        if (causeOfDeath != null) {
                            String rawText = causeOfDeath.getString()
                                    .replace("☠", "")
                                    .trim();

                            IFormattableTextComponent hardcoreDeathText = new StringTextComponent("☠ " + rawText + " ☠")
                                    .withStyle(TextFormatting.RED, TextFormatting.BOLD);

                            targetField.set(deathScreen, hardcoreDeathText);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[CustomDeathSound] Visual tweak failed safely: " + e.getMessage());
                }
            }
        }

        @SubscribeEvent
        public static void onChatReceived(ClientChatReceivedEvent event) {
            try {
                ITextComponent msg = event.getMessage();
                if (msg == null) return;

                boolean isDeathMessage = false;

                if (msg instanceof TranslationTextComponent) {
                    TranslationTextComponent translatable = (TranslationTextComponent) msg;
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

                    ITextComponent hardcoreMessage = new StringTextComponent("☠ " + rawText + " ☠")
                            .withStyle(TextFormatting.RED, TextFormatting.BOLD);

                    event.setMessage(hardcoreMessage);
                }
            } catch (Exception e) {
            }
        }
    }
}
