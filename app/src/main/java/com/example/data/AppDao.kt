package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Employees
    @Query("SELECT * FROM employees ORDER BY name ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Int): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)

    @Query("DELETE FROM employees WHERE id = :id")
    suspend fun deleteEmployeeById(id: Int)

    // Break Records
    @Query("SELECT * FROM break_records ORDER BY startTime DESC")
    fun getAllBreakRecords(): Flow<List<BreakRecord>>

    @Query("SELECT * FROM break_records WHERE employeeId = :employeeId ORDER BY startTime DESC")
    fun getBreakRecordsForEmployee(employeeId: Int): Flow<List<BreakRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakRecord(record: BreakRecord): Long

    @Delete
    suspend fun deleteBreakRecord(record: BreakRecord)

    @Query("DELETE FROM break_records WHERE id = :id")
    suspend fun deleteBreakRecordById(id: Int)
}
