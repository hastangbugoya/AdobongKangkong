package com.example.adobongkangkong.data.local.db

import androidx.room.TypeConverter
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.LogUnit
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant

class DbTypeConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter fun servingUnitToString(value: ServingUnit?): String? = value?.name
    @TypeConverter fun stringToServingUnit(value: String?): ServingUnit? =
        value?.let { runCatching { ServingUnit.valueOf(it) }.getOrNull() }

    @TypeConverter fun nutrientUnitToDb(value: NutrientUnit): String = value.name
    @TypeConverter fun nutrientUnitFromDb(value: String): NutrientUnit =
        runCatching { NutrientUnit.valueOf(value) }.getOrDefault(NutrientUnit.OTHER)

    @TypeConverter fun nutrientCategoryToDb(value: NutrientCategory?): String? = value?.dbValue
    @TypeConverter fun nutrientCategoryFromDb(value: String?): NutrientCategory =
        NutrientCategory.fromDb(value?.trim()?.lowercase().orEmpty())

    @TypeConverter fun basisTypeToDb(value: BasisType?): String? = value?.name
    @TypeConverter fun basisTypeFromDb(value: String?): BasisType =
        runCatching { BasisType.valueOf(value ?: "") }.getOrDefault(BasisType.PER_SERVING)

    // ---- NutrientMap JSON (String <-> Map<String, Double>) ----

    @TypeConverter
    fun nutrientMapToJson(value: NutrientMap?): String =
        json.encodeToString((value ?: NutrientMap.EMPTY).toCodeMap())

    @TypeConverter
    fun nutrientMapFromJson(raw: String?): NutrientMap =
        if (raw.isNullOrBlank()) NutrientMap.EMPTY
        else NutrientMap.fromCodeMap(json.decodeFromString(raw))


    // Instant <-> Long (epoch millis)
//    @TypeConverter
//    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

//    @TypeConverter
//    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    // LogUnit <-> String
    @TypeConverter
    fun logUnitToString(value: LogUnit?): String? = value?.name

    @TypeConverter
    fun stringToLogUnit(value: String?): LogUnit =
        value?.let { runCatching { LogUnit.valueOf(it) }.getOrNull() } ?: LogUnit.ITEM
}
