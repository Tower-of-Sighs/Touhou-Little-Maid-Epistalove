package com.sighs.touhou_little_maid_epistalove.mixin;

import com.sighs.touhou_little_maid_epistalove.util.mixin.IServerPlayerLanguage;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin implements IServerPlayerLanguage {
    @Unique
    private String language = "en_us";

    @Override
    public String getLanguage() {
        return this.language;
    }

    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void onUpdateOptions(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        this.language = packet.language();
    }
}