package com.tencent.qqmusic.api.demo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast


class CallbackActivity : Activity() {
    companion object {
        const val TAG = "CallbackActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_callback)
        getSchemeInfos()
//        val intent = Intent(this, MainActivity::class.java)
//        startActivity(intent)
        finish()
    }


    override fun onResume() {
        super.onResume()
        //getSchemeInfos()
//        val intent = Intent(this, MainActivity::class.java)
//        startActivity(intent)
        finish()
    }

    private fun getSchemeInfos() {
        // 获取uri参数
        val intent = intent
        val scheme = intent.scheme
        val uri = intent.data
        Log.i(TAG, "uri:$uri")
        if (uri != null) {
            val cmd = uri.getQueryParameter("cmd")
            if (cmd == "open") {
                Log.i(TAG, "ret:" + uri.getQueryParameter("ret"))
            } else if (cmd == "verify") {
                val ret = uri.getQueryParameter("ret")
                val intent = Intent()
                intent.action = "callback_verify_notify"
                intent.putExtra("ret", ret)
                sendBroadcast(intent)
            } else {
                val loginResult = uri.getQueryParameter("qmlogin")
                if (loginResult == "1") {
                    Toast.makeText(this, "QQMusic login success!!!", Toast.LENGTH_LONG).show()
                } else if (loginResult == "0") {
                    Toast.makeText(this, "QQMusic login error!!!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


}
