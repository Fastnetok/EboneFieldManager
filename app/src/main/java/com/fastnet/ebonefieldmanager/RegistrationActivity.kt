package com.fastnet.ebonefieldmanager

import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var mobileInput: EditText
    private lateinit var submitButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_registration
        )

        nameInput =
            findViewById(
                R.id.nameInput
            )

        mobileInput =
            findViewById(
                R.id.mobileInput
            )

        submitButton =
            findViewById(
                R.id.submitButton
            )

        statusText =
            findViewById(
                R.id.statusText
            )

        submitButton.setOnClickListener {

            val employeeName =
                nameInput.text
                    .toString()
                    .trim()

            val mobileNumber =
                mobileInput.text
                    .toString()
                    .trim()

            if (employeeName.isEmpty()) {

                statusText.text =
                    "Enter Employee Name"

                return@setOnClickListener
            }

            if (mobileNumber.isEmpty()) {

                statusText.text =
                    "Enter Mobile Number"

                return@setOnClickListener
            }

            val androidId =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )

            FirebaseManager.registerEmployee(
                androidId,
                employeeName,
                mobileNumber
            )

            EmployeeSession.setEmployeeName(
                employeeName
            )

            RegistrationManager.saveRegistration(
                this,
                employeeName,
                mobileNumber
            )

            statusText.text =
                "Request Sent To Admin"

            submitButton.isEnabled =
                false
        }
    }
}