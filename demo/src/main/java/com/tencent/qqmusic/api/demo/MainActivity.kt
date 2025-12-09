package com.tencent.qqmusic.api.demo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.*

@SuppressLint("SetTextI18n")
/**
 * 完整接口测试
 */
class MainActivity : AppCompatActivity(), ServiceConnection {
    companion object {
        const val TAG = "MainActivity"

        val ACTIONS = Methods::class.java.methods.map { it.name }.toList()
        
        // 方法参数定义映射表
        val METHOD_PARAMS = mapOf(
            "hi" to emptyList(),
            "openQQMusic" to emptyList(),
            "playSongMid" to listOf("midList" to "StringArrayList", "callback" to "Callback"),
            "playSongMidAtIndex" to listOf("midList" to "StringArrayList", "index" to "Int", "callback" to "Callback"),
            "playSongLocalPath" to listOf("pathList" to "StringArrayList", "callback" to "Callback"),
            "playMusic" to emptyList(),
            "stopMusic" to emptyList(),
            "pauseMusic" to emptyList(),
            "resumeMusic" to emptyList(),
            "skipToNext" to emptyList(),
            "skipToPrevious" to emptyList(),
            "getPlaybackState" to emptyList(),
            "getCurrentSong" to emptyList(),
            "addToFavourite" to listOf("midList" to "StringArrayList", "callback" to "Callback"),
            "removeFromFavourite" to listOf("midList" to "StringArrayList", "callback" to "Callback"),
            "addLocalPathToFavourite" to listOf("localPathList" to "StringArrayList", "callback" to "Callback"),
            "removeLocalPathFromFavourite" to listOf("localPathList" to "StringArrayList", "callback" to "Callback"),
            "isFavouriteMid" to listOf("midList" to "StringArrayList", "idList" to "StringArrayList", "typeList" to "StringArrayList", "callback" to "Callback"),
            "isFavouriteLocalPath" to listOf("localPathList" to "StringArrayList", "callback" to "Callback"),
            "registerEventListener" to listOf("events" to "StringArrayList", "listener" to "EventListener"),
            "unregisterEventListener" to listOf("events" to "StringArrayList", "listener" to "EventListener"),
            "playSongId" to listOf("songIdList" to "StringArrayList", "callback" to "Callback"),
            "playSongIdAtIndex" to listOf("songIdList" to "StringArrayList", "index" to "Int", "callback" to "Callback"),
            "getTotalTime" to emptyList(),
            "getCurrTime" to emptyList(),
            "getPlayList" to listOf("page" to "Int", "callback" to "Callback"),
            "getFolderList" to listOf("folderId" to "String", "folderType" to "Int", "page" to "Int", "callback" to "Callback"),
            "getSongList" to listOf("folderId" to "String", "folderType" to "Int", "page" to "Int", "callback" to "Callback"),
            "search" to listOf("keyword" to "String", "searchType" to "Int", "firstPage" to "Boolean", "callback" to "Callback"),
            "getFavouriteFolderId" to emptyList(),
            "getPlayMode" to emptyList(),
            "setPlayMode" to listOf("playMode" to "Int"),
            "setPlayModeWithRet" to listOf("playMode" to "Int"),
            "playFromChorus" to listOf("fromChorus" to "Boolean"),
            "requestAuth" to listOf("encryptString" to "String", "callback" to "Callback"),
            "getUserFolderList" to listOf("openId" to "String", "openToken" to "String", "folderId" to "String", "folderType" to "Int", "page" to "Int", "callback" to "Callback"),
            "getUserSongList" to listOf("openId" to "String", "openToken" to "String", "folderId" to "String", "folderType" to "Int", "page" to "Int", "callback" to "Callback"),
            "isQQMusicForeground" to emptyList(),
            "getLoginState" to emptyList(),
            "seekForward" to listOf("time" to "Long"),
            "seekBack" to listOf("time" to "Long"),
            "getLyric" to listOf("songId" to "Long", "callback" to "Callback"),
            "getLyricWithId" to listOf("songId" to "String", "callback" to "Callback"),
            "getLyricWithIdNew" to listOf("songId" to "String", "ignoreInvalidLyric" to "Boolean", "needEndTime" to "Boolean", "callback" to "Callback"),
            "getLyricIncludeEndTime" to listOf("songId" to "String", "callback" to "Callback"),
            "voiceShortcut" to listOf("intent" to "String", "callback" to "Callback"),
            "voicePlay" to listOf("query" to "String", "slotList" to "StringArrayList", "callback" to "Callback"),
            "startPcmMode" to listOf("callback" to "Callback"),
            "stopPcmMode" to emptyList(),
            "isNewUser" to emptyList(),
            "playFolderType" to listOf("folderId" to "String", "folderType" to "Int", "index" to "Int", "callback" to "Callback"),
            "getCurrentSongIndexInList" to emptyList(),
            "seek" to listOf("seekPos" to "Long"),
            "getEncryptedUin" to emptyList(),
            "report" to listOf("reportData" to "StringArrayList", "callback" to "Callback")
        )
        
        // 示例数据
        val EXAMPLE_DATA = mapOf(
            "playSongMid" to mapOf("midList" to "0039MnYb0qxYhV\n003fA5nd3l4MGe"),
            "playSongMidAtIndex" to mapOf("midList" to "0039MnYb0qxYhV\n003fA5nd3l4MGe", "index" to "0"),
            "playSongId" to mapOf("songIdList" to "102065756\n102254061"),
            "playSongIdAtIndex" to mapOf("songIdList" to "102065756\n102254061", "index" to "0"),
            "addToFavourite" to mapOf("midList" to "0039MnYb0qxYhV"),
            "removeFromFavourite" to mapOf("midList" to "0039MnYb0qxYhV"),
            "isFavouriteMid" to mapOf("midList" to "0039MnYb0qxYhV", "idList" to "", "typeList" to ""),
            "getPlayList" to mapOf("page" to "0"),
            "getFolderList" to mapOf("folderId" to "1", "folderType" to "1", "page" to "0"),
            "getSongList" to mapOf("folderId" to "1", "folderType" to "1", "page" to "0"),
            "search" to mapOf("keyword" to "周杰伦", "searchType" to "0", "firstPage" to "true"),
            "setPlayMode" to mapOf("playMode" to "2"),
            "seekForward" to mapOf("time" to "5000"),
            "seekBack" to mapOf("time" to "5000"),
            "getLyric" to mapOf("songId" to "102065756"),
            "getLyricWithId" to mapOf("songId" to "102065756"),
            "seek" to mapOf("seekPos" to "30000"),
            "voicePlay" to mapOf("query" to "我想听周杰伦的七里香", "slotList" to "singer=周杰伦\nsong=七里香"),
            "voiceShortcut" to mapOf("intent" to "recentPlay")
        )

    }

