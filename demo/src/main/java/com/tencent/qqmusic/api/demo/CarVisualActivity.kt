package com.tencent.qqmusic.api.demo

import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.*
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.tencent.qqmusic.third.api.contract.*
import java.util.*
import android.view.KeyEvent
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

/*
* 可视化的Api调用Demo，如需查看完整调用，参考MainActivity
* */
class CarVisualActivity : AppCompatActivity(), ServiceConnection {


    var cmd = "start"
    var bindFlag = false
    private var openId: String? = null
    private var openToken: String? = null

    private val textMore by lazy { findViewById<TextView>(R.id.text_more) }
    private val txtResult by lazy { findViewById<TextView>(R.id.txtResult) }
    private val txtSongInfos by lazy { findViewById<TextView>(R.id.txtSongInfos) }
    private val txtPlayTime by lazy { findViewById<TextView>(R.id.txtPlayTime) }
    private val songPic by lazy { findViewById<ImageView>(R.id.SongPic) }
    private val progressPaly by lazy { findViewById<ProgressBar>(R.id.ProgressPlay) }
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

    val m_AppId = "1"
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
    var TAG = "CarVisualActivity"
    private var thread: Thread? = null

    private fun myTherad() {
        thread = Thread(Runnable {
            kotlin.run {
                rpc_startRequest()
            }
        })
    }

