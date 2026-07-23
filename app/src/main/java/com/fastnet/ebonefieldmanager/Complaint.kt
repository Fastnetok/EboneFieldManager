package com.fastnet.ebonefieldmanager

data class Complaint(

    var complaintId: String = "",

    var userId: String = "",

    var address: String = "",

    var phoneNumber: String = "",

    var details: String = "",

    var status: String = "Pending",

    var assignedTo: String = "",

    var assignedTime: Long = 0,

    var createdTime: Long = 0,

    var resolvedBy: String = "",

    var resolvedTime: Long = 0,

    var displayOrder: Long = 0,

    var seenByEmployee: Boolean = false,

    var seenTime: Long = 0


)