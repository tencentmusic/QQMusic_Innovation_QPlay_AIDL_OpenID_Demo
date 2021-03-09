package com.tencent.qqmusic.api.demo.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.tencent.qqmusic.third.api.contract.CommonCmd
import com.tencent.qqmusic.third.api.contract.CommonCmd.AIDL_PLATFORM_TYPE_PHONE
import com.tencent.qqmusic.third.api.contract.IQQMusicApi

class QPlayBindHelper(private val context: Context, private val bindPlatform: String = AIDL_PLATFORM_TYPE_PHONE) {
    companion object {
        private const val TAG = "QQMusicBindHelper"
    }

    private var api: IQQMusicApi? = null

    private val handler: Handler by lazy { Handler() }

    private val deathRecipient by lazy {
        IBinder.DeathRecipient {
            Log.e(TAG, "[binderDied] ")
            api = null
        }
    }

    private val conn = object : ServiceConnection {
        var success: (() -> Unit)? = null
        override fun onServiceDisconnected(name: ComponentName?) {
            api = null
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            api = IQQMusicApi.Stub.asInterface(service)
            // 注册死亡代理
            try {
                service?.linkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                Log.e(TAG, "[onServiceConnected] link to death exception ", e)
            }
            success?.invoke()
            success = null
        }
    }

    fun bindQQMusic(callback: ((Boolean) -> Unit)? = null, retry: Boolean = false) {
        if (isBindQQMusic()) {
            callback?.invoke(true)
        }
        // 必须显式绑定
        val intent = getQQMusicApiServiceIntent()

        val ret = try {
            Log.d(TAG, "[bindQQMusic] bindService $retry")
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "startBindQQMusic failed")
            false
        }
        if (!ret) {
            // 绑定失败，可能是qq音乐进程未启动，或者可能是魅族系列手机，需要先startService，才能bind
            if (retry.not()) {
                val startComponent = try {
                    // 如果qq音乐进程没有启动，高版本的Android系统start service会返回null
                    context.startService(intent)
                } catch (e: Throwable) {
                }
                if (startComponent != null) {
                    bindQQMusic(callback, true)
                } else {
                    callback?.invoke(false)
                }
            } else {
                callback?.invoke(false)
            }
        } else {
            conn.success = {
                callback?.invoke(true)
            }
        }
    }

    fun unBindQQMusic() {
        context.unbindServiceSafe(conn)
    }

    /**
     * bindService不成功时，调用一次startService，但还是需要调用方延迟后进行重试
     */
    fun ensureQQMusicBind(callback: ((Boolean) -> Unit)? = null) {
        bindQQMusic(callback)
    }

    fun ensureQQMusicBindByStartProcess(isStartProcess: Boolean = true, callback: ((Boolean) -> Unit)? = null) {
        bindQQMusic({ succeed ->
            if (!succeed) {
                if (isStartProcess) {
                    CommonCmd.startQQMusicProcess(context, context.packageName)
                }
                handler.postDelayed({
                    ensureQQMusicBindByStartProcess(false, callback)
                }, 800)
            } else {
                callback?.invoke(true)
            }
        }, true)
    }

    fun isBindQQMusic() = api != null

    fun getQQMusicApi() = api

    private fun getQQMusicApiServiceIntent(): Intent {
        var intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
        // 必须显式绑定
        when (bindPlatform) {
            CommonCmd.AIDL_PLATFORM_TYPE_PHONE -> {
                intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusic"
            }
            CommonCmd.AIDL_PLATFORM_TYPE_CAR -> {
                intent = Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusiccar"
            }
            CommonCmd.AIDL_PLATFORM_TYPE_TV -> {
                intent = Intent("com.tencent.qqmusictv.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusictv"
            }
            else -> {
                Log.e(TAG, "platform 不匹配")
                Toast.makeText(context, "请先在Config中填写配置信息！", Toast.LENGTH_SHORT).show()
            }
        }
        return intent
    }

}

fun Context.unbindServiceSafe(conn: ServiceConnection) {
    try {
        unbindService(conn)
    } catch (e: Throwable) {
        Log.e("CommExt", "unbindService failed", e)
    }
}