package com.fastnet.ebonefieldmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        val usernameField =
            findViewById<EditText>(
                R.id.usernameField
            )

        val passwordField =
            findViewById<EditText>(
                R.id.passwordField
            )

        val loginButton =
            findViewById<Button>(
                R.id.loginButton
            )

        loginButton.setOnClickListener {

            val username =
                usernameField.text.toString().trim()

            val password =
                passwordField.text.toString().trim()

            if (
                username.isNotEmpty() &&
                password.isNotEmpty()
            ) {

                EmployeeSession
                    .setEmployeeName(
                        username
                    )

                startActivity(
                    Intent(
                        this,
                        MainActivity::class.java
                    )
                )

                finish()

            } else {

                Toast.makeText(
                    this,
                    "Enter Username & Password",
                    Toast.LENGTH_SHORT
                ).show()

            }

        }

    }

}