# Chapter 04 配置管理

### 前言

#### 目标

使用基于nacos的配置中心，可以动态的获取加载特定的配置信息，更改实时生效。这些信息通常写死在`application.properties`中，启动的时候读取，需要刷新的时候必须重启程序。配置动态化的好处是，程序在不重启的情况下，在提前规划好的范围内，应变能力更强；最小化对于持续服务能力的影响。

原理并不复杂，增加一层重定向（redirection），增加一层抽象（abstraction）。但是非常有用。

在最后一节实战中，动态配置的对象是流量控制策略。

### 实验

#### 准备：运行nacos服务

所有的配置文件将nacos服务地址指向`nacos:8848`，所以需要在hosts中创建映射。

##### 要点：测试的时候可以考虑不要将nacos部署在本地（`localhost:8848`）

Nacos的client代码默认配置是使用这个本地nacos服务。我们程序可能不需要启用nacos client的特性，但是因为种种原因最后nacos client可能还是启用了（比如可能我们配置没有做好）。由于这个默认的本地nacos是可访问的，nacos client代码不会报错，我们就不会知道这个意外情况。

* 比如，nacos一共有三个配置：`spring.cloud.nacos.discovery.server-addr`，`spring.cloud.nacos.config.server-addr`，`spring.cloud.nacos.server-addr`。还有spring cloud的`spring.cloud.config.discovery.enabled`。
* 如果`spring.cloud.nacos.config.server-addr`没有配置，会使用`spring.cloud.nacos.server-addr`的值（默认为`localhost:8848`）。
* 所以为了不要引起混乱，一定要明确配置清楚nacos的discovery和config的特性是否启用，其服务又分别是什么。不要配置`spring.cloud.nacos.server-addr`。

##### 透过REST API对nacos进行基本的配置提交和读取测试

```
curl -X POST "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=nacos.cfg.dataId&group=test&content=helloWorld"
curl -X GET  "http://127.0.0.1:8848/nacos/v1/cs/configs?dataId=nacos.cfg.dataId&group=test"
```

如果不行，可以考虑重启nacos服务（略显粗暴）。https://blog.csdn.net/linzhiji/article/details/108983381

#### 4.2 Spring/Spring Boot 与配置

示例：不创建web服务的一个spring boot app怎么做：

```java
    public static void main(String[] args) {
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .sources(ProfilePropertiesApplication.class)
            .run(args);
    }
```

#### 4.3.1 使用 Alibaba Nacos 体验配置的获取以及动态刷新：

先运行nacos，创建名为`nacos-configuration-sample.properties`性质为properties的配置：

```properties
boot.category = spring cloud
book.author = jim green the 3rdd
```

运行项目`spring-cloud-alibaba-nacos-configuration`。

提交REST API交互：

```
C:\>http localhost:8080/config/
HTTP/1.1 200
Connection: keep-alive
Content-Length: 192
Content-Type: text/plain;charset=UTF-8
Date: Mon, 19 Apr 2021 05:57:15 GMT
Keep-Alive: timeout=60

env.get('book.category')=unknown<br/>env.get('book.author')=jim green the 3rdd<br/>bookAuthor=jim green the 3rdd<br/>bookProperties=BookProperties{category='null', author='jim green the 3rdd'}
```

##### 分析：为什么这个子项目，nacos registry必须配置不然报错？

首先，由于nacos的client代码在不配服务端时会默认到`localhost:8848`。所以这个报错需要把nacos单独部署的时候才能暴露出来。

直观上，NacosWatch这个bean被创建了，refresh的时候，#start()就会被调用，从而对外发出请求。

配置分别写入`NacosDiscoveryProperties`还有`NacosConfigProperties`

最本质的原因还是，`spring.cloud.nacos.discovery.enabled` 这个配置项的值是true（原因未知），需要在bootstrap.properties里面配置为false。

##### 为什么要引入`spring-boot-configuration-processor`依赖？

起因：`BookProperties`被注释为`@ConfigurationProperties(prefix = "book")`，在`bootstrapping.properties`中配置了`book.author = jim`。在不能从nacos中获取到这个字段的值的时候，这个值会作为默认值启用。

这是IDEA提示的。本质上对程序的运行没有任何影响，只是提升了开发体验。

操作：添加一个可选的依赖，然后在`bootstrapping.properties`中做的配置就会在IDEA中被识别，可通过超链接转到`BookProperties`这个类的set函数代码上。

```
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-configuration-processor</artifactId>
			<version>${spring-boot.version}</version>
			<optional>true</optional>
		</dependency>
```

参考：

* 按照要求来：https://docs.spring.io/spring-boot/docs/2.2.13.RELEASE/reference/html/appendix-configuration-metadata.html#configuration-metadata-annotation-processor

* https://stackoverflow.com/questions/33483697/re-run-spring-boot-configuration-annotation-processor-to-update-generated-metada

