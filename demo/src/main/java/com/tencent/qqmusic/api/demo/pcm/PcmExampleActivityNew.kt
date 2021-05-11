package com.tencent.qqmusic.api.demo.pcm

import android.content.*
import android.media.AudioFormat
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.tencent.qqmusic.api.demo.*
import com.tencent.qqmusic.api.demo.R
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.api.demo.util.QPlayBindHelper
import com.tencent.qqmusic.third.api.contract.*
import com.tencent.qqmusic.third.api.contract.Events.API_EVENT_PLAY_SONG_CHANGED
import com.tencent.qqmusic.third.api.contract.Keys.API_PARAM_KEY_MEDIA_INFO
import com.tencent.qqmusic.third.api.contract.Keys.API_PARAM_KEY_PCM_FILE_DESCRIPTOR


class PcmExampleActivityNew : AppCompatActivity() {
    companion object {
        const val TAG = "PcmExampleActivity"
    }

    private lateinit var informationTextView: TextView
    private var playService: IPlayService? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            playService = IPlayService.Stub.asInterface(service)
            playService?.setPrintMessageCallback(object :IPrint.Stub(){
                override fun print(msg: String?) {
                    runOnUiThread {
                        informationTextView.text = msg
                    }
                }
            })
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playService?.setPrintMessageCallback(null)
            playService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcm_example)

        informationTextView = findViewById(R.id.tv_information)

        val intent = Intent(this, PlayService::class.java)
        startService(intent)
        bindService(intent, conn, BIND_AUTO_CREATE)

        findViewById<Button>(R.id.bt_bind_service).setOnClickListener {
            playService?.bindService()
        }

        findViewById<Button>(R.id.bt_start_pcm_mode).setOnClickListener {
            playService?.startPcmMode()
        }

        findViewById<Button>(R.id.bt_stop_pcm_mode).setOnClickListener {
            playService?.stopPcmMode()
        }

        findViewById<Button>(R.id.bt_play).setOnClickListener {
            playService?.resumeOrPause()
        }

        findViewById<Button>(R.id.bt_next).setOnClickListener {
            playService?.playNext()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(conn)
    }

}