    private var myHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                22 -> {
                    val bindRet = bindQQMusicApiService()
                    Log.i("CarVisualActivity", "bindRet:" + bindRet)
                    if (!bindRet) {
                        sendEmptyMessageDelayed(22, 100)
                    }
                }
                else -> {
                }
            }
        }
    }
    private var m_Receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            var ret = p1?.extras?.get("ret")
            Log.d(TAG, "ret:" + ret)
            if (ret == "0") {
                initData()
            } else {
                print("授权失败($ret)")
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_visual)
        folderAdapter = FolderListAdapter(this@CarVisualActivity, curFolderlist)
        folderListView.adapter = folderAdapter
        //点击处理
        folderListView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            if (position >= curFolderlist.size) {
                return@OnItemClickListener
            }
            var folder = curFolderlist[position]

            if (folder.type == backId) {
                onBackClick(view)
                return@OnItemClickListener
            }

            var needPush = true
            if (pathStack.isEmpty() == false) {
                var stackFolder = pathStack.peek()
                if (stackFolder.id == curFolder?.id && stackFolder.type == curFolder?.type) {
                    needPush = false
                }
            }
            if (needPush)
                pathStack.push(curFolder)

            curFolder = folder
            if (folder?.isSongFolder == true) {
                getSongList(folder, 0)
            } else {
                if (folder != null)
                    getFolderList(folder, 0)
            }
        }

        songAdapter = SongListAdapter(this)
        songListView.adapter = songAdapter
        songListView.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            var song = curSonglist[position]

            if (song.id == backId.toString()) {
                onBackClick(view)
            } else {
                var songlist = curSonglist.subList(1, curSonglist.size - 1)
                playSonglist(songlist, song)
            }
        }

        backFolder = Data.FolderInfo()
        backFolder?.type = backId
        backFolder?.mainTitle = ".. 返回上一级"
        backSong = Data.Song()
        backSong?.id = backId.toString()
        backSong?.title = ".. 返回上一级"
        textMore.visibility = VISIBLE
        textMore.setOnClickListener() {
            initMorePopupWindow()
        }

        var filter = IntentFilter()
        filter.addAction("callback_verify_notify")
        registerReceiver(m_Receiver, filter)
    }

    fun rpc_startRequest() {
        CommonCmd.startQQMusicProcess(this, packageName)
    }

    fun rpc_verifyRequest() {
        val time = System.currentTimeMillis()
        val nonce = time.toString()
        val encryptString = OpenIDHelper.getEncryptString(nonce)
        CommonCmd.verifyCallerIdentity(this, m_AppId, packageName, encryptString, "qqmusicapidemo://xxx")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)
            qqmusicApi?.unregisterEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)
        } catch (ignored: Throwable) {
        }
        if (bindFlag) {
            unbindService(this)
        }
        unregisterReceiver(m_Receiver)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onServiceConnected(p0: ComponentName, p1: IBinder) {
//         绑定成功
        qqmusicApi = IQQMusicApi.Stub.asInterface(p1)
        txtResult.text = "已连接QQ音乐"
        bindFlag = true
        Log.d(TAG, "service has connected")
        CommonCmd.init(CommonCmd.AIDL_PLATFORM_TYPE_CAR)
        sayHi()
    }

    override fun onServiceDisconnected(p0: ComponentName) {
        // 失去连接，可能QQ音乐退出了
        txtResult.text = "和QQ音乐断开连接"
        bindFlag = false
    }

    private fun sayHi() {
        var bundle = Bundle()
        bundle.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
        bundle.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, CommonCmd.AIDL_PLATFORM_TYPE_CAR)
        var result = qqmusicApi?.execute("hi", bundle)
        Log.d(TAG, "sayHi ret:" + result!!.getInt(Keys.API_RETURN_KEY_CODE))
        if (commonOpen(result)) {
            initData()
        }
    }

    /**
     * 绑定QQ音乐API服务
     */
    private fun bindQQMusicApiService(): Boolean {
        // 必须显式绑定
        val intent = Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
        intent.`package` = "com.tencent.qqmusiccar"
        return bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun initData() {

        // 可选：注册事件回调
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_SONG_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_LIST_CHANGED), eventListener)
        qqmusicApi?.registerEventListener(arrayListOf(Events.API_EVENT_PLAY_STATE_CHANGED), eventListener)

        //获取根目录
        var rootFolder = Data.FolderInfo()
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

    fun onActiveClick(view: View) {
        val bindRet = bindQQMusicApiService()
        if (!bindRet) {
            txtResult.text = "连接QQ音乐失败"
            myTherad()
            thread?.start()
            myHandler.sendEmptyMessage(22)
        } else {
            initData()
        }
    }

    fun onPlayPre(view: View) {
        val result = qqmusicApi?.execute("skipToPrevious", null)
        var errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
        if (errorCode != ErrorCodes.ERROR_OK) {
            print("上一首失败($errorCode)")
        }
    }

    fun onPlayNext(view: View) {
        val result = qqmusicApi?.execute("skipToNext", null)
        var errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
        if (errorCode != ErrorCodes.ERROR_OK) {
            print("下一首失败($errorCode)")
        }
    }

    fun onPlayPause(view: View) {

        if (curPlaySong == null)
            return

        if (isPlaying()) {
            val result = qqmusicApi?.execute("pauseMusic", null)
            var errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                print("暂停音乐失败($errorCode)")
            }
        } else {
            val result = qqmusicApi?.execute("playMusic", null)
            var errorCode = result?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
            if (errorCode != ErrorCodes.ERROR_OK) {
                print("开始播放音乐失败($errorCode)")
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
        var midList = ArrayList<String>()
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
                        runOnUiThread({
                            setLoveStateText(true)
                        })
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
                        runOnUiThread({
                            setLoveStateText(false)
                        })
                    } else {
                        print("取消收藏失败（$code)")
                    }
                }
            })
        }


    }

    private fun commonOpen(p0: Bundle?): Boolean {
        if (p0 != null) {
            val code = p0.getInt(Keys.API_RETURN_KEY_CODE)
            if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                rpc_verifyRequest()
                return false
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                CommonCmd.loginQQMusic(this@CarVisualActivity, "qqmusicapidemo://xxx")
                return false
            } else if (code == ErrorCodes.ERROR_API_NOT_INITIALIZED) {
                return false
            }
            return true
        }
        return false
    }

    private fun getFolderList(folder: Data.FolderInfo, page: Int) {

        print("获取歌单... ${folder.id},${folder.type}")

        runOnUiThread {
            curSonglist.clear()
            songAdapter?.notifyDataSetChanged()
            songListView.visibility = GONE
            folderListView.visibility = VISIBLE
        }

        val isUserFolder = this.isUserFolder(folder.type)
        if (isUserFolder) {
            //用户歌单使用新API获取
            if (this.openId.isNullOrEmpty() || this.openToken.isNullOrEmpty()) {
                startAIDLAuth({ success ->
                    if (success)
                        getUserFolderList(folder, page)
                })
                return
            }
            getUserFolderList(folder, page)
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

        qqmusicApi?.executeAsync("getFolderList", params, object : IQQMusicApiCallback.Stub() {

            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    var dataJson = result.getString(Keys.API_RETURN_KEY_DATA)

                    val array = JsonParser().parse(dataJson).asJsonArray
                    var tmpList = ArrayList<Data.FolderInfo>()
                    if (folder.type != 0)
                        backFolder?.let { tmpList.add(it) }
                    for (elem in array) {
                        var folder = gson.fromJson(elem, Data.FolderInfo::class.java)
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

        qqmusicApi?.executeAsync("getUserFolderList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)

                if (code == ErrorCodes.ERROR_OK) {
                    var dataJson = result.getString(Keys.API_RETURN_KEY_DATA)

                    val array = JsonParser().parse(dataJson).asJsonArray
                    var tmpList = ArrayList<Data.FolderInfo>()
                    if (folder.type != 0)
                        backFolder?.let { tmpList.add(it) }
                    for (elem in array) {
                        var folder = gson.fromJson(elem, Data.FolderInfo::class.java)
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


    private fun getSongList(folder: Data.FolderInfo, page: Int) {

        val isUserFolder = this.isUserFolder(folder.type)
        if (isUserFolder) {
            //用户歌曲使用新API获取
            if (this.openId.isNullOrEmpty() || this.openToken.isNullOrEmpty()) {
                startAIDLAuth({ success ->
                    if (success)
                        getUserSongList(folder, page)
                })
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

        var result = qqmusicApi?.execute("getCurrentSong", null)
        var curSongJson = result?.getString(Keys.API_RETURN_KEY_DATA)
        this.curPlaySong = gson.fromJson(curSongJson, Data.Song::class.java)
        if (this.curPlaySong == null)
            return
        txtSongInfos.text = curPlaySong?.title
        txtAlbum.text = curPlaySong?.album?.title + " - " + curPlaySong?.singer?.title
        if (!curPlaySong?.album?.coverUri.isNullOrEmpty()) {
            setSongUrlImage(curPlaySong?.album?.coverUri ?: "")
        }

        result = qqmusicApi?.execute("getPlaybackState", null)
        this.curPlayState = result?.getInt(Keys.API_RETURN_KEY_DATA) ?: 0

        result = qqmusicApi?.execute("getCurrTime", null)
        curPlayTime = (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000

        result = qqmusicApi?.execute("getTotalTime", null)
        totalPlayTime = (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000

        txtPlayTime.text = "$curPlayTime/$totalPlayTime"

        var midList = ArrayList<String>()
        midList.add(curPlaySong?.mid ?: "")
        val params = Bundle()
        params.putStringArrayList("midList", midList)
        qqmusicApi?.executeAsync("isFavouriteMid", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                // 回调的结果
                commonOpen(result)
                val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                if (code == ErrorCodes.ERROR_OK) {
                    var boolArray = result.getBooleanArray(Keys.API_RETURN_KEY_DATA)
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
        var isPlayingNow = (curPlayState == PlayState.STARTED
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
                            print("授权成功 票据：$openId,$openToken")
                        }
                    }
                    if (!authOK) {
                        print("授权失败")
                    }

                } else {
                    print("授权失败（$code)")
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
    fun getURLimage(url: String): Bitmap? {
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


    fun onClickSchemeAction(view: View){
        CommonCmd.init(CommonCmd.AIDL_PLATFORM_TYPE_CAR)
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
                        progressPaly.max = totalPlayTime.toInt()
                        progressPaly.progress = curPlayTime.toInt()
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

    inner class FolderListAdapter : BaseAdapter {


        private var mInflater: LayoutInflater? = null
        private var list: ArrayList<Data.FolderInfo>

        constructor(context: Context, list: ArrayList<Data.FolderInfo>) : super() {
            mInflater = LayoutInflater.from(context)
            this.list = list
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
            var holder: ViewHolder?
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
            var folder = this.list[position]
            if (folder != null) {
                holder.txtTitle?.text = folder.mainTitle

            }
            return view as View
        }

    }

    inner class SongListAdapter : BaseAdapter {


        private var mInflater: LayoutInflater? = null

        constructor(context: Context) : super() {
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
            var holder: ViewHolder?
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
            var song = curSonglist[position]
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
//        var width = textMore.width
//        if (width == 0) {
        val width = resources.getDimension(R.dimen.dimen_width).toInt()
//        }

        val mPopupWindow = PopupWindow(contentView, width, ViewGroup.LayoutParams.WRAP_CONTENT)
        mPopupWindow.setFocusable(true)
        mPopupWindow.setTouchable(true)
        mPopupWindow.setOutsideTouchable(true)
        mPopupWindow.getContentView().isFocusable = true
        mPopupWindow.getContentView().isFocusableInTouchMode = true
        mPopupWindow.getContentView().setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (mPopupWindow != null && mPopupWindow.isShowing()) {
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
            val intent = Intent(this@CarVisualActivity, MainActivity::class.java)
            startActivity(intent)
            if (mPopupWindow.isShowing) {
                mPopupWindow.dismiss()
            }
        }
    }


}
