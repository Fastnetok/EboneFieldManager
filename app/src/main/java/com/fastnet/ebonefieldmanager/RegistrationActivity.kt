package com.fastnet.ebonefieldmanager

import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

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

            submitButton.isEnabled = false
            statusText.text = "Registering..."

            val androidId =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )

            // NEW: sign in anonymously first (or reuse existing session)
            // so the app has an auth.uid before writing to Firebase.
            // This uid is what the security rules use to verify the
            // device later, so it MUST be saved together with the
            // registration data.
            val auth = FirebaseAuth.getInstance()

            fun proceedWithUid(uid: String) {
                FirebaseManager.registerEmployee(
                    androidId,
                    employeeName,
                    mobileNumber,
                    uid
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
            }

            val existingUid = auth.currentUser?.uid
            if (existingUid != null) {
                proceedWithUid(existingUid)
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        val uid = result.user?.uid
                        if (uid != null) {
                            proceedWithUid(uid)
                        } else {
                            statusText.text = "Sign-in failed, try again"
                            submitButton.isEnabled = true
                        }
                    }
                    .addOnFailureListener {
                        statusText.text = "Sign-in failed, try again"
                        submitButton.isEnabled = true
                    }
            }
        }
    }
}