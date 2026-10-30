# 1.5 当前维护入口

当前入口为 ResolverRouter：默认选下载网站，网站打不开时临时使用 LocalTencentResolver；用户可在设置中手动选元宝。Preferences 持久化用户选择，WebsiteProbe 检测可用性。元宝需要本机腾讯会话，不需要自建服务。文件头处理仍为 PrefixMaskStore + HttpTransfer。详见 RESOLVER.md；界面见 DESIGN.md。以下版本编号说明历史演变，签名步骤仍适用。

# 维护与发布

## 先读什么

从 `MainActivity → MainViewModel → VideoResolver / TransferScheduler` 开始。AppGraph 是一个简单的构造器组合，不使用 DI 框架。UI 不直接调用解析服务，也不持有下载协程。解析契约是 `VideoInfo`；加入新解析方式应替换 `VideoResolver`，不要修改 MediaStore 或下载页面。

使用真实 Miuix 0.8.8 组件和颜色系统。`GlassNavigation.kt` 与 `ui/liquid/` 使用 Kyant AndroidLiquidGlass 1.0.6 的 Apache-2.0 示例和 Backdrop 渲染库，它也是 Miuix 液态玻璃示例所引用的上游。API 33+ 使用 RuntimeShader 实现真实背景透镜、色散、弹性拖动；低版本使用库的兼容降级。修改时保留 UPSTREAM.md 和许可证，不能只换成半透明胶囊。根节点提供 LocalContentColor，避免深色文字变黑。InstallerX-Revived 是视觉参考，未复制其 GPL 代码。

## 数据和状态

- SQLite `HistoryStore` 的每条记录保存来源、真实媒体 URL、进度、HTTP 校验标识和媒体 URI。升级 schema 必须新增显式迁移，不能删除历史重建数据库。
- `QUEUED → DOWNLOADING → SAVING → COMPLETE`；网络失败为 `FAILED`，系统中断为 `PAUSED`，用户取消为 `CANCELLED`。
- 同一来源的活跃下载去重。协调层 Mutex 与引擎的运行 ID 集合分别避免重复调度和重复写文件。
- 私有文件 `filesDir/transfers/<id>.part` 是断点依据；只有强 ETag 或 Last-Modified 才使用 If-Range。206 必须从期望偏移开始；返回 200 则截断重写，不能拼接两份文件。
- 重试超过 15 分钟的地址会重新解析，地址变化时丢弃旧片段。失败保留片段，取消和删除清理片段。
- 完整 MP4 必须有 `ftyp` 和可识别视频轨道。MediaStore 行在写入期间 `IS_PENDING=1`，完成后发布为 0；只报告发布完成的视频为成功。
- URI 在复制之前写数据库。进程若在发布前终止，下次重试会清理 pending 行；在发布后终止则识别已发布行，避免生成重复视频。
- 相册与数据库不是跨进程事务；短暂发布阶段不可取消。不要扩大 NonCancellable 范围到下载或大文件复制。
- 移除历史和删除相册视频是两个明确选项。播放前检查 URI；外部已删除的文件不应使 App 崩溃。

## Android 后台行为

Android 14+ 使用系统 User Initiated Data Transfer Job；Android 10–13 使用 dataSync 前台服务与有超时的 PARTIAL_WAKE_LOCK。下载从用户点击发起，均展示系统通知；通知权限被拒绝不阻止用户发起的下载。JobService 使用系统分配的 Network。

