package com.fastnet.ebonefieldmanager

import android.content.Context

object RegistrationManager {

    private const val PREF_NAME =
        "employee_registration"

    private const val KEY_NAME =
        "employee_name"

    private const val KEY_MOBILE =
        "employee_mobile"

    fun saveRegistration(

        context: Context,

        employeeName: String,

        mobileNumber: String

    ) {

        val pref =

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        pref.edit()

            .putString(
                KEY_NAME,
                employeeName
            )

            .putString(
                KEY_MOBILE,
                mobileNumber
            )

            .apply()

    }

    fun isRegistered(
        context: Context
    ): Boolean {

        val pref =

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return pref.contains(
            KEY_MOBILE
        )

    }

    fun getEmployeeName(
        context: Context
    ): String {

        val pref =

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return pref.getString(
            KEY_NAME,
            ""
        ) ?: ""

    }

    fun getMobileNumber(
        context: Context
    ): String {

        val pref =

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        return pref.getString(
            KEY_MOBILE,
            ""
        ) ?: ""

    }

    fun clearRegistration(
        context: Context
    ) {

        val pref =

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        pref.edit().clear().apply()

    }
}