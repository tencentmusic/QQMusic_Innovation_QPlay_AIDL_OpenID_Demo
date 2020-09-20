package com.tencent.qqmusic.api.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.collections.ArrayList

@SuppressLint("SetTextI18n")
/**
 * 完整接口测试
 */
class MainActivity : Activity(), ServiceConnection {
    companion object {
        const val TAG = "MainActivity"

        val ACTIONS = Methods::class.java.methods.map { it.name }.toList()

    }
    //top bar 返回键
    private val textBack by lazy { findViewById<TextView>(R.id.text_back) }

    //service连接状态
    private val connectStateTextView by lazy { findViewById<TextView>(R.id.tv_connect_state) }

    //tv_result 显示请求结果

    //正在播放歌曲
    private val currentSongTextView by lazy { findViewById<TextView>(R.id.tv_current_song) }
    private val songListSizeTextView by lazy { findViewById<TextView>(R.id.tv_song_list_size) }

    //action
    private val actionEditText by lazy { findViewById<AutoCompleteTextView>(R.id.et_action) }
    //action参数key
    private val paramsKeyEditText by lazy { findViewById<EditText>(R.id.et_params_key) }
    //action参数value
    private val paramsValueEditText by lazy { findViewById<EditText>(R.id.et_params_value) }

    private val executeButton by lazy { findViewById<Button>(R.id.bt_execute) }
    private val executeAsyncButton by lazy { findViewById<Button>(R.id.bt_execute_async) }

    private val eventListener = object : IQQMusicApiEventListener.Stub() {
        override fun onEvent(event: String, extra: Bundle) {
            Log.d(TAG, "event:$event,${extra.toPrintableString()}")

            runOnUiThread {
                if (event == Events.API_EVENT_PLAY_SONG_CHANGED) {
                    currentSongTextView.text = "歌曲信息： ".plus(extra.getString(Keys.API_EVENT_KEY_PLAY_SONG))
                } else if (event == Events.API_EVENT_PLAY_LIST_CHANGED) {
                    val size = extra.getInt(Keys.API_EVENT_KEY_PLAY_LIST_SIZE)
                    songListSizeTextView.text = "歌曲数量： $size"
                }
            }
        }
    }

    private var qqmusicApi: IQQMusicApi? = null

