package com.tencent.qqmusic.api.demo

import android.os.Bundle
import android.support.annotation.Nullable
import android.util.Log
import com.google.gson.Gson
import com.tencent.qqmusic.third.api.contract.*
import com.tencent.qqmusic.third.api.contract.ErrorCodes.ERROR_API_NOT_INITIALIZED


//
// Created by tylertan on 2020-03-19.
// Copyright (c) 2020 Tencent. All rights reserved.
//

/**
 * 对[Methods]中的接口进行基础封装
 *
 */
object ApiSample {

    const val TAG = "ApiSample"

    /**
     * 获取接口返回错误码，对应[ErrorCodes]中的错误码列表
     *
     * @return 返回[ErrorCodes.ERROR_OK]代表成功，反之为失败
     */
    private fun apiCode(bundle: Bundle?): Int? {
        return bundle?.getInt(Keys.API_RETURN_KEY_CODE)
    }

    /**
     * 获取接口返回错误详细信息
     *
     */
    private fun apiError(bundle: Bundle?): String? {
        return bundle?.getString(Keys.API_RETURN_KEY_ERROR)
    }

    fun hi(api: IQQMusicApi?) {
        val bundle = Bundle()
        bundle.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION)
        bundle.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, CommonCmd.AIDL_PLATFORM_TYPE_PHONE)
        val result = api?.execute("hi", bundle)
        Log.i(TAG, "sayHi ret:" + result!!.getInt(Keys.API_RETURN_KEY_CODE))
    }

    fun playSongMid(api: IQQMusicApi?, ids: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("midList", ids)
        api?.executeAsync("playSongMid", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    private fun playSongMidAtIndex(api: IQQMusicApi?, songList: List<Data.Song>, index: Int, block: (() -> Unit)) {
        val midList = ArrayList<String>()
        for (i in songList.indices) {
            midList.add(songList[i].mid)
        }

        val params = Bundle()
        params.putStringArrayList("midList", midList)
        params.putInt("index", index)
        Log.i(TAG, "[playSongMidAtIndex] 播放歌曲列表... $index,${songList.size}")
        api?.executeAsync("playSongMidAtIndex", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun playSongLocalPath(api: IQQMusicApi?, pathList: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("pathList", pathList)
        api?.executeAsync("playSongLocalPath", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun playMusic(api: IQQMusicApi?): Int {
        val ret = api?.execute("playMusic", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun stopMusic(api: IQQMusicApi?): Int {
        val ret = api?.execute("stopMusic", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun pauseMusic(api: IQQMusicApi?): Int {
        val ret = api?.execute("pauseMusic", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun resumeMusic(api: IQQMusicApi?): Int {
        val ret = api?.execute("resumeMusic", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun skipToNext(api: IQQMusicApi?): Int {
        val ret = api?.execute("skipToNext", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun skipToPrevious(api: IQQMusicApi?): Int {
        val ret = api?.execute("skipToPrevious", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    fun getPlaybackState(api: IQQMusicApi?): Int {
        val ret = api?.execute("getPlaybackState", null)
        return ret?.getInt(Keys.API_RETURN_KEY_CODE) ?: 0
    }

    @Nullable
    fun getCurrentSong(api: IQQMusicApi?, gson: Gson): Data.Song? {
        val ret = api?.execute("getCurrentSong", null)
        val curSongJson = ret?.getString(Keys.API_RETURN_KEY_DATA)
        return gson.fromJson(curSongJson, Data.Song::class.java)
    }

    fun addToFavourite(api: IQQMusicApi?, midList: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("midList", midList)
        api?.executeAsync("addToFavourite", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun removeFromFavourite(api: IQQMusicApi?, midList: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("midList", midList)
        api?.executeAsync("removeFromFavourite", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun addLocalPathToFavourite(api: IQQMusicApi?, localPathList: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("localPathList", localPathList)
        api?.executeAsync("addLocalPathToFavourite", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun removeLocalPathFromFavourite(api: IQQMusicApi?, localPathList: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("localPathList", localPathList)
        api?.executeAsync("removeLocalPathFromFavourite", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun isFavouriteMid(api: IQQMusicApi?, midList: ArrayList<String>, block: ((ret: BooleanArray?) -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("midList", midList)
        api?.executeAsync("isFavouriteMid", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                val ret = result.getBooleanArray(Keys.API_RETURN_KEY_DATA)
                block(ret)
            }
        })
    }

    fun isFavouriteLocalPath(api: IQQMusicApi?, localPathList: ArrayList<String>, block: ((ret: BooleanArray?) -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("localPathList", localPathList)
        api?.executeAsync("isFavouriteLocalPath", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                val ret = result.getBooleanArray(Keys.API_RETURN_KEY_DATA)
                block(ret)
            }
        })
    }

    fun registerEventListener(api: IQQMusicApi?, events: ArrayList<String>, block: (() -> Unit)) {
        api?.registerEventListener(events, object : IQQMusicApiEventListener.Stub() {
            override fun onEvent(event: String, extra: Bundle) {
                block()
            }
        })
    }

    fun unregisterEventListener(api: IQQMusicApi?, events: ArrayList<String>, block: (() -> Unit)) {
        api?.unregisterEventListener(events, object : IQQMusicApiEventListener.Stub() {
            override fun onEvent(event: String, extra: Bundle) {
                block()
            }
        })
    }

    fun playFromChorus(api: IQQMusicApi?, fromChorus: Boolean) {
        val params = Bundle()
        params.putBoolean("fromChorus", fromChorus)
        api?.execute("playFromChorus", params)
    }

    @Deprecated("Use playSongMid instead.")
    fun playSongId(api: IQQMusicApi?, ids: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("songIdList", ids)
        api?.executeAsync("playSongId", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    @Deprecated("Use playSongMidAtIndex instead.")
    fun playSongIdAtIndex(api: IQQMusicApi?, ids: ArrayList<String>, block: (() -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("songIdList", ids)
        api?.executeAsync("playSongIdAtIndex", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                block()
            }
        })
    }

    fun getTotalTime(api: IQQMusicApi?): Long {
        val result = api?.execute("getTotalTime", null)
        return (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000
    }

    fun getCurrTime(api: IQQMusicApi?): Long {
        val result = api?.execute("getCurrTime", null)
        return (result?.getLong(Keys.API_RETURN_KEY_DATA) ?: 0) / 1000
    }

    fun getPlayList(api: IQQMusicApi?, ids: ArrayList<String>, block: ((json: String?) -> Unit)) {
        val params = Bundle()
        params.putStringArrayList("songIdList", ids)
        api?.executeAsync("getPlayList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(result: Bundle) {
                val json = result.getString(Keys.API_RETURN_KEY_DATA)
                block(json)
            }
        })
    }

    fun getFolderList(api: IQQMusicApi?, folderId: String, folderType: Int, page: Int, block: (json: String?) -> Unit) {
        val params = Bundle().apply {
            putString("folderId", folderId)
            putInt("folderType", folderType)
            putInt("page", page)
        }

        api?.executeAsync("getFolderList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val json = p0?.getString(Keys.API_RETURN_KEY_DATA)
                block(json)
            }
        })
    }

    fun getSongList(api: IQQMusicApi?, folderId: String, folderType: Int, page: Int, block: (json: String?) -> Unit) {
        val params = Bundle().apply {
            putString("folderId", folderId)
            putInt("folderType", folderType)
            putInt("page", page)
        }

        api?.executeAsync("getSongList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val json = p0?.getString(Keys.API_RETURN_KEY_DATA)
                block(json)
            }
        })
    }

    fun getUserFolderList(api: IQQMusicApi?, openId: String, openToken: String, folderId: String, folderType: Int, page: Int, block: (json: String?) -> Unit) {
        val params = Bundle().apply {
            putString("openId", openId)
            putString("openToken", openToken)
            putString("folderId", folderId)
            putInt("folderType", folderType)
            putInt("page", page)
        }

        api?.executeAsync("getUserFolderList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val json = p0?.getString(Keys.API_RETURN_KEY_DATA)
                block(json)
            }
        })
    }

    fun getUserSongList(api: IQQMusicApi?, openId: String, openToken: String, folderId: String, folderType: Int, page: Int, block: (json: String?) -> Unit) {
        val params = Bundle().apply {
            putString("openId", openId)
            putString("openToken", openToken)
            putString("folderId", folderId)
            putInt("folderType", folderType)
            putInt("page", page)
        }

        api?.executeAsync("getUserSongList", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val json = p0?.getString(Keys.API_RETURN_KEY_DATA)
                block(json)
            }
        })
    }

    fun search(api: IQQMusicApi?, block: () -> Unit) {
        val params = Bundle().apply {
            putString("keyword", "周杰伦")
            putInt("searchType", 0)
            putBoolean("firstPage", true)
        }

        api?.executeAsync("search", params, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                block()
            }
        })
    }

    fun voiceShortcut(api: IQQMusicApi?, intent: String, block: (code: Int?) -> Unit) {
        val bundle = Bundle()
        bundle.apply {
            putString("intent", intent)
        }
        api?.executeAsync("voiceShortcut", bundle, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val code = p0?.getInt(Keys.API_RETURN_KEY_CODE) ?: ERROR_API_NOT_INITIALIZED
                block(code)
            }
        })
    }

    fun voicePlay(api: IQQMusicApi?, query: String, slotList: ArrayList<String>, block: (code: Int, json: String?) -> Unit) {
        val bundle = Bundle()
        bundle.apply {
            putString("query", query)
            putStringArrayList("slotList", slotList)
        }
        api?.executeAsync("voicePlay", bundle, object : IQQMusicApiCallback.Stub() {
            override fun onReturn(p0: Bundle?) {
                val code = p0?.getInt(Keys.API_RETURN_KEY_CODE) ?: ERROR_API_NOT_INITIALIZED
                val json = p0?.getString(Keys.API_RETURN_KEY_DATA)
                block(code, json)
            }
        })
    }

}