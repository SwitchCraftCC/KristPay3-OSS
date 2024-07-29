/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.util.supervised
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

val commandScope = CoroutineScope(Dispatchers.IO.supervised())

val logger = KotlinLogging.logger { }
fun <S, T : ArgumentBuilder<S, T>> ArgumentBuilder<S, T>.executesAsync(command: suspend (ctx: CommandContext<S>) -> Unit): T
    = this.executes {
        commandScope.launch {
            try {
                command(it)
            } catch (e: Exception) {
                logger.error(e) { "OOPSIE WOOPSIE!! Uwu We made a fucky wucky!! A wittle fucko boingo! The code monkeys at our headquarters are working VEWY HAWD to fix this!" }
            }
        }

        Command.SINGLE_SUCCESS
    }
