/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.sc3.kristpay.api.KristPayConsumer
import io.sc3.kristpay.api.UserID
import io.sc3.kristpay.api.model.*
import io.sc3.kristpay.api.model.frontend.TransactionSymbol
import io.sc3.kristpay.api.model.util.lazyMap
import io.sc3.kristpay.core.config.CONFIG
import io.sc3.kristpay.core.krist.RESOLVED_TO_ADDRESS
import io.sc3.kristpay.core.plugin.ASSOCIATED_ORDER_ID
import io.sc3.kristpay.core.plugin.PRODUCT_ID
import io.sc3.kristpay.core.plugin.REQUESTER_NAME_ID
import io.sc3.kristpay.core.welfare.IS_REVERSED
import io.sc3.kristpay.core.welfare.REVERSAL_OF
import io.sc3.kristpay.core.welfare.WELFARE_TX_CLASS
import io.sc3.kristpay.core.welfare.WelfareType
import io.sc3.kristpay.fabric.FabricFrontend
import io.sc3.kristpay.fabric.command.RevertCommand.MANUAL_REVERSAL_MARKER
import io.sc3.kristpay.fabric.config.STYLE
import io.sc3.kristpay.fabric.extensions.*
import io.sc3.kristpay.fabric.text.LazyPagination
import io.sc3.kristpay.fabric.text.buildSpacedText
import io.sc3.kristpay.fabric.text.buildText
import io.sc3.kristpay.util.intercalate
import io.sc3.text.callback
import io.sc3.text.formatKristValue
import io.sc3.text.hover
import io.sc3.text.pagination.Pagination
import io.sc3.text.plural
import io.sc3.text.plus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import mu.KLoggable
import mu.KLogging
import net.minecraft.command.argument.GameProfileArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting.*
import java.time.format.DateTimeFormatter

internal val transactionSymbolHandlers = mutableSetOf<(KristPayWallet, TransactionSnapshot) -> TransactionSymbol?>()
internal val transactionDescriptorHandlers = mutableSetOf<(KristPayWallet, TransactionSnapshot) -> List<String>?>()

@KristPayCommand
object ListTransactionsCommand : KristPayConsumer(), CommandObject, KLoggable by KLogging() {
    object Permission {
        private const val ROOT = "kristpay.transactions"
        const val LIST         = "$ROOT.list.base"
        const val LIST_OTHER   = "$ROOT.list.others"
    }

    object KristWebUrl {
        private const val BASE = "https://krist.club/network/"
        fun transaction(id: Int)     = BASE + "transactions/$id"
        fun address(address: String) = BASE + "addresses/$address"
        fun name(name: String)       = BASE + "names/$name"
    }

    private fun execute(ctx: CommandContext<ServerCommandSource>) {
        val wallet = API.getDefaultWallet(ctx.source.playerOrThrow.uuid)!!
        val you = KristPayWallet(wallet)
        val txs = API.listTransactions(wallet)

        val title = of(txs.size.toString(), STYLE.accent) + of(" transaction".plural(txs.size), STYLE.primary)
        val callbackOwner = ctx.source.player?.uuid
        val contents = txs.lazyMap { formatTransactionLine(it, you, you)
            .callback(owner = callbackOwner, name = "View Transaction ${it.id}") { cctx ->
                viewTransaction(you, you, it, cctx)
            }
        }

        LazyPagination(ctx.source, contents, title).sendTo(ctx.source, 1)
    }

    private fun executeOther(ctx: CommandContext<ServerCommandSource>) {
        val players = GameProfileArgumentType.getProfileArgument(ctx, "player")
        if (players.size != 1) {
            ctx.source.sendError(of("You must specify exactly one player.", STYLE.error))
            return
        }

        val you = ctx.source.player?.let { KristPayWallet(API.getDefaultWallet(it.uuid)!!) }

        val wallet = API.getDefaultWallet(players.first().id)
            ?: return ctx.source.sendError(of("Could not find specified player.", STYLE.error))

        val target = KristPayWallet(wallet)

        val txs = API.listTransactions(wallet)

        val title = of("(", STYLE.primary) + of(players.first().name, STYLE.address) + ") " +
                of(txs.size.toString(), STYLE.accent) + " transaction".plural(txs.size)
        val callbackOwner = ctx.source.player?.uuid
        val contents = txs.lazyMap { formatTransactionLine(it, target, you)
            .callback(owner = callbackOwner, name = "View Transaction ${it.id}") { cctx ->
                viewTransaction(target, you, it, cctx)
            }
        }
        LazyPagination(ctx.source, contents, title).sendTo(ctx.source, 1)
    }

