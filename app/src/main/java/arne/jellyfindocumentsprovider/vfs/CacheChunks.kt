package arne.jellyfindocumentsprovider.vfs

import io.objectbox.converter.PropertyConverter

open class CacheChunks(
    private val comparator: Comparator<LongRange> = compareBy { it.first },
    private val innerList: MutableList<LongRange> = mutableListOf(),
) : List<LongRange> by innerList {

    @Synchronized
    fun add(element: LongRange) {
        val index = innerList.binarySearch(element, comparator).let { if (it < 0) -it - 1 else it }
        innerList.add(index, element)
        merge()
    }

    @Synchronized
    fun offsetInChunks(offset: Long): LongRange? {
        merge()
        return innerList.find { it.first <= offset && it.last >= offset }
    }

    private fun mergeOverlappingRanges(sortedRanges: MutableList<LongRange>): List<LongRange> {
        val merged = mutableListOf<LongRange>()
        var current = sortedRanges[0]

        for (i in 1 until sortedRanges.size) {
            val next = sortedRanges[i]

            current = if (current.last + 1 >= next.first) { // Check for overlap
                current.first..maxOf(current.last, next.last)
            } else {
                merged.add(current)
                next
            }
        }

        merged.add(current)
        return merged
    }

    @Synchronized
    protected fun merge() {
        if (innerList.isEmpty() || innerList.size == 1) return
        val nList = mergeOverlappingRanges(this.innerList)
        innerList.clear()
        innerList.addAll(nList)
    }

//    fun gaps(): List<LongRange> {
//        merge()
//        val gaps = mutableListOf<LongRange>()
//
//        for (i in 0 until innerList.size - 1) {
//            gaps.add((innerList[i].last + 1) until innerList[i + 1].first)
//        }
//        return gaps.toList()
//    }

    @Synchronized
    fun noGapsIn(range: LongRange): Boolean {
        merge()
        return innerList.any { it.contains(range.first) && it.contains(range.last) }
    }
}

class CacheChunksConverter : PropertyConverter<List<LongRange>, String> {
    override fun convertToEntityProperty(databaseValue: String?): List<LongRange> {
        return CacheChunks(innerList = databaseValue?.split(';')?.filter { it.isNotBlank() }
            ?.map {
                val split = it.split(',').map { s -> s.trim().toLong() }
                split[0]..split[1]
            }?.toMutableList() ?: mutableListOf())
    }

    override fun convertToDatabaseValue(cc: List<LongRange>?): String {
        return cc?.joinToString(";") {
            it.first.toString() + "," + it.last
        } ?: ""
    }
}