    override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
        // 绑定成功
        qqmusicApi = IQQMusicApi.Stub.asInterface(p1)
        // 可选：注册事件回调
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)

        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = true }
        connectStateTextView.text = "连接状态: connected"
    }

    override fun onServiceDisconnected(p0: ComponentName) {
        // 失去连接，可能QQ音乐退出了
        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = false }
        connectStateTextView.text = "连接状态: disconnected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init actionText 随着action改变 设置key value的值
        val adapter = ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, ACTIONS)
        actionEditText.setAdapter(adapter)
        actionEditText.setOnItemClickListener { adapterView: AdapterView<*>, view1: View, i: Int, l: Long ->
            when (actionEditText.text.toString()) {
                "getFolderList" -> {
                    paramsKeyEditText.setText("folderId,folderType,page")
                    paramsValueEditText.setText("0\n0\n0")
                }
                "getSongList" -> {
                    paramsKeyEditText.setText("folderId,folderType,page")
                    paramsValueEditText.setText("201|1\n101\n0")
                }
                "playSongId" -> {
                    paramsKeyEditText.setText("songIdList")
                    paramsValueEditText.setText("108756239|0")
                }
                "search" -> {
                    paramsKeyEditText.setText("keyword,searchType,firstPage")
                    paramsValueEditText.setText("周杰伦\n0\ntrue")
                }
                "setPlayMode" -> {
                    paramsKeyEditText.setText("playMode")
                    paramsValueEditText.setText("0")
                }
                "voicePlay" -> {
                    paramsKeyEditText.setText("query;slotList")
                    paramsValueEditText.setText("我想听周杰伦的七里香\nsinger=周杰伦\nsong=七里香")
                }
                "voiceShortcut" -> {
                    paramsKeyEditText.setText("intent")
                    paramsValueEditText.setText("favorite")
                }
            }
        }

        //init executeButton executeAsyncButton
        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = false }
        executeButton.setOnClickListener {
            val params = Bundle().apply {//字符串转成bundle，key通过,分割 value通过行数
                if (paramsKeyEditText.text.toString().contains(",")) {
                    val params = paramsKeyEditText.text.toString().split(",")
                    for (i in params.indices) {
                        if (params[i] == "folderType" || params[i] == "page" || params[i] == "searchType") {
                            putInt(params[i], paramsValueEditText.text.lines()[i].toInt())
                        } else {
                            putString(params[i], paramsValueEditText.text.lines()[i])
                        }
                    }
                } else {
                    if (paramsKeyEditText.text.toString() == "page" || paramsKeyEditText.text.toString() == "playMode") {
                        putInt(paramsKeyEditText.text.toString(), paramsValueEditText.text.lines()[0].toInt())
                    } else if (paramsKeyEditText.text.toString() == "time") {
                        putLong(paramsKeyEditText.text.toString(), paramsValueEditText.text.lines()[0].toLong())
                    } else {
                        putStringArrayList(paramsKeyEditText.text.toString(), ArrayList(paramsValueEditText.text.lines()))
                    }
                }
                //tv端需要添加from key
                putLong("from", 1)
            }
            execute(actionEditText.text.toString(), params, false)
        }

        executeAsyncButton.setOnClickListener {
            val params = Bundle().apply {
                if (paramsKeyEditText.text.toString().contains(",")) {
                    val params = paramsKeyEditText.text.toString().split(",")
                    for (i in params.indices) {
                        if (params[i] == "folderType" || params[i] == "page" || params[i] == "searchType") {
                            putInt(params[i], paramsValueEditText.text.lines()[i].toInt())
                        } else if (params[i] == "firstPage") {
                            putBoolean(params[i], paramsValueEditText.text.lines()[i].toBoolean())
                        } else {
                            putString(params[i], paramsValueEditText.text.lines()[i])
                        }
                    }
                } else if(paramsKeyEditText.text.toString().contains(";")) {
                    val params = paramsKeyEditText.text.toString().split(";")
                    val slotList = ArrayList<String>()
                    for (i in params.indices) {
                        if (params[i] == "query") {
                            putString(params[i], paramsValueEditText.text.lines()[i])
                        } else {
                            slotList.add(paramsValueEditText.text.lines()[i])
                            slotList.add(paramsValueEditText.text.lines()[i+1])
                        }
                    }
                    putStringArrayList(params[1], slotList)
                } else {
                    if (paramsKeyEditText.text.toString() == "page") {
                        putInt(paramsKeyEditText.text.toString(), paramsValueEditText.text.lines()[0].toInt())
                    } else if(paramsKeyEditText.text.toString() == "intent") {
                        putString(paramsKeyEditText.text.toString(), paramsValueEditText.text.lines()[0])
                    } else {
                        putStringArrayList(paramsKeyEditText.text.toString(), ArrayList(paramsValueEditText.text.lines()))
                    }
                }
                //tv端需要添加from key
                putLong("from", 1)
            }
            execute(actionEditText.text.toString(), params, true)
        }

        //bindQQMusicApiService
        connectStateTextView.text = "connecting..."
        val bindRet = bindQQMusicApiService(Config.BIND_PLATFORM)
        if (!bindRet) {
            connectStateTextView.text = "failed to connect"
        }

        //init top bar
        textBack.visibility = View.VISIBLE
        textBack.setOnClickListener {
            this@MainActivity.finish()
        }
        bt_register.setOnClickListener {
            qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_SONG_FAVORITE_STATE_CHANGED), eventListener)
        }
        bt_unregister.setOnClickListener {
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_SONG_FAVORITE_STATE_CHANGED), eventListener)
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
        Log.d(TAG,"executing [$action]")
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
                Log.d(TAG, "commonOpen: CommonCmd.verifyCallerIdentity")

                val time = System.currentTimeMillis()
                val nonce = time.toString()
                val encryptString = OpenIDHelper.getEncryptString(nonce)
                CommonCmd.verifyCallerIdentity(this, Config.OPENID_APPID, packageName, encryptString, "qqmusicapidemo://xxx")
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                Log.d(TAG, "commonOpen: CommonCmd.loginQQMusic")
                //qqmusic://qq.com/other/aidl?p={"cmd":"login","callbackurl": "qqmusicapidemo://xxx"}
                CommonCmd.loginQQMusic(this@MainActivity, "qqmusicapidemo://xxx")
            }
        }
    }

    /**
     * 绑定QQ音乐API服务
     */
    private fun bindQQMusicApiService(platform:String): Boolean {
        var intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
        // 必须显式绑定
        when(platform){
            CommonCmd.AIDL_PLATFORM_TYPE_PHONE -> {
                intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusic"}
            CommonCmd.AIDL_PLATFORM_TYPE_CAR -> {
                intent = Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusiccar"}
            CommonCmd.AIDL_PLATFORM_TYPE_TV -> {
                intent = Intent("com.tencent.qqmusictv.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusictv"}
            else -> {
                Log.e(TAG,"platform error!",RuntimeException())
            }
        }
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

    private fun print(result: Any?) {
        runOnUiThread {
            if (result == null) {
                tv_result.text = "null"
            } else {
                if (result is Bundle) {
                    tv_result.text = result.toPrintableString()
                    Log.d(TAG, result.toPrintableString())
                } else {
                    tv_result.text = result.toString()
                    Log.d(TAG, result.toString())
                }
            }
        }
    }

}

/**
 * 每一个key对应字符串使用"\n"分割，并以key:value格式输出
 */
fun Bundle.toPrintableString(): String {
    return keySet().joinToString(separator = "\n", transform = { "$it: ${get(it)}" })
}