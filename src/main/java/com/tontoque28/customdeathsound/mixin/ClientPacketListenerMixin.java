package com.tontoque28.customdeathsound.mixin;

import com.tontoque28.customdeathsound.CustomDeathSoundClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Redirect(method = "handleChat", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;handleChat(Lnet/minecraft/network/chat/ChatType;Lnet/minecraft/network/chat/Component;Ljava/util/UUID;)V"))
    private void customdeathsound$transformChat(Gui gui, ChatType type, Component message, UUID sender) {
        gui.handleChat(type, type == ChatType.SYSTEM ? CustomDeathSoundClient.transform(message) : message, sender);
    }
}
