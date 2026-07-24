package com.example.adobongkangkong.domain.grocery

import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject

class UpdatePinnedGroceryItemUseCase @Inject constructor(
    private val repository: PinnedGroceryItemRepository
) {
    suspend operator fun invoke(
        id: Long,
        name: String,
        quantityText: String?
    ) {
        repository.updateText(
            id = id,
            name = name,
            quantityText = quantityText
        )
    }
}
