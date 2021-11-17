### 注意事项

请使用Gradle 5.1.1及其更高版本

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

### 申请AppId
按照以下格式发邮件：

```
title	OpenID申请-XXXXX公司
send	shuozhao@tencent.com
cc	tangotang@tencent.com

正文信息：
1、组织名称	XX公司
2、应用名称	
3、联系人名	（接口人即可）
4、联系电话	（接口人即可）
5、联系邮件	（接受账号开通信息）
6、应用包名	（如果有多个包名就用;分割）
7、应用图标	（ 文件:1K-1M）
8、业务公钥	申请人自己生成的RSA公钥，参见《生成RSA公私钥》小节
```

### 运行Demo
请在编译之前，务必修改业务参数，在`Config.java`中修改如下常量：

```java
public class Config {
    public static final String OPENID_APPID = "";
    public static final String OPENID_APP_PRIVATE_KEY = "";
    public static final String BIND_PLATFORM = "";
}

```
这三个值都要配置，**不可以**留空

请一定注意：**上述三个数值（除BIND_PLATFORM）一定来自OpenID业务，不要与OpenAPI业务混淆**。

### 如何运行

执行如下命令，得到APK文件
>$ gradle :demo:assembleDebug

### 如何安装

执行如下命令，安装示例Demo
>$ adb install ./demo/build/outputs/apk/debug/demo-debug.apk
