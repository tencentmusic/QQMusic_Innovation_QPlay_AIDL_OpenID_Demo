package com.tencent.qqmusic.api.demo;

import com.tencent.qqmusic.third.api.contract.CommonCmd;

public class Config {

    /**
     * 需向QQ音乐发邮件申请，具体参考README
     */
    public static final String OPENID_APPID = "";

    /**
     * 配置接入方自己生成的RSA私钥，由于是Demo，所以直接写在代码中了，真实环境中需要注意私钥泄漏的风险。
     */
    public static final String OPENID_APP_PRIVATE_KEY = "";

    /**
     * 配置平台类型，可选值如下:
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_PHONE }
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_TV}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_CAR}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_PAD}
     */
    public static String BIND_PLATFORM = "";

}