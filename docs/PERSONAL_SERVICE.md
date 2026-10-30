> 这是 **1.2 历史方案**，1.3 手机端不需要部署本服务。当前使用方法见 [手机登录](PHONE_LOGIN.md)。

# 私人解析服务配置（1.2）

这个方案让手机独立下载，不需要电脑微信持续运行，也不使用 Paste2Vid / 550W AI。你需要一次性部署服务，并提供你自己有效的腾讯元宝网页登录会话。

**能控制的是自己的代码和服务，不是腾讯接口。私人服务没有每日解析计数或付费解锁，但不能保证永久不失效。会话可能过期，腾讯可能风控或改接口，托管平台也有资源上限。** DeepSeek 的语言模型 API 不提供微信视频访问凭证，不能替代这里的登录会话。

## 要准备什么

| 项目 | 用途 |
| --- | --- |
| 你自己的 Cloudflare 账号，或有 HTTPS 域名的服务器 | 运行这个小型解析器 |
| RESOLVER_TOKEN | 你自己生成的随机访问密钥，防止服务被别人使用 |
| YUANBAO_COOKIE | 你本人在 yuanbao.tencent.com 登录后的网页会话，服务只将它发送给腾讯元宝 |

这两个值用途不同。**不要把元宝 Cookie 填进 App，也不要把 DeepSeek Key 当访问密钥。不要把这些值发到聊天、GitHub、日志或截图中。**

生成访问密钥的方法（Node.js 22+）：

```sh
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
```

得到 64 位十六进制随机文本，在服务端和 App 中使用同一个值。不要使用示例占位符。

## 获取自己的元宝会话

