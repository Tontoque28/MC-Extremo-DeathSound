package com.tontoque28.customdeathsound;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> CUSTOM_SOUND_PATH;

    static {
        BUILDER.comment(
                "Custom DeathSound - Configuración de sonido personalizado",
                "",
                "customSoundPath: ruta a un archivo .wav que quieras usar como sonido",
                "de muerte en lugar del sonido predeterminado (el clásico sonido Hardcore).",
                "",
                "- Deja el valor vacío (\"\") para usar siempre el sonido por defecto.",
                "- Puede ser una ruta absoluta (ej: C:/Users/tu_usuario/Music/mi_sonido.wav)",
                "  o relativa a la carpeta del juego (ej: config/customdeathsound/mi_sonido.wav).",
                "- Solo se admite formato .WAV (limitación de la librería de audio de Java).",
                "- Este ajuste solo afecta el sonido que TÚ escuchas cuando TÚ mueres.",
                "  Otros jugadores del servidor seguirán escuchando el sonido por defecto",
                "  cuando alguien muera, a menos que ellos mismos configuren el suyo."
        ).push("sound");

        CUSTOM_SOUND_PATH = BUILDER
                .comment("Ruta al archivo .wav personalizado. Vacío = usar el sonido por defecto.")
                .define("customSoundPath", "");

        BUILDER.pop();
        CLIENT_SPEC = BUILDER.build();
    }

    private static volatile String cachedPath = "";

    public static void refreshCache() {
        try {
            cachedPath = CUSTOM_SOUND_PATH.get();
        } catch (Exception e) {
            cachedPath = "";
        }
    }

    public static String getCustomSoundPath() {
        return cachedPath == null ? "" : cachedPath;
    }

    public static void setCustomSoundPath(String path) {
        CUSTOM_SOUND_PATH.set(path);
        CUSTOM_SOUND_PATH.save();
        cachedPath = path;
    }

    public static Path resolveCustomSoundPath() {
        String raw = getCustomSoundPath();
        if (raw == null || raw.trim().isEmpty()) return null;

        Path direct = Paths.get(raw);
        if (Files.exists(direct)) return direct;

        Path relativeToGameDir = FMLPaths.GAMEDIR.get().resolve(raw);
        if (Files.exists(relativeToGameDir)) return relativeToGameDir;

        return null;
    }

    public static Path getCustomSoundsFolder() {
        Path folder = FMLPaths.GAMEDIR.get().resolve("config").resolve("customdeathsound").resolve("custom");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            System.err.println("[CustomDeathSound] No se pudo crear la carpeta de sonidos personalizados: " + e.getMessage());
        }
        return folder;
    }
}
