package com.tontoque28.customdeathsound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;

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
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.addScheduledTask(() -> {
                try {
                    EntityPlayer player = minecraft.player;
                    if (player != null) {
                        minecraft.getSoundHandler().playSound(new PositionedSoundRecord(
                                CustomDeathSound.DEATH_SOUND, SoundCategory.MASTER, 1.0F, 1.0F,
                                (float) player.posX, (float) player.posY, (float) player.posZ));
                    } else {
                        minecraft.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(CustomDeathSound.DEATH_SOUND, 1.0F));
                    }
                } catch (Exception ignored) {
                }
            });
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
