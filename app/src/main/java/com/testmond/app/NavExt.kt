package com.testmond.app

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Pops the back stack in response to a user action (a back arrow, a Done button, a back gesture),
 * but only when that is safe:
 *
 *  - The screen must be fully on top (RESUMED). While it is animating away its entry is no
 *    longer resumed, so rapid extra taps on the same back arrow are ignored. Without this each
 *    extra tap popped ANOTHER screen -- the home screen included -- until the back stack was
 *    empty and the app showed a black screen that nothing could bring back.
 *  - There must be a screen underneath to return to, so the start destination (Home) can never
 *    be popped by mistake.
 */
fun NavHostController.safePopBackStack(): Boolean {
    if (previousBackStackEntry == null) return false
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return false
    return popBackStack()
}

/**
 * Navigates in response to a user tap, but only while the current screen is fully on top
 * (RESUMED). A quick double tap on a test, a menu item or a button used to open the same screen
 * twice (so it then needed two back presses); the second tap now arrives while the first
 * navigation is still running and is ignored.
 */
fun NavHostController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route, builder)
}
