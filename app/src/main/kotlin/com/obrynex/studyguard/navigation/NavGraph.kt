package com.obrynex.studyguard.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.obrynex.studyguard.BuildConfig
import com.obrynex.studyguard.ai.AIModelState
import com.obrynex.studyguard.ai.AiTutorScreen
import com.obrynex.studyguard.ai.AiTutorViewModel
import com.obrynex.studyguard.ai.DebugInfoScreen
import com.obrynex.studyguard.ai.DebugInfoViewModel
import com.obrynex.studyguard.data.prefs.PrefsManager
import com.obrynex.studyguard.di.ServiceLocator
import com.obrynex.studyguard.di.ViewModelProviders
import com.obrynex.studyguard.booksummarizer.BookSummarizerScreen
import com.obrynex.studyguard.booksummarizer.BookSummarizerViewModel
import com.obrynex.studyguard.islamic.ui.IslamicScreen
import com.obrynex.studyguard.islamic.ui.IslamicViewModel
import com.obrynex.studyguard.summarizer.ui.SummarizerScreen
import com.obrynex.studyguard.summarizer.ui.SummarizerViewModel
import com.obrynex.studyguard.tracker.SessionDetailScreen
import com.obrynex.studyguard.tracker.SessionDetailViewModel
import com.obrynex.studyguard.tracker.TrackerScreen
import com.obrynex.studyguard.tracker.TrackerViewModel
import com.obrynex.studyguard.ui.onboarding.OnboardingScreen
import com.obrynex.studyguard.ui.theme.*
import com.obrynex.studyguard.wellbeing.WellbeingScreen
import com.obrynex.studyguard.wellbeing.WellbeingViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App URI scheme for deep links. */
private const val URI_SCHEME = "studyguard"

private object Routes {
    const val ONBOARDING       = "onboarding"
    const val TRACKER          = "tracker"
    const val SUMMARIZER       = "summarizer"
    const val BOOK_SUMMARIZER  = "book_summarizer"
    const val WELLBEING        = "wellbeing"
    const val ISLAMIC          = "islamic"
    const val AI_TUTOR         = "ai_tutor"
    const val AI_SETUP         = "ai_setup"          // landing page when NotFound
    const val SESSION_DETAIL   = "session_detail/{sessionId}"
    const val DEBUG_INFO       = "debug_info"

    fun sessionDetail(id: Long) = "session_detail/$id"

    /** Deep link URIs */
    object DeepLinks {
        const val AI_TUTOR   = "$URI_SCHEME://ai_tutor"
        const val AI_SETUP   = "$URI_SCHEME://ai_setup"
        const val DEBUG_INFO = "$URI_SCHEME://debug_info"
        const val TRACKER    = "$URI_SCHEME://tracker"
    }
}

private sealed class Tab(
    val route        : String,
    val label        : String,
    val icon         : ImageVector,
    val iconSelected : ImageVector
) {
    object Tracker       : Tab(Routes.TRACKER,         "المذاكرة", Icons.Outlined.Timer,        Icons.Filled.Timer)
    object Summarizer    : Tab(Routes.SUMMARIZER,      "تلخيص",    Icons.Outlined.AutoStories,  Icons.Filled.AutoStories)
    object BookSummarizer: Tab(Routes.BOOK_SUMMARIZER, "كتاب",     Icons.Outlined.MenuBook,     Icons.Filled.MenuBook)
    object AiTutor       : Tab(Routes.AI_TUTOR,        "مساعد",    Icons.Outlined.Psychology,   Icons.Filled.Psychology)
    object Islamic       : Tab(Routes.ISLAMIC,         "أذكار",    Icons.Outlined.AutoAwesome,  Icons.Filled.AutoAwesome)
    object Wellbeing     : Tab(Routes.WELLBEING,       "الشاشة",   Icons.Outlined.PhoneAndroid, Icons.Filled.PhoneAndroid)
}

private val TABS = listOf(Tab.Tracker, Tab.Summarizer, Tab.BookSummarizer, Tab.AiTutor, Tab.Islamic, Tab.Wellbeing)
private val BOTTOM_NAV_ROUTES = TABS.map { it.route }.toSet()

