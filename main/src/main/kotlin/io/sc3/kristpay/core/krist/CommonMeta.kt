/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist

import io.sc3.kristpay.util.Memo
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

sealed interface MetaTag

@JvmInline value class UnkeyedValue(val value: String): MetaTag
@JvmInline value class KeyedValue(val value: Pair<String, String>): MetaTag

class CommonMeta(
    val tags: List<MetaTag> = mutableListOf()
): List<MetaTag> by tags {
    private val lookupMap by Memo({ tags }) { tags.mapIndexed { index, metaTag ->
        when (metaTag) {
            is UnkeyedValue -> index.toString() to metaTag.value
            is KeyedValue -> metaTag.value
        }
    }.toMap() }

    constructor(metastr: String) : this(metastr.split(";").map {
        if (it.contains("=")) KeyedValue(it.substringBefore("=") to it.substringAfter("="))
        else UnkeyedValue(it)
    })

    constructor(vararg tags: Pair<String, String>) : this(tags.map { KeyedValue(it) }.toList())

    val recipient: String? get() = lookupMap["0"]?.lowercase()
    val metaname: String? by NameDelegate(::recipient)
    val name: String? by NameDelegate(::recipient)

    val `return`: String? get() = lookupMap["return"]?.lowercase()
    val returnRecipient: String? by ::`return`
    val returnMetaname: String? by NameDelegate(::returnRecipient)
    val returnName: String? by NameDelegate(::returnRecipient)

    // Other common fields
    val message: String? get() = lookupMap["message"]
    val error: String? get() = lookupMap["error"]
    val donate: Boolean get() = lookupMap["donate"].equals("true", ignoreCase = true)

    class NameDelegate(private val addressProp: KProperty0<String?>): ReadOnlyProperty<CommonMeta, String?> {
        private val nameRegex = Regex("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})(\\.kst)$", RegexOption.IGNORE_CASE)
        override operator fun getValue(thisRef: CommonMeta, property: KProperty<*>): String? {
            if (addressProp.get()?.matches(nameRegex) != true) return null
            val address = addressProp.get()?.split("@") ?: return null
            return if (property.name.lowercase().contains("metaname")) {
                if (address.size > 1) address[0] else null
            } else {
                address.last()
            }
        }
    }

    fun get(key: String): String? = lookupMap[key]

    override fun toString(): String
        = tags.joinToString(";") {
            when (it) {
                is UnkeyedValue -> it.value
                is KeyedValue -> "${it.value.first}=${it.value.second}"
            }
        }
}
