/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net") { name = "Fabric" }

        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        val loomVersion: String by settings
        id("fabric-loom").version(loomVersion)
        val kotlinVersion: String by System.getProperties()
        kotlin("jvm").version(kotlinVersion)
    }
}

rootProject.name = "kristpay"

include("main")
include("api")
include("shared")
include("client")