1. 在你自己的浏览器打开 [腾讯元宝](https://yuanbao.tencent.com/)，用自己的账号登录。
2. 打开浏览器开发者工具的 Network / 网络页，在元宝中解析一条你有权访问的视频号分享链接，筛选 get_parse_result。
3. 在这条请求的 Request Headers / 请求头中复制完整 Cookie 字段的**值**，不要包含字段名 Cookie:，不要复制到公开位置。
4. 只存到下文服务端的 YUANBAO_COOKIE Secret。不要复制上游源码里的其他人的 t-userid、x-id 或设备标识。
5. 如果找不到请求或没有返回 playable_url，不要猜 Cookie 或宣称成功；先确认你自己的元宝网页登录和该链接在网页中可用。

部署和首次取得会话可以使用浏览器开发者工具；之后日常下载只需手机联网。这个方案不承诺 Cookie 永不过期；失效时需要更新。单纯 document.cookie 可能遗漏 HttpOnly 会话，因此优先取真实请求头。

## 方式 A：Cloudflare Worker

适合希望少维护一台服务器的人。需要所在网络能访问你自己的 Worker 域名；平台套餐限制仍适用。

### 通过控制台部署

1. 登录 Cloudflare，进入 Workers & Pages，创建自己的 Worker。
2. 用源码 server/worker.mjs 的完整内容替换默认代码并部署。它是单文件 ES Module，没有运行时 npm 依赖。
3. 在 Worker 的 Settings → Variables and Secrets 中添加两个 **Secret**：
   - RESOLVER_TOKEN：上面生成的随机访问密钥。
   - YUANBAO_COOKIE：你自己的完整有效元宝 Cookie。
4. 保存并部署变量变更。不要将它们写在 worker.mjs 源码中。
5. 记下该 Worker 的 HTTPS 根地址，例如 https://你的名称.你的子域.workers.dev。也可以绑定自己的域名。

官方说明：[Worker Secrets](https://developers.cloudflare.com/workers/configuration/secrets/)、[通过 Dashboard 创建 Worker](https://developers.cloudflare.com/workers/get-started/dashboard/)。

### 通过命令行部署

在 server 目录，使用 Node.js 22+：

```sh
npx wrangler@4.135.0 login
npx wrangler@4.135.0 deploy
npx wrangler@4.135.0 secret put RESOLVER_TOKEN
npx wrangler@4.135.0 secret put YUANBAO_COOKIE
```

按各命令的交互提示输入值。源码没有嵌入任何私人令牌。配置了与应用用途不符的账户、登录风控或平台网络限制时，部署成功仍不等于视频解析成功。

## 方式 B：你自己的服务器

服务端支持 Node.js 22+，没有运行时 npm 依赖。也提供 Docker Compose + Caddy 自动 HTTPS。

1. 将 server 文件夹上传到自己的服务器。
2. 将自己的域名 DNS 指向服务器，允许 HTTPS 所需的 80 / 443 端口。
3. 复制 .env.example 为 .env，填写 RESOLVER_TOKEN、YUANBAO_COOKIE，并增加 RESOLVER_DOMAIN=你的域名。Cookie 一行用双引号包裹。不要把 .env 上传到代码仓库。
4. 执行：

```sh
chmod 600 .env
docker compose up -d --build
```

视频不会经服务器转发：服务只交换解析信息，App 直接从腾讯下载 MP4。解析器端口只暴露给容器网络；公网通过 Caddy HTTPS 访问。

已有 HTTPS 反向代理时，也可直接运行：

```sh
node --env-file=.env node-server.mjs
```

默认只监听 127.0.0.1:8787，把自己域名的 HTTPS 请求反向代理到此端口。禁止直接用明文 HTTP 在公网传输访问密钥。Node.js 与 Docker 的运行日志不输出 Cookie、请求体或签名视频 URL；也不要在前置代理启用记录 Authorization 的日志。

## 在手机上连接

安装 1.2，进入设置 → 私人解析服务：

1. HTTPS 服务地址：只填根地址，不包含 /v1/resolve 或任何参数。
2. 访问密钥：填 RESOLVER_TOKEN 的值。
3. 点击“保存并检查连接”。

提示“服务已连接”只证明连接、鉴权和服务器变量存在。回到首页解析真实链接、下载并播放成功，才算你的账号完成验收。手机密钥以 Android Keystore AES-GCM 加密，元宝 Cookie 留在你的服务端。

未配置服务时，新版会明确提示配置，不会偷偷改用公共解析站。带有效 token/eid 的腾讯预览页链接仍可直接读取腾讯接口。

## 日后会遇到的错误

| 提示 | 处理 |
| --- | --- |
| 私人服务访问密钥不正确 | 检查 App 与 RESOLVER_TOKEN 是否一致 |
| 服务器尚未配置登录会话 | 配置 YUANBAO_COOKIE 并重新部署 / 重启 |
| 腾讯未接受服务器的登录会话 | 重新登录自己的元宝、更新 Cookie；这不是“下载次数用完” |
| 腾讯接口暂时限制请求 | 等待腾讯解除限制，不轮换账号绕过 |
| 腾讯接口格式可能已变化 | 检查 worker.mjs 两阶段协议、上游当前源码；只改解析模块 |
| TLS、网络或超时 | 确认手机能访问你的 HTTPS 域名、证书有效，服务器能访问腾讯 |

更新 Cookie：Cloudflare 再运行 secret put YUANBAO_COOKIE；自建服务器修改 .env 后执行 docker compose up -d --force-recreate。

## 接口与测试

- GET /v1/health：需要 Bearer Token，只返回协议版本和会话是否已配置。
- POST /v1/resolve：需要 Bearer Token，JSON 请求为 {"url":"https://weixin.qq.com/sph/..."}。
- 成功返回 video 对象：sourceUrl、title、author、coverUrl、videoUrl。
- 输入仅接受腾讯 SPH 链接；不提供任意 URL 代理、不代替用户下载媒体。
- `node --test test/*.test.mjs` 运行服务端测试。

本次已测试鉴权、请求大小、域名校验、两阶段协议适配、凭证隔离、错误处理和不设每日计数。**没有你的有效私人会话，也未登录你的托管账号，因此没有替你部署公网服务，不能声称你的私人账号已经完成真实解析。** 上一版通过公共服务下载的证据不作为这一模式的成功证据。

