package com.example.adobongkangkong.domain.usda.model

enum class OpenReason {
    ExistingUserAssigned,          // user-assigned mapping exists
    ExistingUsdaUpToDate,          // incoming published <= existing published
    ExistingUsdaNoDateConservative,// date missing; conservative skip overwrite
    ExistingChosenByUser           // user explicitly chose Open Existing
}