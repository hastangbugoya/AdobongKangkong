package com.example.adobongkangkong.data.local.db

import androidx.room.TypeConverter
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import java.time.Instant

class DbTypeConverters {

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun servingUnitToString(value: ServingUnit?): String? = value?.name

    @TypeConverter
    fun stringToServingUnit(value: String?): ServingUnit? =
        value?.let { runCatching { ServingUnit.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun toDb(value: NutrientUnit): String = value.name

    @TypeConverter fun fromDb(value: String): NutrientUnit =
        runCatching { NutrientUnit.valueOf(value) }.getOrDefault(NutrientUnit.OTHER)

    // ---- NutrientCategory (uses dbValue) ----
    @TypeConverter
    fun nutrientCategoryToDb(value: NutrientCategory?): String? =
        value?.dbValue

    @TypeConverter
    fun nutrientCategoryFromDb(value: String?): NutrientCategory =
        NutrientCategory.fromDb(value?.trim()?.lowercase().orEmpty())

    // ---- (Optional) BasisType if you store it as enum ----
    @TypeConverter
    fun basisTypeToDb(value: BasisType?): String? =
        value?.name

    @TypeConverter
    fun basisTypeFromDb(value: String?): BasisType =
        runCatching { BasisType.valueOf(value ?: "") }.getOrDefault(BasisType.PER_SERVING)
}
