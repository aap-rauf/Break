package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String = "",
    val isOnBreak: Boolean = false,
    val breakStartTime: Long? = null,
    val avatarIndex: Int = 0
)
