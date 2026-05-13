package com.Tontoque28.DeathSound;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mod(DeadSound.MODID)
public class DeadSound {

    public static final String MODID = "deathsound";

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> DEATH_SOUND = SOUNDS.register("death_sound",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "death_sound")));

    public DeadSound() {
        SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("testsound")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof Player player) {
                        player.playNotifySound(DEATH_SOUND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                    return 1;
                })
        );
    }

    // --- EVENTO DE MUERTE GLOBAL (SERVIDOR) ---
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity() instanceof Player player) {
                if (!player.level().isClientSide) {
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        // Crear una copia de la lista para evitar ConcurrentModificationException
                        List<ServerPlayer> playersSafe = new ArrayList<>(server.getPlayerList().getPlayers());
                        
                        for (ServerPlayer p : playersSafe) {
                            if (p != null) {
                                p.playNotifySound(DEATH_SOUND.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Prevenir crash del servidor por sonido
            System.err.println("[DeathSound] Error playing death sound: " + e.getMessage());
        }
    }

    // --- EVENTOS SOLO CLIENTE ---
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientEvents {

        // --- MAGIA PARA LA PANTALLA DE MUERTE ---
        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof DeathScreen deathScreen) {
                // Reproducir sonido localmente (redundancia visual/auditiva)
                try {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(DEATH_SOUND.get(), 1.0F, 1.0F)
                    );
                } catch (Exception ignored) {}

                // INTENTO DE REFLEXIÓN ROBUSTA (Busca por TIPO, no por NOMBRE)
                try {
                    Field targetField = null;
                    
                    // Buscar el campo que sea de tipo Component (el mensaje de muerte)
                    // Ignoramos campos estáticos y buscamos en la instancia
                    for (Field f : DeathScreen.class.getDeclaredFields()) {
                        if (f.getType() == Component.class) {
                            f.setAccessible(true);
                            Object val = f.get(deathScreen);
                            // Verificación simple: si tiene valor, asumimos que es el mensaje
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
                                    .replace("\u2620", "")
                                    .trim();

                            Component hardcoreDeathText = Component.literal("☠ " + rawText + " ☠")
                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                            targetField.set(deathScreen, hardcoreDeathText);
                        }
                    }
                } catch (Exception e) {
                    // Silencioso: Si falla la visual, no crasheamos el juego
                    System.out.println("[DeathSound] Visual tweak failed safely: " + e.getMessage());
                }
            }
        }

        // --- MAGIA PARA EL CHAT ---
        @SubscribeEvent
        public static void onChatReceived(ClientChatReceivedEvent event) {
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
                            .replace("\u2620", "")
                            .trim();

                    Component hardcoreMessage = Component.literal("☠ " + rawText + " ☠")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                    event.setMessage(hardcoreMessage);
                }
            } catch (Exception e) {
                // Evitar crash por chat
            }
        }
    }
}