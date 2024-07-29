/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

plugins {
    id("fabric-loom")
}

base {
    archivesName.set("kristpay-shared")
}

//repositories {
//    maven("https://jitpack.io") // CC:Restitched
//    maven("https://squiddev.cc/maven")
//    maven("https://maven.terraformersmc.com/releases") // Mod Menu
//}

val minecraftVersion: String by project
val minecraftTargetVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricKotlinVersion: String by project
val fabricVersion: String by project

val ccVersion: String by project
val ccMcVersion: String by project

dependencies {
    minecraft("com.mojang", "minecraft", minecraftVersion)
    mappings("net.fabricmc", "yarn", yarnMappings, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", loaderVersion)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", fabricVersion) {
        exclude("net.fabricmc.fabric-api", "fabric-gametest-api-v1")
    }
    modImplementation("net.fabricmc", "fabric-language-kotlin", fabricKotlinVersion)

    modApi("cc.tweaked:cc-tweaked-$ccMcVersion-fabric:$ccVersion") {
        exclude("net.fabricmc.fabric-api", "fabric-gametest-api-v1")
    }
}

configurations.namedElements.get().extendsFrom(configurations.implementation.get())

tasks {
    runClient { enabled = false }
    runServer { enabled = false }
}
