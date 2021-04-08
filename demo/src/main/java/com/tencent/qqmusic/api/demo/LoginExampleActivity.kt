package com.tencent.qqmusic.api.demo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.api.demo.util.QPlayBindHelper
import com.tencent.qqmusic.api.demo.util.QQMusicApiWrapper
import com.tencent.qqmusic.third.api.contract.CommonCmd
import com.tencent.qqmusic.third.api.contract.IQQMusicApi
import com.tencent.qqmusic.third.api.contract.Keys
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 使用QQMusic手机端完成登录获取Openid OpenToken的例子
 */
class LoginExampleActivity : AppCompatActivity() {
    companion object {
        const val TAG = "LoginExampleActivity"
        const val BASE_SCHEME = "qqmusicapidemo://"
        const val URI_LOGIN = BASE_SCHEME + "login"

        const val LOGIN_FAILED = 1
    }

    private val qPlayBindHelper = QPlayBindHelper(this)

    lateinit var qqMusicApi: IQQMusicApi
    lateinit var qqMusicApiWrapper: QQMusicApiWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login_example)

        findViewById<Button>(R.id.bt_bind_service).setOnClickListener {
            qPlayBindHelper.ensureQQMusicBindByStartProcess {
                if (it && qPlayBindHelper.isBindQQMusic()) {
                    Log.d(TAG, "isBindQQMusic")
                    qqMusicApi = qPlayBindHelper.getQQMusicApi()!!
                    qqMusicApiWrapper = QQMusicApiWrapper(qqMusicApi)

                    CommonCmd.loginQQMusic(this, URI_LOGIN)

                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        qPlayBindHelper.unBindQQMusic()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent $intent")
        val uri = intent?.data ?: return
        handleLogin(intent)
    }

    private fun handleLogin(intent: Intent?): Boolean {
        if (handleJumpQQMusicLoginPageReturnBack(intent)) {
            return true
        }
        if (handleQQMusicVerifyReturnBack(intent)) {
            return true
        }
        return false
    }

    private fun handleJumpQQMusicLoginPageReturnBack(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        val qmLogin = uri.getQueryParameter("qmlogin") ?: return false
        // 跳转qq音乐登录，登录成功后返回：qqmusicapidemo://login?qmlogin=1
        // qmlogin=0表示失败
        if (uri.toString().startsWith(URI_LOGIN)) {
            if (qmLogin == "1") {
                // 下一步是打开qq音乐的授权页，但是如果已经授权过的话是不需要延时的，
                // 因此这里做一个优化，先尝试requestAuth，失败了再延时打开授权页
                Log.d(TAG, "[handleJumpQQMusicLoginPageReturnBack] tryQqMusicRequestAuth")
                tryQqMusicRequestAuth { _, _ ->
                    qqMusicApiWrapper.verifyCallerIdentity(this, BASE_SCHEME)
                }
            } else {
                // 如果用户在登录过程中出现错误，其实qq音乐并不会回调错误给我们，只在用户按了返回才会回调0。
                // 现阶段无法判断用户在登录过程中是否出现错误，因此这里统一当成取消登录。
                onLoginFailed("登录失败")
            }
            return true
        }
        return false
    }

    private fun tryQqMusicRequestAuth(failed: ((Int, String?) -> Unit)? = null) {
        Log.d(TAG, "tryQqMusicRequestAuth")
        qPlayBindHelper.ensureQQMusicBind { bind ->
            if (bind) {
                qqMusicApiWrapper.requestAuthNew(success = { encrypt ->
                    Log.d(TAG, "request auth succeed $encrypt")

                    val qmDecryptString = OpenIDHelper.decryptQQMEncryptString(encrypt)
                    if (qmDecryptString != null) {
                        val appParser = JSONTokener(qmDecryptString)
                        val appDecryptJson = appParser.nextValue() as JSONObject
                        val sign = appDecryptJson.getString(Keys.API_RETURN_KEY_SIGN)
                        val returnNonce = appDecryptJson.getString(Keys.API_RETURN_KEY_NONCE)
                        //检查签名
                        if (OpenIDHelper.checkQMSign(sign, returnNonce)) {
                            val openId = appDecryptJson.getString(Keys.API_RETURN_KEY_OPEN_ID)
                            val openToken = appDecryptJson.getString(Keys.API_RETURN_KEY_OPEN_TOKEN)
                            var expireTime = appDecryptJson.getString(Keys.API_PARAM_KEY_SDK_EXPIRETIME)
                            Log.d(TAG, "openid:$openId,openToken:$openToken")
                            runOnUiThread {
                                Toast.makeText(this, "登录成功openid:$openId,openToken:$openToken", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }, failed = { code, errorMsg ->
                    Log.d(TAG, "request auth failed: code=$code, msg=$errorMsg")
                    failed?.invoke(code, errorMsg)
                })
            } else {
                failed?.invoke(LOGIN_FAILED, "try request auth bind failed")
            }
        }
    }

    private fun handleQQMusicVerifyReturnBack(intent: Intent?): Boolean {
        intent ?: return false
        val uri = intent.data ?: return false
        val cmd = uri.getQueryParameter("cmd")
        val ret = uri.getQueryParameter("ret")
        // qq音乐verifyCallerIdentity返回，ret=0表示成功，ret=-2表示取消授权，其余表示失败
        if ("verify" == cmd) {
            when (ret) {
                "0" -> {
                    // qq音乐的登录还需要再申请授权
                    qqMusicRequestAuth()
                }
                "-2" -> {
                    // 取消登录
                    onLoginFailed("取消登录")
                }
                else -> {
                    onLoginFailed("verify failed[ret:${ret}]")
                    if (ret == "-1") {
                        // qq音乐在网络较差时比较有可能返回-1
                        onLoginFailed("授权失败，请检查网络")
                    }
                }
            }
            return true
        }
        return false
    }

    private fun qqMusicRequestAuth() {
        Log.d(TAG, "qqMusicRequestAuth")
        tryQqMusicRequestAuth { errorCode, errorMsg ->
            onLoginFailed("code[$errorCode];msg[$errorMsg]")
        }
    }

    private fun onLoginFailed(errorMsg: String) {
        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
    }
}