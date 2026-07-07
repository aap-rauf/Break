package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "break_records")
data class BreakRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val employeeName: String,
    val dateStr: String, // "yyyy-MM-dd"
    val startTime: Long, // epoch millis
    val endTime: Long, // epoch millis
    val duration: Long // duration in millis
)