不使用 `NETWORK_TYPE_ANY`，因为 AOSP 会追加 VALIDATED 条件；在无法访问系统探测域名的网络上可能永不启动。当前明确要求 INTERNET，实际连接错误由下载层反馈。参见 [AOSP JobInfo](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/job/JobInfo.java) 与 [官方 UIDT 文档](https://developer.android.com/develop/background-work/background-tasks/uidt)。

Activity 重建仅重建 UI；前台任务不跟随 Activity 销毁。用户强制停止与系统重启不强行绕过，下次打开根据 SQLite 和系统任务列表显示继续按钮。HyperOS 真机仍需测试锁屏、后台省电和用户主动停止场景。

## 凭证与日志

元宝路线使用 LocalTencentResolver 在手机内请求腾讯。TencentLoginActivity 展示官方网页登录，TencentSession 只读取本应用 WebView CookieManager 中的元宝会话；Cookie 不写日志、不传给 Channels/CDN、没有 JS 密码桥接。只有真实解析成功才标记会话已验证。网页记录由 Android 应用隔离保护，不宣称采用了旧私人密钥的 Keystore 加密。清除会话或重新保存会递增 revision，旧请求不能重新标记已退出状态。ResolvedVideoCache 按会话摘要隔离，五分钟过期。所有应用备份关闭。PrivateVideoResolver / PrivateServiceSettings 及 server/ 保留为 1.2 历史参考和测试，当前 AppGraph 不使用。

release 只允许 HTTPS。仅 debug 的网络安全配置允许 localhost 明文，服务于真实本机 HTTP 集成测试；测试生成的 MP4、MockWebServer 和示例响应均只在测试 source set，release 不包含这些测试路径。

## 修改后的检查

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

下载、状态或 MediaStore 改动必须跑设备测试。解析改动先运行 JVM 测试，登录和退出改动跑 TencentSessionTest / LoginActivityTest；在 debug App 中完成本人官方网页登录后，显式启用 LiveDownloadTest。LocalMetadataLiveTest 只验证匿名元数据，不等于真实下载。腾讯会话、口令和验证码不进入测试参数或日志。完整下载并解码才算成功，不得用旧公共服务记录或测试响应替代新的真实验收。UI 改动查看浅色、深色、系统字体缩放和窄屏，尤其检查底部悬浮栏遮挡。测试之外的条件见 `VERIFICATION.md`。

`.github/workflows/android.yml` 提供构建和 API 36 模拟器 CI；本次没有向 GitHub 推送或触发此工作流。

## Release 签名

交付 APK 使用单独生成的 RSA 3072 / PKCS12 发布密钥，不是 debug 密钥。交付目录外置 `signing/streamsave-release.p12` 和 `signing/private-signing.json`。这两份是私人发布资料，务必一起离线备份，不上传 Git 或发给最终用户；源码压缩包不包含它们。以后更新必须使用同一密钥并增加 versionCode。

在 Windows 工程根目录：

```powershell
.\tools\build-release.ps1 -SigningConfig ..\signing\private-signing.json
```

脚本只向当前 Gradle 子进程提供签名环境变量，结束后恢复，APK 位于 `app/build/outputs/apk/release/app-release.apk`。其他平台可设置 `STREAMSAVE_KEYSTORE`（绝对路径）、`STREAMSAVE_STORE_PASSWORD`、`STREAMSAVE_KEY_ALIAS`、`STREAMSAVE_KEY_PASSWORD` 后执行 `./gradlew assembleRelease`。不配置时生成 unsigned release，不应把它交给用户安装。

交付包保留对应的 R8 mapping。每次发布应记录 APK SHA-256、证书摘要、源码版本和测试结果，并保留新 mapping。不要用不同的 debug 签名尝试覆盖 release 安装。


## 内置解析器更新

策略与健康检查维护位置是 parser/ResolverRouter.kt、parser/WebsiteProbe.kt；网站协议在 parser/MiuiSiteClient.kt 与 MiuiSiteResolver.kt；元宝协议在 parser/LocalTencentResolver.kt；登录在 login/ 和 data/TencentSession.kt。不要误改旧 server/ 后认为手机解析已更新。保持 VideoInfo 和下载层契约稳定。网页版本检查是本 App 的兼容性要求，不能把“组件 >=100”当成所有未来腾讯网页必然可用的保证。旧组件应明确引导更新，不能白屏或吞掉崩溃。
