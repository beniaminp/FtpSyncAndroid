package com.padana.ftpsync.utils

import java.util.*
import kotlin.math.ceil
import kotlin.math.min


class Partition<T>(list: MutableList<T>, chunkSize: Int) : AbstractList<List<T>?>() {
    private val list: MutableList<T> = list
    private val chunkSize: Int = chunkSize

    override fun get(index: Int): MutableList<T> {
        val start = index * chunkSize
        val end = min(start + chunkSize, list.size)
        if (start > end) {
            throw IndexOutOfBoundsException("Index " + index + " is out of the list range <0," + (size - 1) + ">")
        }
        return list.subList(start, end)
    }

    override val size: Int
        get() = ceil(list.size.toDouble() / chunkSize.toDouble()).toInt()


    companion object {
        fun <T> ofSize(list: MutableList<T>, chunkSize: Int): Partition<T> {
            return Partition(list, chunkSize)
        }
    }

}