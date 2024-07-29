/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

buildscript {
    dependencies {
        // Ran into GSON classpath issues with Loom 1.5. Manually including a higher version in the classpath (any
        // version that supports records, really) works around the issue. This is NOT a fix.
        // See: https://github.com/orgs/FabricMC/discussions/3546#discussioncomment-8345643
        classpath("com.google.code.gson:gson:2.10.1")
    }
}

plugins {
//    id("fabric-loom")
    val kotlinVersion: String by System.getProperties()
    kotlin("jvm").version(kotlinVersion)
    kotlin("plugin.serialization").version(kotlinVersion)
    id("maven-publish")
    id("signing")
}

val modVersion: String by project
version = modVersion
val mavenGroup: String by project
group = mavenGroup

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

val luckpermsApiVersion: String by project
val fabricPermissionsApiVersion: String by project

val ccVersion: String by project
val ccTargetVersion: String by project
val scTextVersion: String by project

//repositories {
//    maven("https://oss.sonatype.org/content/repositories/snapshots")
//    maven("https://squiddev.cc/maven")
//    maven("https://maven.terraformersmc.com/releases") // Mod Menu
//}

allprojects {
    version = modVersion
    group = mavenGroup

    apply(plugin = "kotlin")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    repositories {
        mavenCentral()

        maven("https://squiddev.cc/maven") {
            content {
                includeGroup("cc.tweaked")
                includeModule("org.squiddev", "Cobalt")
            }
        }

        maven("https://maven.terraformersmc.com") {
            // Mod Menu
            content {
                includeGroup("com.terraformersmc")
            }
        }

        maven("https://maven.shedaniel.me") {
            // cloth-config
            content {
                includeGroup("me.shedaniel.cloth")
                includeGroup("me.shedaniel.cloth.api")
            }
        }

        // sc-text
        mavenLocal {
            content {
                includeModule("io.sc3", "sc-text")
            }
        }
        maven {
            url = uri("https://repo.lem.sh/releases")
            content {
                includeGroup("io.sc3")
                includeGroup("me.lucko")
            }
        }

        // forgeconfigapiport-fabric, dependency of CC: Tweaked
        maven("https://raw.githubusercontent.com/Fuzss/modresources/main/maven/") {
            content {
                includeModule("fuzs.forgeconfigapiport", "forgeconfigapiport-fabric")
            }
        }

        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.justwrote.kjob")
            }
        }
    }

    dependencies {
        implementation(kotlin("reflect"))

        implementation("org.jetbrains", "annotations", annotationsVersion)
        implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json", kotlinSerializationVersion)
        implementation("org.jetbrains.kotlinx", "kotlinx-datetime", datetimeVersion)
        implementation("io.github.microutils", "kotlin-logging-jvm", kloggerVersion)
    }
}

//loom {
//    log4jConfigs.from(file("log4j-dev.xml"))
//}

allprojects {
    tasks {
        val javaVersion = JavaVersion.VERSION_17
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            sourceCompatibility = javaVersion.toString()
            targetCompatibility = javaVersion.toString()
            options.release.set(javaVersion.toString().toInt())
        }

        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = javaVersion.toString()
                freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.contracts.ExperimentalContracts"
            }
        }

        java {
            withSourcesJar()
        }

        if (project.name !in listOf("api")) {
            processResources {
                inputs.property("version", modVersion)

                filesMatching("fabric.mod.json") {
                    expand(
                        mutableMapOf(
                            "version" to modVersion,
                            "minecraft_target_version" to minecraftTargetVersion,
                            "fabric_language_kotlin" to fabricKotlinVersion,
                            "fabric_loader" to loaderVersion,
                            "cc_target_version" to ccTargetVersion
                        )
                    )
                }
            }
        }
    }

    afterEvaluate {
        publishing {
            publications {
                create<MavenPublication>("maven") {
                    artifactId = project.base.archivesName.get()
                    from(components["java"])
                }
            }

            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/SwitchCraftCC/KristPay3-OSS")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }

                    authentication {
                        create<BasicAuthentication>("basic")
                    }
                }
            }
        }
    }
}

tasks.register<GradleBuild>("publishKristpay") {
    group = "publishing"
    tasks = setOf("api", "shared", "client", "main")
        .map { ":${it}:publish" }
}

tasks.register<GradleBuild>("publishKristpayToMavenLocal") {
    group = "publishing"
    tasks = setOf("api", "shared", "client", "main")
        .map { ":${it}:publishToMavenLocal" }
}

//subprojects {
//    if (tasks.any { it.name == "runServer" }) {
//        tasks.runClient { enabled = false }
//        tasks.runServer { enabled = false }
//    }
//}

//tasks {
//    loom {
//        runs {
//            configureEach {
//                property("fabric.debug.disableModShuffle")
//            }
//        }
//    }
//}
