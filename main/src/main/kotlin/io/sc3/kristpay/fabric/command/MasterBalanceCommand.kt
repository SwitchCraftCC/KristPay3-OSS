/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.fabric.extensions.of
import io.sc3.text.formatKristValue
import io.sc3.text.plus
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.MutableText
import net.minecraft.util.Formatting.*

@KristPayCommand
object MasterBalanceCommand: KristPayConsumer(), CommandObject {
    object Permission {
        private const val ROOT = "kristpay.masterbal"
        const val CHECK        = "$ROOT.check"
    }

    private fun execute(ctx: CommandContext<ServerCommandSource>) {
        val masterWallet = API.getMasterWallet()
        val masterBalance = masterWallet.balance
        val allocated = masterWallet.allocated
        val allocatedPercentage = allocated.amount.toFloat() / masterBalance.amount.toFloat() * 100.0f
        val unallocated = masterWallet.unallocated
        val unallocatedPercentage = unallocated.amount.toFloat() / masterBalance.amount.toFloat() * 100.0f

        ctx.source.sendFeedback({
            listOf(
                of("Master wallet information:\n", GOLD),
                of("Address: ", AQUA),
                of(masterWallet.address.address, YELLOW),
                of("\n"),
                of("Balance: ", AQUA),
                formatKristValue(masterBalance.amount, long = true),
                of("\n"),
                of("  Allocated: ", AQUA),
                formatKristValue(allocated.amount, long = true),
                of(" (", AQUA),
                of(String.format("%.1f%%", allocatedPercentage), GOLD),
                of(")", AQUA),
                of("\n"),
                of("  Unallocated: ", AQUA),
                formatKristValue(unallocated.amount, long = true),
                of(" (", AQUA),
                of(String.format("%.1f%%", unallocatedPercentage), GOLD),
                of(")", AQUA)
            ).reduce(MutableText::plus)
        }, false)
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("masterbal")
                .requiresPermission(Permission.CHECK, 3)
                .executesAsync(::execute)
        )
    }
}
