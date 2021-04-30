## Chapter 05 熔断器

### 实验

#### 5.4 Sentinel 与 RestTemplate，OpenFeign，Spring Cloud Circuit Breaker 的整合



#### 5.4.4 Sentinel 与spring-cloud-gateway整合实现限流逻辑：alibaba-sentinel-spring-cloud-gateway

Spring cloud gateway基于spring-webflux，而后者依赖于netty。

端口配置清单：

| 程序实体           | 端口类型              | 端口值 | 备注                                                         |
| ------------------ | --------------------- | ------ | ------------------------------------------------------------ |
| sentinel dashboard | 接入服务入口          | 18080  | dashboard自己启动时配置JVM参数`-Dserver.port`                |
| app+sentinel       | app的sentinel API入口 | 8719   | 配置JVM参数`csp.sentinel.api.port`，默认8719；见TransportConfig类。 |
| app                | app 自己的服务端口    | 8080   | 默认的spring-boot项目服务端口                                |

dashboard接入服务入口配置：

* dashboard自身配置JVM参数`-Dserver.port`
* app配置JVM参数`-Dcsp.sentinel.dashboard.server`，或者在`application.yaml`中配置`spring.cloud.sentinel.transport.dashboard`；前者优先。如果配置错误（比如所配置的端口根本没有dashboard服务运行），错误会在sentinel的日志中出现（比如${user.home}/logs/csp/sentinel-record.log.*）。

运行dashboard：`java -Dserver.port=9090 -Dcsp.sentinel.dashboard.server=localhost:18080 -Dproject.name=sentinel-dashboard -jar sentinel-dashboard-1.8.1.jar`

* Dashboard自身也是一个普通的app，也可以把自己接入 ，也就是使用JVM参数`-Dcsp.sentinel.dashboard.server`

运行app，参考`scripts\gateway.sh`，发起REST API请求，发现限制规则生效：

```
C:\>curl -v -H "LANG:zh-cn" http://localhost:8080/httpbin/status/500
*   Trying ::1...
* TCP_NODELAY set
* Connected to localhost (::1) port 8080 (#0)
> GET /httpbin/status/500 HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.55.1
> Accept: */*
> LANG:zh-cn
>
< HTTP/1.1 429 Too Many Requests
< Content-Type: application/json;charset=UTF-8
< Content-Length: 64
<
{"code":429,"message":"Blocked by Sentinel: ParamFlowException"}* Connection #0 to host localhost left intact
```

将请求参数改为`LANG1:zh-cn`再次请求，则限制规则失效：

```
C:\>curl -v -H "LANG1:zh-cn" http://localhost:8080/httpbin/status/500
*   Trying ::1...
* TCP_NODELAY set
* Connected to localhost (::1) port 8080 (#0)
> GET /httpbin/status/500 HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.55.1
> Accept: */*
> LANG1:zh-cn
>
< HTTP/1.1 500 Internal Server Error
< Date: Thu, 29 Apr 2021 12:44:01 GMT
< Content-Type: text/html; charset=utf-8
< Content-Length: 0
< Server: gunicorn/19.9.0
< Access-Control-Allow-Origin: *
< Access-Control-Allow-Credentials: true
<
* Connection #0 to host localhost left intact
```

#### 5.4.4 Sentinel 与zuul整合实现限流逻辑：`alibaba-sentinel-zuul`

按照`scripts\gateway.sh`进行。

```
>curl -H "LANG:zh-cn" http://localhost:8080/springcloud
{"code":403, "message":"Provider2 Block", "route":"my-provider2"}
>curl http://localhost:8080/dubbo
{"code":403, "message":"Provider1 Block", "route":"my-provider1"}
>curl http://localhost:8080/s-c-alibaba?name=2
{"code":403, "message":"Sentinel Block", "route":"my-provider3"}
>curl http://localhost:8080/springcloud
{"timestamp":"2021-04-30T07:47:19.057+0000","status":500,"error":"Internal Server Error","message":"GENERAL"}
```

前三条请求通不过（限流规则生效）。最后一条命令，程序控制台会输出

```
Caused by: com.netflix.client.ClientException: Load balancer does not have available server for client: my-provider2
	at com.netflix.loadbalancer.LoadBalancerContext.getServerFromLoadBalancer(LoadBalancerContext.java:483) ~[ribbon-loadbalancer-2.3.0.jar:2.3.0]
```

意思应该是，此请求通过（不限流），但是程序并未定义这几条路由。

