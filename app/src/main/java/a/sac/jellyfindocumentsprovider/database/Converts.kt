package a.sac.jellyfindocumentsprovider.database

import io.objectbox.converter.PropertyConverter

class ListLongRangeConvert : PropertyConverter<List<LongRange>, String> {
    override fun convertToEntityProperty(databaseValue: String?): List<LongRange> {
        return databaseValue?.split(';')?.map {
            val split = it.split(',').map { s -> s.trim().toLong() }
            split[0]..split[1]
        } ?: listOf()
    }

    override fun convertToDatabaseValue(entityProperty: List<LongRange>?): String {
        return entityProperty?.joinToString(";") {
            it.first.toString() + "," + it.last
        } ?: ""
    }

}