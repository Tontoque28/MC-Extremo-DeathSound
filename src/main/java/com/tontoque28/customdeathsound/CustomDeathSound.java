package com.tontoque28.customdeathsound;

import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mod(modid = CustomDeathSound.MODID, name = "Custom DeathSound", version = "1.0.0")
public class CustomDeathSound {

    public static final String MODID = "customdeathsound";

    public static final SoundEvent DEATH_SOUND = new SoundEvent(new ResourceLocation(MODID, "death_sound"))
            .setRegistryName(new ResourceLocation(MODID, "death_sound"));

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.init(event.getModConfigurationDirectory(), event.getSuggestedConfigurationFile(), event.getSide() == Side.CLIENT);
        Config.getCustomSoundsFolder();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CustomDeathSoundCommand());
    }

    @SubscribeEvent
    public void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(DEATH_SOUND);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity() instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) event.getEntity();
                if (!player.world.isRemote) {
                    MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                    if (server != null) {
                        List<EntityPlayerMP> playersSafe = new ArrayList<>(server.getPlayerList().getPlayers());

                        for (EntityPlayerMP p : playersSafe) {
                            if (p != null && p.connection != null) {
                                p.connection.sendPacket(new SPacketSoundEffect(
                                        DEATH_SOUND, SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, 1.0F, 1.0F));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CustomDeathSound] Error playing death sound: " + e.getMessage());
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, value = Side.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
            if (event.getGui() instanceof GuiGameOver) {
                GuiGameOver deathScreen = (GuiGameOver) event.getGui();
                CustomSoundPlayer.playLocal();

                try {
                    Field targetField = null;

                    for (Field f : GuiGameOver.class.getDeclaredFields()) {
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
                            String rawText = causeOfDeath.getUnformattedText()
                                    .replace("☠", "")
                                    .trim();

                            ITextComponent hardcoreDeathText = new TextComponentString("☠ " + rawText + " ☠")
                                    .setStyle(new Style().setColor(TextFormatting.RED).setBold(true));

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

                if (msg instanceof TextComponentTranslation) {
                    TextComponentTranslation translatable = (TextComponentTranslation) msg;
                    if (translatable.getKey().startsWith("death.")) {
                        isDeathMessage = true;
                    }
                }

                if (!isDeathMessage) {
                    String text = msg.getUnformattedText();
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
                    String rawText = msg.getUnformattedText()
                            .replace("☠", "")
                            .trim();

                    ITextComponent hardcoreMessage = new TextComponentString("☠ " + rawText + " ☠")
                            .setStyle(new Style().setColor(TextFormatting.RED).setBold(true));

                    event.setMessage(hardcoreMessage);
                }
            } catch (Exception e) {
            }
        }
    }
}
