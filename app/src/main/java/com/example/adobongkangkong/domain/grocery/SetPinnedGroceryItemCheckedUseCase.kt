package com.example.adobongkangkong.domain.grocery

import com.example.adobongkangkong.domain.repository.PinnedGroceryItemRepository
import javax.inject.Inject

class SetPinnedGroceryItemCheckedUseCase @Inject constructor(
    private val repository: PinnedGroceryItemRepository
) {
    suspend operator fun invoke(
        id: Long,
        isChecked: Boolean
    ) {
        repository.setChecked(
            id = id,
            isChecked = isChecked
        )
    }
}
