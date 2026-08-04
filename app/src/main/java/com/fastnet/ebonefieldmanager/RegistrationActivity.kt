package com.fastnet.ebonefieldmanager

import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * CHANGED: Employee now registers with Name + a one-time 6-digit PIN
 * (pre-created by Admin via AddEmployeeActivity in Ebone Admin Panel) —
 * same pattern as CustomerIDApp's Customer ID + PIN. This fixes the
 * duplicate-employee bug, because the PIN (not the Android ID) is now the
 * unique key, and it's explicitly device-locked once claimed — a second
 * device can never claim the same PIN again.
 */
class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameInput: EditText
    private lateinit var mobileInput: EditText
    private lateinit var pinInput: EditText
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

        pinInput =
            findViewById(
                R.id.pinInput
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

            val pin =
                pinInput.text
                    .toString()
                    .trim()

            if (employeeName.isEmpty()) {
                statusText.text = "Enter Employee Name"
                return@setOnClickListener
            }

            if (mobileNumber.isEmpty()) {
                statusText.text = "Enter Mobile Number"
                return@setOnClickListener
            }

            if (pin.length != 6) {
                statusText.text = "Enter the 6-digit PIN given by Admin"
                return@setOnClickListener
            }

            submitButton.isEnabled = false
            statusText.text = "Registering..."

            val androidId =
                Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ANDROID_ID
                )

            val auth = FirebaseAuth.getInstance()

            fun proceedWithUid(uid: String) {
                FirebaseManager.claimEmployeePin(
                    pin = pin,
                    enteredName = employeeName,
                    mobileNumber = mobileNumber,
                    androidId = androidId,
                    uid = uid
                ) { success, message ->
                    runOnUiThread {
                        if (success) {
                            EmployeeSession.setEmployeeName(employeeName)
                            RegistrationManager.saveRegistration(
                                this,
                                employeeName,
                                mobileNumber
                            )
                            statusText.text = "Request Sent To Admin"
                        } else {
                            statusText.text = message
                            submitButton.isEnabled = true
                        }
                    }
                }
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