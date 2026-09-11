package com.fastnet.ebonefieldmanager

data class NewConnection(
    var id: String = "",
    var customerName: String = "",
    var address: String = "",
    var phoneNumber: String = "",
    var comments: String = "",
    var status: String = "Pending", // Pending, Progress, Completed, Cancelled
    var assignedTo: String = "",
    var assignedTime: Long = 0,
    var createdTime: Long = 0,
    var seenByEmployee: Boolean = false,
    var seenTime: Long = 0,
    var completionTime: Long = 0,
    var cancellationReason: String = "",
    var displayOrder: Long = 0
)
