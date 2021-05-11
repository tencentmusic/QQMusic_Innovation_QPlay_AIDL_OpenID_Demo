package com.tencent.qqmusic.api.demo.pcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.os.Binder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.tencent.qqmusic.api.demo.Config
import com.tencent.qqmusic.api.demo.R
import com.tencent.qqmusic.api.demo.VisualActivity
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.api.demo.toPrintableString
import com.tencent.qqmusic.api.demo.util.QPlayBindHelper
import com.tencent.qqmusic.third.api.contract.*
import com.tencent.qqmusic.third.api.contract.Events.API_EVENT_PLAY_SONG_CHANGED
import com.tencent.qqmusic.third.api.contract.Keys.API_PARAM_KEY_MEDIA_INFO
import com.tencent.qqmusic.third.api.contract.Keys.API_PARAM_KEY_PCM_FILE_DESCRIPTOR


class PcmExampleActivity : AppCompatActivity() {
    companion object {
        const val TAG = "PcmExampleActivity"
    }

    private lateinit var informationTextView: TextView

    private val qPlayBindHelper = QPlayBindHelper(this)
    var qqMusicApi: IQQMusicApi? = null
    private var curPlayState: Int = 0

    /**
     * QQ音乐事件回调
     */
    private val eventListener = object : IQQMusicApiEventListener.Stub() {
        override fun onEvent(event: String, extra: Bundle) {
            Log.d(TAG, "onEvent:$event extra:${extra.toPrintableString()}")

            runOnUiThread {
                when (event) {
                    Events.API_EVENT_PLAY_STATE_CHANGED -> {
                        curPlayState = extra.getInt(Keys.API_EVENT_KEY_PLAY_STATE)
                    }
                }
            }
        }
    }

    private var activeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            val ret = p1?.extras?.get("ret")
            if (ret == "0") {
                Log.d(TAG, "activeBroadcastReceiver 授权成功")
                Toast.makeText(this@PcmExampleActivity, "授权成功", Toast.LENGTH_SHORT).show()
                qqMusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED, API_EVENT_PLAY_SONG_CHANGED), eventListener)
            } else {
                Log.d(TAG, "activeBroadcastReceiver 授权失败($ret)")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcm_example)

        informationTextView = findViewById(R.id.tv_information)

        findViewById<Button>(R.id.bt_bind_service).setOnClickListener {
            qPlayBindHelper.ensureQQMusicBindByStartProcess {
                if (it && qPlayBindHelper.isBindQQMusic()) {
                    qqMusicApi = qPlayBindHelper.getQQMusicApi()!!

                    //execute hi
                    val bundle = Bundle().apply {
                        putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
                        putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, Config.BIND_PLATFORM)
                    }
                    val result = qqMusicApi?.execute("hi", bundle)
                    val code = result?.getInt(Keys.API_RETURN_KEY_CODE)
                    if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                        val time = System.currentTimeMillis()
                        val nonce = time.toString()
                        val encryptString = OpenIDHelper.getEncryptString(nonce)
                        CommonCmd.verifyCallerIdentity(this, Config.OPENID_APPID, packageName, encryptString, "qqmusicapidemo://")
                    } else {
                        Toast.makeText(this@PcmExampleActivity, "授权成功", Toast.LENGTH_SHORT).show()
                        qqMusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED, API_EVENT_PLAY_SONG_CHANGED), eventListener)
                    }
                }
            }
        }

        findViewById<Button>(R.id.bt_start_pcm_mode).setOnClickListener {
            Log.d(TAG, "bt_request_pcm_data setOnClickListener")

            qqMusicApi?.executeAsync("startPcmMode", null, object : IQQMusicApiCallback.Stub() {
                override fun onReturn(registerResult: Bundle?) {
                    val ret = registerResult?.getInt(Keys.API_RETURN_KEY_CODE) ?: -1
                    Log.d(TAG, "onReturn $ret")
                    if (ret != ErrorCodes.ERROR_OK) {
                        Log.d(TAG, "startPcmMode return failed, ret=$ret")
                        return
                    }

                    registerResult?.classLoader = classLoader

                    val mediaInfo = registerResult?.getParcelable<Data.MediaInfo>(API_PARAM_KEY_MEDIA_INFO)
                    mediaInfo ?: return
                    Log.d(TAG, "initData")
                    AudioTrackManager.initData(mediaInfo.sampleRateInHz, if (mediaInfo.channelConfig == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO, mediaInfo.audioFormat)

                    val parcelFileDescriptor = registerResult.getParcelable<ParcelFileDescriptor>(API_PARAM_KEY_PCM_FILE_DESCRIPTOR)
                    parcelFileDescriptor ?: return
                    AudioTrackManager.startPlayByFileDescriptor(parcelFileDescriptor)
                }
            })
        }

        findViewById<Button>(R.id.bt_stop_pcm_mode).setOnClickListener {
            AudioTrackManager.stopPlay()
            qqMusicApi?.execute("stopPcmMode", null)
        }

        findViewById<Button>(R.id.bt_play).setOnClickListener {
            if (isPlaying()) {
                Log.d(TAG, "pauseMusic")
                val result = qqMusicApi?.execute("pauseMusic", null)
                val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
                if (errorCode != ErrorCodes.ERROR_OK) {
                    Log.d(TAG, "暂停音乐失败($errorCode)")
                }
                AudioTrackManager.pause()
            } else {
                if (curPlayState == PlayState.PAUSED) {
                    Log.d(TAG, "resumeMusic")
                    val result = qqMusicApi?.execute("resumeMusic", null)
                    val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
                    if (errorCode != ErrorCodes.ERROR_OK) {
                        Log.d(VisualActivity.TAG, "继续播放音乐失败($errorCode)")
                    }
                } else {
                    Log.d(TAG, "playMusic")
                    val result = qqMusicApi?.execute("playMusic", null)
                    val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
                    if (errorCode != ErrorCodes.ERROR_OK) {
                        Log.d(VisualActivity.TAG, "开始播放音乐失败($errorCode)")
                    }
                }
                AudioTrackManager.play()
            }

        }

        findViewById<Button>(R.id.bt_next).setOnClickListener {
            val result = qqMusicApi?.execute("skipToNext", null)
            val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                Log.d(VisualActivity.TAG, "下一首失败($errorCode)")
            }
        }

        AudioTrackManager.printMessageCallback = {
            runOnUiThread {
                informationTextView.text = it
            }
        }

        val filter = IntentFilter()
        filter.addAction("callback_verify_notify")
        registerReceiver(activeBroadcastReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioTrackManager.stopPlay()
        unregisterReceiver(activeBroadcastReceiver)
    }

    private fun isPlaying(): Boolean {
        Log.d(TAG, "[isPlaying]curPlayState:$curPlayState")
        return (curPlayState == PlayState.STARTED
                || curPlayState == PlayState.INITIALIZED
                || curPlayState == PlayState.PREPARED
                || curPlayState == PlayState.PREPARING
                || curPlayState == PlayState.BUFFERING)
    }

}