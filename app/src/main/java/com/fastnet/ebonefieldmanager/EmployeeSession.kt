package com.fastnet.ebonefieldmanager

object EmployeeSession {

    private var employeeName = ""

    fun setEmployeeName(
        name: String
    ) {
        employeeName = name
    }

    fun getEmployeeName(): String {

        if (
            employeeName.isEmpty()
        ) {
            return "Unknown"
        }

        return employeeName
    }
}