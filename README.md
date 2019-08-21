### 注意事项

请使用Gradle 5.1.1及其更高版本

### 如何配置

请在编译之前，务必修改业务参数，在`Config.java`中修改如下变量：

```java
public static final String OPENID_APPID = "";
public static final String OPENID_APP_PRIVATE_KEY = "";
public static final String BIND_PLATFORM = "";
```

请在`build.gradle`中修改如下变量：

```
applicationId ""
```

请一定注意：**上述四个数值一定来自OpenID业务，不要与OpenAPI业务混淆**。

### 如何运行

执行如下命令，得到APK文件
>$ gradle :demo:assembleDebug

### 如何安装

执行如下命令，安装示例Demo
>$ adb install ./demo/build/outputs/apk/debug/demo-debug.apk
