package com.example.ui

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.AppContainer
import com.example.core.config.ThemeMode
import com.example.core.designsystem.SkillectBrandBadge
import com.example.core.designsystem.SkillectSnackbarPill
import com.example.core.designsystem.SkillectTheme
import com.example.ui.auth.AuthMode
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AccountRestrictedScreen
import com.example.ui.auth.PasswordResetScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.OnboardingScreen
import com.example.ui.communities.CommunitiesScreen
import com.example.ui.communities.CommunityDetailScreen
import com.example.ui.composer.ComposerScreen
import com.example.ui.explore.ExploreCommunitiesViewModel
import com.example.ui.explore.ExploreScreen
import com.example.ui.feed.FeedScreen
import com.example.ui.feed.FeedViewModel
import com.example.ui.feed.ShortsScreen
import com.example.ui.messages.ChatDetailScreen
import com.example.ui.messages.MessagesInboxScreen
import com.example.ui.messages.MessagesNotificationsViewModel
import com.example.ui.navigation.SkillectRoute
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileSettingsViewModel
import com.example.ui.settings.ModerationAdminScreen
import com.example.ui.settings.SettingsScreen

private data class TopNavItem(
    val route: SkillectRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
)

private class SkillectViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(container.repository) as T
            modelClass.isAssignableFrom(FeedViewModel::class.java) -> FeedViewModel(container.repository) as T
            modelClass.isAssignableFrom(ExploreCommunitiesViewModel::class.java) -> ExploreCommunitiesViewModel(container.repository) as T
            modelClass.isAssignableFrom(MessagesNotificationsViewModel::class.java) -> MessagesNotificationsViewModel(container.repository) as T
            modelClass.isAssignableFrom(ProfileSettingsViewModel::class.java) -> ProfileSettingsViewModel(container.repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

@Composable
fun SkillectApp(
    container: AppContainer,
    deepLinkType: String? = null,
    deepLinkEntityId: String? = null,
    onNotificationDeepLinkConsumed: () -> Unit = {},
    authDeepLinkUri: String? = null,
    onAuthDeepLinkConsumed: () -> Unit = {},
) {
    val factory = remember(container) { SkillectViewModelFactory(container) }
    val authVm: AuthViewModel = viewModel(factory = factory)
    val feedVm: FeedViewModel = viewModel(factory = factory)
    val exploreVm: ExploreCommunitiesViewModel = viewModel(factory = factory)
    val msgVm: MessagesNotificationsViewModel = viewModel(factory = factory)
    val profileVm: ProfileSettingsViewModel = viewModel(factory = factory)

    val authState by authVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )
    var showNotificationPermissionPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            val preferences = context.getSharedPreferences("skillect_permissions", Context.MODE_PRIVATE)
            if (!granted && !preferences.getBoolean("notification_prompt_seen", false)) {
                showNotificationPermissionPrompt = true
            }
        }
    }

    if (showNotificationPermissionPrompt) {
        AlertDialog(
            onDismissRequest = {
                context.getSharedPreferences("skillect_permissions", Context.MODE_PRIVATE)
                    .edit().putBoolean("notification_prompt_seen", true).apply()
                showNotificationPermissionPrompt = false
            },
            title = { Text("Stay updated on Skillect") },
            text = {
                Text("Allow notifications for messages, community activity and important account updates. You can change this anytime in Android settings.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("skillect_permissions", Context.MODE_PRIVATE)
                            .edit().putBoolean("notification_prompt_seen", true).apply()
                        showNotificationPermissionPrompt = false
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("skillect_permissions", Context.MODE_PRIVATE)
                            .edit().putBoolean("notification_prompt_seen", true).apply()
                        showNotificationPermissionPrompt = false
                    },
                ) { Text("Not now") }
            },
        )
    }

    val feedState by feedVm.uiState.collectAsStateWithLifecycle()
    val exploreState by exploreVm.uiState.collectAsStateWithLifecycle()
    val msgState by msgVm.uiState.collectAsStateWithLifecycle()
    val profileState by profileVm.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUserId = if (authState.isAuthenticated) {
        authState.currentUserId.takeIf { it.isNotBlank() }
            ?: authState.currentProfile?.id
            ?: profileState.myProfile?.id
            ?: ""
    } else {
        ""
    }
    var previousUserId by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentUserId.isNotBlank()) {
                msgVm.refreshNotifications()
                msgVm.refreshInbox()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requireAuth: (Boolean, String) -> Unit = remember(authVm) {
        { createAccount, reason ->
            authVm.openAuthPrompt(
                mode = if (createAccount) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                reason = reason,
            )
        }
    }

    // Handle password-recovery deep link URIs (skillect://auth-callback#...)
    LaunchedEffect(authDeepLinkUri) {
        if (!authDeepLinkUri.isNullOrBlank()) {
            authVm.handleDeepLinkUri(authDeepLinkUri)
            onAuthDeepLinkConsumed()
        }
    }

    // Keep Firebase analytics identity aligned with Supabase auth.
    // FCM token registration is centralized in the distinct session observer.
    LaunchedEffect(currentUserId, authState.isAuthenticated) {
        container.analyticsTracker.setUserId(currentUserId.takeIf { it.isNotBlank() })

        // Smooth Guest <-> Account transitions without restarting the app.
        if (previousUserId == null) {
            previousUserId = currentUserId
            return@LaunchedEffect
        }
        if (previousUserId != currentUserId) {
            previousUserId = currentUserId
            if (currentUserId.isNotBlank()) {
                feedVm.refreshAll()
                exploreVm.refreshExplore()
                msgVm.refreshInbox()
                msgVm.refreshNotifications()
                profileVm.loadMyProfileAndSaved()
            } else {
                msgVm.clearPrivateState()
                profileVm.clearPrivateState()
                exploreVm.clearPrivateState()
                feedVm.clearUserSpecificState()
            }
        }
    }

    // Aggregate snackbars cleanly
    val activeSnackbar = feedState.snackbarMessage
        ?: exploreState.statusMessage
        ?: msgState.statusMessage
        ?: profileState.statusMessage

    LaunchedEffect(activeSnackbar) {
        if (!activeSnackbar.isNullOrBlank()) {
            snackbarHostState.showSnackbar(activeSnackbar)
            feedVm.clearSnackbar()
            exploreVm.clearStatus()
            msgVm.clearStatus()
            profileVm.clearStatus()
        }
    }

    // Handle FCM deep links if launched from notification
    LaunchedEffect(deepLinkType, deepLinkEntityId, authState.isAuthenticated) {
        if (deepLinkType != null) {
            if (!authState.isAuthenticated) {
                authVm.openAuthPrompt(AuthMode.SIGN_IN, "Sign in to view notifications and private messages.")
            } else {
                if (deepLinkType == "message" && !deepLinkEntityId.isNullOrBlank()) {
                    navController.navigate(SkillectRoute.ChatDetail(deepLinkEntityId))
                } else {
                    navController.navigate(SkillectRoute.Notifications)
                }
                onNotificationDeepLinkConsumed()
            }
        }
    }

    val isDarkActive = when (profileState.preferences.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val toggleTheme: () -> Unit = {
        profileVm.setThemeMode(if (isDarkActive) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    val selectedLanguage = profileState.preferences.languageCode
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(selectedLanguage, baseConfiguration) {
        Configuration(baseConfiguration).apply {
            setLocale(Locale.forLanguageTag(selectedLanguage))
        }
    }
    SideEffect {
        Locale.setDefault(Locale.forLanguageTag(selectedLanguage))
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalLayoutDirection provides if (selectedLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
    SkillectTheme(themeMode = profileState.preferences.themeMode) {
        if (authState.isRestoringSession) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    SkillectBrandBadge(size = 44.dp)
                    CircularProgressIndicator(modifier = Modifier.padding(top = 20.dp))
                    Text(
                        text = "Opening your account",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return@SkillectTheme
        }

        if (authState.isAuthenticated && authState.accountEnforcement != null) {
            AccountRestrictedScreen(
                uiState = authState,
                viewModel = authVm,
            )
            return@SkillectTheme
        }

        if (authState.mode == AuthMode.RESET_PASSWORD) {
            PasswordResetScreen(
                uiState = authState,
                viewModel = authVm,
            )
            return@SkillectTheme
        }

        if (authState.isAuthenticated && authState.needsOnboarding) {
            OnboardingScreen(
                uiState = authState,
                viewModel = authVm,
                mediaUploadManager = container.mediaUploadManager,
                isDarkMode = isDarkActive,
                onToggleTheme = toggleTheme,
            )
            return@SkillectTheme
        }

        if (authState.authSheetOpen) {
            BackHandler { authVm.closeAuthPrompt() }
            AuthScreen(
                uiState = authState,
                viewModel = authVm,
                onContinueGuestExplorer = { authVm.closeAuthPrompt() },
                isDarkMode = isDarkActive,
                onToggleTheme = toggleTheme,
            )
            return@SkillectTheme
        }

        val navItems = listOf(
            TopNavItem(SkillectRoute.Feed, stringResource(com.example.R.string.nav_feed), Icons.Rounded.Home, Icons.Outlined.Home, "nav_feed"),
            TopNavItem(SkillectRoute.Explore, stringResource(com.example.R.string.nav_explore), Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_explore"),
            TopNavItem(SkillectRoute.Composer(), stringResource(com.example.R.string.nav_create), Icons.Rounded.AddBox, Icons.Outlined.AddBox, "nav_create"),
            TopNavItem(SkillectRoute.Messages, stringResource(com.example.R.string.nav_messages), Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_messages"),
            TopNavItem(SkillectRoute.Profile(), stringResource(com.example.R.string.nav_profile), Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile"),
        )

        val onSelectNavItem: (TopNavItem) -> Unit = { item ->
            if (item.route is SkillectRoute.Composer && currentUserId.isBlank()) {
                authVm.openAuthPrompt(
                    mode = AuthMode.SIGN_UP,
                    reason = "Create an account or sign in to publish posts, stories, and short videos.",
                )
            } else {
                navController.navigate(item.route) {
                    launchSingleTop = true
                }
            }
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val dest = backStackEntry?.destination
        val showChrome = dest?.hasRoute<SkillectRoute.Shorts>() != true &&
            dest?.hasRoute<SkillectRoute.ChatDetail>() != true &&
            dest?.hasRoute<SkillectRoute.Composer>() != true

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        SkillectSnackbarPill(data = data)
                    }
                },
                bottomBar = {
                    if (showChrome && !isWide) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.background,
                                tonalElevation = 0.dp,
                                modifier = Modifier.navigationBarsPadding(),
                            ) {
                                navItems.forEach { item ->
                                    val selected = dest?.hasRoute(item.route::class) == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { onSelectNavItem(item) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = Color.Transparent,
                                        ),
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = item.label,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        },
                                        alwaysShowLabel = false,
                                        modifier = Modifier.testTag(item.tag),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (showChrome && isWide) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.background,
                            header = {
                                SkillectBrandBadge(
                                    size = 36.dp,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        ) {
                            navItems.forEach { item ->
                                val selected = dest?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { onSelectNavItem(item) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.tag),
                                )
                            }
                        }
                        VerticalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    val contentModifier = if (dest?.hasRoute<SkillectRoute.Shorts>() == true) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    }

                    Box(modifier = contentModifier) {
                        NavHost(
                            navController = navController,
                            startDestination = SkillectRoute.Feed,
                            enterTransition = { fadeIn(animationSpec = tween(180)) },
                            exitTransition = { fadeOut(animationSpec = tween(150)) },
                        ) {
                            composable<SkillectRoute.Feed> {
                                FeedScreen(
                                    uiState = feedState,
                                    currentUserId = currentUserId,
                                    unreadNotificationCount = msgState.unreadNotificationsCount,
                                    mediaUploadManager = container.mediaUploadManager,
                                    viewModel = feedVm,
                                    isDarkMode = isDarkActive,
                                    onToggleTheme = toggleTheme,
                                    onOpenShorts = { navController.navigate(SkillectRoute.Shorts) },
                                    onOpenCommunities = { navController.navigate(SkillectRoute.Communities) },
                                    onOpenNotifications = { navController.navigate(SkillectRoute.Notifications) },
                                    onOpenSettings = { navController.navigate(SkillectRoute.Settings) },
                                    onOpenComposer = {
                                        if (currentUserId.isBlank()) {
                                            requireAuth(true, "Create an account or sign in to publish posts.")
                                        } else {
                                            navController.navigate(SkillectRoute.Composer())
                                        }
                                    },
                                    onOpenProfile = { uid -> navController.navigate(SkillectRoute.Profile(uid)) },
                                    onSearchHashtag = { tag ->
                                        exploreVm.updateQuery(tag)
                                        exploreVm.refreshExplore()
                                        navController.navigate(SkillectRoute.Explore)
                                    },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.Shorts> {
                                ShortsScreen(
                                    uiState = feedState,
                                    currentUserId = currentUserId,
                                    viewModel = feedVm,
                                    onBack = { navController.popBackStack() },
                                    onOpenProfile = { uid -> navController.navigate(SkillectRoute.Profile(uid)) },
                                    onOpenComposer = {
                                        if (currentUserId.isBlank()) {
                                            requireAuth(true, "Create an account or sign in to upload short videos.")
                                        } else {
                                            navController.navigate(SkillectRoute.Composer())
                                        }
                                    },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.Explore> {
                                ExploreScreen(
                                    uiState = exploreState,
                                    viewModel = exploreVm,
                                    onOpenProfile = { uid -> navController.navigate(SkillectRoute.Profile(uid)) },
                                    onOpenCommunity = { cid -> navController.navigate(SkillectRoute.CommunityDetail(cid)) },
                                )
                            }

                            composable<SkillectRoute.Composer> { backStack ->
                                val route = backStack.toRoute<SkillectRoute.Composer>()
                                ComposerScreen(
                                    currentUserId = currentUserId,
                                    communityId = route.communityId,
                                    repository = container.repository,
                                    mediaUploadManager = container.mediaUploadManager,
                                    onPostCreated = {
                                        feedVm.refreshFeed(reset = true)
                                        navController.popBackStack()
                                    },
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable<SkillectRoute.Communities> {
                                CommunitiesScreen(
                                    uiState = exploreState,
                                    currentUserId = currentUserId,
                                    viewModel = exploreVm,
                                    onOpenCommunity = { cid -> navController.navigate(SkillectRoute.CommunityDetail(cid)) },
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.CommunityDetail> { backStack ->
                                val route = backStack.toRoute<SkillectRoute.CommunityDetail>()
                                CommunityDetailScreen(
                                    communityId = route.communityId,
                                    currentUserId = currentUserId,
                                    uiState = exploreState,
                                    viewModel = exploreVm,
                                    onCreateCommunityPost = { cid ->
                                        if (currentUserId.isBlank()) {
                                            requireAuth(true, "Create an account or sign in to post in communities.")
                                        } else {
                                            navController.navigate(SkillectRoute.Composer(cid))
                                        }
                                    },
                                    onOpenProfile = { uid -> navController.navigate(SkillectRoute.Profile(uid)) },
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.Messages> {
                                MessagesInboxScreen(
                                    uiState = msgState,
                                    currentUserId = currentUserId,
                                    viewModel = msgVm,
                                    onOpenConversation = { cid, peerName ->
                                        navController.navigate(SkillectRoute.ChatDetail(cid, peerName))
                                    },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.ChatDetail> { backStack ->
                                val route = backStack.toRoute<SkillectRoute.ChatDetail>()
                                ChatDetailScreen(
                                    conversationId = route.conversationId,
                                    peerName = route.peerName,
                                    currentUserId = currentUserId,
                                    uiState = msgState,
                                    viewModel = msgVm,
                                    onBack = { navController.popBackStack() },
                                )
                            }

                            composable<SkillectRoute.Notifications> {
                                NotificationsScreen(
                                    uiState = msgState,
                                    currentUserId = currentUserId,
                                    viewModel = msgVm,
                                    onBack = { navController.popBackStack() },
                                    onOpenProfile = { uid -> navController.navigate(SkillectRoute.Profile(uid)) },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.Profile> { backStack ->
                                val route = backStack.toRoute<SkillectRoute.Profile>()
                                ProfileScreen(
                                    targetUserId = route.userId,
                                    currentUserId = currentUserId,
                                    uiState = profileState,
                                    viewModel = profileVm,
                                    mediaUploadManager = container.mediaUploadManager,
                                    onOpenSettings = { navController.navigate(SkillectRoute.Settings) },
                                    onOpenConversation = { cid, peerName ->
                                        navController.navigate(SkillectRoute.ChatDetail(cid, peerName))
                                    },
                                    onRequireAuth = requireAuth,
                                    onBack = if (route.userId != null) {
                                        { navController.popBackStack() }
                                    } else {
                                        null
                                    },
                                )
                            }

                            composable<SkillectRoute.Settings> {
                                SettingsScreen(
                                    uiState = profileState,
                                    currentUserId = currentUserId,
                                    viewModel = profileVm,
                                    onOpenAdminModeration = { navController.navigate(SkillectRoute.ModerationAdmin) },
                                    onBack = { navController.popBackStack() },
                                    onRequireAuth = requireAuth,
                                )
                            }

                            composable<SkillectRoute.ModerationAdmin> {
                                ModerationAdminScreen(
                                    uiState = profileState,
                                    viewModel = profileVm,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
     }
    }
}
