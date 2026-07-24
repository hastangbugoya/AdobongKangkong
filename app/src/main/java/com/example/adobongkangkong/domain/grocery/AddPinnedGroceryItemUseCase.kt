package com.example.adobongkangkong.domain.grocery

import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject

class AddPinnedGroceryItemUseCase @Inject constructor(
    private val repository: PinnedGroceryItemRepository
) {
    suspend operator fun invoke(
        name: String,
        quantityText: String?
    ): Long = repository.add(
        name = name,
        quantityText = quantityText
    )
}
