package com.tontoque28.customdeathsound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class CustomSoundPlayer {

    public static void playLocal() {
        Path custom = Config.resolveCustomSoundPath();

        if (custom != null) {
            boolean played = tryPlayWav(custom);
            if (played) return;
        }

        playDefault();
    }

    private static void playDefault() {
        try {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(CustomDeathSound.DEATH_SOUND.get(), 1.0F, 1.0F)
            );
        } catch (Exception ignored) {
        }
    }

    private static boolean tryPlayWav(Path path) {
        try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
            return true;
        } catch (Exception e) {
            System.err.println("[CustomDeathSound] No se pudo reproducir el sonido personalizado ("
                    + path + "): " + e.getMessage() + ". Usando sonido por defecto.");
            return false;
        }
    }
}
