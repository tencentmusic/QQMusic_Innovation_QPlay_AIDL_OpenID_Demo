package com.tencent.qqmusic.api.demo.util

import android.os.Bundle
import com.tencent.qqmusic.api.demo.ApiSample
import com.tencent.qqmusic.third.api.contract.IQQMusicApi
import com.tencent.qqmusic.third.api.contract.Keys.API_RETURN_KEY_CODE
import com.tencent.qqmusic.third.api.contract.Keys.API_RETURN_KEY_ERROR

typealias OnBack = (String) -> Unit

object ApiTestUtil {

    fun testApi(method: String, api: IQQMusicApi, onBack: OnBack) {
        when (method) {
            "hi" -> {
                ApiSample.hi(api)
                onBack("hi 方法调用完成")
            }

            "openQQMusic" -> {
                ApiSample.openQQMusic(api)
                onBack("openQQMusic 方法调用完成")
            }

            "playSongMid" -> {
                ApiSample.playSongMid(api, arrayListOf("0039MnYb0qxYhV", "001xd0HI0X9GNq")) {
                    onBack(getShowMsg(it))
                }
            }

            "playMusic" -> {
                val result = ApiSample.playMusic(api)
                onBack("playMusic 返回码: $result")
            }

            "stopMusic" -> {
                val result = ApiSample.stopMusic(api)
                onBack("stopMusic 返回码: $result")
            }

            "pauseMusic" -> {
                val result = ApiSample.pauseMusic(api)
                onBack("pauseMusic 返回码: $result")
            }

            "resumeMusic" -> {
                val result = ApiSample.resumeMusic(api)
                onBack("resumeMusic 返回码: $result")
            }

            "skipToNext" -> {
                val result = ApiSample.skipToNext(api)
                onBack("skipToNext 返回码: $result")
            }

            "skipToPrevious" -> {
                val result = ApiSample.skipToPrevious(api)
                onBack("skipToPrevious 返回码: $result")
            }

            "getPlaybackState" -> {
                val result = ApiSample.getPlaybackState(api)
                onBack("getPlaybackState 返回码: $result")
            }

            "getCurrentSong" -> {
                val song = ApiSample.getCurrentSong(api, com.google.gson.Gson())
                onBack("getCurrentSong: ${song?.title ?: "无当前歌曲"}")
            }

            "addToFavourite" -> {
                ApiSample.addToFavourite(api, arrayListOf("0039MnYb0qxYhV", "001xd0HI0X9GNq")) {
                    onBack(getShowMsg(it))
                }
            }

            "removeFromFavourite" -> {
                ApiSample.removeFromFavourite(api, arrayListOf("0039MnYb0qxYhV", "001xd0HI0X9GNq")) {
                    onBack(getShowMsg(it))
                }
            }

            "addLocalPathToFavourite" -> {
                ApiSample.addLocalPathToFavourite(api, arrayListOf("/sdcard/music/test1.mp3", "/sdcard/music/test2.mp3")) {
                    onBack(getShowMsg(it))
                }
            }

            "removeLocalPathFromFavourite" -> {
                ApiSample.removeLocalPathFromFavourite(api, arrayListOf("/sdcard/music/test1.mp3", "/sdcard/music/test2.mp3")) {
                    onBack(getShowMsg(it))
                }
            }

            "isFavouriteMid" -> {
                ApiSample.isFavouriteMid(api, arrayListOf("0039MnYb0qxYhV", "001xd0HI0X9GNq")) {
                    onBack(getShowMsg(it))
                }
            }

            "isFavouriteLocalPath" -> {
                ApiSample.isFavouriteLocalPath(api, arrayListOf("/sdcard/music/test1.mp3", "/sdcard/music/test2.mp3")) {
                    onBack(getShowMsg(it))
                }
            }

            "registerEventListener" -> {
                ApiSample.registerEventListener(api, arrayListOf("onPlayStateChanged", "onSongChanged")) { event, extra ->
                    onBack("事件监听: $event, 数据: ${extra.toString()}")
                }
                onBack("registerEventListener 注册完成")
            }

            "unregisterEventListener" -> {
                ApiSample.unregisterEventListener(api, arrayListOf("onPlayStateChanged", "onSongChanged")) { event, extra ->
                    onBack("事件取消监听: $event, 数据: ${extra.toString()}")
                }
                onBack("unregisterEventListener 取消注册完成")
            }

            "playFromChorus" -> {
                ApiSample.playFromChorus(api, true)
                onBack("playFromChorus 方法调用完成")
            }

            "playSongId" -> {
                ApiSample.playSongId(api, arrayListOf("123456", "789012")) {
                    onBack(getShowMsg(it))
                }
            }

            "playSongIdAtIndex" -> {
                ApiSample.playSongIdAtIndex(api, arrayListOf("123456", "789012")) {
                    onBack(getShowMsg(it))
                }
            }

            "getTotalTime" -> {
                val totalTime = ApiSample.getTotalTime(api)
                onBack("getTotalTime: ${totalTime}秒")
            }

            "getCurrTime" -> {
                val currTime = ApiSample.getCurrTime(api)
                onBack("getCurrTime: ${currTime}秒")
            }

            "getPlayList" -> {
                ApiSample.getPlayList(api, arrayListOf("123456", "789012")) { json ->
                    onBack("getPlayList: $json")
                }
            }

            "getFolderList" -> {
                ApiSample.getFolderList(api, "0", 1, 1) { json ->
                    onBack("getFolderList: $json")
                }
            }

            "getSongList" -> {
                ApiSample.getSongList(api, "0", 1, 1) { json ->
                    onBack("getSongList: $json")
                }
            }

            "getUserFolderList" -> {
                ApiSample.getUserFolderList(api, "testOpenId", "testOpenToken", "0", 1, 1) { json ->
                    onBack("getUserFolderList: $json")
                }
            }

            "getUserSongList" -> {
                ApiSample.getUserSongList(api, "testOpenId", "testOpenToken", "0", 1, 1) { json ->
                    onBack("getUserSongList: $json")
                }
            }

            "search" -> {
                ApiSample.search(api) {
                    onBack("search 搜索完成")
                }
            }

            "voiceShortcut" -> {
                ApiSample.voiceShortcut(api, "播放音乐") { code ->
                    onBack("voiceShortcut 返回码: $code")
                }
            }

            "voicePlay" -> {
                ApiSample.voicePlay(api, "播放周杰伦的歌", arrayListOf("周杰伦")) { code, json ->
                    onBack("voicePlay 返回码: $code, 数据: $json")
                }
            }

            else -> {
                onBack("未找到对应的测试方法: $method")
            }
        }
    }