##### 问题：两种配置动态刷新的方式：一种生效，一种不生效

往nacos提交的配置情况：data ID为`nacos-configuration-sample.properties`，Group为`DEFAULT_GROUP`；写入内容：

```
boot.category = spring cloud
book.author = jim green
```

其中`boot.category`部分不会生效（很奇怪，书里主要是要演示这个），`book.author`可以立即生效：可以透过REST API获取到，甚至能观察程序控制台输出实时看到改变提醒。

##### 分析：动态刷新的原理：循环持续请求见ClientWorker

检查投递任务的队列、执行队列：

```java
        this.executor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.Worker." + agent.getName());
                t.setDaemon(true);
                return t;
            }
        });
        
        this.executorService = Executors
                .newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName("com.alibaba.nacos.client.Worker.longPolling." + agent.getName());
                        t.setDaemon(true);
                        return t;
                    }
                });
        
        this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    checkConfigInfo();
                } catch (Throwable e) {
                    LOGGER.error("[" + agent.getName() + "] [sub-check] rotate check error", e);
                }
            }
        }, 1L, 10L, TimeUnit.MILLISECONDS);
```

投递任务：#checkConfigInfo()，随着cacheMap的数据量增大，额外调用一个新的LongPollingRunnable任务。

循环执行：每个LongPollingRunnable#run() 开始运行后，会永不停息，至程序退出方休：在每次执行结束的时候，会重新将自己投递一次。如果执行失败，间隔2s（错误惩罚）。

#### 4.4.1 使用 File System 文件系统作为 Spring Cloud Config Server 里的 EnvironmentRepository 实现类的例子

要点：透过EnvironmentRepository 从classpath（这里具体是resources目录）读取properties配置文件，透过REST API服务出去。

背景：每一个配置需要application、profile、label作为参数。参考interface EnvironmentRepository。

运行`spring-cloud-config-server-file`，然后做REST API请求：

```
C:\>http localhost:8080/book/dev/
HTTP/1.1 200
Connection: keep-alive
Content-Type: application/json
Date: Sat, 17 Apr 2021 15:40:28 GMT
Keep-Alive: timeout=60
Transfer-Encoding: chunked

{
    "label": null,
    "name": "book",
    "profiles": [
        "dev"
    ],
    "propertySources": [
        {
            "name": "classpath:/master/book.properties",
            "source": {
                "book.author": "jim"
            }
        },
        {
            "name": "classpath:/book.properties",
            "source": {
                "book.category": "spring cloud"
            }
        }
    ],
    "state": null,
    "version": "1.0.0"
}
```

We've got files in resources folder:

```
$ find . -type f |xargs head -n 100
==> ./application.properties <==
spring.application.name=sc-config-server-file
server.port=8080

spring.profiles.active=native

spring.cloud.config.server.native.searchLocations=classpath:/
#spring.cloud.config.server.native.searchLocations=classpath:/{label}

spring.cloud.config.server.native.version=1.0.0
==> ./book-prod.properties <==
book.name=deep in spring cloud
==> ./book.properties <==
book.category=spring cloud
==> ./master/book.properties <==
book.author=jim
==> ./master/master/book.properties <==
book.publishYear=2020
```

#### 4.4.1 使用 JDBC 数据库作为 Spring Cloud Config Server 里的 EnvironmentRepository 实现类的例子

与基于File System的Config Server功能相同，实现不同。都使用8080端口，这样使用Client的时候可以无缝切换。

我们这里自信一点，把数据库用户名和密码都定死。首先，在MySQL数据库中使用root账户创建：

```sql
CREATE DATABASE IF NOT EXISTS deepin_sc;
CREATE USER IF NOT EXISTS 'deepin_sc'@'localhost' IDENTIFIED BY 'b4.nvjad_7L-';
GRANT ALL PRIVILEGES ON deepin_sc.* TO 'deepin_sc'@'localhost';
USE deepin_sc;
SOURCE properties.sql;
```

注意demo数据默认的label应该用`master`而不是null。

然后进行REST API交互：

```
C:\>http localhost:18081/book/prod
HTTP/1.1 200
Connection: keep-alive
Content-Type: application/json
Date: Sun, 18 Apr 2021 05:28:58 GMT
Keep-Alive: timeout=60
Transfer-Encoding: chunked

{
    "label": null,
    "name": "book",
    "profiles": [
        "prod"
    ],
    "propertySources": [
        {
            "name": "book-prod",
            "source": {
                "book.author": "jim",
                "book.category": "spring cloud",
                "book.name": "deep in spring cloud"
            }
        }
    ],
    "state": null,
    "version": null
}
```

#### 4.4.2 使用 Spring Cloud Config Client 完成配置读取的例子

需要运行4.4.1中的任一config server（地址：https://localhost:8080/）。

一次读取：`spring-cloud-config-client`

要点是：