    private fun formatTransactionLine(
        it: TransactionSnapshot,
        subject: KristPayWallet,
        you: KristPayWallet?
    ) = listOf(
            of(formatInstant(it.timestamp), STYLE.primary),
            getTransactionSymbol(subject, it).let { s ->
                of(s.symbol, byName(s.cooperativeColor) ?: WHITE, BOLD)
                    .hover(of(s.description, byName(s.attentionColor) ?: WHITE))
            },
            *formatDescriptor(it, subject, you).toTypedArray()
        )
            .intercalate(of(" ", STYLE.primary))
            .reduce(MutableText::plus)
            .styleForStatus(it)

    private fun formatDescriptor(snapshot: TransactionSnapshot, subject: KristPayWallet, you: KristPayWallet?): List<MutableText> {
        // Check if a plugin wants to override the transaction descriptor first
        transactionDescriptorHandlers.asSequence()
            .mapNotNull { it(subject, snapshot) }
            .firstOrNull()
            ?.let { texts ->
                // Deserialize the JSON strings into MutableText objects
                return texts.mapNotNull { Text.Serializer.fromJson(it) }
            }

        when (snapshot.systemMetadata?.getString("command")) {
            "setbal" -> return formatSetBal(snapshot)
            "grant" -> return formatGrant(snapshot)
        }

        when (snapshot.systemMetadata?.getString(WELFARE_TX_CLASS)) {
            WelfareType.STARTING_BALANCE.name -> return listOf(
                of("Starting balance of", YELLOW),
                formatKristValue(snapshot.amount.amount, false, GREEN)
            )
            WelfareType.LOGIN_BONUS.name, WelfareType.ROLLING_DAILY_BONUS.name -> return listOf(
                of("Redeemed", YELLOW),
                formatKristValue(snapshot.amount.amount, false, GREEN),
                of("via Basic Income", YELLOW)
            )
            WelfareType.FAUCET.name -> return listOf(
                of("Redeemed", YELLOW),
                formatKristValue(snapshot.amount.amount, false, GREEN),
                of("via Faucet", YELLOW)
            )
            WelfareType.REVERSAL.name -> return listOf(
                of("Returned", GOLD),
                formatKristValue(snapshot.amount.amount, false, GREEN),
                of("to", GOLD),
                of(CONFIG.welfare.bankName, GOLD)
            )
        }

        snapshot.systemMetadata?.getObject(PRODUCT_ID)?.let { productObject ->
            return listOf(formatProduct(productObject, snapshot))
        }

        return listOf(
            formatActor(snapshot.from, you, snapshot),
            of("\u27a1", WHITE),
            formatKristValue(snapshot.amount.amount, false, GREEN),
            of("\u27a1", WHITE),
            formatActor(snapshot.to, you, snapshot)
        )
    }

    private fun formatProduct(productObject: JsonObject, snapshot: TransactionSnapshot): MutableText {
        val product = Json.decodeFromJsonElement<ServiceProduct>(productObject)
        val wasReversed = snapshot.systemMetadata?.getBoolean(IS_REVERSED) ?: false
        val reversalOf = snapshot.systemMetadata?.getArray(REVERSAL_OF) != null

        return if (reversalOf) {
            buildSpacedText {
                +YELLOW
                +"Refund of" + formatKristValue(snapshot.amount.amount, false, GREEN)
                +"for" + of(product.friendlyName, GOLD)
            }
        } else {
            buildSpacedText {
                +YELLOW
                +"Purchased" + of(product.friendlyName, GOLD) + "for"
                +formatKristValue(snapshot.amount.amount, false, GREEN)
            }.styled { it.withStrikethrough(wasReversed) }
        }
    }

