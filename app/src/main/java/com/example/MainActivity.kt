package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.BreakRecord
import com.example.data.Employee
import com.example.data.EmployeeRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import com.example.viewmodel.BreakTrackerViewModel
import com.example.viewmodel.BreakTrackerViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup local database and repository
        val database = AppDatabase.getDatabase(this)
        val repository = EmployeeRepository(database.appDao())
        val sharedPrefs = getSharedPreferences("break_tracker_prefs", MODE_PRIVATE)

        setContent {
            val viewModel: BreakTrackerViewModel = viewModel(
                factory = BreakTrackerViewModelFactory(repository, sharedPrefs)
            )

            // Start background monitoring for overtime alerts
            LaunchedEffect(Unit) {
                viewModel.startNotificationService(applicationContext)
            }

            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkMode, colorTheme = selectedTheme, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BreakTrackerApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BreakTrackerApp(
    viewModel: BreakTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeEmployeeId by remember { mutableStateOf<Int?>(null) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Employees, 1 = History, 2 = Settings

    // Dialog flags
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var showManagerProfileDialog by remember { mutableStateOf(false) }
    var employeeToDelete by remember { mutableStateOf<Employee?>(null) }
    var recordToDelete by remember { mutableStateOf<BreakRecord?>(null) }

    // Notification Permission Request
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Overtime reminders require notification access", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Handle back presses when in the Detail view
    if (activeEmployeeId != null) {
        androidx.activity.compose.BackHandler {
            activeEmployeeId = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (activeEmployeeId != null) {
            // Employee Detail Page
            EmployeeDetailPage(
                employeeId = activeEmployeeId!!,
                viewModel = viewModel,
                onBack = { activeEmployeeId = null },
                onDelete = { employee ->
                    employeeToDelete = employee
                }
            )
        } else {
            // Home / Dashboard Page
            Column(modifier = Modifier.fillMaxSize()) {
                // Customized Header & App Title
                HomeHeader(
                    viewModel = viewModel,
                    currentTab = currentTab,
                    onProfileClick = { showManagerProfileDialog = true }
                )

                // Tab contents
                Box(modifier = Modifier.weight(1f)) {
                    when (currentTab) {
                        0 -> {
                            EmployeesTabContent(
                                viewModel = viewModel,
                                onEmployeeClick = { employeeId ->
                                    activeEmployeeId = employeeId
                                },
                                onEmployeeLongClick = { employee ->
                                    employeeToDelete = employee
                                }
                            )

                            // Floating Action Button to add employee
                            FloatingActionButton(
                                onClick = { showAddEmployeeDialog = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(24.dp)
                                    .testTag("add_employee_fab"),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Employee")
                            }
                        }
                        1 -> {
                            HistoryTabContent(
                                viewModel = viewModel,
                                onRecordLongClick = { record ->
                                    recordToDelete = record
                                }
                            )
                        }
                        2 -> {
                            SettingsTabContent(
                                viewModel = viewModel
                            )
                        }
                    }
                }

                // Beautiful bottom navigation bar from the Professional Polish theme
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.People, contentDescription = "Employees") },
                        label = { Text("Employees", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.History, contentDescription = "Break Logs") },
                        label = { Text("Break Logs", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.testTag("settings_tab")
                    )
                }
            }
        }

        // Dialog: Add Employee
        if (showAddEmployeeDialog) {
            AddEmployeeDialog(
                onDismiss = { showAddEmployeeDialog = false },
                onAdd = { name, role, avatarIndex ->
                    viewModel.addEmployee(name, role, avatarIndex)
                    showAddEmployeeDialog = false
                    Toast.makeText(context, "Added $name", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Dialog: Manager Profile
        if (showManagerProfileDialog) {
            ManagerProfileDialog(
                viewModel = viewModel,
                onDismiss = { showManagerProfileDialog = false }
            )
        }

        // Dialog: Delete Employee Confirmation
        if (employeeToDelete != null) {
            AlertDialog(
                onDismissRequest = { employeeToDelete = null },
                title = { Text("Delete Employee?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to permanently delete ${employeeToDelete!!.name}?\nAll active breaks and history entries for this employee will be removed.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (activeEmployeeId == employeeToDelete!!.id) {
                                activeEmployeeId = null
                            }
                            viewModel.deleteEmployee(employeeToDelete!!)
                            employeeToDelete = null
                            Toast.makeText(context, "Employee deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { employeeToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog: Delete History Record Confirmation
        if (recordToDelete != null) {
            AlertDialog(
                onDismissRequest = { recordToDelete = null },
                title = { Text("Delete History Entry?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete this historical break entry for ${recordToDelete!!.employeeName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBreakRecord(recordToDelete!!)
                            recordToDelete = null
                            Toast.makeText(context, "History record deleted", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun HomeHeader(
    viewModel: BreakTrackerViewModel,
    currentTab: Int,
    onProfileClick: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Break Tracker",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Workplace Overtime Monitor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dark/Light Theme Toggle Action
                IconButton(
                    onClick = { viewModel.toggleDarkMode() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                        .testTag("dark_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Manager Profile Picture Button
                val managerAvatar by viewModel.managerAvatar.collectAsStateWithLifecycle()
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onProfileClick() }
                        .testTag("manager_profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (managerAvatar == "img_profile_avatar") {
                        Image(
                            painter = painterResource(id = R.drawable.img_profile_avatar),
                            contentDescription = "Manager Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val idx = managerAvatar.toIntOrNull() ?: 0
                        val icon = when (idx) {
                            1 -> Icons.Default.Face
                            2 -> Icons.Default.Build
                            3 -> Icons.Default.AccountCircle
                            4 -> Icons.Default.Star
                            5 -> Icons.Default.Notifications
                            else -> Icons.Default.Person
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Manager Profile",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (currentTab == 0) {
            Spacer(modifier = Modifier.height(14.dp))

            // Search Bar Input styled as a beautiful round capsule
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                placeholder = { 
                    Text(
                        "Search employees...", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ) 
                },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ) 
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                Icons.Default.Clear, 
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmployeesTabContent(
    viewModel: BreakTrackerViewModel,
    onEmployeeClick: (Int) -> Unit,
    onEmployeeLongClick: (Employee) -> Unit
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTimeMillis.collectAsStateWithLifecycle()

    if (employees.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Employees Found",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the '+' floating button at the bottom right to register a new employee and track their breaks.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(employees, key = { it.id }) { employee ->
                EmployeeItemCard(
                    employee = employee,
                    currentTime = currentTime,
                    onClick = { onEmployeeClick(employee.id) },
                    onLongClick = { onEmployeeLongClick(employee) },
                    onStartBreak = { viewModel.startBreak(employee) },
                    onStopBreak = { viewModel.stopBreak(employee) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmployeeItemCard(
    employee: Employee,
    currentTime: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStartBreak: () -> Unit,
    onStopBreak: () -> Unit
) {
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    val isOvertime = if (employee.isOnBreak && employee.breakStartTime != null) {
        val durationMs = currentTime - employee.breakStartTime
        (durationMs / 60000).toInt() >= 30
    } else {
        false
    }

    val leftBorderColor = if (isOvertime) {
        MaterialTheme.colorScheme.error
    } else if (employee.isOnBreak) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("employee_card_${employee.id}"),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (employee.isOnBreak) {
                        drawRect(
                            color = leftBorderColor,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height)
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Letter Avatar
            EmployeeAvatar(name = employee.name, avatarIndex = employee.avatarIndex, modifier = Modifier.size(44.dp))

            Spacer(modifier = Modifier.width(14.dp))

            // Employee Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employee.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val dotColor = if (employee.isOnBreak) {
                        if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    } else {
                        SuccessGreen
                    }
                    
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = dotColor.copy(alpha = if (employee.isOnBreak) pulseAlpha else 1f),
                                shape = CircleShape
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    val statusText = if (employee.isOnBreak && employee.breakStartTime != null) {
                        val durationMs = currentTime - employee.breakStartTime
                        val durationSecs = durationMs / 1000
                        val mins = durationSecs / 60
                        val secs = durationSecs % 60
                        val runningTimerText = String.format("%02d:%02d", mins, secs)
                        if (isOvertime) {
                            "On Break • $runningTimerText"
                        } else {
                            "On Break • $runningTimerText"
                        }
                    } else {
                        "Working"
                    }
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (employee.isOnBreak) {
                            if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        } else {
                            SuccessGreen
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Action button (Stop Break or Start Break)
            if (employee.isOnBreak) {
                Button(
                    onClick = onStopBreak,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "STOP BREAK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Button(
                    onClick = onStartBreak,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "START BREAK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
fun EmployeeAvatar(name: String, avatarIndex: Int = 0, modifier: Modifier = Modifier) {
    val firstChar = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val colors = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
        Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DB6AC),
        Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFFF8A65)
    )
    val bgColor = colors[name.hashCode().absoluteValue % colors.size]

    Box(
        modifier = modifier
            .background(bgColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarIndex == 0) {
            Text(
                text = firstChar,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        } else {
            val icon = when (avatarIndex) {
                1 -> Icons.Default.Face
                2 -> Icons.Default.Build
                3 -> Icons.Default.AccountCircle
                4 -> Icons.Default.Star
                5 -> Icons.Default.Notifications
                else -> Icons.Default.Person
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.6f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTabContent(
    viewModel: BreakTrackerViewModel,
    onRecordLongClick: (BreakRecord) -> Unit
) {
    val records by viewModel.allBreakRecords.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (records.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${records.size} Complete LogEntries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Button(
                    onClick = { viewModel.exportHistoryToCsv(context) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV")
                }
            }
        }

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "History is Empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recorded breaks will show up here after they are started and stopped on the employee cards.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryItemCard(
                        record = record,
                        viewModel = viewModel,
                        onLongClick = { onRecordLongClick(record) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    record: BreakRecord,
    viewModel: BreakTrackerViewModel,
    onLongClick: () -> Unit
) {
    val durationSecs = record.duration / 1000
    val mins = durationSecs / 60
    val secs = durationSecs % 60
    val durationFormatted = String.format("%02dm %02ds", mins, secs)

    val tzId by viewModel.selectedTimeZoneId.collectAsStateWithLifecycle()
    val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(tzId)
    }.format(Date(record.startTime))
    val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(tzId)
    }.format(Date(record.endTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {}, // Passive display, only long press deletes
                onLongClick = onLongClick
            )
            .testTag("history_card_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        val employees by viewModel.employees.collectAsStateWithLifecycle()
        val employeeAvatarIndex = remember(employees, record.employeeId) {
            employees.find { it.id == record.employeeId }?.avatarIndex ?: 0
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmployeeAvatar(name = record.employeeName, avatarIndex = employeeAvatarIndex, modifier = Modifier.size(36.dp))

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${record.dateStr}  •  $startTimeFormatted - $endTimeFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmployeeDetailPage(
    employeeId: Int,
    viewModel: BreakTrackerViewModel,
    onBack: () -> Unit,
    onDelete: (Employee) -> Unit
) {
    val employees by viewModel.employees.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val allHistoryRecords by viewModel.allBreakRecords.collectAsStateWithLifecycle()

    val employee = employees.find { it.id == employeeId }
    val employeeRecords = allHistoryRecords.filter { it.employeeId == employeeId }

    if (employee == null) {
        onBack()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Detail Navigation Top bar
        TopAppBarHeader(
            titleText = employee.name,
            onBack = onBack,
            onDeleteAction = { onDelete(employee) }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Part 1: Status & Info Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmployeeAvatar(name = employee.name, avatarIndex = employee.avatarIndex, modifier = Modifier.size(80.dp))

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = employee.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (employee.role.isNotEmpty()) {
                            Text(
                                text = employee.role,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Running timer/status panel
                        if (employee.isOnBreak && employee.breakStartTime != null) {
                            val durationMs = currentTime - employee.breakStartTime
                            val durationSecs = durationMs / 1000
                            val mins = durationSecs / 60
                            val secs = durationSecs % 60
                            val timerText = String.format("%02d:%02d", mins, secs)

                            val textColor = if (mins >= 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

                            Text(
                                text = "CURRENT BREAK DURATION",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = timerText,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Black,
                                color = textColor
                            )
                        } else {
                            Text(
                                text = "Working Productively",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }

            // Part 2: ONLY ONE primary Action Button (Start Break / Stop Break)
            item {
                if (employee.isOnBreak) {
                    Button(
                        onClick = { viewModel.stopBreak(employee) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("action_break_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Stop Break",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.startBreak(employee) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("action_break_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Start Break",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Part 3: Personal History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Individual Break Logs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${employeeRecords.size} entries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }

            if (employeeRecords.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No recorded breaks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            } else {
                items(employeeRecords, key = { it.id }) { record ->
                    HistoryItemCompact(record = record, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCompact(record: BreakRecord, viewModel: BreakTrackerViewModel) {
    val durationSecs = record.duration / 1000
    val mins = durationSecs / 60
    val secs = durationSecs % 60
    val durationFormatted = String.format("%02dm %02ds", mins, secs)

    val tzId by viewModel.selectedTimeZoneId.collectAsStateWithLifecycle()
    val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(tzId)
    }.format(Date(record.startTime))
    val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone(tzId)
    }.format(Date(record.endTime))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = record.dateStr,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$startTimeFormatted - $endTimeFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = durationFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarHeader(
    titleText: String,
    onBack: () -> Unit,
    onDeleteAction: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onDeleteAction, modifier = Modifier.testTag("delete_employee_detail")) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Employee",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun AddEmployeeDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var selectedAvatarIndex by remember { mutableStateOf(0) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Employee", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Alice Smith") },
                    singleLine = true,
                    isError = showError && name.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_employee_name"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (showError && name.isBlank()) {
                    Text(
                        text = "Name is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role / Department") },
                    placeholder = { Text("e.g. Design, Frontend") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_employee_role"),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Select Profile Avatar:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0..5).forEach { index ->
                        val isSelected = selectedAvatarIndex == index
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .padding(2.dp)
                                .clickable { selectedAvatarIndex = index }
                        ) {
                            EmployeeAvatar(
                                name = if (name.isNotBlank()) name else "A",
                                avatarIndex = index,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showError = true
                    } else {
                        onAdd(name.trim(), role.trim(), selectedAvatarIndex)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("dialog_confirm_add")
            ) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManagerProfileDialog(
    viewModel: BreakTrackerViewModel,
    onDismiss: () -> Unit
) {
    val managerName by viewModel.managerName.collectAsStateWithLifecycle()
    val managerRole by viewModel.managerRole.collectAsStateWithLifecycle()
    val managerEmail by viewModel.managerEmail.collectAsStateWithLifecycle()
    val managerAvatar by viewModel.managerAvatar.collectAsStateWithLifecycle()

    var isEditMode by remember { mutableStateOf(false) }
    var editName by remember(managerName) { mutableStateOf(managerName) }
    var editRole by remember(managerRole) { mutableStateOf(managerRole) }
    var editEmail by remember(managerEmail) { mutableStateOf(managerEmail) }
    var editAvatar by remember(managerAvatar) { mutableStateOf(managerAvatar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) "Edit Manager Profile" else "Manager Profile",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile image display / avatar selector
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (editAvatar == "img_profile_avatar") {
                        Image(
                            painter = painterResource(id = R.drawable.img_profile_avatar),
                            contentDescription = "Manager Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Numeric fallback or graphical preset index
                        val idx = editAvatar.toIntOrNull() ?: 0
                        val icon = when (idx) {
                            1 -> Icons.Default.Face
                            2 -> Icons.Default.Build
                            3 -> Icons.Default.AccountCircle
                            4 -> Icons.Default.Star
                            5 -> Icons.Default.Notifications
                            else -> Icons.Default.Person
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                if (isEditMode) {
                    Text(
                        text = "Choose Avatar:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Preset 1: AI Generated
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (editAvatar == "img_profile_avatar") 2.dp else 1.dp,
                                    color = if (editAvatar == "img_profile_avatar") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { editAvatar = "img_profile_avatar" }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_profile_avatar),
                                contentDescription = "AI Portrait",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Presets 1-5 (Vector)
                        (1..5).forEach { index ->
                            val isSelected = editAvatar == index.toString()
                            val icon = when (index) {
                                1 -> Icons.Default.Face
                                2 -> Icons.Default.Build
                                3 -> Icons.Default.AccountCircle
                                4 -> Icons.Default.Star
                                5 -> Icons.Default.Notifications
                                else -> Icons.Default.Person
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .clickable { editAvatar = index.toString() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editRole,
                        onValueChange = { editRole = it },
                        label = { Text("Title / Role") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Text(
                        text = managerName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = managerRole,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = managerEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Brief stats summary
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Authorized Administrator Mode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Access level: Full Admin & Employee Break Compliance Manager.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditMode) {
                Button(
                    onClick = {
                        viewModel.updateManagerProfile(
                            name = editName.trim(),
                            role = editRole.trim(),
                            email = editEmail.trim(),
                            avatar = editAvatar
                        )
                        isEditMode = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Changes")
                }
            } else {
                Button(
                    onClick = { isEditMode = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Edit Profile")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isEditMode) {
                        isEditMode = false
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (isEditMode) "Cancel" else "Close")
            }
        }
    )
}

@Composable
fun SettingsTabContent(
    viewModel: BreakTrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val selectedTimeZoneId by viewModel.selectedTimeZoneId.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTimeMillis.collectAsStateWithLifecycle()
    val appVersion by viewModel.appVersion.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var showTimeZoneDialog by remember { mutableStateOf(false) }

    val formattedTime = remember(currentTime, selectedTimeZoneId) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(selectedTimeZoneId)
        }
        sdf.format(Date(currentTime))
    }

    val offsetFormatted = remember(selectedTimeZoneId) {
        formatTimeZoneOffset(selectedTimeZoneId)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timezone Preference Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timezone_setting_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Timezone Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Set your local timezone to align employee break records, history exports, and tracking alerts to your exact region.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        onClick = { showTimeZoneDialog = true },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_timezone_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Timezone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = selectedTimeZoneId,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = offsetFormatted,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Timezone",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Theme and Visuals Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Visual preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dark_mode_setting_row"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Dark Theme",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Switch between light and dark backgrounds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("settings_dark_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "App Color Selector",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Customize the primary accent color using sliders or select a preset below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()

                    // Parse current selectedTheme into Color and then HSV
                    val parsedColor = remember(selectedTheme) {
                        try {
                            if (selectedTheme.startsWith("#")) {
                                val cleanHex = selectedTheme.removePrefix("#")
                                if (cleanHex.length == 6) {
                                    Color(android.graphics.Color.parseColor("#$cleanHex"))
                                } else if (cleanHex.length == 8) {
                                    Color(android.graphics.Color.parseColor("#$cleanHex"))
                                } else {
                                    Color(0xFF6750A4)
                                }
                            } else {
                                when (selectedTheme.uppercase()) {
                                    "PURPLE" -> Color(0xFF6750A4)
                                    "GREEN" -> Color(0xFF386A20)
                                    "BLUE" -> Color(0xFF0061A4)
                                    "RED" -> Color(0xFFBA1A1A)
                                    "SLATE" -> Color(0xFF435B95)
                                    else -> Color(0xFF6750A4)
                                }
                            }
                        } catch (e: Exception) {
                            Color(0xFF6750A4)
                        }
                    }

                    val initialHsv = remember(selectedTheme) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(
                            android.graphics.Color.argb(
                                255,
                                (parsedColor.red * 255).toInt(),
                                (parsedColor.green * 255).toInt(),
                                (parsedColor.blue * 255).toInt()
                            ), hsv
                        )
                        hsv
                    }

                    var hue by remember(selectedTheme) { mutableStateOf(initialHsv[0]) }
                    var saturation by remember(selectedTheme) { mutableStateOf(initialHsv[1]) }
                    var brightness by remember(selectedTheme) { mutableStateOf(initialHsv[2]) }

                    // Function to convert HSV back to Hex and update ViewModel
                    val updateSelectedColor = { h: Float, s: Float, v: Float ->
                        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
                        val hex = String.format("#%02X%02X%02X", 
                            android.graphics.Color.red(colorInt),
                            android.graphics.Color.green(colorInt),
                            android.graphics.Color.blue(colorInt)
                        )
                        viewModel.setSelectedTheme(hex)
                    }

                    // Large dynamic preview card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = parsedColor.copy(alpha = 0.12f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            parsedColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(parsedColor, shape = RoundedCornerShape(8.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Active Accent Color",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("#%02X%02X%02X", 
                                        (parsedColor.red * 255).toInt(),
                                        (parsedColor.green * 255).toInt(),
                                        (parsedColor.blue * 255).toInt()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // 1. Hue Spectrum Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hue (Color Family)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${hue.toInt()}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Rainbow track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFFF0000), // Red
                                                Color(0xFFFFFF00), // Yellow
                                                Color(0xFF00FF00), // Green
                                                Color(0xFF00FFFF), // Cyan
                                                Color(0xFF0000FF), // Blue
                                                Color(0xFFFF00FF), // Pink
                                                Color(0xFFFF0000)  // Red
                                            )
                                        ),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                            Slider(
                                value = hue,
                                onValueChange = {
                                    hue = it
                                    updateSelectedColor(hue, saturation, brightness)
                                },
                                valueRange = 0f..360f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Saturation Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saturation (Vibrancy)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(saturation * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Saturation gradient from gray/white to fully saturated color at current hue
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFFE0E0E0),
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                                            )
                                        ),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                            Slider(
                                value = saturation,
                                onValueChange = {
                                    saturation = it
                                    updateSelectedColor(hue, saturation, brightness)
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Brightness Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Brightness",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(brightness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Brightness gradient from black to fully bright color at current hue/saturation
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black,
                                                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1f)))
                                            )
                                        ),
                                        shape = RoundedCornerShape(5.dp)
                                    )
                            )
                            Slider(
                                value = brightness,
                                onValueChange = {
                                    brightness = it
                                    updateSelectedColor(hue, saturation, brightness)
                                },
                                valueRange = 0.2f..1f, // constrain min brightness so text and elements are legible
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Transparent,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset Color Row
                    Text(
                        text = "Or select a preset color:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(
                            Pair("Purple", "#6750A4"),
                            Pair("Blue", "#0061A4"),
                            Pair("Teal", "#008080"),
                            Pair("Green", "#386A20"),
                            Pair("Yellow", "#FBC02D"),
                            Pair("Orange", "#F57C00"),
                            Pair("Red", "#BA1A1A"),
                            Pair("Pink", "#D81B60")
                        )

                        presets.forEach { (name, hex) ->
                            val presetColor = Color(android.graphics.Color.parseColor(hex))
                            val isPresetSelected = try {
                                val cleanHex = hex.removePrefix("#")
                                val cleanSelected = selectedTheme.removePrefix("#")
                                // Compare RGB colors roughly
                                val selectedColorInt = android.graphics.Color.parseColor("#$cleanSelected")
                                val presetColorInt = android.graphics.Color.parseColor("#$cleanHex")
                                selectedColorInt == presetColorInt
                            } catch (e: Exception) {
                                selectedTheme == hex
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(presetColor, shape = CircleShape)
                                    .border(
                                        width = if (isPresetSelected) 3.dp else 1.dp,
                                        color = if (isPresetSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        viewModel.setSelectedTheme(hex)
                                    }
                                    .testTag("theme_preset_$name"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPresetSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diagnostic / Time Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "System Clock Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Local Calendar Time:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Device System Timezone:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = TimeZone.getDefault().id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // App Updates Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_updates_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "App Updates & Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Current Application Version: v$appVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = updateState) {
                        is com.example.viewmodel.UpdateState.Idle -> {
                            Text(
                                text = "Keep your app updated with the latest performance diagnostics, UI customization palettes, and reliability enhancements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("check_updates_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Check for Updates")
                                }
                            }
                        }
                        is com.example.viewmodel.UpdateState.Checking -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Checking server for available updates...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Validating version manifest & package hashes...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        is com.example.viewmodel.UpdateState.UpToDate -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "You're all set!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "BreakTracker is fully updated to the latest build (v$appVersion).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("check_again_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Check Again")
                                }
                            }
                        }
                        is com.example.viewmodel.UpdateState.UpdateAvailable -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Update Info",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "New Update Available!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Version v${state.version} • Size: ${state.size}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "What's New in this Version:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.changelog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.downloadAndInstallUpdate() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("download_update_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download & Install Update")
                                    }
                                }
                            }
                        }
                        is com.example.viewmodel.UpdateState.Downloading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Downloading App Update...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Transferring system APK assets safely. Please remain in the application...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is com.example.viewmodel.UpdateState.Installing -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Applying & Installing Update...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Rebuilding application resource mappings and indexes...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        is com.example.viewmodel.UpdateState.InstallReady -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Successfully Updated!",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "BreakTracker is now running v$appVersion.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.resetUpdateState() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("complete_update_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Acknowledge & Finish")
                                }
                            }
                        }
                        is com.example.viewmodel.UpdateState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "An error occurred: ${state.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.resetUpdateState() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Share App Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("share_app_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Share BreakTracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Love using BreakTracker? Share it with colleagues, managers, or other teams to help them streamline and optimize employee break tracking, timezone adjustments, and live interval notifications!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val shareIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Download BreakTracker App")
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "I am using BreakTracker to manage employee breaks, visualize timing intervals, and monitor overtime alerts seamlessly! Try it out here: https://ai.studio/build"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share BreakTracker via"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("share_app_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share App")
                        }
                    }
                }
            }
        }
    }

    if (showTimeZoneDialog) {
        TimeZonePickerDialog(
            currentSelection = selectedTimeZoneId,
            onDismiss = { showTimeZoneDialog = false },
            onSelect = { tzId ->
                viewModel.setTimeZoneId(tzId)
                showTimeZoneDialog = false
                Toast.makeText(context, "Timezone updated to $tzId", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZonePickerDialog(
    currentSelection: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val allTimeZones = remember {
        TimeZone.getAvailableIDs().sorted().map { id ->
            id to formatTimeZoneOffset(id)
        }
    }
    
    val filteredTimeZones = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allTimeZones
        } else {
            allTimeZones.filter { (id, offset) ->
                id.contains(searchQuery, ignoreCase = true) || offset.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Select Local Timezone",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Search or select your region's timezone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by city or GMT...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timezone_search_input")
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (filteredTimeZones.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching timezones found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // "System Default" option as first item
                            item {
                                val defaultTzId = TimeZone.getDefault().id
                                val defaultTzOffset = formatTimeZoneOffset(defaultTzId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(defaultTzId) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("timezone_item_default"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SettingsSuggest,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "System Default",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "$defaultTzId ($defaultTzOffset)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant))
                            }

                            items(filteredTimeZones) { (id, offset) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("timezone_item_$id"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = id.replace('_', ' '),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (id == currentSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (id == currentSelection) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = if (id == currentSelection) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = offset,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (id == currentSelection) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTimeZoneOffset(timeZoneId: String): String {
    val tz = TimeZone.getTimeZone(timeZoneId)
    val offsetMs = tz.getOffset(System.currentTimeMillis())
    val offsetHrs = offsetMs / 3600000
    val offsetMins = (offsetMs % 3600000).absoluteValue / 60000
    val sign = if (offsetMs >= 0) "+" else "-"
    val absHrs = offsetHrs.absoluteValue
    return "GMT$sign${String.format("%02d:%02d", absHrs, offsetMins)}"
}
