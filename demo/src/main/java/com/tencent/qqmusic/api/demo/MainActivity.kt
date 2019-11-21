package com.tencent.qqmusic.api.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import com.tencent.qqmusic.third.api.contract.*
import java.util.*
import kotlin.collections.ArrayList

@SuppressLint("SetTextI18n")
class MainActivity : Activity(), ServiceConnection {
    private val textBack by lazy { findViewById<TextView>(R.id.text_back) }
    private val textView by lazy { findViewById<TextView>(R.id.text) }
    private val paramsKeyText by lazy { findViewById<EditText>(R.id.textView2) }
    private val songText by lazy { findViewById<TextView>(R.id.textViewSong) }
    private val songList by lazy { findViewById<TextView>(R.id.textViewList) }
    private val executeButton by lazy { findViewById<Button>(R.id.button) }
    private val executeAsyncButton by lazy { findViewById<Button>(R.id.button2) }
    private val actionText by lazy { findViewById<AutoCompleteTextView>(R.id.textAction) }
    private val paramsText by lazy { findViewById<EditText>(R.id.textParams) }

    private val eventListener = object : IQQMusicApiEventListener.Stub() {
        override fun onEvent(event: String, extra: Bundle) {
            runOnUiThread {
                if (event == Events.API_EVENT_PLAY_SONG_CHANGED) {
                    songText.text = extra.getString(Keys.API_EVENT_KEY_PLAY_SONG)
                } else if (event == Events.API_EVENT_PLAY_LIST_CHANGED) {
                    var size = extra.getInt(Keys.API_EVENT_KEY_PLAY_LIST_SIZE)
                    songList.text = "".plus(size)
                }

            }
        }

    }

    private var qqmusicApi: IQQMusicApi? = null

