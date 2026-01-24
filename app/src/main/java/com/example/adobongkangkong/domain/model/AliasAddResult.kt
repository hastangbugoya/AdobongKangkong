package com.example.adobongkangkong.domain.model

sealed class AliasAddResult {
    data object Added : AliasAddResult()
    data object IgnoredEmpty : AliasAddResult()
    data object IgnoredDuplicate : AliasAddResult()
}