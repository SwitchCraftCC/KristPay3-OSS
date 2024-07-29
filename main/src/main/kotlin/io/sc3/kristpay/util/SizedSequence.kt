/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import io.sc3.kristpay.api.model.util.SizedSequence
import kotlin.coroutines.*
import kotlin.properties.Delegates
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

fun <T> sizedSequence(generator: suspend SequenceScope<T>.() -> Unit): SizedSequence<T> {
    return SizedSequenceImpl<T>().apply {
        generator.createCoroutineUnintercepted(receiver = this, completion = this).resume(Unit)
    }
}

interface SequenceScope<T> {
    fun yieldCount(count: Int)
    fun yield(value: T)
    suspend fun yieldPageBreak()
}

private enum class State {
    NOT_READY, READY, DONE, FAILED
}

@RestrictsSuspension
private class SizedSequenceImpl<T>: SequenceScope<T>, SizedSequence<T>, Continuation<Unit>, Iterator<T> {
    override var size by Delegates.notNull<Int>(); private set
    val items = mutableListOf<T>()
    var nextItem: Int = 0

    var state: State = State.NOT_READY

    var nextStep: Continuation<Unit>? = null

    override fun iterator(): Iterator<T> = this

    override fun hasNext(): Boolean {
        while (true) {
            when (state) {
                State.NOT_READY -> {}
                State.DONE -> return false
                State.READY -> return true
                State.FAILED -> throw IllegalStateException("Failed")
            }

            state = State.FAILED
            val step = nextStep!!
            nextStep = null
            step.resume(Unit)
        }
    }

    override fun next(): T {
        return when (state) {
            State.NOT_READY -> nextNotReady()
            State.READY -> {
                if (nextItem == items.lastIndex) {
                    state = State.NOT_READY // Revealing the last item
                }

                items[nextItem++]
            }

            State.DONE -> throw NoSuchElementException()
            State.FAILED -> throw IllegalStateException("Failed")
        }
    }

    private fun nextNotReady(): T {
        if (!hasNext()) throw NoSuchElementException() else return next()
    }

    // This gets called as a continuation when the generator completes
    override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow() // just rethrow exception if it is there
        state = State.DONE // If this doesn't get changed, we know we're done
    }

    override fun yieldCount(count: Int) {
        size = count
    }

    override fun yield(value: T) {
        items.add(value)
        state = State.READY
    }

    override suspend fun yieldPageBreak() {
        return suspendCoroutineUninterceptedOrReturn { c ->
            nextStep = c
            COROUTINE_SUSPENDED
        }
    }

    override val context = EmptyCoroutineContext
}
