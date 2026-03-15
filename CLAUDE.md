# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Undertow Gateway 是一个基于 Netty 改造的 Undertow 服务器，在官方放弃的 Undertow 3.0.0-Alpha1 分支基础上，使用 Netty 替代了底层的 XNIO 框架。核心能力是在单个端口上通过字节级协议识别，支持 HTTP、HTTPS、SOCKS5、SSL 等多协议复用，并与 Spring Boot 深度集成。

**关键坐标：** `com.virjar:undertow-gateway:1.3`，JDK 17，Spring Boot 3.2.5，Netty 4.1.77.Final

## Build Commands

```bash
# 编译
mvn clean compile

# 本地安装
mvn clean install

# 打包
mvn clean package

# 发布到 Sonatype 中央仓库（需 GPG 密钥）
mvn clean deploy
```

项目没有测试模块，无测试命令。

## Architecture

### 核心处理流程

```
TCP 连接
  └─ GatewayHandler (协议检测)
       ├─ ProtocolMatcher 列表 (字节级匹配，如 SOCKS5、SSL)
       └─ HTTP 流量
            └─ HttpServerCodec + GatewayHttpInitializer
                 ├─ NettyHttpMatcher 列表 (HTTP 头级匹配，如 WebSocket)
                 └─ NettyHttpServerHandler
                      └─ HttpServerConnection → Servlet 处理链 → 用户 Controller
```

### 关键类

| 类 | 路径 | 职责 |
|---|---|---|
| `GatewayHandler` | `io/undertow/gateway/GatewayHandler.java` | 核心协议路由，定义三个扩展接口 |
| `Protocols` | `io/undertow/gateway/Protocols.java` | 内置协议匹配器（HTTP、HTTP代理、SOCKS5、SSL） |
| `Undertow` | `io/undertow/Undertow.java` | 服务器主类，管理 Netty EventLoopGroup 和端口绑定 |
| `GatewayHttpInitializer` | `io/undertow/GatewayHttpInitializer.java` | HTTP 层初始化，区分 WebSocket 与普通 HTTP |
| `NettyHttpServerHandler` | `io/undertow/protocol/http/NettyHttpServerHandler.java` | Netty HTTP 请求适配为 Undertow 处理 |
| `EmbeddedUndertow` | `io/undertow/gateway/springboot/web/servlet/EmbeddedUndertow.java` | Spring Boot 自动配置入口 |
| `GatewayBuilderCustomizer` | `io/undertow/gateway/GatewayBuilderCustomizer.java` | 将 Spring Bean 注入 Undertow.Builder |
| `UndertowServletWebServerFactory` | `io/undertow/gateway/springboot/web/embed/` | 实现 `ServletWebServerFactory`，连接 Spring Boot 与 Undertow |

### 三个核心扩展接口（定义在 `GatewayHandler` 内部）

- **`ProtocolMatcher`**：字节级协议识别，在 TCP 连接建立后、HTTP 解码前执行。返回 `MATCH`/`MISMATCH`/`PENDING`（数据不足时等待更多字节）。
- **`NettyHttpMatcher`**：HTTP 请求头级别匹配，适用于需要 Netty 直接处理的 HTTP 变体（如 WebSocket）。
- **`GatewayCallback`**：网关生命周期回调，包括连接初始化、协议未匹配、进入 Undertow 等事件。

### Spring Boot 扩展方式

将上述接口的实现注册为 Spring Bean 即可自动生效，`GatewayBuilderCustomizer` 会自动收集并注入：

```java
@Component
public class MyProtocol implements GatewayHandler.ProtocolMatcher {
    @Override
    public MATCH_STATUS match(ChannelHandlerContext ctx, ByteBuf buf) { ... }

    @Override
    public void handlePipeline(ChannelHandlerContext ctx, ChannelPipeline pipeline) { ... }
}
```

### Spring Boot 自动配置

自动配置声明文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

激活条件：classpath 中同时存在 `jakarta.servlet.Servlet`、`io.undertow.Undertow` 和 `io.undertow.gateway.GatewayHandler`。

## Key Design Decisions

- **IO 线程数** = CPU 核心数，**Worker 线程数** = IO 线程数 × 8（在 `Undertow.java` 中配置）
- 空闲连接超时：TCP 层 90 秒，HTTP 层 10 分钟（防慢速攻击）
- Servlet 实现基于 Jakarta Servlet 5.0（Spring Boot 3.x 兼容）
- `org.xnio` 包为兼容层，保留 XNIO API 外观，内部由 Netty 实现
