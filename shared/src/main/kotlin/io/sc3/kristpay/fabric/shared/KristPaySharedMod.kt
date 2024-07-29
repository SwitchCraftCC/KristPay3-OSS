/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.fabric.shared

import net.fabricmc.api.ModInitializer
import io.sc3.kristpay.fabric.shared.argument.ArgumentTypeRegistrar

class KristPaySharedMod : ModInitializer {
    override fun onInitialize() {
        ArgumentTypeRegistrar.register()
    }
}
