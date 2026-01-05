package com.tencent.qqmusic.api.demo

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * 连续调用测试Activity
 * 支持配置多个API调用步骤，并按顺序执行
 * 后续步骤可以自动从前面步骤的结果中提取所需数据
 */
@SuppressLint("SetTextI18n")
class ChainCallActivity : AppCompatActivity(), ServiceConnection {

    companion object {
        const val TAG = "ChainCallActivity"
        
        // 参数类型列表
        val PARAM_TYPES = arrayOf("String", "Int", "Long", "Boolean", "StringArrayList", "Auto")
        
        // 预定义模板（简化版，不需要手动写引用表达式）
        val TEMPLATES = mapOf(
            "获取歌单并播放" to listOf(
                StepTemplate("getFolderList", mapOf("folderId" to "0", "folderType" to "200", "page" to "0")),
                StepTemplate("getSongList", mapOf("folderId" to "Auto", "folderType" to "Auto", "page" to "0")),
                StepTemplate("playSongMid", mapOf("midList" to "Auto"))
            ),
            "搜索并播放" to listOf(
                StepTemplate("search", mapOf("keyword" to "周杰伦", "searchType" to "0", "firstPage" to "true")),
                StepTemplate("playSongMid", mapOf("midList" to "Auto"))
            ),
            "获取收藏歌单并播放" to listOf(
                StepTemplate("getFavouriteFolderId", emptyMap()),
                StepTemplate("getSongList", mapOf("folderId" to "Auto", "folderType" to "201", "page" to "0")),
                StepTemplate("playSongMid", mapOf("midList" to "Auto"))
            ),
            "获取最近播放并播放" to listOf(
                StepTemplate("getSongList", mapOf("folderId" to "0", "folderType" to "203", "page" to "0")),
                StepTemplate("playSongMid", mapOf("midList" to "Auto"))
            )
        )
        
        // 参数名与数据类型的映射关系，用于自动提取
        val PARAM_DATA_MAPPING = mapOf(
            // 歌曲相关
            "midList" to DataType.SONG_MID_LIST,
            "songIdList" to DataType.SONG_ID_LIST,
            // 歌单相关
            "folderId" to DataType.FOLDER_ID,
            "folderType" to DataType.FOLDER_TYPE
        )
        
        // 同步调用的方法列表（其他都是异步调用）
        val SYNC_METHODS = setOf(
            "hi",
            "openMusic",
            "playMusic",
            "stopMusic",
            "pauseMusic",
            "resumeMusic",
            "skipToNext",
            "skipToPrevious",
            "getPlaybackState",
            "getCurrentSong",
            "getTotalTime",
            "getCurrTime",
            "playFromChorus",
            "setPlayMode",
            "getPlayMode",
            "seekTo",
            "getVolume",
            "setVolume"
        )
    }
    
    // 数据类型枚举
    enum class DataType {
        SONG_MID_LIST,      // 歌曲mid列表
        SONG_ID_LIST,       // 歌曲id列表
        FOLDER_ID,          // 歌单ID
        FOLDER_TYPE,        // 歌单类型
        UNKNOWN
    }
    
    // 存储从各步骤提取的数据
    data class ExtractedData(
        var folderList: List<JSONObject> = emptyList(),  // 歌单列表
        var songList: List<JSONObject> = emptyList(),    // 歌曲列表
        var folderId: String? = null,                     // 单个歌单ID
        var folderType: Int? = null,                      // 歌单类型
        var songMidList: List<String> = emptyList(),     // 歌曲mid列表
        var songIdList: List<String> = emptyList(),      // 歌曲id列表
        var rawJson: String? = null                       // 原始JSON
    )

    data class StepTemplate(val action: String, val params: Map<String, String>)

    // 存储每个步骤的执行结果
    private val stepResults = mutableMapOf<Int, Bundle>()
    
    // 存储每个步骤提取的数据
    private val stepExtractedData = mutableMapOf<Int, ExtractedData>()
    
    // 存储步骤视图
    private val stepViews = mutableListOf<View>()

    private val connectStateTextView by lazy { findViewById<TextView>(R.id.tv_connect_state) }
    private val stepsContainer by lazy { findViewById<LinearLayout>(R.id.steps_container) }
    private val btnAddStep by lazy { findViewById<Button>(R.id.btn_add_step) }
    private val btnLoadTemplate by lazy { findViewById<Button>(R.id.btn_load_template) }
    private val btnExecuteChain by lazy { findViewById<Button>(R.id.btn_execute_chain) }
    private val tvResult by lazy { findViewById<TextView>(R.id.tv_result) }
    private val toolBar by lazy { findViewById<Toolbar>(R.id.toolbar) }

