/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

plugins {
    id("fabric-loom")
}

base {
    archivesName.set("kristpay-client")
}

val minecraftVersion: String by project
val minecraftTargetVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricKotlinVersion: String by project
val fabricVersion: String by project
val clothApiVersion: String by project
val clothConfigVersion: String by project
val modMenuVersion: String by project
val scTextVersion: String by project

dependencies {
    minecraft("com.mojang", "minecraft", minecraftVersion)
    mappings("net.fabricmc", "yarn", yarnMappings, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", loaderVersion)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", fabricVersion) {
        exclude("net.fabricmc.fabric-api", "fabric-gametest-api-v1")
    }
    modImplementation("net.fabricmc", "fabric-language-kotlin", fabricKotlinVersion)

    modApi("me.shedaniel.cloth:cloth-config-fabric:$clothConfigVersion") {
        exclude("net.fabricmc.fabric-api")
    }
    include("me.shedaniel.cloth", "cloth-config-fabric", clothConfigVersion)
    modImplementation(include("me.shedaniel.cloth.api", "cloth-utils-v1", clothApiVersion))

    modImplementation(include("com.terraformersmc", "modmenu", modMenuVersion))

    include(project(path = ":shared"))
    implementation(project(path = ":shared", configuration = "namedElements"))

    modImplementation(include("io.sc3", "sc-text", scTextVersion))
}

loom {
    accessWidenerPath.set(file("src/main/resources/kristpay-client.accesswidener"))
}

configurations.namedElements.get().extendsFrom(configurations.implementation.get())

tasks {
    runClient { enabled = true
        args("--uuid d5448ded-95ca-4de7-b174-e116b6b63eb7 --username anemonemma".split(' '))
    }
    runServer { enabled = false }
}
