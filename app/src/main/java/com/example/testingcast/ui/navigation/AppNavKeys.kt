package com.example.testingcast.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object SenderKey : NavKey

@Serializable
data object ReceiverKey : NavKey
