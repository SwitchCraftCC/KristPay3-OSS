/*
 * Copyright (c) 2024 tmpim All rights reserved.
 * Use of this source code is governed by a BSD-style
 * license that can be found in the LICENSE file.
 */

package io.sc3.kristpay.core.metric

import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import io.prometheus.client.SimpleCollector

class CalculatedGauge(private val b: Builder) : SimpleCollector<CalculatedGauge.Child>(b), Collector.Describable {
    class Builder : SimpleCollector.Builder<Builder, CalculatedGauge>() {
        lateinit var value: () -> Double

        fun value(value: () -> Double): Builder {
            this.value = value
            return this
        }

        override fun create(): CalculatedGauge {
            value // make sure value is set
            return CalculatedGauge(this)
        }
    }

    inner class Child {
        fun get(): Double {
            return b.value()
        }
    }

    override fun collect(): MutableList<MetricFamilySamples> {
        val samples: MutableList<MetricFamilySamples.Sample> = ArrayList(children.size)
        for ((key, value) in children) {
            samples.add(MetricFamilySamples.Sample(fullname, labelNames, key, value.get()))
        }
        return familySamplesList(Type.GAUGE, samples)
    }

    override fun newChild() = Child()

    override fun describe(): List<MetricFamilySamples> {
        return listOf<MetricFamilySamples>(GaugeMetricFamily(fullname, help, labelNames))
    }

    companion object {
        fun build() = Builder()
    }
}
