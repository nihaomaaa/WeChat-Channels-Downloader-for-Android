> 这是 **1.2 历史方案**，1.3 手机端不需要部署本服务。当前使用方法见 [手机登录](../docs/PHONE_LOGIN.md)。

# Private WeChat Video Resolver

本服务没有公共解析站依赖、每日解析计数、收费解锁或大模型 API。

完整中文部署指南：[私人服务配置](../docs/PERSONAL_SERVICE.md)。

- Cloudflare Workers：单文件 worker.mjs；wrangler.toml 已提供。
- Node.js 22+：node-server.mjs，无运行时第三方依赖。
- VPS：Dockerfile、compose.yaml、Caddyfile。
- 必需秘密：RESOLVER_TOKEN（你生成的访问密钥）和 YUANBAO_COOKIE（你自己的有效网页会话）。
- 测试：node --test test/*.test.mjs。

只向固定腾讯 HTTPS 接口发请求。不输出请求体或凭证；不转发视频文件，不接收任何 DeepSeek Key。

授权解析协议来自对当前公开源码和官方网页行为的研究。会话和上游接口可能变化，需要维护；不是腾讯官方 API，也不是永久有效性保证。服务代码使用项目 MIT 许可证。

