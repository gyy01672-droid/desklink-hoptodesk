# DeskLink · HopToDesk 安卓主控

这是基于 HopToDesk 1.46.88 的个人分支。单个 APK 包含原生中文首页，以及同一版本上游已经编译好的 Flutter 界面、Android 组件和 Rust 网络引擎。上游维护者没有为这个分支签名。上游的版权、致谢、授权、密码验证及会话界面均包含在软件中。

输入 Windows 被控端的数字设备号后点「连接电脑」。勾选保存只在该应用的私有存储中保存设备号。首次输入密码、以后是否记住密码、需要人工批准的连接都由 HopToDesk 处理。用「打开 HopToDesk」可以访问原生设备列表和设置。应用只通过同包内的显式 Activity 传送数字设备号。

分支使用独立包名 `cn.desklink.hopcontroller`、私有存储和签名，与官方 HopToDesk 或早期 DeskLink 可以并存。桌面、手机和 Android TV 的启动器均进入新的主页。更新请使用同一分支签名的 APK。官方版本号与核心版本号一致，安卓的包名和签名可以区分两个应用。已移除分支的安装其他 APK 权限；在旧 Android 上手动使用上游更新按钮可能打开官方网页。官方包不能覆盖安装这个分支。

## 构建

需要 Python 3.11+、能在 PATH 中找到的 JDK 17+、Android SDK Build Tools 35.0.0、Android API 35 平台的 android.jar、Google Bundletool 1.18.3、自己的 Android 签名密钥。私钥不随源码提供。将签名密码写入构建环境变量 `DESKLINK_SIGNING_PASSWORD`。本项目构建脚本不显示其值。

```text
python build.py --official hoptodesk.apk --bundletool bundletool-all-1.18.3.jar --tools ANDROID_SDK/build-tools/35.0.0 --android-jar ANDROID_SDK/platforms/android-35/android.jar --keystore MY_KEYSTORE --alias MY_KEY_ALIAS --out DeskLink-Android.apk
```

脚本固定校验上游 APK 和 Google Bundletool 的 SHA-256，编译新的 Java/Dex，使用 Google 工具合并 Android 清单，再对新 APK 进行对齐、签名及签名验证。它检查上游 Dex、所有运行库和应用资源逐文件字节相同。两个 ABI 为 arm64-v8a 和 armeabi-v7a。连接组件使用不同包名下的私有显式入口，设备号不会出现在外发 Intent 中。源码和许可证也附在 APK 的 `assets/desklink-source/`。

- [HopToDesk 官方 APK 与签名校验值](https://www.hoptodesk.com/download)
- [HopToDesk 1.46.88 对应源码](https://gitlab.com/hoptodesk/hoptodesk/-/tree/cc56380b38c962770ed9e0d704235c94f1881248)
- [Google Bundletool 1.18.3](https://github.com/google/bundletool/releases/tag/1.18.3)

本目录代码使用 GNU Affero General Public License v3，完整许可证见 LICENSE。
