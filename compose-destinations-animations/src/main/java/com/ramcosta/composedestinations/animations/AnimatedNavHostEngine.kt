package com.ramcosta.composedestinations.animations

import androidx.compose.animation.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.*
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.navigation
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.bottomSheet
import com.ramcosta.composedestinations.manualcomposablecalls.ManualComposableCalls
import com.ramcosta.composedestinations.animations.defaults.*
import com.ramcosta.composedestinations.manualcomposablecalls.DestinationLambda
import com.ramcosta.composedestinations.navigation.DestinationDependenciesContainer
import com.ramcosta.composedestinations.navigation.dependency
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.DestinationStyle
import com.ramcosta.composedestinations.spec.NavGraphSpec
import com.ramcosta.composedestinations.spec.NavHostEngine

/**
 * Remembers and returns an instance of a [NavHostEngine]
 * suitable for navigation animations and bottom sheet styled
 * destinations.
 *
 * @param navHostContentAlignment content alignment for the NavHost.
 * @param rootDefaultAnimations animations to set as default for all destinations that don't specify
 * a destination style via `Destination` annotation's `style` argument. If [rootDefaultAnimations] is not
 * passed in, then no animations will happen by default.
 * @param defaultAnimationsForNestedNavGraph lambda called for each nested navigation graph that
 * allows you to override the default animations of [rootDefaultAnimations] with defaults just for
 * that specific nested navigation graph. Return null for all nested nav graphs, you don't wish
 * to override animations for.
 */
@ExperimentalMaterialNavigationApi
@ExperimentalAnimationApi
@Composable
fun rememberAnimatedNavHostEngine(
    navHostContentAlignment: Alignment = Alignment.Center,
    rootDefaultAnimations: RootNavGraphDefaultAnimations = RootNavGraphDefaultAnimations(),
    defaultAnimationsForNestedNavGraph: Map<NavGraphSpec, NestedNavGraphDefaultAnimations> = mapOf()
): NavHostEngine {
    val defaultNavHostEngine = rememberNavHostEngine()
    return remember {
        AnimatedNavHostEngine(
            navHostContentAlignment = navHostContentAlignment,
            defaultAnimationParams = rootDefaultAnimations,
            defaultAnimationsPerNestedNavGraph = defaultAnimationsForNestedNavGraph,
            defaultNavHostEngine = defaultNavHostEngine
        )
    }
}

