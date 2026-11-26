package app.braindump

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.braindump.data.Dates
import app.braindump.data.Repository
import app.braindump.data.Settings
import app.braindump.notify.Alarms
import app.braindump.notify.Notifications
import app.braindump.ui.BrainDumpTheme
import app.braindump.ui.DayScreen
import app.braindump.ui.JournalScreen
import app.braindump.ui.SearchScreen
import app.braindump.ui.ThreadsScreen
import app.braindump.ui.ThreadScreen
import app.braindump.ui.PastDayPicker
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import app.braindump.ui.Hues
import app.braindump.ui.AppLock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import app.braindump.ui.MonthScreen
import app.braindump.ui.isDark
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var pendingOpen by mutableStateOf<String?>(null)
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L
    private var lockEnabled = false

    override fun onStart() {
        super.onStart()
        // Lock again if the app was away for more than 30 seconds (short trips, like picking a photo, don't count).
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000) { locked = true; app.braindump.ui.HiddenLock.unlocked = false }
        if (lockEnabled && locked) askUnlock()
    }

    private fun askUnlock() = AppLock.authenticate(this) { ok -> if (ok) locked = false }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = Repository.get(this)
        Notifications.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        lifecycleScope.launch { Alarms.scheduleAll(this@MainActivity, repo.settings.current()) }
        pendingOpen = intent?.getStringExtra(Notifications.EXTRA_OPEN)

        setContent {
            // Wait for real settings so a locked journal never flashes on screen.
            val loaded by repo.settings.settings.collectAsStateWithLifecycle(null)
            val settings = loaded ?: return@setContent
            val dark = isDark(settings.theme)
            // Keep status and navigation bar icons readable when the app theme differs from the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BrainDumpTheme(settings.theme) { androidx.compose.runtime.CompositionLocalProvider(app.braindump.ui.LocalTextScale provides settings.textScale) {
                val nav = rememberNavController()
                LaunchedEffect(pendingOpen) {
                    val open = pendingOpen
                    if (open == "review") nav.navigate("day/${Dates.today()}") { launchSingleTop = true }
                    if (open != null && open.startsWith("thread:")) nav.navigate("thread/${open.removePrefix("thread:")}") { launchSingleTop = true }
                    pendingOpen = null
                }
                LaunchedEffect(settings.appLock) {
                    lockEnabled = settings.appLock
                    // With the lock on, the recent-apps screen shows a blank card instead of your journal.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!settings.appLock)
                    if (settings.appLock && locked) askUnlock()
                }
                if (settings.appLock && locked) {
                    // Nothing of the journal shows until you unlock.
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BrainDump", style = MaterialTheme.typography.displaySmall)
                            Spacer(androidx.compose.ui.Modifier.height(16.dp))
                            androidx.compose.material3.TextButton(onClick = { askUnlock() }) { Text("Unlock") }
                        }
                    }
                } else {
                    AppScaffold(nav, repo, settings)
                }
            } }
        }
    }

    // Logging in the app counts as activity, so the next nudge moves out by one interval.
    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        val repo = Repository.get(this)
        lifecycleScope.launch { Alarms.scheduleAll(applicationContext, repo.settings.current()) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingOpen = intent.getStringExtra(Notifications.EXTRA_OPEN)
    }
}

@Composable
private fun AppScaffold(nav: NavHostController, repo: Repository, settings: Settings) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var pickingDay by remember { mutableStateOf(false) }
    if (pickingDay) {
        PastDayPicker(java.time.LocalDate.now(), onDismiss = { pickingDay = false }) { pickingDay = false; nav.navigate("day/$it") }
    }
    Scaffold(
        floatingActionButton = {
            // From the journal, one tap opens today's page.
            if (route == "journal") Column(horizontalAlignment = Alignment.End) {
                // Small +: open any past day from a calendar.
                SmallFloatingActionButton(
                    onClick = { pickingDay = true },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = Hues.Journal,
                ) { Icon(Icons.Rounded.Add, contentDescription = "Write about another day") }
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate("day/${Dates.today()}") { launchSingleTop = true } },
                    containerColor = Hues.Journal,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    text = { Text("Today") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "journal", modifier = Modifier.padding(padding)) {
            composable("journal") {
                JournalScreen(
                    repo, onOpenMonth = { nav.navigate("month/$it") }, onSearch = { nav.navigate("search") },
                    onThreads = { nav.navigate("threads") { launchSingleTop = true } },
                    onOpenThread = { nav.navigate("thread/$it") },
                )
            }
            composable("threads") {
                ThreadsScreen(repo, onDays = { nav.popBackStack("journal", false) }, onOpen = { nav.navigate("thread/$it") }, onSearch = { nav.navigate("search-threads") }, onHidden = { nav.navigate("threads-hidden") { launchSingleTop = true } })
            }
            composable("threads-hidden") {
                ThreadsScreen(repo, onDays = {}, onOpen = { nav.navigate("thread/$it") }, onSearch = {}, hiddenOnly = true, onBack = { nav.popBackStack() })
            }
            composable("thread/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                ThreadScreen(repo, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("search") { SearchScreen(repo, onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate("day/$it") }, onOpenThread = { nav.navigate("thread/$it") }) }
            composable("search-threads") { SearchScreen(repo, onBack = { nav.popBackStack() }, onOpenDay = {}, onOpenThread = { nav.navigate("thread/$it") }, threadsOnly = true) }
            composable("month/{ym}", arguments = listOf(navArgument("ym") { type = NavType.StringType })) { entry ->
                MonthScreen(
                    repo, entry.arguments?.getString("ym") ?: Dates.today().take(7),
                    onBack = { nav.popBackStack() }, onOpenDay = { nav.navigate("day/$it") },
                )
            }
            composable("day/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { entry ->
                DayScreen(repo, entry.arguments?.getString("date") ?: Dates.today(), onBack = { nav.popBackStack() })
            }
        }
    }
}
