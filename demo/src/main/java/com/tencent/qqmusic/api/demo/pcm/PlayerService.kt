package com.tencent.qqmusic.api.demo.pcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.os.*
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.Toast
import com.tencent.qqmusic.api.demo.*
import com.tencent.qqmusic.api.demo.R
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.api.demo.util.QPlayBindHelper
import com.tencent.qqmusic.third.api.contract.*
import kotlinx.android.synthetic.main.activity_visual.view.*


/**
 * Created by clydeazhang on 2021/5/11 6:43 PM.
 * Copyright (c) 2021 Tencent. All rights reserved.
 */
class PlayerService : Service() {

    companion object {
        private const val TAG = "PlayService"
        private const val KEY_ENTER_FOREGROUND = "KEY_ENTER_FOREGROUND"
        private const val CHANNEL_ID = "default"
    }

    private val qPlayBindHelper = QPlayBindHelper(this)
    var qqMusicApi: IQQMusicApi? = null
    private var curPlayState: Int = 0
    private val handler = Handler(Looper.getMainLooper())

    private var audioManager: AudioTrackManager? = null
    private var audioManagerFloat: AudioTrackManager? = null

    /**
     * QQ音乐事件回调
     */
    private val eventListener = object : IQQMusicApiEventListener.Stub() {
        override fun onEvent(event: String, extra: Bundle) {
            extra.classLoader = this@PlayerService.classLoader
            Log.d(TAG, "onEvent:$event extra:${extra.toPrintableString()}")
            handler.post {
                when (event) {
                    Events.API_EVENT_PLAY_STATE_CHANGED -> {
                        curPlayState = extra.getInt(Keys.API_EVENT_KEY_PLAY_STATE)
                    }
                    Events.API_EVENT_MEDIA_INFO_CHANGED -> {
                        onMediaInfoChanged(extra)
                        onMediaInfoChangedFloat(extra)
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
                Toast.makeText(this@PlayerService, "授权成功", Toast.LENGTH_SHORT).show()
                qqMusicApi?.registerEventListener(
                    arrayListOf(
                        Events.API_EVENT_PLAY_STATE_CHANGED,
                        Events.API_EVENT_PLAY_SONG_CHANGED,
                        Events.API_EVENT_MEDIA_INFO_CHANGED
                    ), eventListener
                )
            } else {
                Log.d(TAG, "activeBroadcastReceiver 授权失败($ret)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter()
        filter.addAction("callback_verify_notify")
        registerReceiver(activeBroadcastReceiver, filter)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "默认"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val enterForeground = intent?.getBooleanExtra(KEY_ENTER_FOREGROUND, false) ?: false
        Log.d(TAG, "onStartCommand, enter foreground=$enterForeground")
        if (enterForeground) {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("demo标题")
                .setContentText("demo正在播放音乐")
                .build()
            startForeground(1, notification)
        } else {
            //stopForeground(true)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(activeBroadcastReceiver)
        audioManager?.stopPlay()
        audioManagerFloat?.stopPlay()
        audioManager?.enterForeground = null
        audioManagerFloat?.enterForeground = null
    }

    private var printer: IPrint? = null
    override fun onBind(intent: Intent?): IBinder? {
        return object : IPlayService.Stub() {
            override fun bindService() {
                this@PlayerService.bindService()
            }

            override fun startPcmMode() {
                this@PlayerService.startPcmMode()
            }

            override fun stopPcmMode() {
                this@PlayerService.stopPcmMode()
            }

            override fun resumeOrPause() {
                this@PlayerService.resumeOrPause()
            }

            override fun playNext() {
                this@PlayerService.playNext()
            }

            override fun setPrintMessageCallback(callback: IPrint?) {
                this@PlayerService.printer = callback
            }
        }
    }

    private fun playNext() {
        val result = qqMusicApi?.execute("skipToNext", null)
        val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
        if (errorCode != ErrorCodes.ERROR_OK) {
            Log.d(VisualActivity.TAG, "下一首失败($errorCode)")
        }
    }

    private fun resumeOrPause() {
        if (isPlaying()) {
            Log.d(TAG, "pauseMusic")
            val result = qqMusicApi?.execute("pauseMusic", null)
            val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                Log.d(TAG, "暂停音乐失败($errorCode)")
            }
            audioManager?.pause()
            audioManagerFloat?.pause()
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
            audioManager?.play()
            audioManagerFloat?.play()
        }
    }

    private fun stopPcmMode() {
        audioManager?.stopPlay()
        audioManagerFloat?.stopPlay()
        qqMusicApi?.execute("stopPcmMode", null)
    }

    private fun startPcmMode() {
        Log.d(TAG, "bt_request_pcm_data setOnClickListener")

        qqMusicApi?.executeAsync("startPcmMode", null, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(registerResult: Bundle?) {
                registerResult?.classLoader = classLoader

                val ret = registerResult?.getInt(Keys.API_RETURN_KEY_CODE) ?: -1
                Log.d(TAG, "onReturn $ret")
                if (ret != ErrorCodes.ERROR_OK) {
                    Log.d(TAG, "startPcmMode return failed, ret=$ret")
                    return
                }

                //onMediaInfoChanged(registerResult)
            }
        })
    }

    private fun onMediaInfoChanged(registerResult: Bundle?) {
        audioManager?.stopPlay()
        audioManager = AudioTrackManager()

        audioManager?.printMessageCallback = {
            printer?.print(it)
        }
        audioManagerFloat?.printMessageCallback = {
            printer?.print(it)
        }

        audioManager!!.enterForeground = {
            Log.d(TAG, "enter foreground=$it")
            val intent = Intent(this, PlayerService::class.java)
            intent.putExtra(KEY_ENTER_FOREGROUND, it)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        val mediaInfo = registerResult?.getParcelable<Data.MediaInfo>(Keys.API_PARAM_KEY_MEDIA_INFO)
        mediaInfo ?: return
        Log.d(TAG, "initData, mediaInfo=$mediaInfo")
        audioManager!!.stopPlay()
        audioManager!!.initData(
            mediaInfo.sampleRateInHz,
            if (mediaInfo.channelConfig == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
            mediaInfo.audioFormat
        )

        val parcelFileDescriptor =
            registerResult.getParcelable<ParcelFileDescriptor>(Keys.API_PARAM_KEY_PCM_FILE_DESCRIPTOR)
        parcelFileDescriptor ?: return
        Log.d(TAG, "parcelFileDescriptor: $parcelFileDescriptor")
        audioManager!!.startPlayByFileDescriptor(parcelFileDescriptor)
    }

    private fun onMediaInfoChangedFloat(registerResult: Bundle?) {
        audioManagerFloat?.stopPlay()
        audioManagerFloat = AudioTrackManager()

        audioManagerFloat?.printMessageCallback = {
            printer?.print(it)
        }

        audioManagerFloat!!.enterForeground = {
            Log.d(TAG, "enter foreground=$it")
            val intent = Intent(this, PlayerService::class.java)
            intent.putExtra(KEY_ENTER_FOREGROUND, it)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        val mediaInfo = registerResult?.getParcelable<Data.MediaInfo>(Keys.API_PARAM_KEY_MEDIA_INFO)
        mediaInfo ?: return
        Log.d(TAG, "initData, mediaInfo=$mediaInfo")
        audioManagerFloat!!.stopPlay()
        audioManagerFloat!!.initData(
            mediaInfo.sampleRateInHz,
            if (mediaInfo.channelConfig == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
            mediaInfo.audioFormat
        )

        val parcelFileDescriptor =
            registerResult.getParcelable<ParcelFileDescriptor>(Keys.API_PARAM_KEY_PCM_FILE_DESCRIPTOR_FLOAT)
        parcelFileDescriptor ?: return
        Log.d(TAG, "parcelFileDescriptor: $parcelFileDescriptor")
        audioManagerFloat!!.startPlayByFileDescriptor(parcelFileDescriptor)
    }

    private fun bindService() {
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
                    CommonCmd.verifyCallerIdentity(
                        this,
                        Config.OPENID_APPID,
                        packageName,
                        encryptString,
                        "qqmusicapidemo://"
                    )
                } else {
                    Toast.makeText(this@PlayerService, "授权成功", Toast.LENGTH_SHORT).show()
                    qqMusicApi?.registerEventListener(
                        arrayListOf(
                            Events.API_EVENT_PLAY_STATE_CHANGED,
                            Events.API_EVENT_PLAY_SONG_CHANGED,
                            Events.API_EVENT_MEDIA_INFO_CHANGED
                        ), eventListener
                    )
                }
            }
        }
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