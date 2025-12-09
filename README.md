### 注意事项

请使用Gradle 5.6.4及其更高版本

### 生成RSA公私钥
QQ音乐使用的RSA密钥位数为1024位，密钥格式使用PKCS#8，有两种生成方式：

一、使用OpenSSL来生成：
1. 命令生成原始 RSA私钥文件 rsa_private_key.pem

>$ openssl genrsa -out rsa_private_key.pem 1024

2. 命令将原始 RSA私钥转换为 pkcs8格式，得私钥文件到private_key.pem

>$ openssl pkcs8 -topk8 -inform PEM -in rsa_private_key.pem -outform PEM -nocrypt -out private_key.pem

3. 生成RSA公钥文件 rsa_public_key.pem

>$ openssl rsa -in rsa_private_key.pem -pubout -out rsa_public_key.pem

二、使用Demo中的RSAUtils.genKeyPair生成：

```
val keyPair = RSAUtils.genKeyPair()
val publicKey = RSAUtils.getPublicKey(keyPair)
val privateKey = RSAUtils.getPrivateKey(keyPair)
```
生成的RSA公钥需要发给QQ音乐，而私钥需要自行妥善保管


### 运行Demo
请在编译之前，务必修改业务参数，在`Config.java`中修改如下常量：

```java
public class Config {
    public static final String OPENID_APPID = ""; // 请使用开发者平台申请得到的appid(200000***)
    public static final String OPENID_APP_PRIVATE_KEY = "";// 请使用合作方自己生成的RSA私钥,别忘了把对应的RSA公钥提供给QQ音乐配置
    public static final String BIND_PLATFORM = ""; // 如果需要连接手机端QQ音乐App 请参考sdk中CommonCmd.AIDL_PLATFORM_TYPE_PHONE变量
}

```
这三个值都要配置，**不可以**留空

### 如何运行

执行如下命令，得到APK文件
>$ gradle :demo:assembleDebug

### 如何安装

执行如下命令，安装示例Demo
>$ adb install ./demo/build/outputs/apk/debug/demo-debug.apk