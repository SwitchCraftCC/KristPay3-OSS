/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.client.mixins;

import com.mojang.brigadier.ParseResults;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.command.CommandSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.sc3.kristpay.fabric.client.hud.WarningBar;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Shadow
    @Nullable
    private ParseResults<CommandSource> parse;

    @Inject(method = "refresh", at = @At("RETURN"))
    private void refresh(CallbackInfo ci) {
        WarningBar.INSTANCE.refresh(parse);
    }
}