    private fun formatSetBal(snapshot: TransactionSnapshot): List<MutableText> {
        val newBalance = snapshot.systemMetadata?.getInt("new_balance")?.let { formatKristValue(it) } ?: of("?", STYLE.error)
        val oldBalance = snapshot.systemMetadata?.getInt("old_balance")?.let { formatKristValue(it) } ?: of("?", STYLE.error)
        return listOf(
            of("Balance set to ", YELLOW) + newBalance + of(" (was ", YELLOW) + oldBalance + of(")", YELLOW)
        )
    }

    private fun formatGrant(snapshot: TransactionSnapshot): List<MutableText> =
        if (snapshot.from == KristPayUnallocated) {
            listOf(of("Granted ", YELLOW) + formatKristValue(snapshot.amount.amount))
        } else {
            listOf(of("Deducted ", RED) + formatKristValue(snapshot.amount.amount))
        }

    private fun formatInstant(instant: Instant)
        = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")
            .format(instant.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime())

    fun formatActor(actor: PaymentActor, you: KristPayWallet?, tx: TransactionSnapshot, long: Boolean = false): MutableText {
        return when (actor) {
            is KristPayWallet -> {
                val productRequester = tx.systemMetadata?.getString(REQUESTER_NAME_ID)
                if (actor == you) {
                    of("You", GOLD, ITALIC)
                } else if (productRequester != null) {
                    of(productRequester, GOLD, ITALIC)
                } else {
                    of(API.getWalletName(actor.walletId) ?: "<???>", GREEN)
                }
            }
            is KristPayUnallocated -> {
                of(CONFIG.welfare.bankName, AQUA)
            }
            is KristAddress -> {
                val base = if (actor == tx.from) {
                    val initiator = tx.initiator as? Initiator.KristAddress
                    if (initiator != null && initiator.address != actor.address) {
                        KristAddress(initiator.address)
                    } else {
                        actor
                    }
                } else {
                    actor
                }

                if (long) {
                    buildText {
                        +of(base.address, STYLE.address).openUrl(urlForAddress(base))
                        tx.systemMetadata?.getString(RESOLVED_TO_ADDRESS)?.let {
                            +of(" (", WHITE)
                            +of(it, STYLE.address)
                                .openUrl(KristWebUrl.address(it))
                            +of(")", WHITE)
                        }
                        if (base != actor) {
                            +of(" (", WHITE)
                            +of(actor.address, STYLE.address)
                                .openUrl(urlForAddress(actor))
                            +of(")", WHITE)
                        }
                    }
                } else {
                    of(base.address, STYLE.address)
                        .openUrl(urlForAddress(base))
                }
            }
        }
    }

    private fun urlForAddress(base: KristAddress)
        = if (base.isName) KristWebUrl.name(base.name!!)
          else             KristWebUrl.address(base.address)


    object FakeTransactionType : TransactionSymbol {
        override val symbol = "A"
        override val description = "Administrative Action"
        override val attentionColor = GOLD.name
        override val cooperativeColor = GRAY.name
    }

    object ManualReversalTransactionType : TransactionSymbol {
        override val symbol = "M"
        override val description = "Manual Reversal"
        override val attentionColor = RED.name
    }

    object WelfareTransactionType : TransactionSymbol {
        override val symbol = "F"
        override val description = "Welfare Reward"
        override val attentionColor = AQUA.name
    }

    object WelfareReversalTransactionType : TransactionSymbol {
        override val symbol = "R"
        override val description = "Welfare Reversal"
        override val attentionColor = RED.name
    }

    object ProductTransactionType : TransactionSymbol {
        override val symbol = "P"
        override val description = "Server Product Payment"
        override val attentionColor = GOLD.name
    }

    object WithdrawalTransactionType : TransactionSymbol {
        override val symbol = "W"
        override val description = "Withdrawal"
        override val attentionColor = RED.name
    }

    object DepositTransactionType : TransactionSymbol {
        override val symbol = "D"
        override val description = "Deposit"
        override val attentionColor = DARK_GREEN.name
    }

    object UnknownTransactionType : TransactionSymbol {
        override val symbol = "?"
        override val description = "Unknown"
        override val attentionColor = GRAY.name
    }

