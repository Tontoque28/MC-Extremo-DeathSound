package com.tontoque28.customdeathsound;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {

    private static Configuration configuration;
    private static Property customSoundProperty;
    private static Path gameDir = Paths.get(".");
    private static volatile String cachedPath = "";

    public static void init(File configDir, File configFile, boolean client) {
        File parent = configDir.getAbsoluteFile().getParentFile();
        if (parent != null) {
            gameDir = parent.toPath();
        }
        if (!client) {
            return;
        }
        configuration = new Configuration(configFile);
        configuration.load();
        configuration.setCategoryComment("sound",
                "Custom DeathSound - Configuraci\u00f3n de sonido personalizado\n"
                        + "\n"
                        + "customSoundPath: ruta a un archivo .wav que quieras usar como sonido\n"
                        + "de muerte en lugar del sonido predeterminado (el cl\u00e1sico sonido Hardcore).\n"
                        + "\n"
                        + "- Deja el valor vac\u00edo para usar siempre el sonido por defecto.\n"
                        + "- Puede ser una ruta absoluta (ej: C:/Users/tu_usuario/Music/mi_sonido.wav)\n"
                        + "  o relativa a la carpeta del juego (ej: config/customdeathsound/mi_sonido.wav).\n"
                        + "- Solo se admite formato .WAV (limitaci\u00f3n de la librer\u00eda de audio de Java).\n"
                        + "- Este ajuste solo afecta el sonido que T\u00da escuchas cuando T\u00da mueres.\n"
                        + "  Otros jugadores del servidor seguir\u00e1n escuchando el sonido por defecto\n"
                        + "  cuando alguien muera, a menos que ellos mismos configuren el suyo.");
        customSoundProperty = configuration.get("sound", "customSoundPath", "",
                "Ruta al archivo .wav personalizado. Vac\u00edo = usar el sonido por defecto.");
        refreshCache();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static void refreshCache() {
        try {
            cachedPath = customSoundProperty == null ? "" : customSoundProperty.getString();
        } catch (Exception e) {
            cachedPath = "";
        }
    }

    public static String getCustomSoundPath() {
        return cachedPath == null ? "" : cachedPath;
    }

    public static void setCustomSoundPath(String path) {
        if (customSoundProperty != null) {
            customSoundProperty.set(path);
            configuration.save();
        }
        cachedPath = path;
    }

    public static Path resolveCustomSoundPath() {
        String raw = getCustomSoundPath();
        if (raw == null || raw.trim().isEmpty()) return null;

        try {
            Path direct = Paths.get(raw);
            if (Files.exists(direct)) return direct;
        } catch (Exception ignored) {
        }

        Path relativeToGameDir = gameDir.resolve(raw);
        if (Files.exists(relativeToGameDir)) return relativeToGameDir;

        return null;
    }

    public static Path getCustomSoundsFolder() {
        Path folder = gameDir.resolve("config").resolve("customdeathsound").resolve("custom");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            System.err.println("[CustomDeathSound] No se pudo crear la carpeta de sonidos personalizados: " + e.getMessage());
        }
        return folder;
    }
}