@Composable
fun NavGraph() {
    val ctx           = LocalContext.current
    val navController = rememberNavController()
    val backStack     by navController.currentBackStackEntryAsState()
    val currentRoute  = backStack?.destination?.route
    val scope         = rememberCoroutineScope()

    var startDest by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val done = PrefsManager.onboardingDone(ctx).first()
        startDest = if (done) Routes.TRACKER else Routes.ONBOARDING
    }
    if (startDest == null) return

    val showBottomBar = currentRoute in BOTTOM_NAV_ROUTES

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    NavigationBar(containerColor = BgDark, tonalElevation = 0.dp) {
                        TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected        = selected,
                                alwaysShowLabel = false,
                                onClick = {
                                    // Guard: intercept AI Tutor tab tap when model is NotFound
                                    if (tab is Tab.AiTutor &&
                                        ServiceLocator.aiEngineManager.state.value is AIModelState.NotFound
                                    ) {
                                        navController.navigate(Routes.AI_SETUP) {
                                            launchSingleTop = true
                                        }
                                        return@NavigationBarItem
                                    }
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (selected) tab.iconSelected else tab.icon,
                                        contentDescription = tab.label,
                                        modifier = androidx.compose.ui.Modifier.size(22.dp)
                                    )
                                },
                                label  = { Text(tab.label, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = TextPrimary,
                                    selectedTextColor   = TextPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor      = Surface2
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = checkNotNull(startDest),
            modifier         = androidx.compose.ui.Modifier.padding(padding)
        ) {

            composable(Routes.ONBOARDING) {
                OnboardingScreen {
                    scope.launch { PrefsManager.setOnboardingDone(ctx) }
                    navController.navigate(Routes.TRACKER) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            }

            composable(
                route      = Routes.TRACKER,
                deepLinks  = listOf(navDeepLink { uriPattern = Routes.DeepLinks.TRACKER })
            ) {
                val vm: TrackerViewModel = viewModel(factory = ViewModelProviders.tracker)
                TrackerScreen(vm = vm, onSessionClick = { session ->
                    navController.navigate(Routes.sessionDetail(session.id))
                })
            }

            composable(Routes.SUMMARIZER) {
                val vm: SummarizerViewModel = viewModel(factory = ViewModelProviders.summarizer)
                SummarizerScreen(vm)
            }

            composable(Routes.BOOK_SUMMARIZER) {
                val vm: BookSummarizerViewModel = viewModel(factory = ViewModelProviders.bookSummarizer)
                BookSummarizerScreen(vm)
            }

            /**
             * AI Tutor — guarded.
             *
             * If the engine is in [AIModelState.NotFound] and was never set up,
             * we immediately redirect to [Routes.AI_SETUP] so the user sees the
             * setup instructions rather than a blank screen with an instant error.
             *
             * Deep link: studyguard://ai_tutor
             */
            composable(
                route     = Routes.AI_TUTOR,
                deepLinks = listOf(navDeepLink { uriPattern = Routes.DeepLinks.AI_TUTOR })
            ) {
                val engineState by ServiceLocator.aiEngineManager.state.collectAsState()

                // Redirect to setup if model was never placed on device
                LaunchedEffect(engineState) {
                    if (engineState is AIModelState.NotFound) {
                        navController.navigate(Routes.AI_SETUP) {
                            popUpTo(Routes.AI_TUTOR) { inclusive = true }
                        }
                    }
                }

                if (engineState !is AIModelState.NotFound) {
                    val vm: AiTutorViewModel = viewModel(factory = ViewModelProviders.aiTutor)
                    AiTutorScreen(vm)
                }
            }

            /**
             * AI Setup landing page — shown when the model file has not been placed
             * on the device yet.  Provides setup instructions and a link back to
             * [Routes.AI_TUTOR] once the user has completed setup.
             *
             * Deep link: studyguard://ai_setup
             */
            composable(
                route     = Routes.AI_SETUP,
                deepLinks = listOf(navDeepLink { uriPattern = Routes.DeepLinks.AI_SETUP })
            ) {
                // Obtain the ViewModel here (scoped to this back-stack entry) and pass
                // it down explicitly. This prevents AiSetupLandingScreen from calling
                // viewModel() internally and creating a second, unshared instance that
                // would trigger a duplicate AIEngineManager.validate() call.
                val vm: AiTutorViewModel = viewModel(factory = ViewModelProviders.aiTutor)
                AiSetupLandingScreen(
                    vm           = vm,
                    modelPath    = ServiceLocator.aiEngineManager.modelFilePath,
                    onModelReady = {
                        navController.navigate(Routes.AI_TUTOR) {
                            popUpTo(Routes.AI_SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.WELLBEING) {
                val vm: WellbeingViewModel = viewModel(factory = ViewModelProviders.wellbeing)
                WellbeingScreen(vm)
            }

            composable(Routes.ISLAMIC) {
                val vm: IslamicViewModel = viewModel(factory = ViewModelProviders.islamic)
                IslamicScreen(vm)
            }

            composable(
                route     = Routes.SESSION_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                val vm: SessionDetailViewModel = viewModel(factory = ViewModelProviders.sessionDetail)
                val session by vm.sessionById(sessionId).collectAsState(initial = null)
                session?.let {
                    SessionDetailScreen(
                        session  = it,
                        onBack   = { navController.popBackStack() },
                        onDelete = vm::delete
                    )
                }
            }

            // ── Debug screen — only registered in debug builds ────────────────
            if (BuildConfig.DEBUG) {
                composable(
                    route     = Routes.DEBUG_INFO,
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DeepLinks.DEBUG_INFO })
                ) {
                    val vm: DebugInfoViewModel = viewModel(factory = ViewModelProviders.debugInfo)
                    DebugInfoScreen(vm)
                }
            }
        }
    }
}

/**
 * Standalone setup screen shown when [AIModelState.NotFound] is detected on
 * entry to the AI Tutor destination.
 *
 * The [vm] is created by the caller (NavGraph composable) and passed in explicitly
 * to ensure a single ViewModel instance is shared — prevents a duplicate
 * [AIEngineManager.validate] call that would occur if this function called
 * `viewModel()` internally.
 *
 * Includes the adb push command and a "Done — retry" button that navigates
 * back to the tutor flow.
 */
@Composable
private fun AiSetupLandingScreen(
    vm           : AiTutorViewModel,
    modelPath    : String,
    onModelReady : () -> Unit
) {
    val s by vm.state.collectAsState()

    // Navigate away as soon as the engine becomes Ready (background retry succeeded)
    LaunchedEffect(s.modelState) {
        if (s.modelState is AIModelState.Ready) onModelReady()
    }

    // Reuse the setup banner from AiTutorScreen — single source of UI truth
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // minimal header
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Text(
                "إعداد مساعد الدراسة",
                color      = TextPrimary,
                fontSize   = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
        HorizontalDivider(color = Divider, thickness = 0.5.dp)

        // Delegate the full setup instructions + retry button to AiTutorScreen
        // by letting it handle the NotFound state
        AiTutorScreen(vm)
    }
}
