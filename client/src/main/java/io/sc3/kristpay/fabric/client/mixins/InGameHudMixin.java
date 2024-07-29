/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.mixins;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.sc3.kristpay.fabric.client.config.ClientConfig;
import io.sc3.kristpay.fabric.client.hud.BalanceHud;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private PlayerListHud playerListHud;

    @Inject(
      method = "render",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderAutosaveIndicator(Lnet/minecraft/client/gui/DrawContext;)V")
    )
    private void render(DrawContext ctx, float tickDelta, CallbackInfo ci) {
        ClientConfig.BalanceHUDMode mode = ClientConfig.INSTANCE.getConfig$kristpay_client().getEnum("show_balance_hud", ClientConfig.BalanceHUDMode.class);
        if (mode == ClientConfig.BalanceHUDMode.ALWAYS) {
            BalanceHud.render(client, ctx, client.getLastFrameDuration());
        } else if (mode == ClientConfig.BalanceHUDMode.TAB_MENU) {
            if (playerListHud.visible || BalanceHud.shouldForceRender()) {
                BalanceHud.render(client, ctx, client.getLastFrameDuration());
            }
        }
    }
}
