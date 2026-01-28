package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.seed.SeedDefaultTargetsUseCase
import javax.inject.Inject

/**
 * One-time domain bootstrap entry point.
 *
 * Keep side-effects OUT of Application.
 * Call from the first feature ViewModel that needs targets (Dashboard, etc.).
 */
class BootstrapDomainUseCase @Inject constructor(
    private val seedDefaultTargets: SeedDefaultTargetsUseCase
) {
    suspend operator fun invoke() {
        seedDefaultTargets()
    }
}
