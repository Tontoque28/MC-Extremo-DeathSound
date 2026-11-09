package com.tontoque28.customdeathsound;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {

    private static final String FILE_NAME = "customdeathsound-client.toml";
    private static final Pattern ENTRY = Pattern.compile("^\\s*customSoundPath\\s*=\\s*(.*?)\\s*$");

    private static final String HEADER = String.join("\n",
            "#Custom DeathSound - Configuración de sonido personalizado",
            "#",
            "#customSoundPath: ruta a un archivo .wav que quieras usar como sonido",
            "#de muerte en lugar del sonido predeterminado (el clásico sonido Hardcore).",
            "#",
            "#- Deja el valor vacío (\"\") para usar siempre el sonido por defecto.",
            "#- Puede ser una ruta absoluta (ej: C:/Users/tu_usuario/Music/mi_sonido.wav)",
            "#  o relativa a la carpeta del juego (ej: config/customdeathsound/mi_sonido.wav).",
            "#- Solo se admite formato .WAV (limitación de la librería de audio de Java).",
            "#- Este ajuste solo afecta el sonido que TÚ escuchas cuando TÚ mueres.",
            "#  Otros jugadores del servidor seguirán escuchando el sonido por defecto",
            "#  cuando alguien muera, a menos que ellos mismos configuren el suyo.",
            "[sound]",
            "\t#Ruta al archivo .wav personalizado. Vacío = usar el sonido por defecto.");

    private static volatile String cachedPath = "";

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char next = raw.charAt(i + 1);
                if (next == '\\' || next == '"') {
                    out.append(next);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String parseValue(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return unescape(text.substring(1, text.length() - 1));
        }
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1);
        }
        return "";
    }

    private static void writeFile(String value) throws IOException {
        Path file = configFile();
        Files.createDirectories(file.getParent());
        String content = HEADER + "\n\tcustomSoundPath = \"" + escape(value) + "\"\n";
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized void refreshCache() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) {
                writeFile("");
                cachedPath = "";
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String found = "";
            for (String line : lines) {
                if (line.trim().startsWith("#")) continue;
                Matcher matcher = ENTRY.matcher(line);
                if (matcher.matches()) {
                    found = parseValue(matcher.group(1));
                    break;
                }
            }
            cachedPath = found;
        } catch (Exception e) {
            cachedPath = "";
        }
    }

    public static String getCustomSoundPath() {
        return cachedPath == null ? "" : cachedPath;
    }

    public static synchronized void setCustomSoundPath(String path) {
        try {
            writeFile(path);
        } catch (IOException e) {
            System.err.println("[CustomDeathSound] No se pudo guardar la configuracion: " + e.getMessage());
        }
        cachedPath = path;
    }

    public static Path resolveCustomSoundPath() {
        String raw = getCustomSoundPath();
        if (raw == null || raw.trim().isEmpty()) return null;

        try {
            Path direct = Paths.get(raw);
            if (Files.exists(direct)) return direct;

            Path relativeToGameDir = FabricLoader.getInstance().getGameDir().resolve(raw);
            if (Files.exists(relativeToGameDir)) return relativeToGameDir;
        } catch (InvalidPathException e) {
            return null;
        }

        return null;
    }

    public static Path getCustomSoundsFolder() {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("config").resolve("customdeathsound").resolve("custom");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            System.err.println("[CustomDeathSound] No se pudo crear la carpeta de sonidos personalizados: " + e.getMessage());
        }
        return folder;
    }
}