@ExperimentalMaterialNavigationApi
@ExperimentalAnimationApi
internal class AnimatedNavHostEngine(
    private val navHostContentAlignment: Alignment,
    private val defaultAnimationParams: RootNavGraphDefaultAnimations,
    private val defaultAnimationsPerNestedNavGraph: Map<NavGraphSpec, NestedNavGraphDefaultAnimations>,
    private val defaultNavHostEngine: NavHostEngine
) : NavHostEngine {

    override val type = NavHostEngine.Type.ANIMATED

    @Composable
    override fun rememberNavController(
        vararg navigators: Navigator<out NavDestination>
    ) = rememberAnimatedNavController(*navigators)

    @Composable
    override fun NavHost(
        modifier: Modifier,
        route: String,
        startDestination: DestinationSpec<*>,
        navController: NavHostController,
        builder: NavGraphBuilder.() -> Unit
    ) = with(defaultAnimationParams) {

        AnimatedNavHost(
            navController = navController,
            startDestination = startDestination.route,
            modifier = modifier,
            route = route,
            contentAlignment = navHostContentAlignment,
            enterTransition = enterTransition.toAccompanist(),
            exitTransition = exitTransition.toAccompanist(),
            popEnterTransition = popEnterTransition.toAccompanist(),
            popExitTransition = popExitTransition.toAccompanist(),
            builder = builder
        )
    }

    override fun NavGraphBuilder.navigation(
        navGraph: NavGraphSpec,
        builder: NavGraphBuilder.() -> Unit
    ) = with(defaultAnimationsPerNestedNavGraph[navGraph]) {

        navigation(
            startDestination = navGraph.startDestination.route,
            route = navGraph.route,
            enterTransition = this?.enterTransition?.toAccompanist(),
            exitTransition = this?.exitTransition?.toAccompanist(),
            popEnterTransition = this?.popEnterTransition?.toAccompanist(),
            popExitTransition = this?.popExitTransition?.toAccompanist(),
            builder = builder,
        )
    }

    override fun <T> NavGraphBuilder.composable(
        destination: DestinationSpec<T>,
        navController: NavHostController,
        manualComposableCalls: ManualComposableCalls
    ) {
        when (val destinationStyle = destination.style) {
            is DestinationStyle.Default -> {
                addComposable(
                    destination,
                    navController,
                    manualComposableCalls
                )
            }

            is DestinationStyle.Animated -> {
                addAnimatedComposable(
                    destinationStyle,
                    destination,
                    navController,
                    manualComposableCalls
                )
            }

            is DestinationStyle.BottomSheet -> {
                addBottomSheetComposable(
                    destination,
                    navController,
                    manualComposableCalls
                )
            }

            is DestinationStyle.Dialog -> {
                // We delegate this to the default NavHost Engine
                with(defaultNavHostEngine) {
                    composable(
                        destination,
                        navController,
                        manualComposableCalls
                    )
                }
            }
        }
    }

    private fun <T> NavGraphBuilder.addComposable(
        destination: DestinationSpec<T>,
        navController: NavHostController,
        manualComposableCalls: ManualComposableCalls
    ) {
        composable(
            route = destination.route,
            arguments = destination.arguments,
            deepLinks = destination.deepLinks
        ) { navBackStackEntry ->
            CallComposable(
                destination,
                navController,
                navBackStackEntry,
                manualComposableCalls,
            )
        }
    }

    private fun <T> NavGraphBuilder.addAnimatedComposable(
        animatedStyle: DestinationStyle.Animated,
        destination: DestinationSpec<T>,
        navController: NavHostController,
        manualComposableCalls: ManualComposableCalls
    ) = with(animatedStyle) {
        composable(
            route = destination.route,
            arguments = destination.arguments,
            deepLinks = destination.deepLinks,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { popExitTransition() }
        ) { navBackStackEntry ->
            CallComposable(
                destination,
                navController,
                navBackStackEntry,
                manualComposableCalls
            )
        }
    }

    private fun <T> NavGraphBuilder.addBottomSheetComposable(
        destination: DestinationSpec<T>,
        navController: NavHostController,
        manualComposableCalls: ManualComposableCalls
    ) {
        bottomSheet(
            destination.route,
            destination.arguments,
            destination.deepLinks
        ) { navBackStackEntry ->
            CallComposable(
                destination,
                navController,
                navBackStackEntry,
                manualComposableCalls
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun <T> ColumnScope.CallComposable(
        destination: DestinationSpec<T>,
        navController: NavHostController,
        navBackStackEntry: NavBackStackEntry,
        manualComposableCalls: ManualComposableCalls
    ) {
        val contentWrapper = manualComposableCalls[destination]

        if (contentWrapper == null) {
            destination.Content(
                navController,
                navBackStackEntry,
                DestinationDependenciesContainer().apply { dependency(this@CallComposable) }
            )
        } else {
            contentWrapper as DestinationLambda<T>
            contentWrapper(
                destination = destination,
                navBackStackEntry = navBackStackEntry,
                navController = navController,
                receiver = this
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun <T> AnimatedVisibilityScope.CallComposable(
        destination: DestinationSpec<T>,
        navController: NavHostController,
        navBackStackEntry: NavBackStackEntry,
        manualComposableCalls: ManualComposableCalls,
    ) {
        val contentWrapper = manualComposableCalls[destination]

        if (contentWrapper == null) {
            destination.Content(
                navController,
                navBackStackEntry,
                DestinationDependenciesContainer().apply { dependency(this@CallComposable) }
            )
        } else {
            contentWrapper as DestinationLambda<T>
            contentWrapper(
                destination = destination,
                navBackStackEntry = navBackStackEntry,
                navController = navController,
                receiver = this
            )
        }
    }

    private fun DestinationEnterTransition.toAccompanist(): (AnimatedContentScope<NavBackStackEntry>.() -> EnterTransition) {
        return { enter() }
    }

    private fun DestinationExitTransition.toAccompanist(): (AnimatedContentScope<NavBackStackEntry>.() -> ExitTransition) {
        return { exit() }
    }
}