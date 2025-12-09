package com.tencent.qqmusic.api.demo;

import com.tencent.qqmusic.third.api.contract.CommonCmd;

public class Config {

    public static String OPENID_APPID = "";

    public static String OPENID_APP_PRIVATE_KEY = "";

    /**
     * 配置平台类型，可选值如下:
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_PHONE }
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_TV}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_CAR}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_PAD}
     */
    public static String BIND_PLATFORM = CommonCmd.AIDL_PLATFORM_TYPE_PHONE;


}