    //service连接状态
    private val connectStateTextView by lazy { findViewById<TextView>(R.id.tv_connect_state) }

    //tv_result 显示请求结果

    //正在播放歌曲
    private val currentSongTextView by lazy { findViewById<TextView>(R.id.tv_current_song) }
    private val songListSizeTextView by lazy { findViewById<TextView>(R.id.tv_song_list_size) }

    //action
    private val actionEditText by lazy { findViewById<AutoCompleteTextView>(R.id.et_action) }
    
    // 动态参数容器
    private val paramsContainer by lazy { findViewById<LinearLayout>(R.id.params_container) }
    private val btnAddParam by lazy { findViewById<Button>(R.id.btn_add_param) }
    
    private val executeButton by lazy { findViewById<Button>(R.id.bt_execute) }
    private val executeAsyncButton by lazy { findViewById<Button>(R.id.bt_execute_async) }
    
    // 参数类型列表
    private val paramTypes = arrayOf("String", "Int", "Long", "Boolean", "StringArrayList")
    
    // 存储参数视图的列表
    private val paramViews = mutableListOf<View>()

    private val btRegister by lazy { findViewById<Button>(R.id.bt_register) }
    private val btUnregister by lazy { findViewById<Button>(R.id.bt_unregister) }
    private val tvResult by lazy { findViewById<TextView>(R.id.tv_result) }
    private val toolBar by lazy { findViewById<Toolbar>(R.id.toolbar) }

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

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolBar.setNavigationOnClickListener { onBackPressed() }

