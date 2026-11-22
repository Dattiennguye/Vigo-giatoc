package vn.vigo.nepnha.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.vigo.nepnha.AppContainer
import vn.vigo.nepnha.ui.calendar.CalendarScreen
import vn.vigo.nepnha.ui.clan.ClanCreateScreen
import vn.vigo.nepnha.ui.clan.ClanDetailScreen
import vn.vigo.nepnha.ui.clan.ClanJoinScreen
import vn.vigo.nepnha.ui.clan.ClanScreen
import vn.vigo.nepnha.ui.event.EventEditScreen
import vn.vigo.nepnha.ui.home.HomeScreen
import vn.vigo.nepnha.ui.more.AboutScreen
import vn.vigo.nepnha.ui.more.MoreScreen
import vn.vigo.nepnha.ui.more.ReminderHealthScreen
import vn.vigo.nepnha.ui.more.SettingsScreen
import vn.vigo.nepnha.ui.notifications.NotificationsScreen
import vn.vigo.nepnha.ui.tasks.TaskEditScreen
import vn.vigo.nepnha.ui.tasks.TasksScreen
import vn.vigo.nepnha.ui.theme.LocalPerformance
import vn.vigo.nepnha.ui.theme.NepColors

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val CLAN = "clan"
    const val NOTIFICATIONS = "notifications"
    const val MORE = "more"
    const val EVENT_EDIT = "event/edit?id={id}&date={date}"
    const val TASKS = "tasks"
    const val TASK_EDIT = "task/edit?id={id}&template={template}"
    const val SETTINGS = "settings"
    const val REMINDER_HEALTH = "reminder-health"
    const val ABOUT = "about"
    const val CLAN_CREATE = "clan/create"
    const val CLAN_JOIN = "clan/join"
    const val CLAN_DETAIL = "clan/detail/{clanId}"

    fun clanDetail(id: String) = "clan/detail/$id"

    fun eventEdit(id: String? = null, epochDay: Long? = null) =
        "event/edit?id=${id.orEmpty()}&date=${epochDay ?: -1L}"

    fun taskEdit(id: String? = null, template: Boolean = false) = "task/edit?id=${id.orEmpty()}&template=$template"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Trang chủ", Icons.Filled.Home),
    Tab(Routes.CALENDAR, "Lịch", Icons.Filled.DateRange),
    Tab(Routes.CLAN, "Gia tộc", Icons.Filled.Person),
    Tab(Routes.NOTIFICATIONS, "Thông báo", Icons.Filled.Notifications),
    Tab(Routes.MORE, "Thêm", Icons.Filled.Menu),
)

/** Điều hướng giữa các tab: giữ trạng thái từng tab, không chồng màn hình. */
fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun NepNhaRoot(container: AppContainer, openTab: String?, onTabOpened: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val unreadFlow = remember { container.db.notifications().observeUnread() }
    val unread by unreadFlow.collectAsStateWithLifecycle(0)
    val reduceMotion = LocalPerformance.current.reduceMotion

    LaunchedEffect(openTab) {
        if (openTab == "notifications") {
            nav.openTab(Routes.NOTIFICATIONS)
            onTabOpened()
        }
    }

    Scaffold(
        containerColor = NepColors.Ivory,
        // Mỗi màn hình tự xử lý thanh trạng thái (ScreenHeader) để không bị đệm hai lần.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (TABS.any { it.route == current }) {
                NavigationBar(containerColor = NepColors.WarmWhite, tonalElevation = 0.dp) {
                    TABS.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) nav.openTab(tab.route) },
                            icon = {
                                if (tab.route == Routes.NOTIFICATIONS && unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NepColors.DeepRed,
                                selectedTextColor = NepColors.DeepRed,
                                indicatorColor = NepColors.RedLight,
                                unselectedIconColor = NepColors.Wood,
                                unselectedTextColor = NepColors.InkSoft,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val enter: EnterTransition = if (reduceMotion) EnterTransition.None else fadeIn(tween(160))
        val exit: ExitTransition = if (reduceMotion) ExitTransition.None else fadeOut(tween(120))
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { enter },
            exitTransition = { exit },
            popEnterTransition = { enter },
            popExitTransition = { exit },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    container = container,
                    onAddEvent = { nav.navigate(Routes.eventEdit()) },
                    onAddTask = { nav.navigate(Routes.taskEdit()) },
                    onOpenEvent = { nav.navigate(Routes.eventEdit(it)) },
                    onOpenTasks = { nav.navigate(Routes.TASKS) },
                    onOpenCalendar = { nav.openTab(Routes.CALENDAR) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    container = container,
                    onAddEvent = { date -> nav.navigate(Routes.eventEdit(epochDay = date.toEpochDay())) },
                    onOpenEvent = { nav.navigate(Routes.eventEdit(it)) },
                )
            }
            composable(Routes.CLAN) {
                ClanScreen(
                    container = container,
                    onCreate = { nav.navigate(Routes.CLAN_CREATE) },
                    onJoin = { nav.navigate(Routes.CLAN_JOIN) },
                    onOpenClan = { nav.navigate(Routes.clanDetail(it)) },
                )
            }
            composable(Routes.CLAN_CREATE) {
                container.clans?.let { repo ->
                    ClanCreateScreen(repo, onBack = { nav.popBackStack() }, onCreated = { id ->
                        nav.navigate(Routes.clanDetail(id)) { popUpTo(Routes.CLAN) }
                    })
                }
            }
            composable(Routes.CLAN_JOIN) {
                container.clans?.let { repo -> ClanJoinScreen(repo, onBack = { nav.popBackStack() }) }
            }
            composable(
                Routes.CLAN_DETAIL,
                arguments = listOf(navArgument("clanId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("clanId").orEmpty()
                container.clans?.let { repo -> ClanDetailScreen(repo, id, onBack = { nav.popBackStack() }) }
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(container, onOpenHealth = { nav.navigate(Routes.REMINDER_HEALTH) })
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onOpenTasks = { nav.navigate(Routes.TASKS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenHealth = { nav.navigate(Routes.REMINDER_HEALTH) },
                    onOpenAbout = { nav.navigate(Routes.ABOUT) },
                )
            }
            composable(
                Routes.EVENT_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                EventEditScreen(
                    container = container,
                    eventId = entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() },
                    initialEpochDay = entry.arguments?.getLong("date")?.takeIf { it >= 0 },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.TASKS) {
                TasksScreen(
                    container = container,
                    onBack = { nav.popBackStack() },
                    onOpen = { id, template -> nav.navigate(Routes.taskEdit(id, template)) },
                    onAdd = { template -> nav.navigate(Routes.taskEdit(null, template)) },
                )
            }
            composable(
                Routes.TASK_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("template") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                TaskEditScreen(
                    container = container,
                    taskId = entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() },
                    asTemplate = entry.arguments?.getBoolean("template") ?: false,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(container, onBack = { nav.popBackStack() }) }
            composable(Routes.REMINDER_HEALTH) { ReminderHealthScreen(container, onBack = { nav.popBackStack() }) }
            composable(Routes.ABOUT) { AboutScreen(container, onBack = { nav.popBackStack() }) }
        }
    }
}

