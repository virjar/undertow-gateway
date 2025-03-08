Undertow
========

Undertow is a Java web server based on non-blocking IO. It consists of a few different parts:

* A core HTTP server that supports both blocking and non-blocking IO
* A Servlet 4.0 implementation
* A JSR-356 compliant web socket implementation

Website: http://undertow.io

Issues: https://issues.jboss.org/browse/UNDERTOW

Project Lead: Stuart Douglas <sdouglas@redhat.com>

Mailing List: undertow-dev@lists.jboss.org
http://lists.jboss.org/mailman/listinfo/undertow-dev



modified by virjar
========
这是基于Undertow修改而来的web服务器，用于替代tomcat，本项目主要作用为在http的webServer前端增加网关。

* 对于一个具备多种协议的服务，提供协议特征识别的业务转发，这样多种协议可以公用一个端口
* 对于websocket编程场景，webserver的ws编程模型非常不好用，基于网关剥离的方式可以轻松使用netty来控制websocket

**特别说明：** 本项目来自``3.0.0.Alpha1-SNAPSHOT``
的代码，实际上是Undertow官方放弃的一个分支，官方自3.x尝试使用Netty替换底层的xnio，但是不清楚什么原因只在Alpha就没有推进。而我们的目标就是使用一个使用netty实现的servlet容器，目前能找到的最好的方案只有他了

影响：

* 由于提供网关协议识别，所有https和http将会自动探测，在webserver中，不再可能收到https协议。故webserver永远不会启动在https中
* 压缩：当前的undertow版本不支持自动对返回文件进行压缩，由于压缩相关功能依赖了xnio相关API，暂时适配成本较高，我注释了相关代码(
netty本身具备压缩功能，简单方式可以直接在netty侧直接开启)

* 可能存在很大的不确定bug，毕竟这个版本的undertow没有经过长期生产验证

## 用法

本项目完成了springboot的集成，使用的jdk版本为jdk17，springboot版本为``3.2.5``,你可以通过什么spring的bean的方式挂载hook处理器，实现特定行为的流量拦截。

在springboot-web中排除tomcat的依赖，然后增加本项目依赖，即可将webserver切换为本项目的undertow

### maven
```xml
<dependencies>
    <!-- exludes embedded Tomcat -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <exclusions>
            <exclusion>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-tomcat</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
    <!-- include this netty servlet container -->
    <dependency>
        <groupId>com.virjar</groupId>
        <artifactId>undertow-gateway</artifactId>
        <version>1.2</version>
    </dependency>
</dependencies>
```

### gradle
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.apache.logging.log4j", module = "log4j-to-slf4j")
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
    implementation("com.virjar:undertow-gateway:1.2")
}
```
## 案例如下
### 案例1：托管websocket

其中 ``WebsocketDispatcher``是你的自定义流量处理器，这样所有的websocket就会被您的netty处理器托管，而不会进入undertow服务器

```java
import io.undertow.gateway.GatewayHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import org.springframework.stereotype.Component;

@Component
public class WebSocket implements GatewayHandler.NettyHttpMatcher {
    @Override
    public boolean match(HttpRequest httpRequest) {
        return GatewayHandler.NettyHttpMatcher.isWebSocket(httpRequest);
    }

    @Override
    public void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline) {
        pipeline.addLast(new WebsocketDispatcher());
    }
}
```


### 案例2，开发一个socks代理服务器

挂载``ProtoS5Proxy``后，当前服务就支持提供s5代理协议的支持，当网关检测到当前的请求不是http，而是socks5的时候，请求不会经过webserver，而是路由到您的业务服务器

```java

@Component
public static class ProtoS5Proxy extends Protocols.Socks5 {
    @Override
    public void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline) {
        pipeline.addLast(new SocksInitRequestDecoder());
        pipeline.addLast(new SocksMessageEncoder());
        pipeline.addLast(new ProxySocks5Handler());
        GatewayHandler.ProtocolMatcher.slowAttachDetect(context, ProxySocks5Handler.class, 60_000);
    }
}
```

### 案例3，开发一个自定义的私有协议
重写match方法，当流量特征满足私有协议流量特征的时候，流量请求将会进入自定义的二进制流量处理器

```java
@Component
    public static class ProtoSekiro implements GatewayHandler.ProtocolMatcher {
        private static final CompressorManager compressManager = new CompressorManager();
        private static final int ROUTER_MAGIC_HEADER_LENGTH = 8;

        @Override
        public int match(ChannelHandlerContext context, ByteBuf buf) {
            if (buf.readableBytes() < ROUTER_MAGIC_HEADER_LENGTH) {
                // not enough data
                return PENDING;
            }
            long magic = buf.getLong(buf.readerIndex());
            return magic == PacketCommon.MAGIC ?
                    MATCH : MISMATCH;
        }

        @Override
        public void handlePipeline(ChannelHandlerContext context, ChannelPipeline pipeline) {
            pipeline
                    .addLast(new SekiroPackerEncoder())
                    .addLast(new NettyCompressHandler(compressManager, true))
                    .addLast(new SekiroPacketDecoder())
                    .addLast(new NettyDecompressHandler(compressManager, true))
                    .addLast(new SekiroServerHeartBeatHandler())
                    .addLast(new RouterSekiro())
            ;
        }
    }
```