大概可以从这里开始：https://www.jianshu.com/p/adec9003bfd2

#### 5.5 Hystrix 与 OpenFeign，Spring Cloud Circuit Breaker 的整合



#### 5.5.3 Hystrix 限流

Hystrix的限流策略逻辑配置：既可以在Java代码中配置（比如 `HystrixSpringCloudGatewayApplication`），也可以在配置文件中配置，比如`netflix-hystrix-zuul`模块。但是在配置文件中配置不能被识别出来，说明配置接收处置类没有被@ConfigurationProperties注释（类似于SentinelProperties）。

* 果然，HystrixPropertiesCommandDefault并没有被注释。



##### Hystrix 仪表盘：netflix-hystrix-dashboard



##### Hystrix 对 spring-cloud-gateway 限流：netflix-hystrix-spring-cloud-gateway

按照`scripts\gateway.sh`进行：

```
C:\>curl -s -XGET http://localhost:8080/s-c-alibaba/status/500
{"timestamp":"2021-04-30T07:38:55.301+0000","path":"/s-c-alibaba/status/500","status":503,"error":"Service Unavailable","message":"Unable to find instance for my-provider3","requestId":"d385f01b-1"}
C:\>curl -s -XGET http://localhost:8080/dubbo/status/500
{"timestamp":"2021-04-30T07:38:59.338+0000","path":"/dubbo/status/500","status":500,"error":"Internal Server Error","message":"my-provider1 could not acquire a semaphore for execution and no fallback available.","requestId":"22c15e8a-2"}
C:\>curl -s -XGET http://localhost:8080/springcloud/status/500
{"timestamp":"2021-04-30T07:39:03.581+0000","path":"/springcloud/status/500","status":503,"error":"Service Unavailable","message":"Unable to find instance for my-provider2","requestId":"107a3c39-3"}
```

类似的道理，不能获取semaphore的控制台输出表明请求通不过（限流规则生效）。找不到服务实例表明请求通过（不限流），但路由未定义。

##### hystrix对 zuul 限流：netflix-hystrix-zuul

按照`scripts\gateway.sh`进行。

首先：

```
>curl -s -XGET http://localhost:8080/dubbo/123
{"timestamp":"2021-04-30T08:28:46.559+0000","status":500,"error":"Internal Server Error","message":"REJECTED_SEMAPHORE_EXECUTION"}
```

控制台输出：

```
Caused by: java.lang.RuntimeException: could not acquire a semaphore for execution
	at com.netflix.hystrix.AbstractCommand.handleSemaphoreRejectionViaFallback(AbstractCommand.java:966) ~[hystrix-core-1.5.18.jar:1.5.18]
```

然后

```
>curl -s -XGET http://localhost:8080/springcloud/123
{"timestamp":"2021-04-30T08:27:55.734+0000","status":500,"error":"Internal Server Error","message":"GENERAL"}
```

控制台输出：

```
Caused by: com.netflix.client.ClientException: Load balancer does not have available server for client: my-provider2
	at com.netflix.loadbalancer.LoadBalancerContext.getServerFromLoadBalancer(LoadBalancerContext.java:483) ~[ribbon-loadbalancer-2.3.0.jar:2.3.0]
```

然后

```
D:\bin>curl -s -XGET http://localhost:8080/s-c-alibaba/123
{"timestamp":"2021-04-30T08:31:40.938+0000","status":500,"error":"Internal Server Error","message":"GENERAL"}
```

控制台输出：

```
Caused by: com.netflix.client.ClientException: Load balancer does not have available server for client: my-provider3
	at com.netflix.loadbalancer.LoadBalancerContext.getServerFromLoadBalancer(LoadBalancerContext.java:483) ~[ribbon-loadbalancer-2.3.0.jar:2.3.0]
```

#### 5.7 使用 Sentinel 保护应用防止服务雪崩

##### 准备

安装好wrk工具。

* win10 启用 WSL 安装 ubuntu或其他发行版linux
* 无论哪种方式准备好 linux 之后，按照指引编译安装：https://github.com/wg/wrk/wiki/Installing-wrk-on-Linux

启动nacos于`nacos:8848`，启动服务：

* order-service@8082
* delivery-service@8081，使用JVM参数 `-Xmx64m -Xms64m -Xss1024k`
* sms-service@8080

##### 调用order-service，确认链路正常

