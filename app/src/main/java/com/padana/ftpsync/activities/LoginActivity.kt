package com.padana.ftpsync.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.facebook.*
import com.facebook.login.LoginResult
import com.padana.ftpsync.MyApp
import com.padana.ftpsync.R
import com.padana.ftpsync.activities.ftp_connections.FtpConnectionsActivity
import com.padana.ftpsync.services.utils.LogerFileUtils
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.content_login.*
import org.json.JSONException


class LoginActivity : AppCompatActivity() {
    val callbackManager = CallbackManager.Factory.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        setSupportActionBar(toolbar)

        /*  val info = packageManager.getPackageInfo(
                  "com.padana.ftpsync",
                  PackageManager.GET_SIGNATURES)
          for (signature in info.signatures) {
              val md: MessageDigest = MessageDigest.getInstance("SHA")
              md.update(signature.toByteArray())
              System.err.println("KeyHash:   " + Base64.encodeToString(md.digest(), Base64.DEFAULT))
          }*/

        val loggedOut = AccessToken.getCurrentAccessToken() == null
        if (!loggedOut) {
            getUserProfile(AccessToken.getCurrentAccessToken())
            val intent = Intent(MyApp.getCtx(), FtpConnectionsActivity::class.java)
            startActivity(intent)
        }

        login_button.setPermissions(listOf("email", "public_profile"))
        login_button.registerCallback(callbackManager, object : FacebookCallback<LoginResult?> {
            override fun onSuccess(loginResult: LoginResult?) { // App code
                //loginResult.getAccessToken();
                //loginResult.getRecentlyDeniedPermissions()
                //loginResult.getRecentlyGrantedPermissions()
                val loggedIn = AccessToken.getCurrentAccessToken() == null
                Log.d("API123", "$loggedIn ??")
                val intent = Intent(MyApp.getCtx(), FtpConnectionsActivity::class.java)
                startActivity(intent)
            }

            override fun onCancel() { // App code
            }

            override fun onError(exception: FacebookException) {
                LogerFileUtils.error(exception.message!!)
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getUserProfile(currentAccessToken: AccessToken) {
        val request = GraphRequest.newMeRequest(
                currentAccessToken) { `object`, response ->
            Log.d("TAG", `object`.toString())
            try {
                val first_name = `object`.getString("first_name")
                val last_name = `object`.getString("last_name")
                val email = `object`.getString("email")
                val id = `object`.getString("id")
                val image_url = "https://graph.facebook.com/$id/picture?type=normal"
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        val parameters = Bundle()
        parameters.putString("fields", "first_name,last_name,email,id")
        request.parameters = parameters
        request.executeAsync()
    }

}
