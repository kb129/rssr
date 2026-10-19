package com.kb129.rssr.core.cluster

import kotlin.math.sqrt

/** 語 ID 昇順に並んだ疎ベクトル。 */
class SparseVector(val indices: IntArray, val values: DoubleArray) {

    fun dot(other: SparseVector): Double {
        var i = 0
        var j = 0
        var sum = 0.0
        while (i < indices.size && j < other.indices.size) {
            val a = indices[i]
            val b = other.indices[j]
            when {
                a == b -> { sum += values[i] * other.values[j]; i++; j++ }
                a < b -> i++
                else -> j++
            }
        }
        return sum
    }

    val isEmpty: Boolean get() = indices.isEmpty()

    companion object {
        /** 語 ID → 重みの map から L2 正規化済みベクトルを作る。 */
        fun normalized(weights: Map<Int, Double>): SparseVector {
            val norm = sqrt(weights.values.sumOf { it * it })
            if (norm == 0.0) return SparseVector(IntArray(0), DoubleArray(0))
            val sorted = weights.entries.sortedBy { it.key }
            return SparseVector(
                IntArray(sorted.size) { sorted[it].key },
                DoubleArray(sorted.size) { sorted[it].value / norm },
            )
        }
    }
}
