package com.example.data

import kotlinx.coroutines.flow.Flow

class EmployeeRepository(private val appDao: AppDao) {
    val allEmployees: Flow<List<Employee>> = appDao.getAllEmployees()
    val allBreakRecords: Flow<List<BreakRecord>> = appDao.getAllBreakRecords()

    suspend fun getEmployeeById(id: Int): Employee? = appDao.getEmployeeById(id)

    suspend fun insertEmployee(employee: Employee): Long = appDao.insertEmployee(employee)

    suspend fun updateEmployee(employee: Employee) = appDao.updateEmployee(employee)

    suspend fun deleteEmployee(employee: Employee) = appDao.deleteEmployee(employee)

    suspend fun deleteEmployeeById(id: Int) = appDao.deleteEmployeeById(id)

    fun getBreakRecordsForEmployee(employeeId: Int): Flow<List<BreakRecord>> = 
        appDao.getBreakRecordsForEmployee(employeeId)

    suspend fun insertBreakRecord(record: BreakRecord): Long = appDao.insertBreakRecord(record)

    suspend fun deleteBreakRecord(record: BreakRecord) = appDao.deleteBreakRecord(record)

    suspend fun deleteBreakRecordById(id: Int) = appDao.deleteBreakRecordById(id)
}