    private fun getTransactionSymbol(subject: KristPayWallet, s: TransactionSnapshot): TransactionSymbol {
        // Check if a plugin wants to override the transaction symbol first
        transactionSymbolHandlers.asSequence()
            .mapNotNull { it(subject, s) }
            .firstOrNull()
            ?.let { return it }

        return when {
            s.systemMetadata?.getBoolean("fake") == true -> FakeTransactionType
            s.systemMetadata?.getBoolean(MANUAL_REVERSAL_MARKER) == true -> ManualReversalTransactionType
            s.systemMetadata?.getString(WELFARE_TX_CLASS) == WelfareType.REVERSAL.name -> WelfareReversalTransactionType
            s.systemMetadata?.getString(WELFARE_TX_CLASS) != null -> WelfareTransactionType
            s.systemMetadata?.getString(ASSOCIATED_ORDER_ID) != null -> ProductTransactionType
            s.from == subject -> WithdrawalTransactionType
            s.to == subject -> DepositTransactionType
            else -> UnknownTransactionType
        }
    }

    private fun isTransactionParticipant(user: UserID, transaction: TransactionSnapshot): Boolean {
        val wallet = KristPayWallet(API.getDefaultWallet(user)!!)
        return transaction.from == wallet || transaction.to == wallet
    }

    private fun viewTransaction(subject: KristPayWallet, you: KristPayWallet?, tx: TransactionSnapshot, ctx: CommandContext<ServerCommandSource>) {
        val extras = FabricFrontend.generateSpecialText(tx)
        val type = getTransactionSymbol(subject, tx)
        val typeColor = byName(type.attentionColor) ?: WHITE
        val content = listOfNotNull(
            of("Type: ", STYLE.primary) + of(type.description, typeColor, BOLD) + buildText {
                +of(" (", WHITE)
                +when (tx.state) {
                    TransactionState.COMPLETE -> of("success", STYLE.success)
                    TransactionState.FAILED -> of("failed", STYLE.error)
                    else -> of("pending", STYLE.address)
                }
                +of(")", WHITE)
            },
            tx.systemMetadata?.getObject(PRODUCT_ID)?.let { product ->
                of("Product: ", STYLE.primary) + formatProduct(product, tx)
            },
            of("Date: ", STYLE.primary) + of(formatInstant(tx.timestamp), STYLE.accent),
            of("Amount: ", STYLE.primary) + buildSpacedText {
                +formatKristValue(tx.amount.amount, true, STYLE.accent)
                if (tx.externalReference != null) {
                    +of("(", WHITE)
                    -of("Krist TXID:", GRAY)
                    +of(tx.externalReference.toString(), STYLE.accent)
                        .openUrl(KristWebUrl.transaction(tx.externalReference!!))
                    -of(")", WHITE)
                }
            },
            of("From: ", STYLE.primary) + formatActor(tx.from, you, tx, true),
            of("To: ", STYLE.primary) + formatActor(tx.to, you, tx, true),
            tx.metadata?.let {
                of("Metadata: ", STYLE.primary) + of(tx.metadata, STYLE.note)
            }
        ) + extras

        Pagination(
            content,
            title= of("Transaction", STYLE.primary) + of(" ${tx.id}", STYLE.accent)
        ).sendTo(ctx.source)
    }

    override fun register(dispatcher: CommandDispatcher<ServerCommandSource?>) {
        val aliases = listOf("transactions", "txs")

        aliases.forEach {
            dispatcher.register(
                literal(it)
                    .then(
                        CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .requiresPermission(Permission.LIST_OTHER, 3)
                            .executesAsync(::executeOther)
                    )
                    .requiresPermission(Permission.LIST, 0)
                    .executesAsync(::execute)
            )
        }
    }
}

private fun MutableText.styleForStatus(it: TransactionSnapshot): MutableText {
    return when(it.state) {
        TransactionState.COMPLETE -> this
        TransactionState.FAILED -> removeAllStyle(this).styled { it.withColor(DARK_RED).withItalic(true).withStrikethrough(true) }
            .hover(of("Transaction failed!", RED, ITALIC))

        // All the other states are intermediary pending states
        else -> removeAllStyle(this).styled { it.withColor(DARK_GRAY).withItalic(true) }
            .hover(of("Transaction is pending...", GRAY, ITALIC))
    }
}

private fun removeAllStyle(text: MutableText): MutableText {
    return text.withoutStyle().map{ it.copyContentOnly() }.reduce(MutableText::plus)
}
