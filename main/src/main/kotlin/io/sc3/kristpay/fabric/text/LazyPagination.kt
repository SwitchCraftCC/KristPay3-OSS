/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.text

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import io.sc3.text.of
import io.sc3.text.pagination.ActivePagination
import io.sc3.text.pagination.PaginationHandler

// Because this pagination preserves laziness, it only supports single line text
internal class LazyPagination(
    src: ServerCommandSource,
    private val contents: List<Text>,
    title: Text? = null,
    header: Text? = null,
    footer: Text? = null,
    padding: Text = of("="),
    linesPerPage: Int = 20
) : ActivePagination(src, title, header, footer, padding, linesPerPage) {

    override fun totalPages(): Int = (contents.size + contentLinesPerPage - 1) / contentLinesPerPage

    override fun lines(page: Int): Iterable<Text> {
        if (page !in 1..totalPages()) throw PAGE_NOT_FOUND_EXCEPTION.create(page)

        val start = (page - 1) * contentLinesPerPage
        val end = (start + contentLinesPerPage).coerceAtMost(contents.size)

        val out = contents.subList(start, end).toMutableList()

        if (page == totalPages() && out.size < contentLinesPerPage) {
            // Pad the last page, but only if it isn't the first
            if (page > 1) padPage(out, out.size, false)
        }

        return out
    }

    override fun hasPrevious(page: Int) = page > 1

    override fun hasNext(page: Int) = page < totalPages()

    fun sendTo(src: ServerCommandSource, page: Int = 1) {
        PaginationHandler.paginationState(src, true).put(this)
        this.specificPage(page)
    }

    companion object {
        private val PAGE_NOT_FOUND_EXCEPTION = DynamicCommandExceptionType { of("Page $it does not exist.") }
    }
}
