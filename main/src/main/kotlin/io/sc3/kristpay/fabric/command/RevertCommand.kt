/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.core.kpdb
import io.sc3.kristpay.core.plugin.PRODUCT_ID
import io.sc3.kristpay.core.welfare.IS_REVERSED
import io.sc3.kristpay.core.welfare.REVERSAL_OF
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.*
import io.sc3.kristpay.model.Transaction
import kotlinx.serialization.json.*
import mu.KLoggable
import mu.KLogging
import net.minecraft.command.argument.UuidArgumentType
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

@KristPayCommand
object RevertCommand : KristPayConsumer(), CommandObject, KLoggable by KLogging() {
    object Permission {
        private const val ROOT = "kristpay.revert"
        const val REVERT_TX    = "$ROOT.base"
    }

    const val MANUAL_REVERSAL_MARKER = "manualRevert"

    private fun revertTransaction(ctx: CommandContext<ServerCommandSource>) = transaction(kpdb) {
        ctx.source.sendFeedback({ of("I sure hope you know what you're doing...", STYLE.danger) }, false)

        val txId = UuidArgumentType.getUuid(ctx, "transaction-id")
        val tx = Transaction.findById(txId) ?: run {
            ctx.source.sendFeedback({ of("Transaction not found", STYLE.warning) }, false)
            return@transaction
        }

        // Validate we can revert it
        when (tx.to) {
            is KristPayUnallocated, is KristPayWallet -> { /* Controlled */ }
            else -> {
                ctx.source.sendFeedback({ of("Cannot revert transaction to ${tx.to}", STYLE.warning) }, false)
                return@transaction
            }
        }

        if (tx.systemMetadata?.getBoolean(IS_REVERSED) == true) {
            ctx.source.sendFeedback({ of("Transaction already reverted", STYLE.warning) }, false)
            return@transaction
        }

        val txToExplore = mutableListOf(tx)
        val txToRevert = mutableListOf<Transaction>()

        while (txToExplore.isNotEmpty()) {
            val currentTx = txToExplore.removeFirst()
            txToRevert.add(currentTx)

            // First check if it was a reversal, as we need to un-mark those as reverted
            currentTx.systemMetadata?.getArray(REVERSAL_OF)?.let { array ->
                val affectedTxIds = array.map { it.runCatching { UUID.fromString(this.jsonPrimitive.contentOrNull) }.getOrNull() }
                if (affectedTxIds.any { it == null }) {
                    ctx.source.sendFeedback({ of("Invalid reversal metadata, manual intervention required", STYLE.warning) }, false)
                    return@transaction
                }

                val affectedTxs = Transaction.forIds(affectedTxIds.filterNotNull())
                if (affectedTxs.count().toInt() != affectedTxIds.count()) {
                    ctx.source.sendFeedback({ of("Could not find sub-tx, manual intervention required", STYLE.warning) }, false)
                    return@transaction
                }

                txToExplore.addAll(affectedTxs)
            }
        }

        logger.info("Found ${txToRevert.count()} transactions to revert based on ${tx.id}")

        // Lets get flippin
        txToRevert.forEach {
            if (it.systemMetadata?.getBoolean(IS_REVERSED) == true) {
                it.systemMetadata = it.systemMetadata?.filter { (k, _) -> k != IS_REVERSED }?.let { o -> JsonObject(o) }
            } else {
                it.systemMetadata = JsonObject((it.systemMetadata ?: emptyMap()).plus(Pair(IS_REVERSED, JsonPrimitive(true))))
            }
        }

        API.initializeTransaction(
            from = tx.to,
            to = tx.from,
            amount = MonetaryAmount(tx.amount),
            initiator = ctx.source.player?.let { Initiator.User(it.uuid) } ?: Initiator.Server,
            systemMetadata = buildJsonObject {
                put(MANUAL_REVERSAL_MARKER, true)
                putJsonArray(REVERSAL_OF) {
                    add(tx.id.value.toString())
                }

                tx.systemMetadata?.getObject(PRODUCT_ID)?.let { productObject ->
                    put(PRODUCT_ID, productObject)
                }
            },
            allowDebt = true // Allow balance to go negative
        )

        ctx.source.sendFeedback({ of("Reverted transaction (${txToRevert.count()} sub-transactions)", STYLE.success) }, false)
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        dispatcher.register(
            literal("reverttx")
                .requiresPermission(Permission.REVERT_TX, 3)
                .then(
                    literal("I_KNOW_WHAT_IM_DOING").then(
                        argument("transaction-id", UuidArgumentType.uuid())
                            .executesAsync(RevertCommand::revertTransaction)
                    )
                )
        )
    }
}
