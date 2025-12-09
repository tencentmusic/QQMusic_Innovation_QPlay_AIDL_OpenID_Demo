package com.tencent.qqmusic.api.demo.util

import android.content.Context
import android.os.Bundle
import android.os.DeadObjectException
import android.util.Log
import com.tencent.qqmusic.api.demo.Config
import com.tencent.qqmusic.api.demo.LoginExampleActivity.Companion.LOGIN_FAILED
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.*

class QQMusicApiWrapper(val qqMusicApi: IQQMusicApi) {
    companion object {
        const val TAG = "QQMusicApiWrapper"

        private const val QQMUSIC_VERSION_10_3_0_1 = 10030001

        private var qqMusicVersion: Int = -1

    }

    fun verifyCallerIdentity(context: Context, callbackUri: String?) {
        val time = System.currentTimeMillis()
        val nonce = time.toString()
        val encryptString = OpenIDHelper.getEncryptString(nonce)
        CommonCmd.verifyCallerIdentity(
                context,
                Config.OPENID_APPID,
                context.packageName,
                encryptString,
                callbackUri
        )
    }

    /**
     * qq音乐10.3以后才在 DispacherActivityForThird 中支持 onNewIntent
     */
    fun isQQMusicCanHandleOnNewIntent() = getQQMusicVersion() >= QQMUSIC_VERSION_10_3_0_1

    fun getQQMusicVersion(): Int {
        if (qqMusicVersion != -1) {
            return qqMusicVersion
        }
        val bundle = Bundle()
        bundle.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
        bundle.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, CommonCmd.AIDL_PLATFORM_TYPE_PHONE)
        val result = try {
            qqMusicApi.execute("hi", bundle)
        } catch (e: Exception) {
            null
        }
        qqMusicVersion = result?.getInt(Keys.API_RETURN_KEY_VERSION) ?: -1
        return qqMusicVersion
    }


    /**
     * 申请授权，[success]返回授权成功后的加密数据；[failed]返回错误码和错误说明，错误码参考[LOGIN_FAILED]等常量
     */
    fun requestAuthNew(success: (String) -> Unit, failed: (Int, String?) -> Unit) {
        Log.i(TAG, "[requestAuth] ")
        val time = System.currentTimeMillis()
        val nonce = time.toString()
        val encryptString = OpenIDHelper.getEncryptString(nonce) //解密&加密
        val params = Bundle()
        params.putString(Keys.API_RETURN_KEY_ENCRYPT_STRING, encryptString)

        try {
            qqMusicApi.executeAsync("requestAuth", params, object : IQQMusicApiCallback.Stub() {
                override fun onReturn(result: Bundle) {
                    val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                    Log.i(TAG, "[onReturn] code $code")
                    if (code == ErrorCodes.ERROR_OK) {
                        val qmEncryptString =
                                result.getString(Keys.API_RETURN_KEY_ENCRYPT_STRING)
                        if (qmEncryptString != null) {
                            success(qmEncryptString)
                        } else {
                            failed(LOGIN_FAILED, "qmEncryptString is null")
                        }
                    } else {
                        val errorMsg = result.getString(Keys.API_RETURN_KEY_ERROR)
                        failed(LOGIN_FAILED, errorMsg)
                    }
                }
            })
        } catch (e: Exception) {
            if (e is DeadObjectException) {
                Log.e(TAG, "[requestAuthInternal] dead object exception ", e)
                failed(LOGIN_FAILED, "service is dead")
            } else {
                Log.e(TAG, "[requestAuthInternal] other exception ", e)
                failed(LOGIN_FAILED, "other exception[${e.message}]")
            }
        }
    }

}