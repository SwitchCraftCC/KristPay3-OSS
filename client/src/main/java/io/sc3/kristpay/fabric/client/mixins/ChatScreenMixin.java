/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.mixins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.sc3.kristpay.fabric.client.hud.WarningBar;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void render(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        WarningBar.INSTANCE.render((ChatScreen)(Object)this, ctx);
    }
}
