/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.util

import io.github.classgraph.ClassGraph
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

object ClassScanner {
    fun <I : Any> getClassesAnnotatedWith(annotation: KClass<out Annotation>, interfaceType: KClass<I>): List<KClass<I>> {
        val `package` = annotation.java.`package`.name
        val annotationName = annotation.java.canonicalName

        val classloader = this::class.java.classLoader

        return ClassGraph()
            .enableAllInfo()
            .acceptPackages(`package`)
            .scan().use { scanResult ->
                @Suppress("UNCHECKED_CAST")
                scanResult
                    .getClassesWithAnnotation(annotationName)
                    .mapNotNull { classloader.loadClass(it.name).kotlin }
                    .filter { interfaceType.isSubclassOf(interfaceType) } as List<KClass<I>>
            }
    }

    fun <I : Any> getObjectsAnnotatedWith(annotation: KClass<out Annotation>, interfaceType: KClass<I>): List<I> =
        getClassesAnnotatedWith(annotation, interfaceType).mapNotNull { it.objectInstance }
}
