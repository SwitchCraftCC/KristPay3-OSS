/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

//apply(plugin = "kotlinx-atomicfu")
plugins {
    id("fabric-loom")
}

base {
    archivesName.set("kristpay")
}

//repositories {
//    maven("https://oss.sonatype.org/content/repositories/snapshots")
//    maven("https://squiddev.cc/maven")
//    maven("https://maven.terraformersmc.com/releases") // Mod Menu
//}

val minecraftVersion: String by project
val minecraftTargetVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricKotlinVersion: String by project
val fabricVersion: String by project
val kotlinSerializationVersion: String by project

val kloggerVersion: String by project
val annotationsVersion: String by project
val datetimeVersion: String by project
val ktomlVersion: String by project
val okioVersion: String by project
val hikariVersion: String by project
val postgresqlVersion: String by project
val exposedCore: String by project
val classgraphVersion: String by project
val ktorVersion: String by project
val prometheusVersion: String by project
val kjobVersion: String by project
val cronutilsVersion: String by project

val luckpermsApiVersion: String by project
val fabricPermissionsApiVersion: String by project

val ccVersion: String by project
val ccTargetVersion: String by project
val scTextVersion: String by project

val transitiveInclude: Configuration by configurations.creating {
    exclude(group = "com.mojang")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    implementation(include("com.akuleshov7", "ktoml-core-jvm", ktomlVersion))
    implementation(include("com.akuleshov7", "ktoml-file-jvm", ktomlVersion))
    implementation(include("com.squareup.okio", "okio-jvm", okioVersion))
    implementation(include("com.zaxxer", "HikariCP", hikariVersion))
    implementation(include("org.postgresql", "postgresql", postgresqlVersion))
    implementation(include("org.xerial", "sqlite-jdbc", "3.39.3.0"))
//    implementation(include("org.mariadb.jdbc", "mariadb-java-client", "3.4.0"))
    implementation(include("org.jetbrains.exposed", "exposed-core", exposedCore))
    implementation(include("org.jetbrains.exposed", "exposed-dao", exposedCore))
    implementation(include("org.jetbrains.exposed", "exposed-jdbc", exposedCore))
    implementation(include("org.jetbrains.exposed", "exposed-kotlin-datetime", exposedCore))
    implementation(include("io.github.classgraph","classgraph", classgraphVersion))
    transitiveInclude(implementation("io.ktor", "ktor-client-core", ktorVersion))
    transitiveInclude(implementation("io.ktor", "ktor-client-cio", ktorVersion))
    transitiveInclude(implementation("io.ktor", "ktor-client-websockets", ktorVersion))
    implementation(include("io.prometheus", "simpleclient", prometheusVersion))
    implementation(include("io.prometheus", "simpleclient_hotspot", prometheusVersion))
    implementation(include("io.prometheus", "simpleclient_httpserver", prometheusVersion))

    include(kotlin("reflect"))

    include("org.jetbrains", "annotations", annotationsVersion)
    include("org.jetbrains.kotlinx", "kotlinx-serialization-core-jvm", kotlinSerializationVersion)
    include("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", kotlinSerializationVersion)
    include("org.jetbrains.kotlinx", "kotlinx-datetime-jvm", datetimeVersion)
    include("io.github.microutils", "kotlin-logging-jvm", kloggerVersion)

    minecraft("com.mojang", "minecraft", minecraftVersion)
    mappings("net.fabricmc", "yarn", yarnMappings, null, "v2")
    modImplementation("net.fabricmc", "fabric-loader", loaderVersion)
    modImplementation("net.fabricmc.fabric-api", "fabric-api", fabricVersion) {
        exclude("net.fabricmc.fabric-api", "fabric-gametest-api-v1")
    }
    modImplementation("net.fabricmc", "fabric-language-kotlin", fabricKotlinVersion)

    modImplementation("me.lucko", "fabric-permissions-api", fabricPermissionsApiVersion)

    implementation(include("com.github.justwrote.kjob", "kjob-core", kjobVersion))
    implementation(include("com.github.justwrote.kjob", "kjob-inmem", kjobVersion))
    implementation(include("com.github.justwrote.kjob", "kjob-kron", kjobVersion))
    implementation(include("com.cronutils", "cron-utils", cronutilsVersion))

    afterEvaluate {
        include(project(path = ":api"))
    }
    implementation(project(path = ":api"))

    modImplementation(include("io.sc3", "sc-text", scTextVersion))

    include(project(path = ":shared", configuration = "namedElements"))
    implementation(project(path = ":shared", configuration = "namedElements"))

    transitiveInclude.resolvedConfiguration.resolvedArtifacts.forEach {
        include(it.moduleVersion.id.toString())
    }
}

loom {
    log4jConfigs.from(file("log4j-dev.xml"))
}

configurations.namedElements.get().extendsFrom(configurations.implementation.get())

tasks {
    runClient { enabled = false }
    runServer { enabled = true }

    loom {
        runs {
            configureEach {
                property("fabric.debug.disableModShuffle")
            }
        }
    }
}
