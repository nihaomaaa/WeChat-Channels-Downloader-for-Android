# 微信视频号下载

[English](README.en.md) · [中文](README.zh-CN.md)

Android 10+ 视频号下载工具。作者：[nihaomaaa](https://github.com/nihaomaaa)。Kotlin、Jetpack Compose、Miuix。

1.5 保留 Apple 风格的分组界面、液态玻璃悬浮底栏和顶部/底部渐进模糊。设置中可选择 [下载网站](https://sph.miuistore.com/) 或腾讯元宝；默认选择网站。打开应用时自动检测网站，打不开则临时使用元宝，网站恢复后仍按用户的原选择使用。手动选择元宝时始终使用元宝。

网站方式无需配置服务器、API Key 或腾讯会话；元宝方式保留原有手机直连方案，需要在应用内的腾讯官方页面登录。两种方式均不要求自行部署服务器。元宝登录或接口限制仍由腾讯控制，自动切换并不保证解析成功。

> 这是 1.5 候选版本。已通过 40 项 JVM 测试、Lint 和签名 Release 构建；没有可用的连接设备，设备测试和真实完整下载尚待运行验收。旧版测试不能代替本版结果。第三方网站可能停机、限流或调整收费，不保证永久免费或无限次数。

## 使用

1. 安装签名 APK。
2. 复制视频号分享链接后返回 App，或通过其他应用的分享菜单选择本应用。
3. 等待解析结果，点击“下载到相册”。
4. 如切换到元宝且尚未登录，使用“登录腾讯元宝”入口完成官方网页登录，再重试。可在设置中切换解析方式或重新检测网站。
5. 文件保存到系统媒体库的 `Movies/微信视频号下载`，历史支持播放、分享、重新下载和删除。

仅支持可访问的已发布视频；不支持直播。下载失败会保留可续传状态。网站返回特殊的 `enurl` 格式时会明确提示到原站处理。

## 构建

Android Studio 打开此目录，安装 Android SDK 36，推荐 JDK 21。

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

macOS / Linux 使用 `./gradlew`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。首次构建需要联网。私人签名不进入仓库；发布签名环境变量见 [维护说明](docs/MAINTENANCE.md)。自建 Debug APK 与发行 APK 签名不同，不能覆盖安装。

## 结构

| 模块 | 职责 |
| --- | --- |
| `parser/ResolverRouter.kt`、`WebsiteProbe.kt` | 用户选择、网站可用性检查与临时回退；5 秒检查上限，60 秒合并重复检查 |
| `parser/LocalTencentResolver.kt`、`login/` | 保留的手机元宝解析与官方网页登录；与下载网站隔离会话 |
| `parser/MiuiSiteClient.kt` | 加载网站当前公开脚本并请求解析；没有原生 JS 权限桥，没有复制网站签名代码 |
| `parser/MiuiSiteResolver.kt` | 校验返回域名、错误与视频格式，补充真实封面，缓存解析结果 |
| `download/PrefixMaskStore.kt` | 应用私有目录中的文件头掩码，以 SHA-256 标识 |
| `download/HttpTransfer.kt` | 64 KiB 缓冲流式下载；按绝对文件偏移处理文件头，支持断点续传 |
| `download/DownloadEngine.kt` | 下载生命周期、视频验证、MediaStore 发布 |
| `data/HistoryStore.kt` | SQLite 历史，含版本 1 → 2 的兼容迁移 |
| `ui/ProgressiveEdgeBlur.kt` | 顶底三层渐变遮罩模糊，Android 12 以下使用渐变底色 |
| `ui/liquid/` | 原有 Backdrop 液态玻璃与交互实现 |
| `server/` | 可选的旧私人服务源码，当前两种手机解析均不依赖 |

Kotlin 路径以 `app/src/main/java/app/streamsave/` 为根。

## 隐私、维护与许可

使用网站时，分享链接会发送到 sph.miuistore.com；使用元宝时，直接发送到腾讯。启动检测仅请求网站首页，不携带分享链接、账号或 Cookie。视频从校验后的腾讯域名下载。不会把本机视频、历史或腾讯登录会话发送给解析站。解析页面仅允许该站 HTTPS 子资源，禁用文件访问和第三方 Cookie。第三方服务不是本项目所有或运营。

[解析维护](docs/RESOLVER.md) · [验证边界](docs/VERIFICATION.md) · [设计约定](docs/DESIGN.md) · [第三方许可](THIRD_PARTY_NOTICES.md)

本项目自有代码为 MIT；改编的 Backdrop 组件保留 Apache-2.0 许可。应用内提供 GitHub 项目致谢。未打包 Apple 专有字体或 SF Symbols；这是 Android 上的 Apple 风格设计，不是 Apple 或腾讯官方产品。请仅下载你有权保存和使用的内容。