    override fun onServiceDisconnected(p0: ComponentName) {
        // 失去连接，可能QQ音乐退出了
        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = false }
        textView.text = "disconnected"
    }

    override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
        // 绑定成功
        qqmusicApi = IQQMusicApi.Stub.asInterface(p1)
        // 可选：注册事件回调
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)

        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = true }
        textView.text = "connected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val adapter = ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, ACTIONS)
        actionText.setAdapter(adapter)
        actionText.setOnItemClickListener { adapterView: AdapterView<*>, view1: View, i: Int, l: Long ->
            if (actionText.text.toString() == "getFolderList") {
                paramsKeyText.setText("folderId,folderType,page")
                paramsText.setText("0\n0\n0")
            } else if (actionText.text.toString() == "getSongList") {
                paramsKeyText.setText("folderId,folderType,page")
                paramsText.setText("201|1\n101\n0")
            } else if (actionText.text.toString() == "playSongId") {
                paramsKeyText.setText("songIdList")
                paramsText.setText("108756239|0")
            } else if (actionText.text.toString() == "search") {
                paramsKeyText.setText("keyword,searchType,firstPage")
                paramsText.setText("周杰伦\n0\ntrue")
            } else if (actionText.text.toString() == "setPlayMode") {
                paramsKeyText.setText("playMode")
                paramsText.setText("0")
            } else if (actionText.text.toString() == "voicePlay") {
                paramsKeyText.setText("query;slotList")
                paramsText.setText("我想听周杰伦的七里香\nsinger=周杰伦\nsong=七里香")
            } else if (actionText.text.toString() == "voiceShortcut") {
                paramsKeyText.setText("intent")
                paramsText.setText("favorite")
            }
        }

        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = false }

        executeButton.setOnClickListener {
            val params = Bundle().apply {
                if (paramsKeyText.text.toString().contains(",")) {
                    val params = paramsKeyText.text.toString().split(",")
                    for (i in 0..(params.size - 1)) {
                        if (params[i] == "folderType" || params[i] == "page" || params[i] == "searchType") {
                            putInt(params[i], paramsText.text.lines()[i].toInt())
                        } else {
                            putString(params[i], paramsText.text.lines()[i])
                        }
                    }
                } else {
                    if (paramsKeyText.text.toString() == "page" || paramsKeyText.text.toString() == "playMode") {
                        putInt(paramsKeyText.text.toString(), paramsText.text.lines()[0].toInt())
                    } else if (paramsKeyText.text.toString() == "time") {
                        putLong(paramsKeyText.text.toString(), paramsText.text.lines()[0].toLong())
                    } else {
                        putStringArrayList(paramsKeyText.text.toString(), ArrayList(paramsText.text.lines()))
                    }
                }
            }
            execute(actionText.text.toString(), params, false)

        }

        executeAsyncButton.setOnClickListener {
            val params = Bundle().apply {
                if (paramsKeyText.text.toString().contains(",")) {
                    val params = paramsKeyText.text.toString().split(",")
                    for (i in 0..(params.size - 1)) {
                        if (params[i] == "folderType" || params[i] == "page" || params[i] == "searchType") {
                            putInt(params[i], paramsText.text.lines()[i].toInt())
                        } else if (params[i] == "firstPage") {
                            putBoolean(params[i], paramsText.text.lines()[i].toBoolean())
                        } else {
                            putString(params[i], paramsText.text.lines()[i])
                        }
                    }
                } else if(paramsKeyText.text.toString().contains(";")) {
                    val params = paramsKeyText.text.toString().split(";")
                    val slotList = ArrayList<String>()
                    for (i in 0..(params.size - 1)) {
                        if (params[i] == "query") {
                            putString(params[i], paramsText.text.lines()[i])
                        } else {
                            slotList.add(paramsText.text.lines()[i])
                            slotList.add(paramsText.text.lines()[i+1])
                        }
                    }
                    putStringArrayList(params[1], slotList)
                } else {
                    if (paramsKeyText.text.toString() == "page") {
                        putInt(paramsKeyText.text.toString(), paramsText.text.lines()[0].toInt())
                    } else if(paramsKeyText.text.toString() == "intent") {
                        putString(paramsKeyText.text.toString(), paramsText.text.lines()[0])
                    } else {
                        putStringArrayList(paramsKeyText.text.toString(), ArrayList(paramsText.text.lines()))
                    }
                }
            }
            execute(actionText.text.toString(), params, true)
        }

        textView.text = "connecting..."

        val bindRet = bindQQMusicApiService()
        if (!bindRet) {
            textView.text = "failed to connect"
        }
        textBack.visibility = View.VISIBLE
        textBack.setOnClickListener() {
            this@MainActivity.finish()
        }
    }

    /**
     * 调用QQ音乐AIDL接口
     *
     * @param action 操作，见[ACTIONS]
     * @param params 传参
     * @param async 是否异步，注意，对于不支持的异步的操作，将会在回调中返回错误ERROR_API_UNSUPPORTED_ACTION
     */
    private fun execute(action: String, params: Bundle?, async: Boolean) {
        print("executing [$action]")
        if (async) {
            // 异步执行
            qqmusicApi?.executeAsync(action, params, object : IQQMusicApiCallback.Stub() {
                override fun onReturn(p0: Bundle) {
                    // 回调的结果
                    print(p0)
                    commonOpen(p0)
                }
            })
        } else {
            // 同步执行
            val p1 = qqmusicApi?.execute(action, params)
            print(p1)
            commonOpen(p1 as Bundle)
        }
    }

    private fun commonOpen(p0: Bundle) {
        if (p0 != null) {
            val code = p0.getInt(Keys.API_RETURN_KEY_CODE)
            if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                //qqmusic://qq.com/other/aidl?p={"cmd":"open","callbackurl": "qqmusicapidemo://xxx"}
                CommonCmd.openQQMusic(this@MainActivity, "qqmusicapidemo://xxx")
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                //qqmusic://qq.com/other/aidl?p={"cmd":"login","callbackurl": "qqmusicapidemo://xxx"}
                CommonCmd.loginQQMusic(this@MainActivity, "qqmusicapidemo://xxx")
            }
        }
    }

    /**
     * 绑定QQ音乐API服务
     */
    private fun bindQQMusicApiService(): Boolean {
        // 必须显式绑定
        val intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
        intent.`package` = "com.tencent.qqmusic"
        return bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)
        } catch (ignored: Throwable) {
        }
        unbindService(this)
    }

    private fun print(any: Any?) {
        runOnUiThread {
            if (any == null) {
                textView.text = "null"
            } else {
                if (any is Bundle) {
                    textView.text = any.keySet().joinToString(separator = "\n", transform = { "$it: ${any.get(it)}" })
                } else {
                    textView.text = any.toString()
                }
            }
        }
    }

    companion object {
        val ACTIONS = arrayListOf(
                "hi",
                "playSongMid",
                "playSongMidAtIndex",
                "playSongLocalPath",
                "playMusic",
                "stopMusic",
                "pauseMusic",
                "resumeMusic",
                "skipToNext",
                "skipToPrevious",
                "getPlaybackState",
                "addToFavourite",
                "removeFromFavourite",
                "openQQMusic",
                "getCurrentSong",
                "playSongId",
                "playSongIdAtIndex",
                "getTotalTime",
                "getCurrTime",
                "getPlayList",
                "getFolderList",
                "getSongList",
                "search",
                "getPlayMode",
                "setPlayMode",
                "isFavouriteMid",
                "isQQMusicForeground",
                "getLoginState",
                "seekForward",
                "seekBack",
                "voicePlay",
                "voiceShortcut"
        )
    }
}
