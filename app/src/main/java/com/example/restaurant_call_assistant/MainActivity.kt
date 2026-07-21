package com.example.restaurant_call_assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.restaurant_call_assistant.calendar.CalendarCreateStatus
import com.example.restaurant_call_assistant.calendar.CalendarRepository
import com.example.restaurant_call_assistant.call.CallScreeningRole
import com.example.restaurant_call_assistant.data.AppSettings
import com.example.restaurant_call_assistant.data.LocalStore
import com.example.restaurant_call_assistant.data.PhoneNumbers
import com.example.restaurant_call_assistant.data.Reservation
import com.example.restaurant_call_assistant.data.ReservationStatus
import com.example.restaurant_call_assistant.data.WhitelistEntry
import com.example.restaurant_call_assistant.data.WhitelistMode
import com.example.restaurant_call_assistant.overlay.ReservationOverlayService
import com.example.restaurant_call_assistant.ui.theme.RestaurantcallassistantTheme

private val Black = Color(0xFF050505)
private val White = Color(0xFFFFFFFF)
private val Line = Color(0xFFE6E2DE)
private val DarkBeige = Color(0xFF684E42)
private val InkMuted = Color(0xFF68615D)
private val FieldFill = Color(0xFFF8F6F4)
private val ErrorRed = Color(0xFFA12016)
private val SuccessGreen = Color(0xFF1B6B3A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RestaurantcallassistantTheme {
                ReservationHelperApp()
            }
        }
    }
}

private enum class MainTab(val label: String) {
    Dashboard("Dashboard"),
    Whitelist("Whitelist"),
    History("History"),
    Permissions("Permissions")
}

@Composable
private fun ReservationHelperApp() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var selectedTab by remember { mutableStateOf(MainTab.Dashboard) }
    var showSettings by remember { mutableStateOf(false) }
    var onboardingComplete by remember { mutableStateOf(store.isOnboardingComplete()) }
    var settings by remember { mutableStateOf(store.getSettings()) }
    var reservations by remember { mutableStateOf(store.getReservations()) }
    var whitelist by remember { mutableStateOf(store.getWhitelist()) }

    fun refresh() {
        settings = store.getSettings()
        reservations = store.getReservations()
        whitelist = store.getWhitelist()
        onboardingComplete = store.isOnboardingComplete()
    }

    if (!onboardingComplete || !CallScreeningRole.isReady(context)) {
        OnboardingScreen(
            onComplete = {
                store.setOnboardingComplete(true)
                refresh()
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                onSettingsClick = {
                    showSettings = true
                }
            )
        },
        bottomBar = {
            if (!showSettings) {
                BottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = {
                        selectedTab = it
                        showSettings = false
                    }
                )
            }
        },
        containerColor = White
    ) { padding ->
        Surface(
            color = White,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSettings) {
                SettingsScreen(
                    settings = settings,
                    onSave = {
                        store.saveSettings(it)
                        refresh()
                    },
                    onBack = { showSettings = false }
                )
            } else {
                when (selectedTab) {
                    MainTab.Dashboard -> DashboardScreen(
                        settings = settings,
                        reservations = reservations
                    )
                    MainTab.Whitelist -> WhitelistScreen(
                        entries = whitelist,
                        onSave = {
                            store.saveWhitelistEntry(it)
                            refresh()
                        },
                        onDelete = {
                            store.deleteWhitelistEntry(it)
                            refresh()
                        }
                    )
                    MainTab.History -> HistoryScreen(
                        settings = settings,
                        reservations = reservations,
                        onSave = {
                            store.saveReservation(it)
                            refresh()
                        },
                        onDelete = {
                            store.deleteReservation(it)
                            refresh()
                        }
                    )
                    MainTab.Permissions -> PermissionsScreen()
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var ready by remember {
        mutableStateOf(
            context.requiredPermissionsGranted() &&
                context.canDrawOverlaysCompat() &&
                CallScreeningRole.isReady(context)
        )
    }
    fun refreshPermissions() {
        refreshKey++
        ready = context.requiredPermissionsGranted() &&
            context.canDrawOverlaysCompat() &&
            CallScreeningRole.isReady(context)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshPermissions()
    }
    RefreshPermissionsOnResume { refreshPermissions() }

    Surface(color = White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .background(Black),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Image(
                        painter = painterResource(R.drawable.la_veranda_reservation_manager_logo),
                        contentDescription = "La Veranda Reservations",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(54.dp)
                            .padding(horizontal = 18.dp)
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Black)
                    Text(
                        "Enable the required access so reservation popups and calendar updates work during calls.",
                        color = InkMuted
                    )

                    PermissionChecklist(refreshKey)
                }
            }

            Column(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = "Enable permissions",
                    onClick = { launcher.launch(requiredRuntimePermissions()) }
                )
                SecondaryButton(
                    text = "Enable overlay",
                    onClick = {
                        context.openOverlaySettings()
                        refreshPermissions()
                    }
                )
                if (CallScreeningRole.isAvailable(context)) {
                    SecondaryButton(
                        text = if (CallScreeningRole.isHeld(context)) {
                            "Caller ID enabled"
                        } else {
                            "Enable caller ID"
                        },
                        onClick = {
                            CallScreeningRole.requestIntent(context)?.let(roleLauncher::launch)
                        }
                    )
                }
                SecondaryButton(
                    text = "Battery settings",
                    onClick = {
                        context.openBatterySettings()
                        refreshPermissions()
                    }
                )
                Button(
                    onClick = onComplete,
                    enabled = ready,
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ready) DarkBeige else Line,
                        contentColor = if (ready) White else InkMuted,
                        disabledContainerColor = Line,
                        disabledContentColor = InkMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (ready) "Enter app" else "Finish required permissions")
                }
            }
        }
    }
}

