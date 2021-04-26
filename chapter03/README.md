## Chapter 03

### 设计

#### maven dependencies

It we don't want to set spring-boot-parent as the project parent, we shall import spring-boot-dependencies instead:

```
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>2.2.6.RELEASE</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

How to tell you need them: when we run the start-up class, it complains of missing dependencies HttpClient.

Has to manually add commons-lang3.

#### 端口使用

原则：避免使用随机端口（server.port=0）。

| 模块                                       | 作用                                                         | 端口 | Startup Class             |
| ------------------------------------------ | ------------------------------------------------------------ | ---- | ------------------------- |
| spring-cloud-alibaba-nacos-provider4-lb    | 服务提供者                                                   | 8082 | NacosProvider4LoadBalance |
| spring-cloud-alibaba-nacos-consumer-sclb   | Spring Cloud LoadBalancer示例                                | 8084 | NacosConsumer4SCLB        |
| spring-cloud-alibaba-nacos-consumer-ribbon | Netflix Ribbon 负载均衡示例                                  | 8085 | NacosConsumer4Ribbon      |
| spring-cloud-alibaba-dubbo-order           | Dubbo Spring Cloud服务端示例（模型为order）                  | 8086 | OrderApplication          |
| spring-cloud-alibaba-dubbo-user            | Dubbo Spring Cloud的客户端示例（分为raw-dubbo，feign-dubbo，raw-feign三种方式） | 8087 | UserApplication           |
| spring-cloud-nacos-normal-provider         |                                                              | 8088 |                           |
| spring-cloud-nacos-gray-provider           |                                                              | 8089 |                           |
| spring-cloud-nacos-consumer-ribbonenhance  |                                                              | 8888 |                           |

#### 技术说明：JAX-RS

在这个项目使用：spring-cloud-alibaba-nacos-consumer-openfeign

feign-jaxrs：仅包括这一个接口类：JAXRSContract。

然后来一个bean

```java
public class MyOpenFeignConfiguration {
    @Bean
    public Contract myFeignContract() {
        return new JAXRSContract();
    }
}
```

定义服务：

```java
@FeignClient(name = "nacos-provider-lb", configuration = MyOpenFeignConfiguration.class, contextId = "jaxrsFeign")
interface EchoServiceJAXRS {
    @GET
    @Path("/")
    String echo();
}
```



### 实验

#### 3.2 Spring Cloud LoadBalancer 负载均衡

启动nacos

启动 `spring-cloud-alibaba-nacos-provider4-lb`

启动`spring-cloud-alibaba-nacos-consumer-sclb`

然后访问consumer的REST API：

```
>http localhost:8084/echo
HTTP/1.1 200
Connection: keep-alive
Content-Length: 18
Content-Type: text/plain;charset=UTF-8
Date: Mon, 05 Apr 2021 12:04:13 GMT
Keep-Alive: timeout=60

192.168.0.107:8082
```

如果 `spring-cloud-alibaba-nacos-provider4-lb`没有运行，结果是这样：

```
>http localhost:8084/echo
HTTP/1.1 500
Connection: close
Content-Type: application/json
Date: Mon, 05 Apr 2021 11:24:58 GMT
Transfer-Encoding: chunked

{
    "error": "Internal Server Error",
    "message": "No instances available for nacos-provider-lb",
    "path": "/echo",
    "status": 500,
    "timestamp": "2021-04-05T11:24:58.290+0000"
}
```

#### 3.3 Netflix Ribbon 负载均衡

启动nacos

启动 `spring-cloud-alibaba-nacos-provider4-lb`

启动`spring-cloud-alibaba-nacos-consumer-ribbon`

然后访问consumer的REST API：

```
>http localhost:8085/echo
HTTP/1.1 200
Connection: keep-alive
Content-Length: 18
Content-Type: text/plain;charset=UTF-8
Date: Mon, 05 Apr 2021 12:58:45 GMT
Keep-Alive: timeout=60

192.168.0.107:8082
```

#### 3.4 Dubbo LoadBalance 负载均衡（没有实验）

#### 3.5 OpenFeign: 声明式 Rest 客户端

启动nacos

启动 `spring-cloud-alibaba-nacos-provider4-lb`

启动`spring-cloud-alibaba-nacos-consumer-openfeign`

然后访问consumer的REST API：

```
>http localhost:8086/jaxrs
HTTP/1.1 200
Connection: keep-alive
Content-Length: 18
Content-Type: text/plain;charset=UTF-8
Date: Mon, 05 Apr 2021 13:06:35 GMT
Keep-Alive: timeout=60

192.168.0.107:8082


>http localhost:8086/springmvc
HTTP/1.1 200
Connection: keep-alive
Content-Length: 18
Content-Type: text/plain;charset=UTF-8
Date: Mon, 05 Apr 2021 13:06:45 GMT
Keep-Alive: timeout=60

192.168.0.107:8082
```

#### 3.6 Dubbo Spring Cloud: 服务调用的新选择

启动nacos

启动`spring-cloud-alibaba-dubbo-order`服务，然后启动`spring-cloud-alibaba-dubbo-user`服务。

* 从nacos控制台可以看到：前者提供`sc-dubbo-provider`服务（8086端口），后者提供`sc-dubbo-consumer`服务（8087端口）。
* Apache Dubbo暴露的服务都是接口级别的，而Spring Cloud暴露的服务都是应用级别的。Dubbo把单个应用的所有接口URL（顺带还有service port）都整合在一起放到了meta-data中（可以在nacos控制台的meta-data中看到）。

然后访问consumer的REST API：

```
>http localhost:8086/allOrders/?userId=test
HTTP/1.1 200
Connection: keep-alive
Content-Type: application/json
Date: Tue, 06 Apr 2021 08:16:06 GMT
Keep-Alive: timeout=60
Transfer-Encoding: chunked

[
    {
        "createdTime": "2021-04-06T08:11:47.420+0000",
        "id": "93ffdc4a-3f35-428f-93df-249697b2c7e9",
        "userId": "test"
    }
]

>http localhost:8087/rawDubbo/test
HTTP/1.1 200
Connection: keep-alive
Content-Type: application/json
Date: Tue, 06 Apr 2021 08:16:20 GMT
Keep-Alive: timeout=60
Transfer-Encoding: chunked

[
    {
        "createdTime": "2021-04-06T08:11:47.420+0000",
        "id": "93ffdc4a-3f35-428f-93df-249697b2c7e9",
        "userId": "test"
    }
]
```

#### 3.8 应用流量控制

启动nacos

启动`spring-cloud-nacos-normal-provider`（8088）、`spring-cloud-nacos-gray-provider`（8089）、`spring-cloud-nacos-consumer-ribbonenhance`（8090）

然后访问consumer的REST API：

```
>curl -s -H "gray:true" localhost:8090/echo
192.168.0.107:8089
>curl -s -H "gray:true" localhost:8090/echoFeign
192.168.0.107:8089
>curl -s localhost:8090/echoFeign
192.168.0.107:8088
>curl -s localhost:8090/echo
192.168.0.107:8088
```
