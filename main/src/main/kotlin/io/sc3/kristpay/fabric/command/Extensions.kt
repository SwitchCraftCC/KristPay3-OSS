/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.builder.ArgumentBuilder
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.command.ServerCommandSource

fun <T : ArgumentBuilder<ServerCommandSource, T>> T.requiresPermission(permission: String, defaultRequiredLevel: Int): T =
    this.requires(Permissions.require(permission, defaultRequiredLevel))
