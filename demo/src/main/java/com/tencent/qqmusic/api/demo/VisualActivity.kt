package com.tencent.qqmusic.api.demo

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tencent.qqmusic.api.demo.Config.*
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.*
import com.tencent.qqmusic.third.api.contract.CommonCmd.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

/*
* 可视化的Api调用Demo，如需查看完整调用，参考MainActivity
* */
class VisualActivity : AppCompatActivity(), ServiceConnection {
    companion object{
        const val TAG = "VisualActivity"
    }
    private var isBindQQMusicService = false
    private var openId: String? = null
    private var openToken: String? = null

    private val textMore by lazy { findViewById<TextView>(R.id.text_more) }
    private val txtResult by lazy { findViewById<TextView>(R.id.txtResult) }
    private val txtSongInfos by lazy { findViewById<TextView>(R.id.txtSongInfos) }
    private val txtPlayTime by lazy { findViewById<TextView>(R.id.txtPlayTime) }
    private val songPic by lazy { findViewById<ImageView>(R.id.SongPic) }
    private val progressPlay by lazy { findViewById<ProgressBar>(R.id.ProgressPlay) }
    private val txtAlbum by lazy { findViewById<TextView>(R.id.txtAlbum) }
    private val btnPlayPause by lazy { findViewById<Button>(R.id.btnPlayPause) }
    private val btnLove by lazy { findViewById<Button>(R.id.btnLove) }
    private val btnOpiSearch by lazy { findViewById<Button>(R.id.btnOpiSearch) }
    private val btnOpiMvTag by lazy { findViewById<Button>(R.id.btnOpiMvTag) }
    private val folderListView by lazy { findViewById<ListView>(R.id.listview_folder) }
    private val songListView by lazy { findViewById<ListView>(R.id.listview_song) }

    private var qqmusicApi: IQQMusicApi? = null

    private var folderAdapter: FolderListAdapter? = null
    private var songAdapter: SongListAdapter? = null
    private val pathStack: Stack<Data.FolderInfo> = Stack()

    private val curFolderlist: ArrayList<Data.FolderInfo> = ArrayList()//FolderListView数据
    private var curSonglist: ArrayList<Data.Song> = ArrayList()//SongListView数据
    private var curFolder: Data.FolderInfo? = null
    private var curPlayState: Int = 0
    private var curPlaySong: Data.Song? = null
    private var curPlayTime: Long = 0
    private var totalPlayTime: Long = 0

    var m_OpenAPIAppID = ""
    var m_OpenAPIAppKey = ""
    var m_OpenAPIAppPrivateKey = ""

    private val gson = Gson()
    private var isDataInited: Boolean = false
    private var progressTimer: Timer? = null

    private val backId: Int = -10000
    private var backFolder: Data.FolderInfo? = null
    private var backSong: Data.Song? = null
    //private  val MSG_BIND_LOOPER: Int = 11

    private val handler :Handler by lazy { Handler() }

    private fun tryBindQQMusicApiServiceRecursively() {
        val bindRet = bindQQMusicApiService(BIND_PLATFORM)
        Log.i(TAG, "tryBindQQMusicApiService:$bindRet")
        if (!bindRet) {
            handler.postDelayed(::tryBindQQMusicApiServiceRecursively, 200)
        }
    }

    private var activeBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            val ret = p1?.extras?.get("ret")
            if (ret == "0") {
                Log.d(TAG,"activeBroadcastReceiver 授权成功")
                initData()
            } else {
                Log.d(TAG,"activeBroadcastReceiver 授权失败($ret)")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visual)

        initFolderListView()

        initSongListView()

        backFolder = Data.FolderInfo()
        backFolder?.type = backId
        backFolder?.mainTitle = ".. 返回上一级"

        backSong = Data.Song()
        backSong?.id = backId.toString()
        backSong?.title = ".. 返回上一级"

        //更多 popup window
        textMore.visibility = VISIBLE
        textMore.setOnClickListener {
            initMorePopupWindow()
        }

        //register activeBroadcastReceiver
        val filter = IntentFilter()
        filter.addAction("callback_verify_notify")
        registerReceiver(activeBroadcastReceiver, filter)

