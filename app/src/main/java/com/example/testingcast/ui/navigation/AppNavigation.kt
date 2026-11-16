package com.example.testingcast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.testingcast.ui.home.HomeScreen
import com.example.testingcast.ui.receiver.ReceiverRoute
import com.example.testingcast.ui.sender.SenderRoute

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeKey)
    val goBack: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    onSend = { backStack.add(SenderKey) },
                    onReceive = { backStack.add(ReceiverKey) }
                )
            }
            entry<SenderKey> { SenderRoute(onBack = goBack) }
            entry<ReceiverKey> { ReceiverRoute() }
        }
    )
}
