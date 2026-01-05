package com.tencent.qqmusic.api.demo

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.tencent.qqmusic.third.api.contract.*

/**
 * API接口列表页面
 * 展示所有可测试的API方法，点击进入详情页进行测试
 */
@SuppressLint("SetTextI18n")
class ApiListActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        const val TAG = "ApiListActivity"
        
        // API分类
        val API_CATEGORIES = mapOf(
            "全部" to null,
            "播放控制" to listOf(
                "playMusic", "stopMusic", "pauseMusic", "resumeMusic", 
                "skipToNext", "skipToPrevious", "seekForward", "seekBack", "seek"
            ),
            "歌曲播放" to listOf(
                "playSongMid", "playSongMidAtIndex", "playSongId", "playSongIdAtIndex",
                "playSongLocalPath", "playFolderType"
            ),
            "状态查询" to listOf(
                "getPlaybackState", "getCurrentSong", "getTotalTime", "getCurrTime",
                "getPlayList", "getCurrentSongIndexInList", "getPlayMode", "isQQMusicForeground",
                "getLoginState", "isNewUser"
            ),
            "歌单相关" to listOf(
                "getFolderList", "getSongList", "getUserFolderList", "getUserSongList",
                "getFavouriteFolderId"
            ),
            "收藏相关" to listOf(
                "addToFavourite", "removeFromFavourite", "addLocalPathToFavourite",
                "removeLocalPathFromFavourite", "isFavouriteMid", "isFavouriteLocalPath"
            ),
            "搜索语音" to listOf(
                "search", "voiceShortcut", "voicePlay"
            ),
            "歌词相关" to listOf(
                "getLyric", "getLyricWithId", "getLyricWithIdNew", "getLyricIncludeEndTime"
            ),
            "其他" to listOf(
                "hi", "openQQMusic", "setPlayMode", "setPlayModeWithRet", "playFromChorus",
                "requestAuth", "registerEventListener", "unregisterEventListener",
                "startPcmMode", "stopPcmMode", "getEncryptedUin", "report"
            )
        )
        
        // 同步方法列表
        val SYNC_METHODS = setOf(
            "hi", "openMusic", "playMusic", "stopMusic", "pauseMusic", "resumeMusic",
            "skipToNext", "skipToPrevious", "getPlaybackState", "getCurrentSong",
            "getTotalTime", "getCurrTime", "playFromChorus", "setPlayMode", "getPlayMode",
            "seekTo", "getVolume", "setVolume", "getFavouriteFolderId", "isQQMusicForeground",
            "getLoginState", "isNewUser", "getCurrentSongIndexInList", "getEncryptedUin"
        )
        
        // 获取API描述
        fun getApiDescription(action: String): String {
            val params = MainActivity.METHOD_PARAMS[action] ?: emptyList()
            return if (params.isEmpty()) {
                "无参数"
            } else {
                val paramStr = params
                    .filter { it.second != "Callback" && it.second != "EventListener" }
                    .joinToString(", ") { "${it.first}: ${it.second}" }
                if (paramStr.isEmpty()) "无参数" else "参数: $paramStr"
            }
        }
    }

    private val connectStateTextView by lazy { findViewById<TextView>(R.id.tv_connect_state) }
    private val etSearch by lazy { findViewById<EditText>(R.id.et_search) }
    private val tabContainer by lazy { findViewById<LinearLayout>(R.id.tab_container) }
    private val rvApiList by lazy { findViewById<RecyclerView>(R.id.rv_api_list) }
    private val toolBar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    private var qqmusicApi: IQQMusicApi? = null
    private val allApis = MainActivity.ACTIONS.sorted()
    private var filteredApis = allApis.toMutableList()
    private var currentCategory: String = "全部"
    private lateinit var adapter: ApiAdapter

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        qqmusicApi = IQQMusicApi.Stub.asInterface(service)
        connectStateTextView.text = "连接状态: connected ✓"
        connectStateTextView.setTextColor(Color.parseColor("#4CAF50"))
        adapter.notifyDataSetChanged()
    }

    override fun onServiceDisconnected(name: ComponentName) {
        connectStateTextView.text = "连接状态: disconnected ✗"
        connectStateTextView.setTextColor(Color.parseColor("#F44336"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_list)

        setSupportActionBar(toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "API 接口测试"
        toolBar.setNavigationOnClickListener { onBackPressed() }

        setupTabs()
        setupSearch()
        setupRecyclerView()

        // 绑定服务
        connectStateTextView.text = "connecting..."
        val bindRet = bindQQMusicApiService(Config.BIND_PLATFORM)
        if (!bindRet) {
            connectStateTextView.text = "failed to connect"
            connectStateTextView.setTextColor(Color.parseColor("#F44336"))
        }
    }

    /**
     * 设置分类Tab
     */
    private fun setupTabs() {
        API_CATEGORIES.keys.forEach { category ->
            val tab = TextView(this).apply {
                text = category
                setPadding(24, 16, 24, 16)
                setTextColor(if (category == currentCategory) Color.WHITE else Color.parseColor("#666666"))
                textSize = 13f
                background = createTabBackground(category == currentCategory)
                setOnClickListener {
                    selectCategory(category)
                }
            }
            tabContainer.addView(tab)
        }
    }

    /**
     * 创建Tab背景
     */
    private fun createTabBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 20f
            if (selected) {
                setColor(Color.parseColor("#2196F3"))
            } else {
                setColor(Color.parseColor("#F0F0F0"))
            }
        }
    }

    /**
     * 选择分类
     */
    private fun selectCategory(category: String) {
        currentCategory = category
        
        // 更新Tab样式
        for (i in 0 until tabContainer.childCount) {
            val tab = tabContainer.getChildAt(i) as TextView
            val isSelected = tab.text == category
            tab.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#666666"))
            tab.background = createTabBackground(isSelected)
        }
        
        // 过滤API列表
        filterApis()
    }

    /**
     * 设置搜索
     */
    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApis()
            }
        })
    }

    /**
     * 过滤API列表
     */
    private fun filterApis() {
        val searchText = etSearch.text.toString().toLowerCase()
        val categoryApis = API_CATEGORIES[currentCategory]
        
        filteredApis = allApis.filter { api ->
            val matchCategory = categoryApis == null || categoryApis.contains(api)
            val matchSearch = searchText.isEmpty() || api.toLowerCase().contains(searchText)
            matchCategory && matchSearch
        }.toMutableList()
        
        adapter.notifyDataSetChanged()
    }

    /**
     * 设置RecyclerView
     */
    private fun setupRecyclerView() {
        adapter = ApiAdapter()
        rvApiList.layoutManager = LinearLayoutManager(this)
        rvApiList.adapter = adapter
    }

    /**
     * API列表Adapter
     */
    inner class ApiAdapter : RecyclerView.Adapter<ApiViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApiViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_api, parent, false)
            return ApiViewHolder(view)
        }

        override fun onBindViewHolder(holder: ApiViewHolder, position: Int) {
            val api = filteredApis[position]
            holder.bind(api)
        }

        override fun getItemCount() = filteredApis.size
    }

    /**
     * API ViewHolder
     */
    inner class ApiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvApiName: TextView = itemView.findViewById(R.id.tv_api_name)
        private val tvApiDesc: TextView = itemView.findViewById(R.id.tv_api_desc)
        private val tvApiType: TextView = itemView.findViewById(R.id.tv_api_type)

        fun bind(api: String) {
            tvApiName.text = api
            tvApiDesc.text = getApiDescription(api)
            
            val isSync = SYNC_METHODS.contains(api)
            tvApiType.text = if (isSync) "同步" else "异步"
            tvApiType.background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(if (isSync) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3"))
            }

            itemView.setOnClickListener {
                val intent = Intent(this@ApiListActivity, ApiDetailActivity::class.java)
                intent.putExtra("api_name", api)
                startActivity(intent)
            }
        }
    }

    /**
     * 绑定QQ音乐API服务
     */
    private fun bindQQMusicApiService(platform: String): Boolean {
        val intent = when (platform) {
            CommonCmd.AIDL_PLATFORM_TYPE_PHONE -> {
                Intent("com.tencent.qqmusic.third.api.QQMusicApiService").apply {
                    `package` = "com.tencent.qqmusic"
                }
            }
            CommonCmd.AIDL_PLATFORM_TYPE_CAR -> {
                Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService").apply {
                    `package` = "com.tencent.qqmusiccar"
                }
            }
            CommonCmd.AIDL_PLATFORM_TYPE_TV -> {
                Intent("com.tencent.qqmusictv.third.api.QQMusicApiService").apply {
                    `package` = "com.tencent.qqmusictv"
                }
            }
            CommonCmd.AIDL_PLATFORM_TYPE_LITE -> {
                Intent("com.tencent.qqmusiclite.third.api.QQMusicApiService").apply {
                    `package` = "com.miui.player"
                }
            }
            CommonCmd.AIDL_PLATFORM_TYPE_LITE_DEMO -> {
                Intent("com.tencent.qqmusiclite.third.api.QQMusicApiService").apply {
                    `package` = "com.miui.player_preview"
                }
            }
            else -> {
                Log.e(TAG, "platform error!", RuntimeException())
                return false
            }
        }
        return bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(this)
        } catch (ignored: Throwable) {
        }
    }
}
