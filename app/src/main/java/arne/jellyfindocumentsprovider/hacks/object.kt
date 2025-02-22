package arne.jellyfindocumentsprovider.hacks

import kotlin.collections.get
import kotlin.reflect.full.memberProperties

inline fun <reified T : Any> fromMap(map: Map<String, Any>): T {
    //Get default constructor
    val constructor = T::class.constructors.first()

    //Map constructor parameters to map values
    val args = constructor
        .parameters.associateWith { map[it.name] }

    //return object from constructor call
    return constructor.callBy(args)
}

inline fun <reified T : Any> T.toPropertyMap(): Map<String, Any?> =
    T::class.memberProperties.associate { it.name to it.get(this) }