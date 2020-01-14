package com.padana.ftpsync.activities.ftp_connections

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.padana.ftpsync.R
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import kotlinx.android.synthetic.main.activity_add_ftp_conenctino.*
import kotlinx.android.synthetic.main.content_add_ftp_conenctino.*
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply


class AddFtpConnectionActivity : AppCompatActivity() {
    var fptClientId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ftp_conenctino)
        setSupportActionBar(toolbar)

        var ftpClient: FtpClient? = intent.getSerializableExtra("ftpClient") as FtpClient?

        if (ftpClient != null) {
            fptClientId = ftpClient.id
            txtFtpHost.setText(ftpClient.server)
            txtFtpUser.setText(ftpClient.user)
            txtFtpPassword.setText(ftpClient.password)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btnSaveFtp.setOnClickListener {
            var ftpClient = FtpClient(fptClientId, txtFtpHost.text.toString().trim(), txtFtpUser.text.toString().trim(), txtFtpPassword.text.toString().trim())
            saveFtpClient(ftpClient)
        }

        btnTestFtp.setOnClickListener { v ->
            testFtpClient(txtFtpHost.text.toString(), txtFtpUser.text.toString(), txtFtpPassword.text.toString(), v)
        }
    }

    private fun saveFtpClient(ftpClient: FtpClient) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                DatabaseClient(applicationContext).getAppDatabase().genericDAO.insertFtpClient(ftpClient)
                goToFtpConnectionList()
                return null
            }
        }.execute()
    }

    private fun testFtpClient(host: String, user: String, password: String, v: View) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                var ftp = FTPClient()
                var config = FTPClientConfig()
                ftp.configure(config)
                ftp.connect(host)

                var reply = ftp.replyCode

                if (!FTPReply.isPositiveCompletion(reply)) {
                    ftp.disconnect()
                    Snackbar.make(v, getString(R.string.connError), Snackbar.LENGTH_LONG)
                            .setAction(getString(R.string.error), null).show()
                    return null
                }
                ftp.login(user, password)
                Snackbar.make(v, getString(R.string.connSuccess), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.info), null).show()
                ftp.disconnect()
                return null
            }
        }.execute()
    }

    override fun onBackPressed() {
        goToFtpConnectionList()
    }

    private fun goToFtpConnectionList() {
        val intent = Intent(this, FtpConnectionsActivity::class.java)
        startActivity(intent)
    }


}
