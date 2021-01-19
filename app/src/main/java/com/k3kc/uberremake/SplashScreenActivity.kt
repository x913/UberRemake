package com.k3kc.uberremake

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.renderscript.Sampler
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.k3kc.uberremake.model.DriverInfoModel
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splash_screen.*
import kotlinx.android.synthetic.main.layout_register.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    companion object {
        private val LOGIN_REQUEST_CODE = 7171
    }

    private lateinit var providers: List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var listener:FirebaseAuth.AuthStateListener

    lateinit var database:FirebaseDatabase
    lateinit var driverInfoRef: DatabaseReference

    override fun onStart() {
        super.onStart()
        delaySplashScreen()
    }

    override fun onStop() {
        if(firebaseAuth != null && listener != null)
            firebaseAuth.removeAuthStateListener(listener)

        super.onStop()
    }

    private fun delaySplashScreen() {
        Completable.timer(3, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe({
                firebaseAuth.addAuthStateListener(listener)
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        init()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == LOGIN_REQUEST_CODE) {
            val response = IdpResponse.fromResultIntent(data)
            if(resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser
            } else {
                Toast.makeText(this@SplashScreenActivity, "error " + response!!.error!!.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun init() {

        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener {
            val user = it.currentUser
            if(user != null)
                checkUserFromFirebase()
            else {
                showLoginLayout()
            }
        }

    }

    private fun checkUserFromFirebase() {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)    // get the child element we want
            .addListenerForSingleValueEvent(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()) {
                        Toast
                            .makeText(this@SplashScreenActivity, "User already registered!", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        showRegisterLayout()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast
                        .makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

            })
    }

    private fun showRegisterLayout() {
        val builder = AlertDialog.Builder(this, R.style.DialogTheme)
        val itemView = LayoutInflater.from(this).inflate(R.layout.layout_register, null)

        val edtFirstName = itemView.findViewById<View>(R.id.edt_first_name) as TextInputEditText
        val edtLastName = itemView.findViewById<View>(R.id.edt_last_name) as TextInputEditText
        val edtPhoneNumber = itemView.findViewById<View>(R.id.edt_phone_number) as TextInputEditText

        val btnContinue = itemView.findViewById<View>(R.id.btn_register) as Button

        // set the data
        if(FirebaseAuth.getInstance().currentUser!!.phoneNumber != null && !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber))
                edtPhoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber)

        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        btnContinue.setOnClickListener {
            if(TextUtils.isDigitsOnly(edtFirstName.text.toString())) {
                Toast.makeText(this@SplashScreenActivity, "Please enter first name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if(TextUtils.isDigitsOnly(edtLastName.text.toString())) {
                Toast.makeText(this@SplashScreenActivity, "Please enter last name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else if(TextUtils.isDigitsOnly(edtPhoneNumber.text.toString())){
                Toast.makeText(this@SplashScreenActivity, "Please enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            } else {
                val model = DriverInfoModel()
                model.firstName = edtFirstName.text.toString()
                model.lastName = edtLastName.text.toString()
                model.phoneNumber = edtPhoneNumber.text.toString()
                model.rating = 0.0

                driverInfoRef.child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener { e ->
                        Toast.makeText(this@SplashScreenActivity, e.message, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        progress_bar.visibility = View.GONE
                    }
                    .addOnSuccessListener {
                        Toast.makeText(this@SplashScreenActivity, "Registered successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        progress_bar.visibility = View.GONE
                    }

            }
        }
    }

    private fun showLoginLayout() {
        val authMethodPickerLayout = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build(),
            LOGIN_REQUEST_CODE,
        )
    }
}