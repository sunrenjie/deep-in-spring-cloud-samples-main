## 实验手册

#### 因为

### 实验

#### 6.2.1/6.2.2 Spring 与消息

模块：spring-messaging。主要演示spring-messaging的消息发送相关的特性

* 接口类：Message、MessageBuilder、PollableChannel、SubscribableChannel
* 辅助类：AbstractSubscribableChannel、

所谓的poll vs. subscribe：前者是主动拉去新消息，轮询；后者是注册一个消息处理器（message handler），通过它获取新消息。

##### 启动类 PollableChannelApp

PollableChannel

##### 启动类 SubscribableChannelApp

MySubscribableChannel 这个通道的内部消息发送机制：随机挑选一个subscriber进行发送。

SubscribableChannelApp：

* 创建了3个subscriber，最后随机挑选的subscriber会收到消息。
* 定义了消息钩子preSend()，如果消息头部包含"ignore" = true的值时不发送。

#### 6.2.3 spring-messaging 与 WebSocket

模块：spring-messaging与spring-websocket。

##### WebSocketApplication

这是一个基于spring-boot的基于websocket的完整的前端+后端程序。

前端使用

运行WebSocketApplication，打开浏览器地址：http://localhost:8080/client.html 。

##### 附送的Baeldung WebSockets 示例

这里附送了一个来自于 https://www.baeldung.com/websockets-spring 的另外一个websocket演示。它有助于与原始demo做特性对比，增进理解。

运行com.baeldung.SpringBootApp

###### 打开两个浏览器页面，打开地址 http://localhost:8080 输入昵称连接，然后开始相互聊天

###### 打开 http://localhost:8080/bots.html 看机器人瞎聊

这个每5秒一次的消息触发，来自于ScheduledPushMessages#sendMessage()。

定时触发的机制：

* 这个方法注释了@Scheduled(fixedRate = 5000)

* 调度是由ScheduledTaskRegistrar#afterPropertiesSet()发起的。入口：ScheduledThreadPoolExecutor#scheduleAtFixedRate()。这是spring-context的固有特性。

T