@Composable
private fun TopAppBar(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black)
            .padding(top = 34.dp, start = 16.dp, end = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Image(
                painter = painterResource(R.drawable.la_veranda_reservation_manager_logo),
                contentDescription = "La Veranda Reservations",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(42.dp)
                    .width(115.dp)
            )
        }
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24),
                    contentDescription = "Settings",
                    tint = White
                )
            }
        }
    }
}

@Composable
private fun BottomNavigation(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(White)
            .border(BorderStroke(1.dp, Line))
    ) {
        MainTab.entries.forEach { tab ->
            val selected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(if (selected) Black else White)
                    .border(BorderStroke(1.dp, if (selected) Black else Line))
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = if (selected) White else InkMuted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    settings: AppSettings,
    reservations: List<Reservation>
) {
    val context = LocalContext.current
    val bookedReservations = reservations.count { it.status == ReservationStatus.CREATED }
    Page(title = "Dashboard") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Popup", if (settings.popupEnabled) "On" else "Off", Modifier.weight(1f))
            MetricCard("Booked", bookedReservations.toString(), Modifier.weight(1f))
        }
        MetricCard("Google Calendar", settings.selectedCalendarName ?: "Auto", Modifier.fillMaxWidth())

        PrimaryButton(text = "Test popup", onClick = { context.startReservationOverlay(null) })

        SectionTitle("Recent")
        if (reservations.isEmpty()) {
            EmptyState("No reservations yet.")
        } else {
            ListCard {
                reservations.take(4).forEachIndexed { index, reservation ->
                    CompactReservationRow(reservation)
                    if (index != reservations.take(4).lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun WhitelistScreen(
    entries: List<WhitelistEntry>,
    onSave: (WhitelistEntry) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val contact = uri?.let { context.resolveContact(it) }
        if (contact == null) {
            error = "No phone number found for that contact."
        } else {
            name = contact.name
            phone = contact.phone
            error = null
        }
    }

    Page(title = "Whitelist") {
        ListCard {
            Text("Add ignored caller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Black)
            Text("Choose a contact first. Manual entry is available below.", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = "Select contact",
                    onClick = { pickContact.launch(null) },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Clear",
                    onClick = {
                        name = ""
                        phone = ""
                        error = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            SharpTextField(name, { name = it }, "Contact name")
            SharpTextField(phone, { phone = it }, "Phone number", KeyboardType.Phone)
            error?.let { Text(it, color = ErrorRed) }
            PrimaryButton(
                text = "Add ignored number",
                onClick = {
                    val normalized = PhoneNumbers.normalize(phone)
                    if (normalized.isBlank()) {
                        error = "Add a phone number first."
                    } else {
                        onSave(
                            WhitelistEntry(
                                displayName = name.trim().ifBlank { null },
                                rawPhoneNumber = phone.trim(),
                                normalizedPhoneNumber = normalized,
                                note = null
                            )
                        )
                        name = ""
                        phone = ""
                        error = null
                    }
                }
            )
        }

        SectionTitle("Ignored numbers")
        if (entries.isEmpty()) {
            EmptyState("No ignored callers.")
        } else {
            ListCard {
                entries.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.displayName ?: "Unnamed", fontWeight = FontWeight.SemiBold, color = Black)
                            Text(entry.normalizedPhoneNumber, color = InkMuted)
                        }
                        TextButton(onClick = { onDelete(entry.id) }) {
                            Text("Delete", color = DarkBeige)
                        }
                    }
                    if (index != entries.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    settings: AppSettings,
    reservations: List<Reservation>,
    onSave: (Reservation) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val filtered = reservations.filter {
        val needle = query.trim()
        needle.isBlank() ||
            it.customerName.contains(needle, ignoreCase = true) ||
            it.phoneNumber.orEmpty().contains(needle, ignoreCase = true)
    }

    Page(title = "History") {
        SharpTextField(query, { query = it }, "Search")
        if (filtered.isEmpty()) {
            EmptyState("No matching reservations.")
        } else {
            ListCard {
                filtered.forEachIndexed { index, reservation ->
                    CompactReservationRow(reservation)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (reservation.status == ReservationStatus.FAILED || reservation.status == ReservationStatus.DRAFT) {
                            TextButton(onClick = {
                                val result = CalendarRepository(context).createOrOpenFallback(reservation, settings.selectedCalendarId)
                                onSave(reservation.withCalendarResult(result))
                            }) {
                                Text(if (reservation.status == ReservationStatus.DRAFT) "Create" else "Retry", color = DarkBeige)
                            }
                        }
                        reservation.calendarEventId?.let { eventId ->
                            TextButton(onClick = { runCatching { CalendarRepository(context).openEvent(eventId) } }) {
                                Text("Open", color = DarkBeige)
                            }
                        }
                        TextButton(onClick = { onDelete(reservation.id) }) {
                            Text("Delete", color = InkMuted)
                        }
                    }
                    reservation.errorMessage?.let {
                        Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (index != filtered.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
private fun PermissionsScreen() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshKey++
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshKey++
    }
    RefreshPermissionsOnResume { refreshKey++ }

    Page(title = "Permissions") {
        PermissionChecklist(refreshKey)
        PrimaryButton(text = "Enable missing permissions", onClick = { launcher.launch(requiredRuntimePermissions()) })
        if (CallScreeningRole.isAvailable(context)) {
            SecondaryButton(
                text = if (CallScreeningRole.isHeld(context)) "Caller ID enabled" else "Enable caller ID",
                onClick = { CallScreeningRole.requestIntent(context)?.let(roleLauncher::launch) }
            )
        }
        SecondaryButton(text = "Overlay permission", onClick = { context.openOverlaySettings() })
        SecondaryButton(text = "Battery settings", onClick = { context.openBatterySettings() })
    }
}

@Composable
private fun SettingsScreen(settings: AppSettings, onSave: (AppSettings) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings.copy(callTriggerMode = com.example.restaurant_call_assistant.data.CallTriggerMode.INCOMING)) }
    var durationText by remember(settings) { mutableStateOf(settings.defaultDurationMinutes.toString()) }
    var calendars by remember { mutableStateOf(CalendarRepository(context).writableCalendars()) }

    Page(title = "Settings") {
        SecondaryButton(text = "Back", onClick = onBack)

        ListCard {
            Text("Popup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Black)
            ToggleRow("Enabled", draft.popupEnabled) { draft = draft.copy(popupEnabled = it) }
            ToggleRow("Unknown numbers", draft.showForUnknownNumbers) { draft = draft.copy(showForUnknownNumbers = it) }
            ToggleRow("Autofill caller number", draft.autoFillCallerNumber) { draft = draft.copy(autoFillCallerNumber = it) }
            SharpTextField(
                value = durationText,
                onValueChange = {
                    durationText = it.filter(Char::isDigit)
                    draft = draft.copy(defaultDurationMinutes = durationText.toIntOrNull()?.coerceAtLeast(15) ?: 240)
                },
                label = "Default duration",
                keyboardType = KeyboardType.Number
            )
        }

        ListCard {
            Text("Whitelist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Black)
            ToggleRow("Ignore listed numbers", draft.whitelistMode == WhitelistMode.IGNORE_LISTED) { enabled ->
                draft = draft.copy(
                    whitelistMode = if (enabled) WhitelistMode.IGNORE_LISTED else WhitelistMode.ONLY_LISTED
                )
            }
            Text(
                if (draft.whitelistMode == WhitelistMode.IGNORE_LISTED) {
                    "Calls from saved ignored numbers will not show the popup."
                } else {
                    "The popup will show for every incoming call."
                },
                color = InkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }

        ListCard {
            Text("Google Calendar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Black)
            Text(draft.selectedCalendarName ?: "Auto-select first writable Google calendar", color = InkMuted)
            SecondaryButton(text = "Refresh Google calendars", onClick = { calendars = CalendarRepository(context).writableCalendars() })
            calendars.forEach { calendar ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(calendar.displayName, fontWeight = FontWeight.SemiBold, color = Black)
                        Text(calendar.accountName, color = InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RadioButton(
                        selected = draft.selectedCalendarId == calendar.id,
                        onClick = { draft = draft.copy(selectedCalendarId = calendar.id, selectedCalendarName = calendar.displayName) }
                    )
                }
            }
            if (calendars.isEmpty()) Text("Grant calendar permission, make sure Google Calendar sync is enabled, then refresh.", color = InkMuted)
        }

        PrimaryButton(
            text = "Save settings",
            onClick = {
                val selectedGoogleCalendar = calendars.firstOrNull { it.id == draft.selectedCalendarId }
                onSave(
                    draft.copy(
                        selectedCalendarId = selectedGoogleCalendar?.id,
                        selectedCalendarName = selectedGoogleCalendar?.displayName,
                        callTriggerMode = com.example.restaurant_call_assistant.data.CallTriggerMode.INCOMING
                    )
                )
            }
        )
    }
}

@Composable
private fun RefreshPermissionsOnResume(onResume: () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val handler = Handler(Looper.getMainLooper())
        val delayedRefresh = Runnable { onResume() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
                handler.postDelayed(delayedRefresh, 250)
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            handler.removeCallbacks(delayedRefresh)
            activity?.lifecycle?.removeObserver(observer)
        }
    }
}

@Composable
private fun PermissionChecklist(refreshKey: Int) {
    val context = LocalContext.current
    val ignored = refreshKey
    ListCard {
        requiredRuntimePermissions().forEach { permission ->
            PermissionStatusRow(permissionLabel(permission), context.hasPermission(permission))
        }
        PermissionStatusRow("Overlay", context.canDrawOverlaysCompat())
        if (CallScreeningRole.isAvailable(context)) {
            PermissionStatusRow("Caller ID role", CallScreeningRole.isHeld(context))
        }
        PermissionStatusRow("Battery unrestricted", context.isIgnoringBatteryOptimizations(), required = false)
    }
    ignored.hashCode()
}

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Black)
        content()
    }
}

@Composable
private fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(BorderStroke(1.dp, Black))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = InkMuted)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompactReservationRow(reservation: Reservation) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(reservation.customerName, fontWeight = FontWeight.Bold, color = Black, modifier = Modifier.weight(1f))
            StatusPill(reservation.status)
        }
        val partyText = reservation.partySize?.let { " | $it people" }.orEmpty()
        Text("${reservation.reservationDate} ${reservation.reservationTime}$partyText", color = InkMuted)
        reservation.phoneNumber?.let { Text(it, color = InkMuted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun StatusPill(status: ReservationStatus) {
    val (label, color) = when (status) {
        ReservationStatus.CREATED -> "Created" to SuccessGreen
        ReservationStatus.DRAFT -> "Draft" to DarkBeige
        ReservationStatus.FAILED -> "Failed" to ErrorRed
        ReservationStatus.OPENED_IN_CALENDAR -> "Opened" to DarkBeige
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .border(BorderStroke(1.dp, color))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = DarkBeige)
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Line))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = InkMuted)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = White),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(1.dp, Black),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Black),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text)
    }
}

@Composable
private fun SharpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(0.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Black)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean, required: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Black, fontWeight = FontWeight.SemiBold)
            if (!required) Text("Recommended", color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (granted) "Enabled" else "Missing",
            color = if (granted) SuccessGreen else ErrorRed,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun Reservation.withCalendarResult(result: com.example.restaurant_call_assistant.calendar.CalendarCreateResult): Reservation {
    return when (result.status) {
        CalendarCreateStatus.CREATED -> copy(
            status = ReservationStatus.CREATED,
            calendarEventId = result.eventId,
            errorMessage = null
        )
        CalendarCreateStatus.OPENED_FALLBACK -> copy(
            status = ReservationStatus.OPENED_IN_CALENDAR,
            errorMessage = result.errorMessage
        )
        CalendarCreateStatus.FAILED -> copy(
            status = ReservationStatus.FAILED,
            errorMessage = result.errorMessage
        )
    }
}

private data class PickedContact(val name: String, val phone: String)

private fun Context.resolveContact(uri: Uri): PickedContact? {
    val contactCursor = contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
        null,
        null,
        null
    )
    contactCursor?.use { cursor ->
        if (!cursor.moveToFirst()) return null
        val id = cursor.getString(0)
        val name = cursor.getString(1).orEmpty()
        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(id),
            null
        )
        phoneCursor?.use { phones ->
            if (!phones.moveToFirst()) return null
            return PickedContact(name = name, phone = phones.getString(0).orEmpty())
        }
    }
    return null
}

private fun permissionLabel(permission: String): String {
    return when (permission) {
        Manifest.permission.READ_PHONE_STATE -> "Phone state"
        Manifest.permission.READ_CALL_LOG -> "Caller number"
        Manifest.permission.READ_CALENDAR -> "Calendar read"
        Manifest.permission.WRITE_CALENDAR -> "Calendar write"
        Manifest.permission.READ_CONTACTS -> "Contacts"
        Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
        else -> permission.substringAfterLast(".")
    }
}

private fun requiredRuntimePermissions(): Array<String> {
    return buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.WRITE_CALENDAR)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
}

private fun Context.requiredPermissionsGranted(): Boolean {
    return requiredRuntimePermissions().all { hasPermission(it) }
}

private fun Context.startReservationOverlay(phone: String?) {
    if (!canDrawOverlaysCompat()) {
        openOverlaySettings()
        return
    }
    val intent = ReservationOverlayService.showIntent(this, phone)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.canDrawOverlaysCompat(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
}

private fun Context.openOverlaySettings() {
    startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
    )
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.openBatterySettings() {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:$packageName"))
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { startActivity(requestIntent) }.getOrElse { startActivity(fallbackIntent) }
}
