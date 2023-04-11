package a.sac.jellyfindocumentsprovider.database

import a.sac.jellyfindocumentsprovider.utils.SortedLongRangeList
import io.objectbox.converter.PropertyConverter

class SortedLongRangeListConvert : PropertyConverter<SortedLongRangeList, String> {
    override fun convertToEntityProperty(databaseValue: String?): SortedLongRangeList {
        return SortedLongRangeList(innerList = databaseValue?.split(';')?.map {
            val split = it.split(',').map { s -> s.trim().toLong() }
            split[0]..split[1]
        }?.toMutableList() ?: mutableListOf())
    }

    override fun convertToDatabaseValue(entityProperty: SortedLongRangeList?): String {
        return entityProperty?.joinToString(";") {
            it.first.toString() + "," + it.last
        } ?: ""
    }

}