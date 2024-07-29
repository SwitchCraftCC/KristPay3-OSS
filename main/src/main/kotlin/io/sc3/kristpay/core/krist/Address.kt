/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.krist

import io.sc3.kristpay.util.sha256

fun byteToHexChar(inp: Int): Char {
	var b = 48 + inp / 7
	if (b > 57) b += 39
	if (b > 122) b = 101
	return b.toChar()
}

val nameRegex = Regex("\\b(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kst\\b", RegexOption.IGNORE_CASE)
val addressRegex = Regex("\\bk[a-z0-9]{9}\\b")
val onlyNameRegex = Regex("^(?:([a-z0-9-_]{1,32})@)?([a-z0-9]{1,64})\\.kst\$", RegexOption.IGNORE_CASE)
val onlyAddressRegex = Regex("^k[a-z0-9]{9}\$")
fun validateDestination(destination: String?): Boolean {
	return destination !== null && (onlyNameRegex.matches(destination) || onlyAddressRegex.matches(destination))
}

// https://github.com/Lignum/JKrist
fun makeV2Address(pkey: String): String {
	val address = StringBuilder("k")
	var stick: String = sha256(sha256(pkey))
	val proteins = arrayOfNulls<String>(9)
	for (i in proteins.indices) {
		proteins[i] = stick.substring(0, 2)
		stick = sha256(sha256(stick))
	}
	var i = 0
	while (i < 9) {
		val pair = stick.substring(i * 2, i * 2 + 2)
		val index = pair.toInt(16) % 9
		if (proteins[index] == null) {
			stick = sha256(stick)
		} else {
			val protein = proteins[index]!!.toInt(16)
			address.append(byteToHexChar(protein))
			proteins[index] = null
			i++
		}
	}
	return address.toString()
}
