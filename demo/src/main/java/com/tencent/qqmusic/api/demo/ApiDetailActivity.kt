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
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.tencent.qqmusic.api.demo.openid.OpenIDHelper
import com.tencent.qqmusic.third.api.contract.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * API详情测试页面
 * 针对单个API进行测试，包含单次调用和连续调用测试
 */
@SuppressLint("SetTextI18n")
class ApiDetailActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        const val TAG = "ApiDetailActivity"
        
        // 同步方法列表
        val SYNC_METHODS = setOf(
            "hi", "openMusic", "playMusic", "stopMusic", "pauseMusic", "resumeMusic",
            "skipToNext", "skipToPrevious", "getPlaybackState", "getCurrentSong",
            "getTotalTime", "getCurrTime", "playFromChorus", "setPlayMode", "getPlayMode",
            "seekTo", "getVolume", "setVolume", "getFavouriteFolderId", "isQQMusicForeground",
            "getLoginState", "isNewUser", "getCurrentSongIndexInList", "getEncryptedUin"
        )
        
        // 连续调用场景定义
        // 每个场景包含：场景名称 -> (相关API列表, 步骤列表)
        data class ChainStep(val action: String, val params: Map<String, String>, val autoParams: Set<String> = emptySet())
        data class ChainScenario(val name: String, val relatedApis: Set<String>, val steps: List<ChainStep>)
        
        val CHAIN_SCENARIOS = listOf(
            ChainScenario(
                "获取歌单列表 → 获取歌曲 → 播放",
                setOf("getFolderList", "getSongList", "playSongMid"),
                listOf(
                    ChainStep("getFolderList", mapOf("folderId" to "0", "folderType" to "200", "page" to "0")),
                    ChainStep("getSongList", mapOf("page" to "0"), setOf("folderId", "folderType")),
                    ChainStep("playSongMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "搜索歌曲 → 播放",
                setOf("search", "playSongMid"),
                listOf(
                    ChainStep("search", mapOf("keyword" to "周杰伦", "searchType" to "0", "firstPage" to "true")),
                    ChainStep("playSongMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "获取收藏夹ID → 获取收藏歌曲 → 播放",
                setOf("getFavouriteFolderId", "getSongList", "playSongMid"),
                listOf(
                    ChainStep("getFavouriteFolderId", emptyMap()),
                    ChainStep("getSongList", mapOf("folderType" to "201", "page" to "0"), setOf("folderId")),
                    ChainStep("playSongMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "获取最近播放 → 播放",
                setOf("getSongList", "playSongMid"),
                listOf(
                    ChainStep("getSongList", mapOf("folderId" to "0", "folderType" to "203", "page" to "0")),
                    ChainStep("playSongMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "语音快捷指令 → 播放",
                setOf("voiceShortcut", "playSongMid"),
                listOf(
                    ChainStep("voiceShortcut", mapOf("intent" to "recentPlay")),
                    ChainStep("playSongMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "获取歌单 → 添加到收藏",
                setOf("getFolderList", "getSongList", "addToFavourite"),
                listOf(
                    ChainStep("getFolderList", mapOf("folderId" to "0", "folderType" to "200", "page" to "0")),
                    ChainStep("getSongList", mapOf("page" to "0"), setOf("folderId", "folderType")),
                    ChainStep("addToFavourite", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "获取当前歌曲 → 判断是否收藏",
                setOf("getCurrentSong", "isFavouriteMid"),
                listOf(
                    ChainStep("getCurrentSong", emptyMap()),
                    ChainStep("isFavouriteMid", emptyMap(), setOf("midList"))
                )
            ),
            ChainScenario(
                "判断收藏状态 → 切换收藏 → 再次判断",
                setOf("isFavouriteMid", "addToFavourite", "removeFromFavourite"),
                listOf(
                    ChainStep("isFavouriteMid", mapOf("midList" to "0039MnYb0qxYhV")),
                    ChainStep("addToFavourite", emptyMap(), setOf("midList")),
                    ChainStep("isFavouriteMid", emptyMap(), setOf("midList"))
                )
            )
        )
    }

    private lateinit var apiName: String
    private var qqmusicApi: IQQMusicApi? = null
    private val paramViews = mutableListOf<View>()
    
    // 连续调用时存储的提取数据
    private val stepExtractedData = mutableMapOf<Int, ExtractedData>()
    
    // 数据提取结构
    data class ExtractedData(
        var folderList: List<JSONObject> = emptyList(),
        var songList: List<JSONObject> = emptyList(),
        var folderId: String? = null,
        var folderType: Int? = null,
        var songMidList: List<String> = emptyList(),
        var songIdList: List<String> = emptyList(),
        var rawJson: String? = null,
        var inputParams: Map<String, String> = emptyMap()  // 保存该步骤的输入参数
    )

    private val connectStateTextView by lazy { findViewById<TextView>(R.id.tv_connect_state) }
    private val tvApiName by lazy { findViewById<TextView>(R.id.tv_api_name) }
    private val tvApiType by lazy { findViewById<TextView>(R.id.tv_api_type) }
    private val tvApiParamsInfo by lazy { findViewById<TextView>(R.id.tv_api_params_info) }
    private val paramsContainer by lazy { findViewById<LinearLayout>(R.id.params_container) }
    private val btnExecute by lazy { findViewById<Button>(R.id.btn_execute) }
    private val btnReset by lazy { findViewById<Button>(R.id.btn_reset) }
    private val tvSingleResult by lazy { findViewById<TextView>(R.id.tv_single_result) }
    private val chainScenariosContainer by lazy { findViewById<LinearLayout>(R.id.chain_scenarios_container) }
    private val tvChainResultLabel by lazy { findViewById<TextView>(R.id.tv_chain_result_label) }
    private val tvChainResult by lazy { findViewById<TextView>(R.id.tv_chain_result) }
    private val toolBar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        qqmusicApi = IQQMusicApi.Stub.asInterface(service)
        btnExecute.isEnabled = true
        connectStateTextView.text = "连接状态: connected ✓"
        connectStateTextView.setTextColor(Color.parseColor("#4CAF50"))
    }

    override fun onServiceDisconnected(name: ComponentName) {
        btnExecute.isEnabled = false
        connectStateTextView.text = "连接状态: disconnected ✗"
        connectStateTextView.setTextColor(Color.parseColor("#F44336"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_detail)

        apiName = intent.getStringExtra("api_name") ?: ""
        if (apiName.isEmpty()) {
            finish()
            return
        }

        setSupportActionBar(toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = apiName
        toolBar.setNavigationOnClickListener { onBackPressed() }

        setupApiInfo()
        setupParams()
        setupButtons()
        setupChainScenarios()

        // 绑定服务
        connectStateTextView.text = "connecting..."
        val bindRet = bindQQMusicApiService(Config.BIND_PLATFORM)
        if (!bindRet) {
            connectStateTextView.text = "failed to connect"
            connectStateTextView.setTextColor(Color.parseColor("#F44336"))
        }
    }

    /**
     * 设置API信息展示
     */
    private fun setupApiInfo() {
        tvApiName.text = apiName
        
        val isSync = SYNC_METHODS.contains(apiName)
        tvApiType.text = if (isSync) "同步调用" else "异步调用"
        tvApiType.background = GradientDrawable().apply {
            cornerRadius = 8f
            setColor(if (isSync) Color.parseColor("#4CAF50") else Color.parseColor("#2196F3"))
        }

        val params = MainActivity.METHOD_PARAMS[apiName] ?: emptyList()
        val paramStr = params
            .filter { it.second != "Callback" && it.second != "EventListener" }
            .joinToString("\n") { "• ${it.first}: ${it.second}" }
        tvApiParamsInfo.text = if (paramStr.isEmpty()) "无需参数" else "参数列表:\n$paramStr"
    }

    /**
     * 设置参数输入区域
     */
    private fun setupParams() {
        generateParamsForAction()
    }

    /**
     * 生成参数输入框
     */
    private fun generateParamsForAction() {
        paramsContainer.removeAllViews()
        paramViews.clear()

        val params = MainActivity.METHOD_PARAMS[apiName] ?: emptyList()
        val examples = MainActivity.EXAMPLE_DATA[apiName] ?: emptyMap()

        params.forEach { (paramName, paramType) ->
            if (paramType != "Callback" && paramType != "EventListener") {
                val exampleValue = examples[paramName] ?: ""
                addParamView(paramName, exampleValue, paramType)
            }
        }

        if (paramViews.isEmpty()) {
            val hint = TextView(this).apply {
                text = "该接口无需参数"
                setTextColor(Color.parseColor("#999999"))
                setPadding(0, 16, 0, 16)
            }
            paramsContainer.addView(hint)
        }
    }

    /**
     * 添加参数输入视图
     */
    private fun addParamView(key: String, value: String, type: String) {
        val paramView = LayoutInflater.from(this).inflate(R.layout.item_param, paramsContainer, false)

        val spinnerType = paramView.findViewById<Spinner>(R.id.spinner_param_type)
        val etKey = paramView.findViewById<EditText>(R.id.et_param_key)
        val etValue = paramView.findViewById<EditText>(R.id.et_param_value)
        val btnRemove = paramView.findViewById<Button>(R.id.btn_remove_param)

        val paramTypes = arrayOf("String", "Int", "Long", "Boolean", "StringArrayList")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, paramTypes)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter
        spinnerType.setSelection(paramTypes.indexOf(type).coerceAtLeast(0))

        etKey.setText(key)
        etValue.setText(value)

        btnRemove.setOnClickListener {
            paramsContainer.removeView(paramView)
            paramViews.remove(paramView)
        }

        paramViews.add(paramView)
        paramsContainer.addView(paramView)
    }

    /**
     * 设置按钮事件
     */
    private fun setupButtons() {
        btnExecute.setOnClickListener {
            executeSingleCall()
        }

        btnReset.setOnClickListener {
            generateParamsForAction()
            tvSingleResult.text = ""
        }
    }

    /**
     * 执行单次调用
     */
    private fun executeSingleCall() {
        val params = buildParamsBundle()
        val isSync = SYNC_METHODS.contains(apiName)
        
        tvSingleResult.text = "执行中...\n"
        btnExecute.isEnabled = false

        Thread {
            try {
                if (isSync) {
                    // 同步调用
                    val result = qqmusicApi?.execute(apiName, params)
                    handleResult(result)
                } else {
                    // 异步调用
                    val latch = CountDownLatch(1)
                    var asyncResult: Bundle? = null
                    
                    qqmusicApi?.executeAsync(apiName, params, object : IQQMusicApiCallback.Stub() {
                        override fun onReturn(result: Bundle?) {
                            asyncResult = result
                            latch.countDown()
                        }
                    })
                    
                    val success = latch.await(30, TimeUnit.SECONDS)
                    if (!success) {
                        runOnUiThread {
                            tvSingleResult.text = "调用超时（30秒）"
                            btnExecute.isEnabled = true
                        }
                        return@Thread
                    }
                    
                    handleResult(asyncResult)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvSingleResult.text = "执行出错: ${e.message}"
                    btnExecute.isEnabled = true
                }
                Log.e(TAG, "执行出错", e)
            }
        }.start()
    }

    /**
     * 处理执行结果
     */
    private fun handleResult(result: Bundle?) {
        runOnUiThread {
            btnExecute.isEnabled = true
            
            if (result == null) {
                tvSingleResult.text = "结果: null"
                return@runOnUiThread
            }

            val code = result.getInt(Keys.API_RETURN_KEY_CODE)
            val data = getDataFromBundle(result, Keys.API_RETURN_KEY_DATA)
            val error = result.getString(Keys.API_RETURN_KEY_ERROR)

            val sb = StringBuilder()
            sb.append("Code: $code")
            if (code == ErrorCodes.ERROR_OK) {
                sb.append(" ✓ 成功\n")
            } else {
                sb.append(" ✗ 失败\n")
            }
            
            if (error != null) {
                sb.append("Error: $error\n")
            }
            
            sb.append("\nData:\n")
            sb.append(formatJson(data))
            
            tvSingleResult.text = sb.toString()

            // 处理需要认证的情况
            if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                handleAuth()
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                handleLogin()
            }
        }
    }

    /**
     * 从Bundle中安全获取数据，自适应不同类型
     */
    private fun getDataFromBundle(bundle: Bundle, key: String): String? {
        return try {
            val value = bundle.get(key)
            when (value) {
                null -> null
                is String -> value
                is BooleanArray -> value.contentToString()
                is IntArray -> value.contentToString()
                is LongArray -> value.contentToString()
                is FloatArray -> value.contentToString()
                is DoubleArray -> value.contentToString()
                is Array<*> -> value.contentToString()
                is Boolean -> value.toString()
                is Int -> value.toString()
                is Long -> value.toString()
                is Float -> value.toString()
                is Double -> value.toString()
                else -> value.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取Bundle数据失败: key=$key", e)
            "[获取数据失败: ${e.message}]"
        }
    }

    /**
     * 格式化JSON
     */
    private fun formatJson(json: String?): String {
        if (json == null) return "null"
        return try {
            when {
                json.trim().startsWith("[") -> JSONArray(json).toString(2)
                json.trim().startsWith("{") -> JSONObject(json).toString(2)
                else -> json
            }
        } catch (e: Exception) {
            json
        }
    }

    /**
     * 构建参数Bundle
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
                        "Int" -> putInt(key, value.toIntOrNull() ?: 0)
                        "Long" -> putLong(key, value.toLongOrNull() ?: 0L)
                        "Boolean" -> putBoolean(key, value.toBoolean())
                        "StringArrayList" -> {
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
                }
            }
        }
    }

    /**
     * 设置连续调用场景
     */
    private fun setupChainScenarios() {
        chainScenariosContainer.removeAllViews()
        
        // 过滤出与当前API相关的场景
        val relatedScenarios = CHAIN_SCENARIOS.filter { it.relatedApis.contains(apiName) }
        
        if (relatedScenarios.isEmpty()) {
            val hint = TextView(this).apply {
                text = "暂无与此接口相关的连续调用场景"
                setTextColor(Color.parseColor("#999999"))
                setPadding(0, 16, 0, 16)
            }
            chainScenariosContainer.addView(hint)
            return
        }

        relatedScenarios.forEach { scenario ->
            val scenarioView = LayoutInflater.from(this)
                .inflate(R.layout.item_chain_scenario, chainScenariosContainer, false)
            
            val tvScenarioName = scenarioView.findViewById<TextView>(R.id.tv_scenario_name)
            val tvScenarioSteps = scenarioView.findViewById<TextView>(R.id.tv_scenario_steps)
            val btnRunScenario = scenarioView.findViewById<Button>(R.id.btn_run_scenario)
            
            tvScenarioName.text = scenario.name
            tvScenarioSteps.text = scenario.steps.joinToString(" → ") { it.action }
            
            btnRunScenario.setOnClickListener {
                executeChainScenario(scenario)
            }
            
            chainScenariosContainer.addView(scenarioView)
        }
    }

    /**
     * 执行连续调用场景
     */
    private fun executeChainScenario(scenario: ChainScenario) {
        stepExtractedData.clear()
        tvChainResultLabel.visibility = View.VISIBLE
        tvChainResult.visibility = View.VISIBLE
        tvChainResult.text = "开始执行场景: ${scenario.name}\n\n"

        Thread {
            try {
                scenario.steps.forEachIndexed { index, step ->
                    val stepNumber = index + 1
                    appendChainResult("========== 步骤 $stepNumber: ${step.action} ==========\n")
                    
                    // 构建参数
                    val params = buildChainStepParams(step, stepNumber)
                    appendChainResult("参数: ${params.toPrintableString()}\n")
                    
                    val isSync = SYNC_METHODS.contains(step.action)
                    appendChainResult("[${if (isSync) "同步" else "异步"}调用]\n")
                    
                    val result: Bundle?
                    if (isSync) {
                        result = qqmusicApi?.execute(step.action, params)
                    } else {
                        val latch = CountDownLatch(1)
                        var asyncResult: Bundle? = null
                        
                        qqmusicApi?.executeAsync(step.action, params, object : IQQMusicApiCallback.Stub() {
                            override fun onReturn(r: Bundle?) {
                                asyncResult = r
                                latch.countDown()
                            }
                        })
                        
                        val success = latch.await(30, TimeUnit.SECONDS)
                        if (!success) {
                            appendChainResult("【超时】异步调用超时\n\n")
                            return@Thread
                        }
                        result = asyncResult
                    }
                    
                    // 处理结果
                    if (result != null) {
                        val code = result.getInt(Keys.API_RETURN_KEY_CODE)
                        val json = getDataFromBundle(result, Keys.API_RETURN_KEY_DATA)
                        
                        appendChainResult("code: $code ${if (code == ErrorCodes.ERROR_OK) "✓" else "✗"}\n")
                        appendChainResult("data: ${json?.take(300) ?: "null"}${if ((json?.length ?: 0) > 300) "..." else ""}\n")
                        
                        // 提取数据供后续步骤使用
                        if (code == ErrorCodes.ERROR_OK && json != null) {
                            val extractedData = stepExtractedData[stepNumber] ?: ExtractedData()
                            val newData = extractDataFromJson(json, step.action)
                            // 合并数据，保留已有的 inputParams
                            extractedData.folderList = newData.folderList
                            extractedData.songList = newData.songList
                            extractedData.folderId = newData.folderId
                            extractedData.folderType = newData.folderType
                            extractedData.songMidList = newData.songMidList
                            extractedData.songIdList = newData.songIdList
                            extractedData.rawJson = newData.rawJson
                            stepExtractedData[stepNumber] = extractedData
                            appendChainResult("【自动提取】: ${describeExtractedData(extractedData)}\n")
                        }
                        
                        // 检查认证
                        if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                            appendChainResult("\n需要验证身份，正在处理...\n")
                            handleAuth()
                            return@Thread
                        } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                            appendChainResult("\n需要用户登录，正在处理...\n")
                            handleLogin()
                            return@Thread
                        }
                    } else {
                        appendChainResult("结果: null\n")
                    }
                    
                    appendChainResult("\n")
                    Thread.sleep(200)
                }
                
                appendChainResult("========== 场景执行完成 ==========")
                
            } catch (e: Exception) {
                appendChainResult("执行出错: ${e.message}\n")
                Log.e(TAG, "连续调用执行出错", e)
            }
        }.start()
    }

    /**
     * 构建连续调用步骤的参数
     */
    private fun buildChainStepParams(step: ChainStep, currentStep: Int): Bundle {
        val usedParams = mutableMapOf<String, String>()
        
        return Bundle().apply {
            // 先添加固定参数
            step.params.forEach { (key, value) ->
                putParamByType(this, key, value)
                usedParams[key] = value
            }
            
            // 处理自动填充参数
            step.autoParams.forEach { paramKey ->
                val autoValue = autoFillValue(paramKey, currentStep)
                if (autoValue != null) {
                    appendChainResult("【自动填充】$paramKey = ${autoValue.take(50)}${if (autoValue.length > 50) "..." else ""}\n")
                    putParamByType(this, paramKey, autoValue)
                    usedParams[paramKey] = autoValue
                } else {
                    appendChainResult("【警告】无法自动填充参数: $paramKey\n")
                }
            }
            
            // 保存本步骤使用的参数，供后续步骤复用
            val extractedData = stepExtractedData[currentStep] ?: ExtractedData()
            extractedData.inputParams = usedParams
            stepExtractedData[currentStep] = extractedData
        }
    }

    /**
     * 根据参数名猜测类型并放入Bundle
     */
    private fun putParamByType(bundle: Bundle, key: String, value: String) {
        when {
            key.endsWith("List") || key == "midList" || key == "songIdList" -> {
                val list = if (value.contains("\n")) {
                    ArrayList(value.lines().filter { it.isNotEmpty() })
                } else {
                    ArrayList(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                }
                bundle.putStringArrayList(key, list)
            }
            key == "folderType" || key == "page" || key == "index" || key == "searchType" -> {
                bundle.putInt(key, value.toIntOrNull() ?: 0)
            }
            key == "firstPage" || key == "fromChorus" -> {
                bundle.putBoolean(key, value.toBoolean())
            }
            else -> {
                bundle.putString(key, value)
            }
        }
    }

    /**
     * 自动填充参数值
     */
    private fun autoFillValue(paramKey: String, currentStep: Int): String? {
        for (step in (currentStep - 1) downTo 1) {
            val extractedData = stepExtractedData[step] ?: continue
            
            when (paramKey) {
                "midList" -> {
                    // 优先从输入参数中获取（用于复用第一步的输入）
                    extractedData.inputParams["midList"]?.let { return it }
                    // 其次从提取的歌曲列表中获取
                    if (extractedData.songMidList.isNotEmpty()) {
                        return extractedData.songMidList.joinToString("\n")
                    }
                }
                "songIdList" -> {
                    extractedData.inputParams["songIdList"]?.let { return it }
                    if (extractedData.songIdList.isNotEmpty()) {
                        return extractedData.songIdList.joinToString("\n")
                    }
                }
                "folderId" -> {
                    extractedData.inputParams["folderId"]?.let { return it }
                    extractedData.folderId?.let { return it }
                    if (extractedData.folderList.isNotEmpty()) {
                        val firstFolder = extractedData.folderList[0]
                        val id = firstFolder.optString("id", "").takeIf { it.isNotEmpty() }
                            ?: firstFolder.optString("folderId", "").takeIf { it.isNotEmpty() }
                        if (id != null) return id
                    }
                }
                "folderType" -> {
                    extractedData.inputParams["folderType"]?.let { return it }
                    extractedData.folderType?.let { return it.toString() }
                    if (extractedData.folderList.isNotEmpty()) {
                        val firstFolder = extractedData.folderList[0]
                        val type = firstFolder.optInt("type", -1).takeIf { it != -1 }
                            ?: firstFolder.optInt("folderType", -1).takeIf { it != -1 }
                        if (type != null) return type.toString()
                    }
                }
            }
        }
        return null
    }

    /**
     * 从JSON中提取数据
     */
    private fun extractDataFromJson(json: String, action: String): ExtractedData {
        val data = ExtractedData(rawJson = json)
        
        try {
            if (json.trim().startsWith("[")) {
                val jsonArray = JSONArray(json)
                val items = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i)
                    if (item != null) {
                        items.add(item)
                    }
                }
                
                if (items.isNotEmpty()) {
                    val firstItem = items[0]
                    
                    if (firstItem.has("mid") || firstItem.has("songMid")) {
                        data.songList = items
                        data.songMidList = items.mapNotNull { 
                            val mid = it.optString("mid", "").takeIf { s -> s.isNotEmpty() }
                                ?: it.optString("songMid", "").takeIf { s -> s.isNotEmpty() }
                            mid
                        }
                        data.songIdList = items.mapNotNull {
                            val id = it.optString("id", "").takeIf { s -> s.isNotEmpty() && s != "0" }
                                ?: it.optString("songId", "").takeIf { s -> s.isNotEmpty() && s != "0" }
                            id
                        }
                    } else if (firstItem.has("id") || firstItem.has("folderId")) {
                        data.folderList = items
                        if (items.isNotEmpty()) {
                            data.folderId = firstItem.optString("id", "").takeIf { it.isNotEmpty() } 
                                ?: firstItem.optString("folderId", "").takeIf { it.isNotEmpty() }
                            data.folderType = firstItem.optInt("type", firstItem.optInt("folderType", -1))
                                .takeIf { it != -1 }
                        }
                    }
                }
            } else if (json.trim().startsWith("{")) {
                val jsonObj = JSONObject(json)
                
                if (jsonObj.has("folderId")) {
                    data.folderId = jsonObj.optString("folderId")
                }
                if (jsonObj.has("id")) {
                    data.folderId = jsonObj.optString("id")
                }
                if (jsonObj.has("folderType")) {
                    data.folderType = jsonObj.optInt("folderType")
                }
                if (jsonObj.has("type")) {
                    data.folderType = jsonObj.optInt("type")
                }
                if (jsonObj.has("mid")) {
                    data.songMidList = listOf(jsonObj.optString("mid"))
                }
                
                if (jsonObj.has("list")) {
                    val list = jsonObj.optJSONArray("list")
                    if (list != null) {
                        val items = mutableListOf<JSONObject>()
                        for (i in 0 until list.length()) {
                            list.optJSONObject(i)?.let { items.add(it) }
                        }
                        if (items.isNotEmpty() && items[0].has("mid")) {
                            data.songList = items
                            data.songMidList = items.mapNotNull { 
                                it.optString("mid", "").takeIf { s -> s.isNotEmpty() }
                            }
                        }
                    }
                }
            }
            
            // 特殊处理
            if (action == "getFavouriteFolderId" && data.folderId == null) {
                data.folderId = json.trim().removeSurrounding("\"")
            }
            
            // 处理 getCurrentSong 返回的单个歌曲对象
            if (action == "getCurrentSong" && json.trim().startsWith("{")) {
                val jsonObj = JSONObject(json)
                if (jsonObj.has("mid")) {
                    data.songMidList = listOf(jsonObj.optString("mid"))
                }
                if (jsonObj.has("id")) {
                    data.songIdList = listOf(jsonObj.optString("id"))
                }
            }
            
            // 处理 isFavorateMid 返回的布尔数组
            if (action == "isFavorateMid") {
                // isFavorateMid 返回的是 boolean[]，已经在 getDataFromBundle 中转换为字符串
                // 这里不需要额外处理，只是记录一下
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "解析JSON失败: $json", e)
        }
        
        return data
    }

    /**
     * 描述提取的数据
     */
    private fun describeExtractedData(data: ExtractedData): String {
        val parts = mutableListOf<String>()
        if (data.folderList.isNotEmpty()) {
            parts.add("歌单${data.folderList.size}个")
        }
        if (data.folderId != null) {
            parts.add("歌单ID=${data.folderId}")
        }
        if (data.folderType != null) {
            parts.add("歌单类型=${data.folderType}")
        }
        if (data.songList.isNotEmpty()) {
            parts.add("歌曲${data.songList.size}首")
        }
        if (data.songMidList.isNotEmpty()) {
            parts.add("歌曲mid=${data.songMidList.take(3).joinToString(",")}${if(data.songMidList.size > 3) "..." else ""}")
        }
        return if (parts.isEmpty()) "无可提取数据" else parts.joinToString(", ")
    }

    /**
     * 追加连续调用结果
     */
    private fun appendChainResult(text: String) {
        runOnUiThread {
            tvChainResult.append(text)
        }
    }

    /**
     * 处理认证
     */
    private fun handleAuth() {
        runOnUiThread {
            val time = System.currentTimeMillis()
            val nonce = time.toString()
            val encryptString = OpenIDHelper.getEncryptString(nonce)
            CommonCmd.verifyCallerIdentity(this, Config.OPENID_APPID, packageName, encryptString, "qqmusicapidemo://xxx")
        }
    }

    /**
     * 处理登录
     */
    private fun handleLogin() {
        runOnUiThread {
            CommonCmd.loginQQMusic(this, packageName, Config.OPENID_APPID, "qqmusicapidemo://xxx")
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
