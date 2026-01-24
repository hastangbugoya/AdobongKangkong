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

    @TypeConverter
    fun toDb(value: NutrientCategory): String = value.dbValue

    @TypeConverter
    fun toDb(value: BasisType): String = value.name
}