```java

    @Autowired
    Environment env;

	env.getProperty("book.category", "unknown")
```

原理是从Environment中读取相关配置。

* 配置从远程config server读取，在这里被注入Environment：PropertySourceBootstrapConfiguration#insertPropertySources()

可以和4.3.1对照，为什么那边不生效。

#### 4.4.2 使用 Spring Cloud Config Client 完成动态配置更新的例子

需要运行4.4.1中的任一config server（地址：https://localhost:8080/）。

要点是：

```java
    @RestController
    @RefreshScope
    static class ConfigurationController {

        @Value("${book.author:unknown}")
        String bookAuthor;
    }
```

但是其实服务端并没有刷新的能力（停机修改properties）。

当然观测发现此程序其实并没有持续请求：这一条log在整个程序生命周期只出现一次：

```
o.s.w.c.RestTemplate o.s.c.l.CompositeLog.debug():147 HTTP GET http://localhost:8080/book/prod
```

作为对照，4.4.3的C/S有持续的请求。才能在内容改变时触发事件。

#### 4.4.3 Spring Cloud Config Client 与 Service Registry 整合

目标：config client和server透过nacos的注册中心整合，读取到配置

首先，这里Service Registry和Service Discovery 是同一个意思。比如配置文件里面写：

```
spring.cloud.nacos.discovery.server-addr = nacos:8848
```

而读取该配置做出连接的类的名字是`NacosServiceRegistry`。注意这里不用配置管理，所以不必配置`spring.cloud.nacos.config.server-addr`。

首先运行nacos。再运行这俩：`spring-cloud-config-server-service-registry`和`spring-cloud-config-client-service-registry`。

具体的原理原书没有详细讲。我自己理解的是，服务端和客户端都连接到了nacos发布自己的配置接口（service registry 或 service discovery）。客户端实时刷新服务端的配置情况，透过`DiscoveryClientConfigServiceBootstrapConfiguration$HeartbeatListener`的订阅消息刷新，把配置更新到bean `ConfigClientProperties properties`。

然后Spring Cloud Config Client代码读取这个properties，作为自己的配置源。

* 这个怎么做，原作并没有提到。我们这里是借鉴了PropertySourceBootstrapConfiguration#initialize():
  * 从propertySourceLocators中，让每个locator从environment中locate出PropertySource。所有的PropertySource最后合并为一个composite，通过insertPropertySources()方法插入到environment中的MutablePropertySources。
  * 虽然class name是property source，但是逻辑并不涉及配置是从properties文件中获取这个事实。所以我们可以把这个逻辑用到自己的场合。

更多参考信息：

* Overriding ConfigServicePropertySourceLocator https://github.com/spring-cloud/spring-cloud-config/issues/177

#### 4.6 Spring Cloud 应用流量控制策略动态生效

##### 原理

`spring-cloud-nacos-consumer-ribbonenhance-dynamicupdate` 把chapter03的 `spring-cloud-nacos-consumer-ribbonenhance`项目的流量控制策略动态化。

* 之前的规则是写死的HTTP header `"gray:true"`。动态化的做法是把规则从nacos配置中心中拉取，实时刷新。比如本实验是把灰度规则的gray值改为test。

##### 实验

启动nacos，打开nacos的web控制台，在默认group（`DEFAULT_GROUP`）中创建配置项，名为`nacos-ribbonenhanced-dynamicupdate-consumer.properties`：

```
traffic.rule.type=header
traffic.rule.name=Gray
traffic.rule.value=true
```

启动`spring-cloud-nacos-normal-provider`（8088）、`spring-cloud-nacos-gray-provider`（8089）、`spring-cloud-nacos-consumer-ribbonenhance-dynamicupdate`（8888）

然后访问consumer的REST API：

```
>curl -s -H "gray:true" localhost:8888/echo
192.168.0.107:8089
>curl -s -H "gray:true" localhost:8888/echoFeign
192.168.0.107:8089
>curl -s localhost:8888/echoFeign
192.168.0.107:8088
>curl -s localhost:8888/echo
192.168.0.107:8088
```

打开nacos的web控制台，更改上述nacos配置项的内容（`value`字段的值改为`test`）：

```
traffic.rule.type=header
traffic.rule.name=Gray
traffic.rule.value=test
```

然后重新访问consumer的REST API：

```
>curl -s -H "gray:test" localhost:8888/echo  # 灰度配置生效
192.168.0.107:8089
>curl -s -H "gray:test" localhost:8888/echoFeign
192.168.0.107:8089
>curl -s -H "gray:true" localhost:8888/echoFeign  # 灰度配置没有生效
192.168.0.107:8088
>curl -s -H "gray:true" localhost:8888/echo
192.168.0.107:8088
>curl -s localhost:8888/echoFeign
192.168.0.107:8088
>curl -s localhost:8888/echo
192.168.0.107:8088
```

