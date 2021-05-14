package com.tencent.qqmusic.api.demo.pcm

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import com.tencent.qqmusic.api.demo.*
import com.tencent.qqmusic.api.demo.R


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

        val intent = Intent(this, PlayerService::class.java)
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