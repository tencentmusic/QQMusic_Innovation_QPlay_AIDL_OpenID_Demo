package com.tencent.qqmusic.api.demo

import android.os.Bundle
import com.tencent.qqmusic.third.api.contract.IQQMusicApi


//
// Created by tylertan on 2020-03-19.
// Copyright (c) 2020 Tencent. All rights reserved.
//

object ApiSample {

    fun playFromChorus(api: IQQMusicApi?, fromChorus: Boolean) {
        val params = Bundle()
        params.putBoolean("fromChorus", fromChorus)
        api?.execute("playFromChorus", params)
    }

}