        val btnActive = findViewById<TextView>(R.id.btnActive)
        btnActive.setOnClickListener { onActiveClick(it) }
    }

    private fun initFolderListView() {
        folderAdapter = FolderListAdapter(this, curFolderlist)
        folderListView.adapter = folderAdapter
        //点击处理
        folderListView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            if (position >= curFolderlist.size) {
                return@OnItemClickListener
            }
            val folder = curFolderlist[position]

            if (folder.type == backId) {
                onBackClick(view)
                return@OnItemClickListener
            }

            var needPush = true
            if (!pathStack.isEmpty()) {
                val stackFolder = pathStack.peek()
                if (stackFolder.id == curFolder?.id && stackFolder.type == curFolder?.type) {
                    needPush = false
                }
            }
            if (needPush)
                pathStack.push(curFolder)

            curFolder = folder
            if (folder.isSongFolder) {
                getSongList(folder, 0)
            } else {
                if (folder != null)
                    getFolderList(folder, 0)
            }
        }
    }

    private fun initSongListView() {
        songAdapter = SongListAdapter(this)
        songListView.adapter = songAdapter
        songListView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            val song = curSonglist[position]

            if (song.id == backId.toString()) {
                onBackClick(view)
            } else {
                val songlist = curSonglist.subList(1, curSonglist.size - 1)
                playSonglist(songlist, song)
            }
        }
    }

    /**
     * 授权按钮，绑定QQ音乐服务，失败时尝试启动QQ音乐进程，再不断进行重试
     */
    private fun onActiveClick(view: View) {
        val bindRet = bindQQMusicApiService(BIND_PLATFORM)
        if (!bindRet) {
            Log.d(TAG,"bind失败")
            txtResult.text = "连接QQ音乐失败"

            //启动QQ音乐进程
            startQQMusicProcess()

            tryBindQQMusicApiServiceRecursively()
        } else {
            sayHiAndInitData()
        }
    }

    private fun startQQMusicProcess() {
        CommonCmd.startQQMusicProcess(this, packageName)
    }

    private fun verifyCallerRequest() {
        val time = System.currentTimeMillis()
        val nonce = time.toString()
        val encryptString = OpenIDHelper.getEncryptString(nonce)
        CommonCmd.verifyCallerIdentity(this, Config.OPENID_APPID, packageName, encryptString, "qqmusicapidemo://xxx")
    }

    /**
     *  绑定服务成功回调
     */
    override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
        Log.d(TAG,"bind成功")
        txtResult.text = "已连接QQ音乐"

        qqmusicApi = IQQMusicApi.Stub.asInterface(p1)
        isBindQQMusicService = true
        CommonCmd.init(BIND_PLATFORM)
        sayHiAndInitData()
    }

    override fun onServiceDisconnected(p0: ComponentName) {
        // 失去连接，可能QQ音乐退出了
        txtResult.text = "和QQ音乐断开连接"
        isBindQQMusicService = false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)
        } catch (ignored: Throwable) {
        }
        if (isBindQQMusicService) {
            unbindService(this)
        }
        unregisterReceiver(activeBroadcastReceiver)
    }

    private fun sayHiAndInitData() {
        if(!isBindQQMusicService)
            return

        val bundle = Bundle()
        bundle.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
        bundle.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, CommonCmd.AIDL_PLATFORM_TYPE_PHONE)

        val result = qqmusicApi?.execute("hi", bundle)
        Log.d(TAG, "sayHi ret:" + result?.getInt(Keys.API_RETURN_KEY_CODE))

        if (commonOpen(result)) {
            initData()
        }
    }

    /**
     * 绑定QQ音乐API服务
     */
    private fun bindQQMusicApiService(platform:String): Boolean {
        var intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
        // 必须显式绑定
        when(platform){
            AIDL_PLATFORM_TYPE_PHONE -> {
                intent = Intent("com.tencent.qqmusic.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusic"}
            AIDL_PLATFORM_TYPE_CAR -> {
                intent = Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusiccar"}
            AIDL_PLATFORM_TYPE_TV -> {
                intent = Intent("com.tencent.qqmusictv.third.api.QQMusicApiService")
                intent.`package` = "com.tencent.qqmusictv"}
            else -> {
                Log.e(TAG,"platform error!",RuntimeException())
            }
        }
        return bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun initData() {
        Log.d(TAG,"initData")

        // 可选：注册事件回调
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)

        //获取根目录
        val rootFolder = Data.FolderInfo()
        rootFolder.id = ""
        rootFolder.type = 0
        rootFolder.isSongFolder = false
        curFolder = rootFolder
        getFolderList(rootFolder, 0)

        //同步当前的播放信息
        syncCurrentPlayInfo()
        startProgressTimer()
        isDataInited = true
    }

    //QQ音乐事件回调
    private val eventListener = object : IQQMusicApiEventListener.Stub() {
        override fun onEvent(event: String, extra: Bundle) {
            Log.d(TAG,"onEvent$event extra:$extra")

            runOnUiThread {
                when (event) {
                    Events.API_EVENT_PLAY_SONG_CHANGED -> {
//                        var songJson = extra.getString(Keys.API_EVENT_KEY_PLAY_SONG)
//                        curPlaySong = gson.fromJson(songJson, Data.Song::class.java)
                        syncCurrentPlayInfo()
                    }
                    Events.API_EVENT_PLAY_LIST_CHANGED -> {
                        var size = extra.getInt(Keys.API_EVENT_KEY_PLAY_LIST_SIZE)
                    }
                    Events.API_EVENT_PLAY_STATE_CHANGED -> {
                        curPlayState = extra.getInt(Keys.API_EVENT_KEY_PLAY_STATE)
                        setPlayStateText()
                    }
                }
            }
        }

    }


    fun onPlayPre(view: View) {
        Log.d(TAG, "onPlayPre")

        val result = qqmusicApi?.execute("skipToPrevious", null)
        val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
        if (errorCode != ErrorCodes.ERROR_OK) {
            Log.d(TAG,"上一首失败($errorCode)")
        }
    }

    fun onPlayNext(view: View) {
        Log.d(TAG, "onPlayNext")

        val result = qqmusicApi?.execute("skipToNext", null)
        val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
        if (errorCode != ErrorCodes.ERROR_OK) {
            Log.d(TAG,"下一首失败($errorCode)")
        }
    }

    fun onPlayPause(view: View) {
        if (curPlaySong == null)
            return

        if (isPlaying()) {
            val result = qqmusicApi?.execute("pauseMusic", null)
            val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                Log.d(TAG,"暂停音乐失败($errorCode)")
            }
        } else {
            val result = qqmusicApi?.execute("playMusic", null)
            val errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                Log.d(TAG,"开始播放音乐失败($errorCode)")
            }
        }

    }

    fun onBackClick(view: View) {
        if (pathStack.empty()) {
            var rootFolder = Data.FolderInfo()
            rootFolder.id = ""
            rootFolder.type = 0
            rootFolder.isSongFolder = false
            getFolderList(rootFolder, 0)
            return
        }
        var folder = pathStack.pop()
        curFolder = folder
        if (folder.isSongFolder) {
            getSongList(folder, 0)
        } else {
            getFolderList(folder, 0)
        }

    }

    fun onLoveClick(view: View) {
        val midList = ArrayList<String>()
        midList.add(curPlaySong?.mid ?: "")
        val params = Bundle()
        params.putStringArrayList("midList", midList)

        if (btnLove.text == "收藏") {
            qqmusicApi?.executeAsync("addToFavourite", params, object : IQQMusicApiCallback.Stub() {
                override fun onReturn(result: Bundle) {
                    // 回调的结果
                    commonOpen(result)
                    val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                    if (code == ErrorCodes.ERROR_OK) {
                        runOnUiThread {
                            setLoveStateText(true)
                        }
                    } else {
                        print("添加收藏失败（$code)")
                    }
                }
            })
        } else {
            qqmusicApi?.executeAsync("removeFromFavourite", params, object : IQQMusicApiCallback.Stub() {
                override fun onReturn(result: Bundle) {
                    // 回调的结果
                    commonOpen(result)
                    val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                    if (code == ErrorCodes.ERROR_OK) {
                        runOnUiThread {
                            setLoveStateText(false)
                        }
                    } else {
                        print("取消收藏失败（$code)")
                    }
                }
            })
        }


    }

    private fun commonOpen(returnData: Bundle?): Boolean {
        if (returnData != null) {
            val code = returnData.getInt(Keys.API_RETURN_KEY_CODE)
            if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                Log.d(TAG, "commonOpen: CommonCmd.verifyCallerIdentity 进行OpenID权限验证")
                verifyCallerRequest()
                return false
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                Log.d(TAG, "commonOpen: CommonCmd.loginQQMusic 请求用户登录")
                CommonCmd.loginQQMusic(this@VisualActivity, "qqmusicapidemo://xxx")
                return false
            } else if (code == ErrorCodes.ERROR_API_NOT_INITIALIZED) {
                Log.d(TAG, "commonOpen: ERROR_API_NOT_INITIALIZED")
                return false
            }
            Log.d(TAG, "commonOpen:正常")
            return true
        }
        return false
    }

    private fun getFolderList(folder: Data.FolderInfo, page: Int) {
        Log.d(TAG, "[getFolderList] 获取歌单... ${folder.id},${folder.type}")

        runOnUiThread {
            curSonglist.clear()
            songAdapter?.notifyDataSetChanged()
            songListView.visibility = GONE
            folderListView.visibility = VISIBLE
        }

        val isUserFolder = isUserFolder(folder.type)
        if (isUserFolder) {
            //openId或openToken，使用requestAuth获取
            if (openId.isNullOrEmpty() || openToken.isNullOrEmpty()) {
                startAIDLAuth { success ->
                    if (success)
                        getUserFolderList(folder, page)
                }
            } else {
                //获取用户歌单
                getUserFolderList(folder, page)
            }
        } else {
            //获取普通歌单
            getNormalFolderList(folder, page)
        }
    }

    //获取普通歌单
    private fun getNormalFolderList(folder: Data.FolderInfo, page: Int) {
        val params = Bundle()
        params.putString("folderId", folder.id ?: "")
        params.putInt("folderType", folder.type)
        params.putInt("page", page)
        Log.d(TAG, "[getNormalFolderList] executeAsync getFolderList")
        qqmusicApi?.executeAsync("getFolderList", params, object : IQQMusicApiCallback.Stub() {

            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    val dataJson = result.getString(Keys.API_RETURN_KEY_DATA)

                    val array = JsonParser().parse(dataJson).asJsonArray
                    val tmpList = ArrayList<Data.FolderInfo>()
                    if (folder.type != 0)
                        backFolder?.let { tmpList.add(it) }
                    for (elem in array) {
                        val folder = gson.fromJson(elem, Data.FolderInfo::class.java)
                        tmpList.add(folder)
                    }

                    print("获取歌单成功（${tmpList.size})")
                    runOnUiThread {
                        curFolderlist.clear()
                        tmpList.forEach {
                            curFolderlist.add(it)
                        }
                        folderAdapter?.notifyDataSetChanged()
                    }
                } else {
                    print("获取歌单失败（$code)")
                }

            }
        })
    }

    //获取用户歌单
    private fun getUserFolderList(folder: Data.FolderInfo, page: Int) {
        if (this.openId.isNullOrEmpty() || this.openToken.isNullOrEmpty())
            return

        val params = Bundle()
        params.putString("openId", this.openId)
        params.putString("openToken", this.openToken)
        params.putString("folderId", folder.id ?: "")
        params.putInt("folderType", folder.type)
        params.putInt("page", page)

        Log.d(TAG, "[getUserFolderList] executeAsync getUserFolderList")
        qqmusicApi?.executeAsync("getUserFolderList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    val dataJson = result.getString(Keys.API_RETURN_KEY_DATA)

                    val array = JsonParser().parse(dataJson).asJsonArray
                    val tmpList = ArrayList<Data.FolderInfo>()
                    if (folder.type != 0)
                        backFolder?.let { tmpList.add(it) }
                    for (elem in array) {
                        val folder = gson.fromJson(elem, Data.FolderInfo::class.java)
                        tmpList.add(folder)
                    }

                    Log.d(TAG,"获取歌单成功（${tmpList.size})")
                    runOnUiThread {
                        curFolderlist.clear()
                        tmpList.forEach {
                            curFolderlist.add(it)
                        }
                        folderAdapter?.notifyDataSetChanged()
                    }
                } else {
                    Log.d(TAG,"获取歌单失败（$code)")
                }

            }
        })
    }


    private fun getSongList(folder: Data.FolderInfo, page: Int) {

        val isUserFolder = this.isUserFolder(folder.type)
        if (isUserFolder) {
            //用户歌曲使用新API获取
            if (this.openId.isNullOrEmpty() || this.openToken.isNullOrEmpty()) {
                startAIDLAuth { success ->
                    if (success)
                        getUserSongList(folder, page)
                }
                return
            }
            getUserSongList(folder, page)
        } else {
            getNormalSongList(folder, page)
        }

    }

    //获取普通歌曲列表
    private fun getNormalSongList(folder: Data.FolderInfo, page: Int) {
        val params = Bundle()
        params.putString("folderId", folder.id ?: "")
        params.putInt("folderType", folder.type)
        params.putInt("page", page)
        print("获取歌曲列表... ${folder.id},${folder.type}")

        runOnUiThread {
            curFolderlist.clear()
            folderAdapter?.notifyDataSetChanged()
            songListView.visibility = VISIBLE
            folderListView.visibility = GONE
        }

        qqmusicApi?.executeAsync("getSongList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    var dataJson = result.getString(Keys.API_RETURN_KEY_DATA)
                    val array = JsonParser().parse(dataJson).asJsonArray
                    curSonglist.clear()
                    backSong?.let { curSonglist.add(it) }
                    for (elem in array) {
                        var song = gson.fromJson(elem, Data.Song::class.java)
                        curSonglist.add(song)
                    }
                    print("获取歌曲列表成功（${curSonglist.size})")
                    runOnUiThread { songAdapter?.notifyDataSetChanged() }
                } else {
                    print("获取歌曲列表失败（$code)")
                }

            }
        })
    }

    //获取用户歌曲列表
    private fun getUserSongList(folder: Data.FolderInfo, page: Int) {
        val params = Bundle()
        params.putString("openId", this.openId)
        params.putString("openToken", this.openToken)
        params.putString("folderId", folder.id ?: "")
        params.putInt("folderType", folder.type)
        params.putInt("page", page)
        print("获取歌曲列表... ${folder.id},${folder.type}")

        runOnUiThread {
            curFolderlist.clear()
            folderAdapter?.notifyDataSetChanged()
            songListView.visibility = VISIBLE
            folderListView.visibility = GONE
        }

        qqmusicApi?.executeAsync("getUserSongList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    var dataJson = result.getString(Keys.API_RETURN_KEY_DATA)
                    val array = JsonParser().parse(dataJson).asJsonArray
                    curSonglist.clear()
                    backSong?.let { curSonglist.add(it) }
                    for (elem in array) {
                        var song = gson.fromJson(elem, Data.Song::class.java)
                        curSonglist.add(song)
                    }
                    print("获取歌曲列表成功（${curSonglist.size})")
                    runOnUiThread { songAdapter?.notifyDataSetChanged() }
                } else {
                    print("获取歌曲列表失败（$code)")
                }

            }
        })
    }

    //通过SongId播放歌曲列表
    private fun playSonglist(songList: List<Data.Song>, song: Data.Song) {
        var idList = ArrayList<String>()
        var curIndex = 0
        for (i in songList.indices) {
            idList.add(songList[i].id)
            if (song.id == songList[i].id)
                curIndex = i
        }
        if (curIndex > 0) {
            var subList = idList.subList(0, curIndex).toList()
            try {
                idList.removeAll(subList)
                idList.addAll(subList)
            } catch (e: Exception) {
                print(e)
            }

        }

        val params = Bundle()
        params.putStringArrayList("songIdList", idList)
        print("播放歌曲列表... ${song.title},${songList.size}")
        qqmusicApi?.executeAsync("playSongId", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    runOnUiThread { syncCurrentPlayInfo() }
                } else {
                    print("播放歌曲列表失败（$code)")
                }
            }
        })
    }

    //通过SongId播放歌曲列表
    private fun playSonglistAtIndex(songList: List<Data.Song>, index: Int) {
        var idList = ArrayList<String>()
        for (i in songList.indices) {
            idList.add(songList[i].id)
        }

        val params = Bundle()
        params.putStringArrayList("songIdList", idList)
        params.putInt("index", index)
        print("播放歌曲列表... $index,${songList.size}")
        qqmusicApi?.executeAsync("playSongIdAtIndex", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    runOnUiThread { syncCurrentPlayInfo() }
                } else {
                    print("播放歌曲列表失败（$code)")
                }
            }
        })
    }

    //通过Mid播放歌曲列表
    private fun playSongMidAtIndex(songList: List<Data.Song>, index: Int) {
        var midList = ArrayList<String>()
        for (i in songList.indices) {
            midList.add(songList[i].mid)
        }

        val params = Bundle()
        params.putStringArrayList("midList", midList)
        params.putInt("index", index)
        print("播放歌曲列表... $index,${songList.size}")
        qqmusicApi?.executeAsync("playSongMidAtIndex", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    runOnUiThread { syncCurrentPlayInfo() }
                } else {
                    print("播放歌曲列表失败（$code)")
                }
            }
        })
    }

    //同步当前播放信息
    private fun syncCurrentPlayInfo() {
        Log.d(TAG, "syncCurrentPlayInfo")
        //getCurrentSong
        var result = qqmusicApi?.execute("getCurrentSong", null)
        val curSongJson = result?.getString(Keys.API_RETURN_KEY_DATA)
        this.curPlaySong = gson.fromJson(curSongJson, Data.Song::class.java)
        if (curPlaySong == null)
            return
        Log.d(TAG, "curPlaySong:${curPlaySong?.title}")

        txtSongInfos.text = curPlaySong?.title
        txtAlbum.text = curPlaySong?.album?.title + " - " + curPlaySong?.singer?.title
        if (!curPlaySong?.album?.coverUri.isNullOrEmpty()) {
            setSongUrlImage(curPlaySong?.album?.coverUri ?: "")
        }

        //getPlaybackState
        result = qqmusicApi?.execute("getPlaybackState", null)
        this.curPlayState = result?.getInt(Keys.API_RETURN_KEY_DATA) ?: 0

        //getCurrTime
        result = qqmusicApi?.execute("getCurrTime", null)
        curPlayTime = (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000

        //getTotalTime
        result = qqmusicApi?.execute("getTotalTime", null)
        totalPlayTime = (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000

        txtPlayTime.text = "$curPlayTime/$totalPlayTime"

        //使用isFavouriteMid判断curPlaySong
        val midList = ArrayList<String>()
        midList.add(curPlaySong?.mid ?: "")
        val params = Bundle()
        params.putStringArrayList("midList", midList)
        qqmusicApi?.executeAsync("isFavouriteMid", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                Log.d(TAG, "isFavouriteMid onReturn")
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                if (code == ErrorCodes.ERROR_OK) {
                    val boolArray = result.getBooleanArray(Keys.API_RETURN_KEY_DATA)
                    if (boolArray != null && boolArray.isNotEmpty()) {
                        runOnUiThread { setLoveStateText(boolArray[0]) }
                        print("获取收藏状态成功")
                    }
                } else {
                    print("获取收藏状态失败（$code)")
                }
            }
        })

        setPlayStateText()
    }

    private fun setPlayStateText() {
        var isPlayingNow = isPlaying()
        btnPlayPause.text = if (isPlayingNow) "暂停" else "播放"
    }

    private fun setLoveStateText(isLove: Boolean) {
        btnLove.text = if (isLove) "取消收藏" else "收藏"
    }

    private fun isPlaying(): Boolean {
        if (curPlaySong == null)
            return false
        val isPlayingNow = (curPlayState == PlayState.STARTED
                || curPlayState == PlayState.INITIALIZED
                || curPlayState == PlayState.PREPARED
                || curPlayState == PlayState.PREPARING
                || curPlayState == PlayState.BUFFERING)
        return isPlayingNow
    }

    //AIDL方式请求授权
    private fun startAIDLAuth(finishBlock: ((success: Boolean) -> Unit)) {
        val time = System.currentTimeMillis()
        val nonce = time.toString()
        val encryptString = OpenIDHelper.getEncryptString(nonce) //解密&加密

        val params = Bundle()
        params.putString(Keys.API_RETURN_KEY_ENCRYPT_STRING, encryptString)
        Log.d(TAG,"[startAIDLAuth] executeAsync requestAuth")
        qqmusicApi?.executeAsync("requestAuth", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                commonOpen(result)
                var authOK = false
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                if (code == ErrorCodes.ERROR_OK) {
                    //解密
                    val qmEncryptString = result.getString(Keys.API_RETURN_KEY_ENCRYPT_STRING)
                    val qmDecryptString = OpenIDHelper.decryptQQMEncryptString(qmEncryptString)
                    if (qmDecryptString != null) {
                        val appParser = JSONTokener(qmDecryptString)
                        val appDecryptJson = appParser.nextValue() as JSONObject
                        val sign = appDecryptJson.getString(Keys.API_RETURN_KEY_SIGN)
                        val nonce = appDecryptJson.getString(Keys.API_RETURN_KEY_NONCE)
                        //检查签名
                        if (OpenIDHelper.checkQMSign(sign, nonce)) {
                            authOK = true
                            openId = appDecryptJson.getString(Keys.API_RETURN_KEY_OPEN_ID)
                            openToken = appDecryptJson.getString(Keys.API_RETURN_KEY_OPEN_TOKEN)
                            var expireTime = appDecryptJson.getString(Keys.API_PARAM_KEY_SDK_EXPIRETIME)
                            Log.d(TAG,"授权成功 票据：$openId,$openToken")
                        }
                    }
                    if (!authOK) {
                        Log.d(TAG,"授权失败")
                    }
                } else {
                    Log.d(TAG,"授权失败（$code)")
                }

                finishBlock(authOK)
            }
        })
    }

    private fun isUserFolder(folderType: Int): Boolean {
        return Data.FolderType.MY_FOLDER == folderType || folderType == Data.FolderType.MY_FOLDER_SONG_LIST
    }

    private fun print(any: Any?) {
        runOnUiThread {
            if (any == null) {
                txtResult.text = ""
            } else {
                if (any is Bundle) {
                    txtResult.text = any.keySet().joinToString(separator = "\n", transform = { "$it: ${any.get(it)}" })
                } else {
                    txtResult.text = any.toString()
                }
            }
        }
    }

    private fun setSongUrlImage(url: String) {
        Thread(Runnable {
            val bmp = getURLimage(url)
            if (bmp != null) {
                val msg = Message()
                msg.what = 1
                msg.obj = bmp
                handle.sendMessage(msg)
            }
        }).start()
    }

    //加载图片
    private fun getURLimage(url: String): Bitmap? {
        var bmp: Bitmap? = null
        try {
            val imgUrl = URL(url)
            val conn = imgUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.doInput = true
            conn.useCaches = false
            conn.connect()
            bmp = BitmapFactory.decodeStream(conn.inputStream)
            conn.inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return bmp
    }

    private fun startProgressTimer() {
        stopProgressTimer()
        if (progressTimer == null)
            progressTimer = Timer()
        progressTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (curPlayState == PlayState.STARTED) {
                    if (curPlayTime < totalPlayTime)
                        curPlayTime += 1
                    val message = Message()
                    message.what = 2
                    handle.sendMessage(message)
                }
            }
        }, 1000, 1000/* 表示1000毫秒之後，每隔1000毫秒執行一次 */)
    }

    private fun stopProgressTimer() {
        if (progressTimer != null) {
            progressTimer?.cancel()
            progressTimer = null
        }
    }


    fun getSign(unixTime: Long): String {
        var signStr = "OpitrtqeGzopIlwxs_" + m_OpenAPIAppID + "_" + m_OpenAPIAppKey + "_" + m_OpenAPIAppPrivateKey + "_" + unixTime

        try {
            val instance: MessageDigest = MessageDigest.getInstance("MD5")
            val digest: ByteArray = instance.digest(signStr.toByteArray())
            var sb = StringBuffer()
            for (b in digest) {
                var i: Int = b.toInt() and 0xff
                var hexString = Integer.toHexString(i)
                if (hexString.length < 2) {
                    hexString = "0" + hexString
                }
                sb.append(hexString)
            }
            return sb.toString()

        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return ""
    }

    fun onClickOpiSearch(view: View) {
        if (openId.isNullOrEmpty() || openToken.isNullOrEmpty()) {
            Toast.makeText(this, "请先获取授权", Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "onClickOpiSearch openid:" + openId + "  openToken:" + openToken)
        val time = System.currentTimeMillis()
        val timeStamp = time / 1000
        var searchUrl = String.format("http://cd.y.qq.com/ext-internal/fcgi-bin/music_open_api.fcg?opi_cmd=fcg_music_custom_search.fcg&app_id=%s&app_key=%s&timestamp=%d&sign=%s&login_type=6&qqmusic_open_appid=%s&qqmusic_open_id=%s&qqmusic_access_token=%s&t=0&w=fdsfd",
                m_OpenAPIAppID, m_OpenAPIAppKey, timeStamp, getSign(timeStamp), m_OpenAPIAppKey, openId, openToken)
        sendHttp(searchUrl)
    }

    fun onClickOpiMvTag(view: View) {
        if (openId.isNullOrEmpty() || openToken.isNullOrEmpty()) {
            Toast.makeText(this, "请先获取授权", Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "onClickOpiMvTag openid:" + openId + "  openToken:" + openToken)
        val time = System.currentTimeMillis()
        val timeStamp = time / 1000
        var mvTagUrl = String.format("http://cd.y.qq.com/ext-internal/fcgi-bin/music_open_api.fcg?opi_cmd=fcg_music_custom_get_mv_by_tag.fcg&app_id=%s&app_key=%s&timestamp=%d&sign=%s&login_type=6&mv_area=0&mv_year=0&mv_type=2&mv_tag=10&mv_pageno=1&mv_pagecount=2&mv_cmd=gettaglist&qqmusic_open_appid=%s&qqmusic_open_id=%s&qqmusic_access_token=%s"
                , m_OpenAPIAppID, m_OpenAPIAppKey, timeStamp, getSign(timeStamp), m_OpenAPIAppKey, openId, openToken)
        sendHttp(mvTagUrl)
    }


    fun onClickSchemeAction(view: View) {
        CommonCmd.init(CommonCmd.AIDL_PLATFORM_TYPE_PHONE)
        val bundle = Bundle()
        bundle.putInt(Keys.API_PARAM_KEY_ACTION, 8)
        bundle.putLong(Keys.API_PARAM_KEY_PULL_FROM, 10026465)
        bundle.putString(Keys.API_PARAM_KEY_SEARCH_KEY, "周杰伦")
        //bundle.putLong(Keys.API_PARAM_KEY_M0, 209)
        bundle.putBoolean(Keys.API_PARAM_KEY_M1, false)
        bundle.putBoolean(Keys.API_PARAM_KEY_MB, true)
        bundle.putBoolean(Keys.API_PARAM_KEY_M2, true)
        CommonCmd.action(this, bundle)
    }

    private fun sendHttp(urlString: String) {

        thread(start = true) {
            val obj = URL(urlString)
            try {
                with(obj.openConnection() as HttpURLConnection) {
                    // optional default is GET
                    requestMethod = "GET"

                    println("Sending 'GET' request to URL : $url")
                    println("Response Code : $responseCode")

                    BufferedReader(InputStreamReader(inputStream)).use {
                        val response = StringBuffer()

                        var inputLine = it.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = it.readLine()
                        }
                        println("Response: $response")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }


    private val handle = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> {
                    if (msg.obj != null && msg.obj is Bitmap) {
                        val bmp = msg.obj as Bitmap
                        songPic.setImageBitmap(bmp)
                    }
                }
                2 -> {
                    txtPlayTime.text = "$curPlayTime/$totalPlayTime"
                    if (totalPlayTime > curPlayTime) {
                        progressPlay.max = totalPlayTime.toInt()
                        progressPlay.progress = curPlayTime.toInt()
                    }
                }
            }
        }
    }


    inner class ViewHolder(itemView: View?, isSongItem: Boolean) {
        var txtTitle: TextView? = null
        var txtContent: TextView? = null
        var imgView: ImageView? = null

        init {
            this.txtTitle = itemView?.findViewById<TextView>(R.id.item_title)
            if (isSongItem) {
                this.txtContent = itemView?.findViewById<TextView>(R.id.item_content)
                this.imgView = itemView?.findViewById<ImageView>(R.id.item_img)
            }
        }
    }

    inner class FolderListAdapter(context: Context, private var list: ArrayList<Data.FolderInfo>) : BaseAdapter() {
        private var mInflater: LayoutInflater? = null

        init {
            mInflater = LayoutInflater.from(context)
        }

        override fun getItem(position: Int): Any {
            return this.list[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return this.list.size
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View?
            val holder: ViewHolder?
            if (convertView == null) {

                view = mInflater?.inflate(R.layout.folder_list_view_item, null)
                holder = ViewHolder(view, false)
                view?.tag = holder

            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }
            if (position >= count) {
                return view as View
            }
            val folder = this.list[position]
            if (folder != null) {
                holder.txtTitle?.text = folder.mainTitle

            }
            return view as View
        }
    }

    inner class SongListAdapter(context: Context) : BaseAdapter() {
        private var mInflater: LayoutInflater? = null

        init {
            mInflater = LayoutInflater.from(context)
        }

        override fun getItem(position: Int): Any {
            return curSonglist[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return curSonglist.size
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View?
            val holder: ViewHolder?
            if (convertView == null) {

                view = mInflater?.inflate(R.layout.song_list_view_item, null)
                holder = ViewHolder(view, true)
                view?.tag = holder

            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            if (position >= count) {
                return view as View
            }
            val song = curSonglist[position]
            if (song != null) {
                holder.txtTitle?.text = song.title
                if (song.singer != null) {
                    holder.txtContent?.text = song.singer.title + "  " + song.album.title
                    holder.imgView?.visibility = VISIBLE
                } else {
                    holder.txtContent?.text = ""
                    holder.imgView?.visibility = GONE
                }

            }
            return view as View
        }

    }

    private fun initMorePopupWindow() {
        val contentView = layoutInflater.inflate(R.layout.view_popview_more, null)
        val width = resources.getDimension(R.dimen.dimen_width).toInt()

        val mPopupWindow = PopupWindow(contentView, width, ViewGroup.LayoutParams.WRAP_CONTENT)
        mPopupWindow.isFocusable = true
        mPopupWindow.isTouchable = true
        mPopupWindow.isOutsideTouchable = true
        mPopupWindow.contentView.isFocusable = true
        mPopupWindow.contentView.isFocusableInTouchMode = true
        mPopupWindow.contentView.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mPopupWindow != null && mPopupWindow.isShowing) {
                    mPopupWindow.dismiss()
                }
                return@OnKeyListener true
            }
            false
        })
        mPopupWindow.setBackgroundDrawable(BitmapDrawable())
        mPopupWindow.showAsDropDown(textMore, 0, 0)
        mPopupWindow.update()

        val text_Api = contentView.findViewById(R.id.text_test_api) as TextView
        text_Api.setOnClickListener {
            val intent = Intent(this@VisualActivity, MainActivity::class.java)
            startActivity(intent)
            if (mPopupWindow.isShowing) {
                mPopupWindow.dismiss()
            }
        }
    }

}
