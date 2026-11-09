package com.tontoque28.customdeathsound;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.lang.reflect.Field;

public class CustomDeathSoundClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Config.refreshCache();

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof DeathScreen) {
                onDeathScreen((DeathScreen) screen);
            }
        });
    }

    private static void onDeathScreen(DeathScreen deathScreen) {
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

                    Component hardcoreDeathText = new TextComponent("☠ " + rawText + " ☠")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                    targetField.set(deathScreen, hardcoreDeathText);
                }
            }
        } catch (Exception e) {
            System.out.println("[CustomDeathSound] Visual tweak failed safely: " + e.getMessage());
        }
    }

    public static Component transform(Component msg) {
        try {
            if (msg == null) return msg;

            boolean isDeathMessage = false;

            if (msg instanceof TranslatableComponent) {
                TranslatableComponent translatable = (TranslatableComponent) msg;
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

                return new TextComponent("☠ " + rawText + " ☠")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            }
        } catch (Exception e) {
            return msg;
        }
        return msg;
    }
}
