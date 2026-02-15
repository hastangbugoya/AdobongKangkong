package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodBarcodeRepositoryImpl @Inject constructor(
    private val dao: FoodBarcodeDao
) : FoodBarcodeRepository {

    override suspend fun getByBarcode(normalizedBarcode: String): FoodBarcodeEntity? {
        return dao.getByBarcode(normalizedBarcode)
    }

    override suspend fun getFoodIdForBarcode(normalizedBarcode: String): Long? {
        return dao.getFoodIdForBarcode(normalizedBarcode)
    }

    override suspend fun upsert(entity: FoodBarcodeEntity) {
        dao.upsert(entity)
    }

    override suspend fun deleteByBarcode(normalizedBarcode: String) {
        dao.deleteByBarcode(normalizedBarcode)
    }

    override suspend fun touchLastSeen(normalizedBarcode: String, epochMs: Long) {
        dao.touchLastSeen(normalizedBarcode, epochMs)
    }

    override suspend fun countForFood(foodId: Long): Int {
        return dao.countForFood(foodId)
    }

    override suspend fun getAllBySource(source: BarcodeMappingSource): List<FoodBarcodeEntity> {
        return dao.getAllBySource(source)
    }

    override suspend fun upsertAndTouch(entity: FoodBarcodeEntity, nowEpochMs: Long) {
        dao.upsertAndTouch(entity, nowEpochMs)
    }

    override suspend fun getAllBarcodesForFood(foodId: Long): List<FoodBarcodeEntity> {
        return dao.getAllForFood(foodId)
    }
}
