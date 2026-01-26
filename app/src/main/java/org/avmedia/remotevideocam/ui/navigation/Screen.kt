package org.avmedia.remotevideocam.ui.navigation

/** Sealed class representing all screens in the app */
sealed class Screen(val route: String) {
    data object Waiting : Screen("waiting")
    data object Main : Screen("main")
    data object Camera : Screen("camera")
    data object Display : Screen("display")
}
