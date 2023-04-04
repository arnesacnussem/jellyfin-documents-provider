package a.sac.jellyfindocumentsprovider

import java.util.LinkedList

class FixedCapacityList<T>(val capacity: Int) : LinkedList<T>() {

    override fun add(element: T): Boolean {
        makeSpace()
        return super.add(element)
    }

    override fun add(index: Int, element: T) {
        makeSpace()
        super.add(index, element)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        makeSpace(elements.size)
        return super.addAll(elements)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        makeSpace(elements.size)
        return super.addAll(index, elements)
    }

    override fun addFirst(e: T) {
        makeSpace()
        super.addFirst(e)
    }

    override fun addLast(e: T) {
        makeSpace()
        super.addLast(e)
    }

    private fun makeSpace(amount: Int = 1) {
        val spaceNeeding = (this.size + amount) - this.capacity
        if (spaceNeeding > 0)
            this.removeRange(0, amount)
    }
}