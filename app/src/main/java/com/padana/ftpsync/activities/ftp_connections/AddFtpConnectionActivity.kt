package com.padana.ftpsync.activities.ftp_connections

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.padana.ftpsync.R
import com.padana.ftpsync.database.DatabaseClient
import com.padana.ftpsync.entities.FtpClient
import com.padana.ftpsync.utils.ConnTypes
import kotlinx.android.synthetic.main.activity_add_ftp_conenctino.*
import kotlinx.android.synthetic.main.content_add_ftp_conenctino.*
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPReply
import java.util.*


class AddFtpConnectionActivity : AppCompatActivity() {
    var fptClientId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_ftp_conenctino)
        setSupportActionBar(toolbar)

        var ftpClient: FtpClient? = intent.getSerializableExtra("ftpClient") as FtpClient?
        val adapter = ArrayAdapter
                .createFromResource(this, R.array.connTypesArray,
                        android.R.layout.simple_spinner_item)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerConnectionType.adapter = adapter


        if (ftpClient != null) {
            fptClientId = ftpClient.id
            txtFtpHost.setText(ftpClient.server)
            txtFtpUser.setText(ftpClient.user)
            txtFtpPassword.setText(ftpClient.password)
            txtRootFileLocation.setText(ftpClient.rootLocation)
            txtRootFileLocation.isEnabled = false
            spinnerConnectionType.setSelection(adapter.getPosition(ftpClient.connectionType))
            txtHostName.setText(ftpClient.hostName)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btnSaveFtp.setOnClickListener {
            var ftpClient = FtpClient(fptClientId,
                    txtFtpHost.text.toString().trim(),
                    txtFtpUser.text.toString().trim(),
                    txtFtpPassword.text.toString().trim(),
                    txtRootFileLocation.text.toString().trim(),
                    spinnerConnectionType.selectedItem.toString().trim(),
                    txtHostName.text.toString().trim())
            saveFtpClient(ftpClient)
        }

        btnTestFtp.setOnClickListener { v ->
            testFtpClient(txtFtpHost.text.toString(), txtFtpUser.text.toString(), txtFtpPassword.text.toString(), v,
                    spinnerConnectionType.selectedItem.toString().trim())
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

    private fun testFtpClient(host: String, user: String, password: String, v: View, connType: String?) {
        object : AsyncTask<Void, Void, Void>() {
            override fun doInBackground(vararg voids: Void): Void? {
                if (connType!!.toLowerCase().equals(ConnTypes.FTP)) {
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
                } else if (connType!!.toLowerCase().equals(ConnTypes.SFTP)) {
                    try {
                        val jsch = JSch()

                        val config = Properties()
                        config["StrictHostKeyChecking"] = "no"

                        val session: Session = jsch.getSession(user, host)
                        session.setPassword(password)
                        session.setConfig(config)
                        session.connect()

                        val sftpChannel = session.openChannel("sftp") as ChannelSftp
                        sftpChannel.connect()
                        Snackbar.make(v, getString(R.string.connSuccess), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.info), null).show()
                        sftpChannel.disconnect()
                        session.disconnect()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Snackbar.make(v, getString(R.string.connError), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.error), null).show()
                    }
                }
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