    private var qqmusicApi: IQQMusicApi? = null

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        qqmusicApi = IQQMusicApi.Stub.asInterface(service)
        btnExecuteChain.isEnabled = true
        connectStateTextView.text = "连接状态: connected"
    }

    override fun onServiceDisconnected(name: ComponentName) {
        btnExecuteChain.isEnabled = false
        connectStateTextView.text = "连接状态: disconnected"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chain_call)

        setSupportActionBar(toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "连续调用测试"
        toolBar.setNavigationOnClickListener { onBackPressed() }

        btnAddStep.setOnClickListener { addStep() }
        
        btnLoadTemplate.setOnClickListener { showTemplateDialog() }
        
        btnExecuteChain.setOnClickListener { executeChain() }
        btnExecuteChain.isEnabled = false

        // 绑定服务
        connectStateTextView.text = "connecting..."
        val bindRet = bindQQMusicApiService(Config.BIND_PLATFORM)
        if (!bindRet) {
            connectStateTextView.text = "failed to connect"
        }

        // 默认添加一个步骤
        addStep()
    }

    /**
     * 添加一个执行步骤
     */
    private fun addStep(template: StepTemplate? = null) {
        val stepNumber = stepViews.size + 1
        val stepView = LayoutInflater.from(this).inflate(R.layout.item_chain_step, stepsContainer, false)

        val tvStepNumber = stepView.findViewById<TextView>(R.id.tv_step_number)
        val etAction = stepView.findViewById<AutoCompleteTextView>(R.id.et_action)
        val paramsContainer = stepView.findViewById<LinearLayout>(R.id.params_container)
        val btnAddParam = stepView.findViewById<Button>(R.id.btn_add_param)
        val btnRemoveStep = stepView.findViewById<Button>(R.id.btn_remove_step)
        val tvStepResult = stepView.findViewById<TextView>(R.id.tv_step_result)

        tvStepNumber.text = "步骤 $stepNumber"

        // 设置action自动完成
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, MainActivity.ACTIONS)
        etAction.setAdapter(adapter)

        // 监听action选择，自动生成参数
        etAction.setOnItemClickListener { _, _, position, _ ->
            val selectedAction = adapter.getItem(position) ?: return@setOnItemClickListener
            generateParamsForAction(selectedAction, paramsContainer, stepView)
        }

        // 添加参数按钮
        btnAddParam.setOnClickListener {
            addParamToStep(paramsContainer, stepView)
        }

        // 删除步骤按钮
        btnRemoveStep.setOnClickListener {
            stepsContainer.removeView(stepView)
            stepViews.remove(stepView)
            updateStepNumbers()
        }

        // 存储参数视图列表
        stepView.tag = mutableListOf<View>()

        // 如果有模板，填充数据
        if (template != null) {
            etAction.setText(template.action)
            generateParamsForAction(template.action, paramsContainer, stepView, template.params)
        }

        stepViews.add(stepView)
        stepsContainer.addView(stepView)
    }

    /**
     * 更新步骤编号
     */
    private fun updateStepNumbers() {
        stepViews.forEachIndexed { index, view ->
            view.findViewById<TextView>(R.id.tv_step_number).text = "步骤 ${index + 1}"
        }
    }

    /**
     * 根据选择的方法自动生成参数输入框
     */
    private fun generateParamsForAction(
        action: String, 
        paramsContainer: LinearLayout, 
        stepView: View,
        templateParams: Map<String, String>? = null
    ) {
        paramsContainer.removeAllViews()
        @Suppress("UNCHECKED_CAST")
        val paramViewsList = stepView.tag as MutableList<View>
        paramViewsList.clear()

        val params = MainActivity.METHOD_PARAMS[action] ?: emptyList()
        val examples = templateParams ?: MainActivity.EXAMPLE_DATA[action] ?: emptyMap()

        params.forEach { (paramName, paramType) ->
            if (paramType != "Callback" && paramType != "EventListener") {
                val exampleValue = examples[paramName] ?: ""
                // 如果值是"Auto"，使用Auto类型
                val finalType = if (exampleValue == "Auto") "Auto" else paramType
                addParamToStep(paramsContainer, stepView, paramName, exampleValue, finalType)
            }
        }
    }

    /**
     * 为步骤添加参数
     * @param isAuto 是否为自动填充参数（不需要用户输入）
     */
    private fun addParamToStep(
        paramsContainer: LinearLayout,
        stepView: View,
        key: String = "",
        value: String = "",
        type: String = "String",
        isAuto: Boolean = false
    ) {
        // 如果是Auto类型，显示简化的标签视图
        if (isAuto || value == "Auto") {
            addAutoParamLabel(paramsContainer, stepView, key)
            return
        }
        
        val paramView = LayoutInflater.from(this).inflate(R.layout.item_param, paramsContainer, false)

        val spinnerType = paramView.findViewById<Spinner>(R.id.spinner_param_type)
        val etKey = paramView.findViewById<EditText>(R.id.et_param_key)
        val etValue = paramView.findViewById<EditText>(R.id.et_param_value)
        val btnRemove = paramView.findViewById<Button>(R.id.btn_remove_param)

        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, PARAM_TYPES)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = typeAdapter
        spinnerType.setSelection(PARAM_TYPES.indexOf(type).coerceAtLeast(0))

        etKey.setText(key)
        etValue.setText(value)

        btnRemove.setOnClickListener {
            paramsContainer.removeView(paramView)
            @Suppress("UNCHECKED_CAST")
            (stepView.tag as MutableList<View>).remove(paramView)
        }

        @Suppress("UNCHECKED_CAST")
        (stepView.tag as MutableList<View>).add(paramView)
        paramsContainer.addView(paramView)
    }
    
    /**
     * 添加自动填充参数的标签（简化显示，不需要用户输入）
     */
    private fun addAutoParamLabel(
        paramsContainer: LinearLayout,
        stepView: View,
        key: String
    ) {
        // 创建一个简单的TextView来显示自动填充的参数
        val labelView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
            
            // 参数名标签
            addView(TextView(context).apply {
                text = "$key: "
                setTextColor(0xFF333333.toInt())
                textSize = 14f
            })
            
            // 自动填充提示
            addView(TextView(context).apply {
                text = "🔄 自动从前一步获取"
                setTextColor(0xFF666666.toInt())
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.ITALIC)
            })
        }
        
        // 标记为Auto类型参数
        labelView.tag = AutoParamInfo(key)
        
        @Suppress("UNCHECKED_CAST")
        (stepView.tag as MutableList<View>).add(labelView)
        paramsContainer.addView(labelView)
    }
    
    /**
     * 自动参数信息类
     */
    data class AutoParamInfo(val key: String)

    /**
     * 显示模板选择对话框
     */
    private fun showTemplateDialog() {
        val templateNames = TEMPLATES.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择模板")
            .setItems(templateNames) { _, which ->
                val templateName = templateNames[which]
                loadTemplate(TEMPLATES[templateName] ?: emptyList())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 加载模板
     */
    private fun loadTemplate(templates: List<StepTemplate>) {
        // 清空现有步骤
        stepsContainer.removeAllViews()
        stepViews.clear()
        stepResults.clear()
        stepExtractedData.clear()

        // 添加模板步骤
        templates.forEach { template ->
            addStep(template)
        }
    }

    /**
     * 执行连续调用
     */
    private fun executeChain() {
        if (stepViews.isEmpty()) {
            Toast.makeText(this, "请先添加执行步骤", Toast.LENGTH_SHORT).show()
            return
        }

        stepResults.clear()
        stepExtractedData.clear()
        tvResult.text = "开始执行...\n"

        Thread {
            try {
                stepViews.forEachIndexed { index, stepView ->
                    val stepNumber = index + 1
                    val etAction = stepView.findViewById<AutoCompleteTextView>(R.id.et_action)
                    val action = etAction.text.toString()

                    if (action.isEmpty()) {
                        appendResult("步骤 $stepNumber: 未选择接口，跳过\n")
                        return@forEachIndexed
                    }

                    appendResult("========== 步骤 $stepNumber: $action ==========\n")

                    // 构建参数，自动填充Auto类型的参数
                    val params = buildParamsWithAutoFill(stepView, stepNumber)
                    appendResult("参数: ${params.toPrintableString()}\n")

                    // 根据方法类型选择同步或异步调用
                    val isSyncMethod = SYNC_METHODS.contains(action)
                    
                    if (isSyncMethod) {
                        // 同步调用
                        appendResult("[同步调用]\n")
                        val result = qqmusicApi?.execute(action, params)
                        val shouldContinue = handleStepResult(result, stepNumber, stepView, action)
                        if (!shouldContinue) {
                            return@Thread
                        }
                    } else {
                        // 异步调用，使用CountDownLatch等待结果
                        appendResult("[异步调用]\n")
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var asyncResult: Bundle? = null
                        
                        qqmusicApi?.executeAsync(action, params, object : IQQMusicApiCallback.Stub() {
                            override fun onReturn(result: Bundle?) {
                                asyncResult = result
                                latch.countDown()
                            }
                        })
                        
                        // 等待异步结果，最多等待30秒
                        val success = latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                        if (!success) {
                            appendResult("【超时】异步调用超时\n")
                        }
                        
                        val shouldContinue = handleStepResult(asyncResult, stepNumber, stepView, action)
                        if (!shouldContinue) {
                            return@Thread
                        }
                    }

                    // 步骤间延迟，避免过快调用
                    Thread.sleep(200)
                }

                appendResult("========== 全部执行完成 ==========")

            } catch (e: Exception) {
                appendResult("执行出错: ${e.message}\n")
                Log.e(TAG, "执行出错", e)
            }
        }.start()
    }

    /**
     * 处理步骤执行结果
     * @return true表示继续执行，false表示需要中断（如需要认证）
     */
    private fun handleStepResult(result: Bundle?, stepNumber: Int, stepView: View, action: String): Boolean {
        if (result != null) {
            stepResults[stepNumber] = result
            
            val code = result.getInt(Keys.API_RETURN_KEY_CODE)
            val json = result.getString(Keys.API_RETURN_KEY_DATA)
            
            appendResult("code: $code\n")
            appendResult("data: ${json?.take(500) ?: "null"}${if ((json?.length ?: 0) > 500) "..." else ""}\n\n")

            // 提取数据供后续步骤使用
            if (code == ErrorCodes.ERROR_OK && json != null) {
                val extractedData = extractDataFromJson(json, action)
                stepExtractedData[stepNumber] = extractedData
                appendResult("【自动提取】: ${describeExtractedData(extractedData)}\n\n")
            }

            // 更新步骤结果显示
            runOnUiThread {
                val tvStepResult = stepView.findViewById<TextView>(R.id.tv_step_result)
                tvStepResult.visibility = View.VISIBLE
                tvStepResult.text = if (code == ErrorCodes.ERROR_OK) "✓ 执行成功" else "✗ 错误码: $code"
            }

            // 检查是否需要认证
            if (code == ErrorCodes.ERROR_API_NO_PERMISSION) {
                appendResult("需要验证身份，正在处理...\n")
                handleAuth()
                return false
            } else if (code == ErrorCodes.ERROR_NEED_USER_AUTHENTICATION) {
                appendResult("需要用户登录，正在处理...\n")
                handleLogin()
                return false
            }
        } else {
            appendResult("结果: null\n\n")
        }
        return true
    }

    /**
     * 从JSON中提取数据
     */
    private fun extractDataFromJson(json: String, action: String): ExtractedData {
        val data = ExtractedData(rawJson = json)
        
        try {
            // 尝试解析为数组（歌单列表或歌曲列表）
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
                    
                    // 判断是歌曲列表还是歌单列表
                    if (firstItem.has("mid") || firstItem.has("songMid")) {
                        // 歌曲列表
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
                        Log.d(TAG, "提取到歌曲列表: ${data.songMidList.size}首")
                    } else if (firstItem.has("id") || firstItem.has("folderId")) {
                        // 歌单列表
                        data.folderList = items
                        // 默认取第一个歌单的ID和类型
                        if (items.isNotEmpty()) {
                            data.folderId = firstItem.optString("id", "").takeIf { it.isNotEmpty() } 
                                ?: firstItem.optString("folderId", "").takeIf { it.isNotEmpty() }
                            data.folderType = firstItem.optInt("type", firstItem.optInt("folderType", -1))
                                .takeIf { it != -1 }
                        }
                        Log.d(TAG, "提取到歌单列表: ${data.folderList.size}个")
                    }
                }
            } else if (json.trim().startsWith("{")) {
                // 尝试解析为对象
                val jsonObj = JSONObject(json)
                
                // 检查是否有歌单ID
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
                
                // 检查是否有歌曲mid
                if (jsonObj.has("mid")) {
                    data.songMidList = listOf(jsonObj.optString("mid"))
                }
                
                // 检查是否有嵌套的列表
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
            
            // 特殊处理 getFavouriteFolderId 返回的数据
            if (action == "getFavouriteFolderId" && data.folderId == null) {
                // 返回可能是纯字符串
                data.folderId = json.trim().removeSurrounding("\"")
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
     * 构建参数Bundle，自动填充Auto类型的参数
     */
    private fun buildParamsWithAutoFill(stepView: View, currentStep: Int): Bundle {
        @Suppress("UNCHECKED_CAST")
        val paramViews = stepView.tag as MutableList<View>

        return Bundle().apply {
            paramViews.forEach { paramView ->
                // 检查是否是Auto类型参数（简化标签）
                val autoParamInfo = paramView.tag as? AutoParamInfo
                if (autoParamInfo != null) {
                    // Auto类型参数，自动填充
                    val key = autoParamInfo.key
                    val autoValue = autoFillValue(key, currentStep)
                    if (autoValue != null) {
                        appendResult("【自动填充】$key = ${autoValue.take(100)}${if (autoValue.length > 100) "..." else ""}\n")
                        val actualType = guessTypeByKey(key)
                        putParamByType(this, key, autoValue, actualType)
                    } else {
                        appendResult("【警告】无法自动填充参数: $key\n")
                    }
                    return@forEach
                }
                
                // 普通参数输入框
                val spinnerType = paramView.findViewById<Spinner>(R.id.spinner_param_type) ?: return@forEach
                val etKey = paramView.findViewById<EditText>(R.id.et_param_key) ?: return@forEach
                val etValue = paramView.findViewById<EditText>(R.id.et_param_value) ?: return@forEach

                val key = etKey.text.toString().trim()
                var value = etValue.text.toString().trim()
                val type = spinnerType.selectedItem.toString()

                if (key.isEmpty()) return@forEach

                // 处理Auto类型或空值需要自动填充的情况
                if (type == "Auto" || value.isEmpty()) {
                    val autoValue = autoFillValue(key, currentStep)
                    if (autoValue != null) {
                        value = autoValue
                        appendResult("【自动填充】$key = ${value.take(100)}${if (value.length > 100) "..." else ""}\n")
                    }
                }

                try {
                    // 根据参数名决定实际类型
                    val actualType = if (type == "Auto") guessTypeByKey(key) else type
                    putParamByType(this, key, value, actualType)
                } catch (e: Exception) {
                    Log.e(TAG, "参数转换失败: key=$key, value=$value, type=$type", e)
                }
            }
        }
    }
    
    /**
     * 根据类型将参数放入Bundle
     */
    private fun putParamByType(bundle: Bundle, key: String, value: String, type: String) {
        when (type) {
            "String" -> bundle.putString(key, value)
            "Int" -> bundle.putInt(key, value.toIntOrNull() ?: 0)
            "Long" -> bundle.putLong(key, value.toLongOrNull() ?: 0L)
            "Boolean" -> bundle.putBoolean(key, value.toBoolean())
            "StringArrayList" -> {
                val list = if (value.contains("\n")) {
                    ArrayList(value.lines().filter { it.isNotEmpty() })
                } else {
                    ArrayList(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                }
                bundle.putStringArrayList(key, list)
            }
        }
    }
    
    /**
     * 根据参数名猜测类型
     */
    private fun guessTypeByKey(key: String): String {
        return when {
            key.endsWith("List") || key == "midList" || key == "songIdList" -> "StringArrayList"
            key == "folderType" || key == "page" || key == "index" || key == "searchType" -> "Int"
            key == "firstPage" || key == "fromChorus" -> "Boolean"
            else -> "String"
        }
    }

    /**
     * 自动填充参数值
     */
    private fun autoFillValue(paramKey: String, currentStep: Int): String? {
        // 从最近的步骤开始查找
        for (step in (currentStep - 1) downTo 1) {
            val extractedData = stepExtractedData[step] ?: continue
            
            when (paramKey) {
                // 歌曲相关
                "midList" -> {
                    if (extractedData.songMidList.isNotEmpty()) {
                        return extractedData.songMidList.joinToString("\n")
                    }
                }
                "songIdList" -> {
                    if (extractedData.songIdList.isNotEmpty()) {
                        return extractedData.songIdList.joinToString("\n")
                    }
                }
                // 歌单相关
                "folderId" -> {
                    extractedData.folderId?.let { return it }
                    // 如果有歌单列表，取第一个
                    if (extractedData.folderList.isNotEmpty()) {
                        val firstFolder = extractedData.folderList[0]
                        val id = firstFolder.optString("id", "").takeIf { it.isNotEmpty() }
                            ?: firstFolder.optString("folderId", "").takeIf { it.isNotEmpty() }
                        if (id != null) return id
                    }
                }
                "folderType" -> {
                    extractedData.folderType?.let { return it.toString() }
                    // 如果有歌单列表，取第一个的类型
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
     * 追加结果显示
     */
    private fun appendResult(text: String) {
        runOnUiThread {
            tvResult.append(text)
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