        //init actionText
        val adapter = ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line, ACTIONS)
        actionEditText.setAdapter(adapter)
        
        // 监听 action 选择，自动生成参数
        actionEditText.setOnItemClickListener { _, _, position, _ ->
            val selectedAction = adapter.getItem(position) ?: return@setOnItemClickListener
            generateParamsForAction(selectedAction)
        }
        
        // 添加参数按钮
        btnAddParam.setOnClickListener {
            addParamView()
        }
        
        // 默认为 hi 方法生成参数
        generateParamsForAction("hi")

        //init executeButton executeAsyncButton
        arrayOf(executeButton, executeAsyncButton).forEach { it.isEnabled = false }
        executeButton.setOnClickListener {
            val params = buildParamsBundle()
            Thread {
                execute(actionEditText.text.toString(), params, false)
            }.start()
        }

        executeAsyncButton.setOnClickListener {
            val params = buildParamsBundle()
            execute(actionEditText.text.toString(), params, true)
        }

        //bindQQMusicApiService
        connectStateTextView.text = "connecting..."
        val bindRet = bindQQMusicApiService(Config.BIND_PLATFORM)
        if (!bindRet) {
            connectStateTextView.text = "failed to connect"
        }

        btRegister.setOnClickListener {
            qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_SONG_FAVORITE_STATE_CHANGED), eventListener)
        }
        btUnregister.setOnClickListener {
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_SONG_FAVORITE_STATE_CHANGED), eventListener)
        }
    }

    /**
     * 根据选择的方法自动生成参数输入框
     */
    private fun generateParamsForAction(action: String) {
        // 清空现有参数
        paramsContainer.removeAllViews()
        paramViews.clear()
        
        // 获取该方法需要的参数
        val params = METHOD_PARAMS[action] ?: emptyList()
        val examples = EXAMPLE_DATA[action] ?: emptyMap()
        
        // 为每个参数创建输入框（排除 callback 和 listener）
        params.forEach { (paramName, paramType) ->
            if (paramType != "Callback" && paramType != "EventListener") {
                val exampleValue = examples[paramName] ?: ""
                addParamView(paramName, exampleValue, paramType)
            }
        }
        
        // 如果没有参数，显示提示
        if (paramViews.isEmpty()) {
            Toast.makeText(this, "该方法无需参数", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 添加一个参数输入视图
     */
    private fun addParamView(key: String = "", value: String = "", type: String = "String") {
        val paramView = LayoutInflater.from(this).inflate(R.layout.item_param, paramsContainer, false)
        
        val spinnerType = paramView.findViewById<Spinner>(R.id.spinner_param_type)
        val etKey = paramView.findViewById<EditText>(R.id.et_param_key)
        val etValue = paramView.findViewById<EditText>(R.id.et_param_value)
        val btnRemove = paramView.findViewById<Button>(R.id.btn_remove_param)
        
        // 设置类型选择器
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, paramTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter
        spinnerType.setSelection(paramTypes.indexOf(type))
        
        // 设置初始值
        etKey.setText(key)
        etValue.setText(value)
        
        // 删除按钮
        btnRemove.setOnClickListener {
            paramsContainer.removeView(paramView)
            paramViews.remove(paramView)
        }
        
        paramViews.add(paramView)
        paramsContainer.addView(paramView)
    }
    
    /**
     * 根据当前参数视图构建Bundle
     */
    private fun buildParamsBundle(): Bundle {
        return Bundle().apply {
            paramViews.forEach { paramView ->
                val spinnerType = paramView.findViewById<Spinner>(R.id.spinner_param_type)
                val etKey = paramView.findViewById<EditText>(R.id.et_param_key)
                val etValue = paramView.findViewById<EditText>(R.id.et_param_value)
                
                val key = etKey.text.toString().trim()
                val value = etValue.text.toString().trim()
                val type = spinnerType.selectedItem.toString()
                
                if (key.isEmpty()) return@forEach
                
                try {
                    when (type) {
                        "String" -> putString(key, value)
                        "Int" -> putInt(key, value.toInt())
                        "Long" -> putLong(key, value.toLong())
                        "Boolean" -> putBoolean(key, value.toBoolean())
                        "StringArrayList" -> {
                            // 支持换行分割或逗号分割
                            val list = if (value.contains("\n")) {
                                ArrayList(value.lines().filter { it.isNotEmpty() })
                            } else {
                                ArrayList(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            }
                            putStringArrayList(key, list)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "参数转换失败: key=$key, value=$value, type=$type", e)
                    Toast.makeText(this@MainActivity, "参数 $key 转换失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
                CommonCmd.loginQQMusic(this@MainActivity, packageName,Config.OPENID_APPID,"qqmusicapidemo://xxx")
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
            CommonCmd.AIDL_PLATFORM_TYPE_LITE -> {
                intent = Intent("com.tencent.qqmusiclite.third.api.QQMusicApiService")
                intent.`package` = "com.miui.player"}
            CommonCmd.AIDL_PLATFORM_TYPE_LITE_DEMO -> {
                intent = Intent("com.tencent.qqmusiclite.third.api.QQMusicApiService")
                intent.`package` = "com.miui.player_preview"}
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
                tvResult.text = "null"
            } else {
                if (result is Bundle) {
                    tvResult.text = result.toPrintableString()
                    Log.d(TAG, result.toPrintableString())
                } else {
                    tvResult.text = result.toString()
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
    return keySet().joinToString(separator = "\n", transform = { "$it: ${
        when (val value = get(it)) {
            is BooleanArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is IntArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is LongArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is FloatArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is DoubleArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is ByteArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is ShortArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is CharArray -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            is ArrayList<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ", ")
            else -> value
        }
    }" })
}