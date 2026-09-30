package com.diego.awedio.ui

import androidx.annotation.StringRes

data class UiMessage(
    @StringRes val resId: Int,
    val args: List<Any> = emptyList()
)