```
>http localhost:8082/order
HTTP/1.1 200
Connection: keep-alive
Content-Length: 51
Content-Type: text/plain;charset=UTF-8
Date: Sat, 01 May 2021 16:17:54 GMT
Keep-Alive: timeout=60

order be81e630-91ab-4354-aeea-db3aff1355a5 generate
```

在sms-service控制台输出观察到消息发送记录： `be81e630-91ab-4354-aeea-db3aff1355a5 send successfully`

##### 禁用 Sentinel 保护，压测delivery-service崩溃连累上游服务失去响应

修改delivery-service的applications.properties，将`feign.sentinel.enabled`配置值改为false。

运行压测：

```
wrk -c 500 -t 10 -d 10 http://localhost:8081/delivery?orderId=ssss
```

观测到delivery-service服务出现OOM错误。调用order-service，确认服务不正常：请求需要等待好久然后返回内部错误：

```
>http localhost:8082/order
HTTP/1.1 500
Connection: close
Content-Type: application/json
Date: Sat, 01 May 2021 16:30:51 GMT
Transfer-Encoding: chunked

{
    "error": "Internal Server Error",
    "message": "Read timed out executing GET http://delivery-service/delivery?orderId=da9acde1-3a62-41f7-83c8-44183b1d601f",
    "path": "/order",
    "status": 500,
    "timestamp": "2021-05-01T16:30:51.667+0000"
}
```

##### 启用 Sentinel 保护，压测delivery-service仍然能正常服务

修改delivery-service的applications.properties，将`feign.sentinel.enabled`配置值改回true。

运行压测：

```
wrk -c 500 -t 10 -d 10 http://localhost:8081/delivery?orderId=ssss
```

观测到delivery-service服务正常；控制台看到熔断的日志：

```
2021-05-03 00:41:39,007 DEBUG [http-nio-8081-exec-4] o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor o.s.c.l.LogFormatUtils.traceDebug():91 Writing ["delivery null failed: sms service has some problems"]
```

调用order-service，服务正常。（但是目前我的实验环境里，启用了保护还是发生了OOM。原因未知。可能需要根据实际运行环境情况进行调优）

#### 5.7 使用 Sentinel 保护应用防止服务雪崩：手工运行的版本

本例子用到的是熔断规则是基于平均响应时间 (DEGRADE_GRADE_RT)，主要实现位于ResponseTimeCircuitBreaker。

* 在默认的放通状态（CLOSED）的资源，如果一个统计时间窗口内（DegradeRule.statIntervalMs，默认为1000ms），其请求超时，也就是响应时间超过阈值（DegradeRule.count，以 ms 为单位，无默认值）的情况发生次数超过阈值（DegradeRule.minRequestAmount，默认值为5），且发生比率（超时请求数占总请求数）超过阈值（maxSlowRequestRatio，读取自 DegradeRule.slowRatioThreshold，默认1.0意为100%），资源进入断开状态（OPEN）。
* 在断开状态（OPEN）的资源等待满一段时间（recoveryTimeoutMs，来源DegradeRule 中的 timeWindow；但后者单位是s），后续进来的第一个请求则尝试进入半通状态（HALF_OPEN）：放通一次，如果调用成功，则回到放通状态下（CLOSED）。否则重新进入断开状态（OPEN）。

https://blog.csdn.net/xiongxianze/article/details/87572916

将`degraderule-openfeign.json`中的规则

```json
[
  {
    "resource": "GET:http://sms-service/send",
    "count": 20,
    "grade": 0,
    "timeWindow": 30
  }
]
```

改为

```json
[
  {
    "resource": "GET:http://sms-service/send",
    "count": 20,
    "grade": 0,
    "statIntervalMs": 300000,
    "minRequestAmount": 2,
    "slowRatioThreshold": 0.01,
    "timeWindow": 300
  }
]
```

也就是，只要发生两次调用缓慢，就进行熔断降级。

将SMSApplication改为第二次就开始变慢：

```java
        @GetMapping("/send")
        public String send(String orderId, int delaySecs) {
            int num = count.addAndGet(1);
            if (num >= 2) { // <---------------------------------- 这个数
                if(delaySecs > 0) {
                    try {
                        Thread.sleep(1000 * delaySecs);
                    } catch (InterruptedException e) {
                        return "Interrupted: " + e.getMessage();
                    }
                }
            }
            System.out.println(orderId + " send successfully");
            return "success";
        }
```

在300秒内完成下述实验：手工调用`http://localhost:8081/delivery?orderId=ssss`三次，观察到熔断降级现象。