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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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

        setContent {
            val viewModel: BreakTrackerViewModel = viewModel(
                factory = BreakTrackerViewModelFactory(repository)
            )

            // Start background monitoring for overtime alerts
            LaunchedEffect(Unit) {
                viewModel.startNotificationService(applicationContext)
            }

            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkMode, dynamicColor = false) {
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
    var currentTab by remember { mutableStateOf(0) } // 0 = Employees, 1 = History

    // Dialog flags
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
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
                HomeHeader(viewModel = viewModel)

                // Tab contents
                Box(modifier = Modifier.weight(1f)) {
                    if (currentTab == 0) {
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
                    } else {
                        HistoryTabContent(
                            viewModel = viewModel,
                            onRecordLongClick = { record ->
                                recordToDelete = record
                            }
                        )
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
                }
            }
        }

        // Dialog: Add Employee
        if (showAddEmployeeDialog) {
            AddEmployeeDialog(
                onDismiss = { showAddEmployeeDialog = false },
                onAdd = { name, role ->
                    viewModel.addEmployee(name, role)
                    showAddEmployeeDialog = false
                    Toast.makeText(context, "Added $name", Toast.LENGTH_SHORT).show()
                }
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
fun HomeHeader(viewModel: BreakTrackerViewModel) {
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
        }

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
            EmployeeAvatar(name = employee.name, modifier = Modifier.size(44.dp))

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
fun EmployeeAvatar(name: String, modifier: Modifier = Modifier) {
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
        Text(
            text = firstChar,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
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
                    HistoryItemCard(record = record, onLongClick = { onRecordLongClick(record) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    record: BreakRecord,
    onLongClick: () -> Unit
) {
    val durationSecs = record.duration / 1000
    val mins = durationSecs / 60
    val secs = durationSecs % 60
    val durationFormatted = String.format("%02dm %02ds", mins, secs)

    val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.startTime))
    val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.endTime))

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmployeeAvatar(name = record.employeeName, modifier = Modifier.size(36.dp))

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
                        EmployeeAvatar(name = employee.name, modifier = Modifier.size(80.dp))

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
                    HistoryItemCompact(record = record)
                }
            }
        }
    }
}

@Composable
fun HistoryItemCompact(record: BreakRecord) {
    val durationSecs = record.duration / 1000
    val mins = durationSecs / 60
    val secs = durationSecs % 60
    val durationFormatted = String.format("%02dm %02ds", mins, secs)

    val startTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.startTime))
    val endTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.endTime))

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
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showError = true
                    } else {
                        onAdd(name.trim(), role.trim())
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
