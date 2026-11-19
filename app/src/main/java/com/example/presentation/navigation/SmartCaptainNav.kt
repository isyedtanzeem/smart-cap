package com.example.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface SmartCaptainRoute {
    @Serializable
    data object Dashboard : SmartCaptainRoute

    @Serializable
    data object History : SmartCaptainRoute

    @Serializable
    data object Settings : SmartCaptainRoute

    @Serializable
    data object Permissions : SmartCaptainRoute
}
