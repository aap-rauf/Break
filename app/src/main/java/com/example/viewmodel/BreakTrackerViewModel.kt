package com.example.viewmodel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BreakRecord
import com.example.data.Employee
import com.example.data.EmployeeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val version: String, val changelog: String, val size: String) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Installing : UpdateState()
    object InstallReady : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class BreakTrackerViewModel(
    private val repository: EmployeeRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedTheme = MutableStateFlow(sharedPrefs.getString("color_theme", "#6750A4") ?: "#6750A4")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    private val _selectedTimeZoneId = MutableStateFlow(sharedPrefs.getString("timezone_id", TimeZone.getDefault().id) ?: TimeZone.getDefault().id)
    val selectedTimeZoneId: StateFlow<String> = _selectedTimeZoneId.asStateFlow()

    private val _appVersion = MutableStateFlow(sharedPrefs.getString("app_version", "1.2.1") ?: "1.2.1")
    val appVersion: StateFlow<String> = _appVersion.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _managerName = MutableStateFlow(sharedPrefs.getString("manager_name", "Rauf Hossain") ?: "Rauf Hossain")
    val managerName: StateFlow<String> = _managerName.asStateFlow()

    private val _managerRole = MutableStateFlow(sharedPrefs.getString("manager_role", "Workplace Administrator") ?: "Workplace Administrator")
    val managerRole: StateFlow<String> = _managerRole.asStateFlow()

    private val _managerAvatar = MutableStateFlow(sharedPrefs.getString("manager_avatar", "img_profile_avatar") ?: "img_profile_avatar")
    val managerAvatar: StateFlow<String> = _managerAvatar.asStateFlow()

    private val _managerEmail = MutableStateFlow(sharedPrefs.getString("manager_email", "raufhossain548@gmail.com") ?: "raufhossain548@gmail.com")
    val managerEmail: StateFlow<String> = _managerEmail.asStateFlow()

    fun updateManagerProfile(name: String, role: String, email: String, avatar: String) {
        _managerName.value = name
        _managerRole.value = role
        _managerEmail.value = email
        _managerAvatar.value = avatar
        sharedPrefs.edit()
            .putString("manager_name", name)
            .putString("manager_role", role)
            .putString("manager_email", email)
            .putString("manager_avatar", avatar)
            .apply()
    }

    // Employees list filtered by search query
    val employees: StateFlow<List<Employee>> = repository.allEmployees
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { it.name.contains(query, ignoreCase = true) || it.role.contains(query, ignoreCase = true) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allBreakRecords: StateFlow<List<BreakRecord>> = repository.allBreakRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // A clock Flow that ticks every second for real-time timer UI
    val currentTimeMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // Tracks notifications sent to avoid duplicates in the same break session:
    // Pair of (employeeId, milestoneIndex)
    private val sentNotifications = mutableSetOf<Pair<Int, Int>>()

    fun toggleDarkMode() {
        val newValue = !_isDarkMode.value
        _isDarkMode.value = newValue
        sharedPrefs.edit().putBoolean("dark_mode", newValue).apply()
    }

    fun setSelectedTheme(theme: String) {
        _selectedTheme.value = theme
        sharedPrefs.edit().putString("color_theme", theme).apply()
    }

    fun setTimeZoneId(tzId: String) {
        _selectedTimeZoneId.value = tzId
        sharedPrefs.edit().putString("timezone_id", tzId).apply()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addEmployee(name: String, role: String, avatarIndex: Int = 0) {
        viewModelScope.launch {
            repository.insertEmployee(Employee(name = name, role = role, avatarIndex = avatarIndex))
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
        }
    }

    fun deleteBreakRecord(record: BreakRecord) {
        viewModelScope.launch {
            repository.deleteBreakRecord(record)
        }
    }

    fun startBreak(employee: Employee) {
        viewModelScope.launch {
            val updated = employee.copy(
                isOnBreak = true,
                breakStartTime = System.currentTimeMillis()
            )
            repository.updateEmployee(updated)
        }
    }

    fun stopBreak(employee: Employee) {
        val startTime = employee.breakStartTime ?: return
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime

        viewModelScope.launch {
            // Update employee state
            val updated = employee.copy(
                isOnBreak = false,
                breakStartTime = null
            )
            repository.updateEmployee(updated)

            // Save break history
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone(_selectedTimeZoneId.value)
            }
            val dateStr = dateFormat.format(Date(startTime))

            val record = BreakRecord(
                employeeId = employee.id,
                employeeName = employee.name,
                dateStr = dateStr,
                startTime = startTime,
                endTime = endTime,
                duration = duration
            )
            repository.insertBreakRecord(record)

            // Clear active notification states for this employee
            val toRemove = sentNotifications.filter { it.first == employee.id }.toSet()
            sentNotifications.removeAll(toRemove)
        }
    }

    // Ticking notification checks
    fun startNotificationService(context: Context) {
        viewModelScope.launch {
            while (isActive) {
                checkActiveBreaksForNotifications(context)
                delay(10000) // Check every 10 seconds
            }
        }
    }

    private fun checkActiveBreaksForNotifications(context: Context) {
        val now = System.currentTimeMillis()
        employees.value.forEach { employee ->
            if (employee.isOnBreak && employee.breakStartTime != null) {
                val elapsedMs = now - employee.breakStartTime
                val elapsedMinutes = (elapsedMs / 60000).toInt()

                // Rule: Notifications after 30 mins, and every 10 mins after
                // Milestone 0: 30 minutes
                // Milestone 1: 40 minutes
                // Milestone 2: 50 minutes
                // and so on...
                val milestoneIndex = if (elapsedMinutes >= 30) {
                    ((elapsedMinutes - 30) / 10)
                } else {
                    -1
                }

                if (milestoneIndex >= 0) {
                    val key = Pair(employee.id, milestoneIndex)
                    if (!sentNotifications.contains(key)) {
                        sentNotifications.add(key)
                        val totalMinutesOfBreak = 30 + (milestoneIndex * 10)
                        sendSystemNotification(context, employee.id, employee.name, totalMinutesOfBreak)
                    }
                }
            }
        }
    }

    private fun sendSystemNotification(context: Context, employeeId: Int, name: String, minutes: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "break_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Break Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when employees are on break too long"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            employeeId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Break Limit Warning")
            .setContentText("$name has been on break for $minutes minutes!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(employeeId + minutes, builder.build())
    }

    fun exportHistoryToCsv(context: Context) {
        val records = allBreakRecords.value
        if (records.isEmpty()) return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(_selectedTimeZoneId.value)
        }
        val csvHeader = "Record ID,Employee ID,Employee Name,Date,Start Time,End Time,Duration (seconds),Duration (formatted)\n"
        val csvBody = records.joinToString("\n") { record ->
            val startFormatted = dateFormat.format(Date(record.startTime))
            val endFormatted = dateFormat.format(Date(record.endTime))
            val durationSecs = record.duration / 1000
            val mins = durationSecs / 60
            val secs = durationSecs % 60
            val durationFormatted = String.format("%02d:%02d", mins, secs)

            "${record.id},${record.employeeId},\"${record.employeeName.replace("\"", "\"\"")}\",${record.dateStr},$startFormatted,$endFormatted,$durationSecs,$durationFormatted"
        }

        val csvContent = csvHeader + csvBody

        try {
            // Write to a temporary file
            val file = File(context.cacheDir, "break_tracker_history.csv")
            file.writeText(csvContent)

            // Get URI using FileProvider
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Break Tracker History Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Export CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading || _updateState.value is UpdateState.Installing) return

        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            delay(1500) // Simulate network request delay

            val currentVer = _appVersion.value
            if (currentVer == "1.2.1") {
                _updateState.value = UpdateState.UpdateAvailable(
                    version = "1.3.0",
                    changelog = "• Multi-Theme Visual Color Customization\n• System Clock Diagnostics & Timezone Selector\n• Live Dynamic Performance & Memory Enhancements\n• Accessibility Touch Target Standard Upgrades (48dp)",
                    size = "2.4 MB"
                )
            } else {
                _updateState.value = UpdateState.UpToDate
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val currentState = _updateState.value
        if (currentState !is UpdateState.UpdateAvailable) return

        viewModelScope.launch {
            _updateState.value = UpdateState.Downloading(0.0f)
            
            val steps = 20
            for (i in 1..steps) {
                delay(150) // Simulate progressive download
                val progress = i.toFloat() / steps
                _updateState.value = UpdateState.Downloading(progress)
            }

            _updateState.value = UpdateState.Installing
            delay(1500) // Simulate background installer unpacking/applying assets

            val newVersion = currentState.version
            _appVersion.value = newVersion
            sharedPrefs.edit().putString("app_version", newVersion).apply()

            _updateState.value = UpdateState.InstallReady
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }
}

class BreakTrackerViewModelFactory(
    private val repository: EmployeeRepository,
    private val sharedPrefs: android.content.SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BreakTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BreakTrackerViewModel(repository, sharedPrefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
