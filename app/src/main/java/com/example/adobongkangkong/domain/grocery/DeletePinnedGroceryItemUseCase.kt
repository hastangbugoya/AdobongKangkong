package com.example.adobongkangkong.domain.grocery

import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject

class DeletePinnedGroceryItemUseCase @Inject constructor(
    private val repository: PinnedGroceryItemRepository
) {
    suspend operator fun invoke(id: Long) {
        repository.delete(id)
    }
}
