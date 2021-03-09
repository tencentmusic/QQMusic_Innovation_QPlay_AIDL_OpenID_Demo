package com.tencent.qqmusic.api.demo.pcm

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import com.tencent.qqmusic.api.demo.Config
import com.tencent.qqmusic.api.demo.util.QPlayBindHelper
import com.tencent.qqmusic.api.demo.R
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.CommonCmd
import com.tencent.qqmusic.third.api.contract.ErrorCodes
import com.tencent.qqmusic.third.api.contract.IQQMusicApi
import com.tencent.qqmusic.third.api.contract.Keys

class PcmExampleActivity : AppCompatActivity() {
    companion object {
        const val TAG = "PcmExampleActivity"
    }

    private val qPlayBindHelper = QPlayBindHelper(this)

    lateinit var qqMusicApi: IQQMusicApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcm_example)

        findViewById<Button>(R.id.bt_bind_service).setOnClickListener {
            qPlayBindHelper.ensureQQMusicBindByStartProcess{
                if (it && qPlayBindHelper.isBindQQMusic()) {
                    qqMusicApi = qPlayBindHelper.getQQMusicApi()!!
                    val bundle = Bundle()
                    bundle.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
                    bundle.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, Config.BIND_PLATFORM)
                    val result = qqMusicApi?.execute("hi", bundle)
                    val code = result?.getInt(Keys.API_RETURN_KEY_CODE)
                    if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                        val time = System.currentTimeMillis()
                        val nonce = time.toString()
                        val encryptString = OpenIDHelper.getEncryptString(nonce)
                        CommonCmd.verifyCallerIdentity(this, Config.OPENID_APPID, packageName, encryptString, "qqmusicapidemo://xxx")
                    }
                }
            }
        }

        findViewById<Button>(R.id.bt_request_pcm_data).setOnClickListener {
            qqMusicApi.execute("", null)
        }

    }
}