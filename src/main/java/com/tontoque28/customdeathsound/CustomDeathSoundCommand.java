package com.tontoque28.customdeathsound;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class CustomDeathSoundCommand extends CommandBase {

    private static final List<String> SUBCOMMANDS = Arrays.asList("list", "reset", "select", "setsound", "test");

    @Override
    public String getName() {
        return "customdeathsound";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/customdeathsound <list|reset|select <archivo>|setsound <ruta>|test>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    private static boolean rejectOnDedicatedServer(ICommandSender sender) {
        if (FMLCommonHandler.instance().getSide().isServer()) {
            sendFailure(sender, "[CustomDeathSound] Este comando solo esta disponible en el cliente.");
            return true;
        }
        return false;
    }

    private static void sendSuccess(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    private static void sendFailure(ICommandSender sender, String text) {
        TextComponentString message = new TextComponentString(text);
        message.getStyle().setColor(TextFormatting.RED);
        sender.sendMessage(message);
    }

    private static String joinFrom(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "test":
                if (rejectOnDedicatedServer(sender)) return;
                CustomSoundPlayer.playLocal();
                return;
            case "setsound": {
                if (args.length < 2) {
                    throw new WrongUsageException(getUsage(sender));
                }
                if (rejectOnDedicatedServer(sender)) return;
                String path = joinFrom(args, 1);
                Config.setCustomSoundPath(path);
                sendSuccess(sender, "[CustomDeathSound] Sonido personalizado configurado: " + path);
                CustomSoundPlayer.playLocal();
                return;
            }
            case "list": {
                List<String> files = listCustomSoundFiles();
                if (files.isEmpty()) {
                    sendSuccess(sender, "[CustomDeathSound] No hay archivos .wav en config/customdeathsound/custom/. "
                            + "Coloca uno ahí y vuelve a intentar.");
                } else {
                    sendSuccess(sender, "[CustomDeathSound] Archivos disponibles: " + String.join(", ", files)
                            + "  -> usa /customdeathsound select <archivo>");
                }
                return;
            }
            case "select": {
                if (args.length < 2) {
                    throw new WrongUsageException(getUsage(sender));
                }
                if (rejectOnDedicatedServer(sender)) return;
                String file = joinFrom(args, 1);
                Path target = Config.getCustomSoundsFolder().resolve(file);
                if (!Files.exists(target)) {
                    sendFailure(sender, "[CustomDeathSound] No se encontró \"" + file
                            + "\" en config/customdeathsound/custom/. Usa /customdeathsound list para ver los disponibles.");
                    return;
                }
                Config.setCustomSoundPath("config/customdeathsound/custom/" + file);
                sendSuccess(sender, "[CustomDeathSound] Sonido personalizado seleccionado: " + file);
                CustomSoundPlayer.playLocal();
                return;
            }
            case "reset":
                if (rejectOnDedicatedServer(sender)) return;
                Config.setCustomSoundPath("");
                sendSuccess(sender, "[CustomDeathSound] Sonido restaurado al predeterminado.");
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("select")) {
            return getListOfStringsMatchingLastWord(args, listCustomSoundFiles());
        }
        return Collections.emptyList();
    }

    static List<String> listCustomSoundFiles() {
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
}
