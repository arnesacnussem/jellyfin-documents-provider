package a.sac.jellyfindocumentsprovider

class SortedLongRangeList(
    private val comparator: Comparator<LongRange> = compareBy { it.first },
    private val innerList: MutableList<LongRange> = mutableListOf(),
) : List<LongRange> by innerList {

    @Synchronized
    fun add(element: LongRange) {
        val index = innerList.binarySearch(element, comparator).let { if (it < 0) -it - 1 else it }
        innerList.add(index, element)
        merge()
    }

    fun remove(element: LongRange): Boolean {
        val index = innerList.binarySearch(element, comparator)
        return if (index >= 0) {
            innerList.removeAt(index)
            true
        } else {
            false
        }
    }

    private fun mergeOverlappingRanges(sortedRanges: MutableList<LongRange>): List<LongRange> {
        val mergedRanges = mutableListOf<LongRange>()
        var currentRange = sortedRanges[0]

        for (i in 1 until sortedRanges.size) {
            val nextRange = sortedRanges[i]

            currentRange = if (currentRange.last + 1 >= nextRange.first) { // Check for overlap
                currentRange.first..maxOf(currentRange.last, nextRange.last)
            } else {
                mergedRanges.add(currentRange)
                nextRange
            }
        }

        mergedRanges.add(currentRange)
        return mergedRanges
    }

    @Synchronized
    fun merge() {
        if (innerList.isEmpty() || innerList.size == 1) return
        val nList = mergeOverlappingRanges(this.innerList)
        innerList.clear()
        innerList.addAll(nList)
    }

    fun gaps(): List<LongRange> {
        merge()
        val gaps = mutableListOf<LongRange>()

        for (i in 0 until innerList.size - 1) {
            gaps.add((innerList[i].last + 1) until innerList[i + 1].first)
        }
        return gaps.toList()
    }

    @Synchronized
    fun noGapsIn(range: LongRange): Boolean {
        merge()
        return innerList.any { it.contains(range.first) && it.contains(range.last) }
    }
}