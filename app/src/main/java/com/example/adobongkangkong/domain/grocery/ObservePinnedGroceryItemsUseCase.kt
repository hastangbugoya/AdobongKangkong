package com.example.adobongkangkong.domain.grocery

import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObservePinnedGroceryItemsUseCase @Inject constructor(
    private val repository: PinnedGroceryItemRepository
) {
    operator fun invoke(): Flow<List<PinnedGroceryItem>> = repository.observeAll()
}