    fun testOtherMethod(method: String, api: IQQMusicApi, onBack: OnBack) {
        when(method) {
            "openQQMusic" -> {
                ApiSample.openQQMusic(api)
                onBack("openQQMusic 方法调用完成")
            }
            "playSongLocalPath" -> {
                ApiSample.playSongLocalPath(api, arrayListOf("/sdcard/music/test1.mp3", "/sdcard/music/test2.mp3")) {
                    onBack(getShowMsg(it))
                }
            }
        }
    }

    fun testForBorder(method: String, api: IQQMusicApi, onBack: OnBack) {
        when (method) {
            "playSongMid" -> {
                // 测试空列表
                ApiSample.playSongMid(api, arrayListOf()) {
                    onBack("边界测试 - playSongMid 空列表: ${getShowMsg(it)}")
                }
            }

            "playSongLocalPath" -> {
                // 测试空路径列表
                ApiSample.playSongLocalPath(api, arrayListOf()) {
                    onBack("边界测试 - playSongLocalPath 空列表: ${getShowMsg(it)}")
                }
            }

            "addToFavourite" -> {
                // 测试空收藏列表
                ApiSample.addToFavourite(api, arrayListOf()) {
                    onBack("边界测试 - addToFavourite 空列表: ${getShowMsg(it)}")
                }
            }

            "removeFromFavourite" -> {
                // 测试空收藏列表
                ApiSample.removeFromFavourite(api, arrayListOf()) {
                    onBack("边界测试 - removeFromFavourite 空列表: ${getShowMsg(it)}")
                }
            }

            "addLocalPathToFavourite" -> {
                // 测试空本地路径列表
                ApiSample.addLocalPathToFavourite(api, arrayListOf()) {
                    onBack("边界测试 - addLocalPathToFavourite 空列表: ${getShowMsg(it)}")
                }
            }

            "removeLocalPathFromFavourite" -> {
                // 测试空本地路径列表
                ApiSample.removeLocalPathFromFavourite(api, arrayListOf()) {
                    onBack("边界测试 - removeLocalPathFromFavourite 空列表: ${getShowMsg(it)}")
                }
            }

            "isFavouriteMid" -> {
                // 测试空mid列表
                ApiSample.isFavouriteMid(api, arrayListOf()) {
                    onBack("边界测试 - isFavouriteMid 空列表: ${getShowMsg(it)}")
                }
            }

            "isFavouriteLocalPath" -> {
                // 测试空本地路径列表
                ApiSample.isFavouriteLocalPath(api, arrayListOf()) {
                    onBack("边界测试 - isFavouriteLocalPath 空列表: ${getShowMsg(it)}")
                }
            }

            "registerEventListener" -> {
                // 测试空事件列表
                ApiSample.registerEventListener(api, arrayListOf()) { event, extra ->
                    onBack("边界测试 - registerEventListener 空事件: $event")
                }
                onBack("边界测试 - registerEventListener 空列表注册完成")
            }

            "unregisterEventListener" -> {
                // 测试空事件列表
                ApiSample.unregisterEventListener(api, arrayListOf()) { event, extra ->
                    onBack("边界测试 - unregisterEventListener 空事件: $event")
                }
                onBack("边界测试 - unregisterEventListener 空列表取消注册完成")
            }

            "playSongId" -> {
                // 测试空ID列表
                ApiSample.playSongId(api, arrayListOf()) {
                    onBack("边界测试 - playSongId 空列表: ${getShowMsg(it)}")
                }
            }

            "playSongIdAtIndex" -> {
                // 测试空ID列表
                ApiSample.playSongIdAtIndex(api, arrayListOf()) {
                    onBack("边界测试 - playSongIdAtIndex 空列表: ${getShowMsg(it)}")
                }
            }

            "getPlayList" -> {
                // 测试空ID列表
                ApiSample.getPlayList(api, arrayListOf()) { json ->
                    onBack("边界测试 - getPlayList 空列表: $json")
                }
            }

            "getFolderList" -> {
                // 测试无效参数
                ApiSample.getFolderList(api, "", -1, 0) { json ->
                    onBack("边界测试 - getFolderList 无效参数: $json")
                }
            }

            "getSongList" -> {
                // 测试无效参数
                ApiSample.getSongList(api, "", -1, 0) { json ->
                    onBack("边界测试 - getSongList 无效参数: $json")
                }
            }

            "getUserFolderList" -> {
                // 测试空参数
                ApiSample.getUserFolderList(api, "", "", "", -1, 0) { json ->
                    onBack("边界测试 - getUserFolderList 空参数: $json")
                }
            }

            "getUserSongList" -> {
                // 测试空参数
                ApiSample.getUserSongList(api, "", "", "", -1, 0) { json ->
                    onBack("边界测试 - getUserSongList 空参数: $json")
                }
            }

            "voiceShortcut" -> {
                // 测试空指令
                ApiSample.voiceShortcut(api, "") { code ->
                    onBack("边界测试 - voiceShortcut 空指令返回码: $code")
                }
            }

            "voicePlay" -> {
                // 测试空查询和空slot列表
                ApiSample.voicePlay(api, "", arrayListOf()) { code, json ->
                    onBack("边界测试 - voicePlay 空参数返回码: $code, 数据: $json")
                }
            }

            "getCurrentSong" -> {
                // 测试获取当前歌曲（可能为空）
                val song = ApiSample.getCurrentSong(api, com.google.gson.Gson())
                onBack("边界测试 - getCurrentSong: ${song?.title ?: "无当前歌曲"}")
            }

            else -> {
                onBack("未找到对应的边界测试方法: $method")
            }
        }
    }

    private fun getShowMsg(bundle: Bundle): String {
        val code = bundle.get(API_RETURN_KEY_CODE)
        val subCode = bundle.get("errorType")
        val errMsg = bundle.get(API_RETURN_KEY_ERROR)
        return "code: $code \n subCode: $subCode \n errMsg: $errMsg"
    }

    /**
     * 获取所有可用的测试方法列表
     */
    fun getAllTestMethods(): List<String> {
        return listOf(
            "hi",
            "openQQMusic",
            "playSongMid",
            "playSongLocalPath",
            "playMusic",
            "stopMusic",
            "pauseMusic",
            "resumeMusic",
            "skipToNext",
            "skipToPrevious",
            "getPlaybackState",
            "getCurrentSong",
            "addToFavourite",
            "removeFromFavourite",
            "addLocalPathToFavourite",
            "removeLocalPathFromFavourite",
            "isFavouriteMid",
            "isFavouriteLocalPath",
            "registerEventListener",
            "unregisterEventListener",
            "playFromChorus",
            "playSongId",
            "playSongIdAtIndex",
            "getTotalTime",
            "getCurrTime",
            "getPlayList",
            "getFolderList",
            "getSongList",
            "getUserFolderList",
            "getUserSongList",
            "search",
            "voiceShortcut",
            "voicePlay"
        )
    }

    /**
     * 获取所有可用的边界测试方法列表
     */
    fun getAllBorderTestMethods(): List<String> {
        return listOf(
            "playSongMid",
            "playSongLocalPath",
            "addToFavourite",
            "removeFromFavourite",
            "addLocalPathToFavourite",
            "removeLocalPathFromFavourite",
            "isFavouriteMid",
            "isFavouriteLocalPath",
            "registerEventListener",
            "unregisterEventListener",
            "playSongId",
            "playSongIdAtIndex",
            "getPlayList",
            "getFolderList",
            "getSongList",
            "getUserFolderList",
            "getUserSongList",
            "voiceShortcut",
            "voicePlay",
            "getCurrentSong"
        )
    }
}