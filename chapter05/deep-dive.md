## Deep dive of source code for chapter05

目的：此项目中的实际问题驱动的源码阅读。因此，目标应该是选择性的深究，回答特定的问题，解决特定的问题；而不是漫无目的，无差别的深挖。

### 5.4.4 Sentinel 与spring-cloud-gateway整合实现限流逻辑：alibaba-sentinel-spring-cloud-gateway

REST API access:

```
>curl -v -H "LANG:zh-cn" http://localhost:8080/httpbin/status/500
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

#### How is the `HTTP/1.1 429 Too Many Requests` returned because of execution of the rules

AbstractServerHttpResponse.setRawStatusCode(statusCode=429) (actual: ReactorServerHttpResponse)
DefaultServerResponseBuilder$BodyInserterResponse

DefaultServerResponseBuilder(HttpStatus status) status=429

DefaultBlockRequestHandler.handleRequest()
```java
    @Override
    public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
        if (acceptsHtml(exchange)) {
            return htmlErrorResponse(ex);
        }
        // JSON result by default.
        return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
            .contentType(MediaType.APPLICATION_JSON_UTF8)
            .body(fromObject(buildErrorResult(ex)));
    }
```

SentinelGatewayBlockExceptionHandler#handle() ex is of type ParamFlowException; works for BlockException.isBlockException(ex)

Break at constructors of ParamFlowException, we arrive at the site at GatewayFlowSlot#checkGatewayParamFlow():

```java
    private void checkGatewayParamFlow(ResourceWrapper resourceWrapper, int count, Object... args) // resourceWrapper actual type: StringResourceWrapper
        throws BlockException {
        if (args == null) { // ["zh-cn"]
            return;
        }

        List<ParamFlowRule> rules = GatewayRuleManager.getConvertedParamRules(resourceWrapper.getName()); // look up the rules map; name = "httpbin_route"
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (ParamFlowRule rule : rules) { // rule: ParamFlowRule{grade=1, paramIdx=0, count=0.0, controlBehavior=0, maxQueueingTimeMs=500, burstCount=0, durationInSec=1, paramFlowItemList=[], clusterMode=false, clusterConfig=null}
            // Initialize the parameter metrics.
            ParameterMetricStorage.initParamMetricsFor(resourceWrapper, rule);

            if (!ParamFlowChecker.passCheck(resourceWrapper, rule, count, args)) {
                String triggeredParam = "";
                if (args.length > rule.getParamIdx()) {
                    Object value = args[rule.getParamIdx()];
                    triggeredParam = String.valueOf(value);
                }
                throw new ParamFlowException(resourceWrapper.getName(), triggeredParam, rule); // "httpbin_route", "zh-cn", the-rule
            }
        }
    }
```

ParamFlowChecker.passCheck() returns false because count > rule.count + burstCount.count (number of counts requested is larger than allowed).

#### Definition of gateway.json

GatewayFlowRule and helper GatewayParamFlowItem. Validity checks performed by GatewayRuleManager

The so-called "parse-strategy" values are defined in SentinelGatewayConstants:

```java
    public static final int PARAM_PARSE_STRATEGY_CLIENT_IP = 0;
    public static final int PARAM_PARSE_STRATEGY_HOST = 1;
    public static final int PARAM_PARSE_STRATEGY_HEADER = 2;
    public static final int PARAM_PARSE_STRATEGY_URL_PARAM = 3;
    public static final int PARAM_PARSE_STRATEGY_COOKIE = 4;
```

So our rule with value of 2 matches against http header.

#### Why is that we could not see our app in the sentinel dashboard?

The lazy mechanism. The app by default starts to send heartbeats to the dashboard (and gets itself shown in the there) after the first request to its API, or otherwise since start up if `spring.cloud.sentinel.eager` is set to true.

This is controlled indirectly by the TransportConfig.setRuntimePort(). This runtime port is the actual port used for the API server; it is initially null, in which case the heartbeat-sending logic will skip the job. SimpleHttpCommandCenter.start(). will call setRuntimePort() method to assign it.

In retrospect, we've arrived at runtimePort while debugging why our app is not shown in the dashboard in the first place. Yet there is absolute no comment in the source code that may help understand this.

This code would send heartbeat only when the runtimePort is set, which means either way we shall send it.

```java
    @Override
    public boolean sendHeartbeat() throws Exception {
        if (TransportConfig.getRuntimePort() <= 0) {
            RecordLog.info("[SimpleHttpHeartbeatSender] Command server port not initialized, won't send heartbeat");
            return false;
        }
        // ...
    }
```

Internally, the dashboard is called console server ("控制台服务器"？)

So here is the lesson:

* Don't provide syntax sugar or shortcuts that may not be useful at all; if you do, document it carefully and thoroughly.

##### How does the option `spring.cloud.sentinel.eager` work?

In SentinelAutoConfiguration#init(), it will call InitExecutor#doInit(). The latter will then create a service loader, discover the init functions, sort, run them one by one. The SimpleHttpCommandCenter.start() is one of them.

#### How is the dashboard port read from external configuration

There is a direct mapping between contents of the config file `application.yml` and SentinelProperties$Transport, because SentinelProperties is annotated with `@ConfigurationProperties(prefix = SentinelConstants.PROPERTY_PREFIX)` (where PROPERTY_PREFIX = "spring.cloud.sentinel").

SentinelAutoConfiguration.init() would try to read from config file (via SentinelProperties properties) only if the JVM option is not set (which means the latter has the higher precedence).

```java
		if (StringUtils.isEmpty(System.getProperty(TransportConfig.CONSOLE_SERVER))
				&& StringUtils.hasText(properties.getTransport().getDashboard())) {
			System.setProperty(TransportConfig.CONSOLE_SERVER,
					properties.getTransport().getDashboard());
		}
```

SentinelConfigLoader#load(): would first read from file `classpath:sentinel.properties` (alternatively defined in environment variable `SENTINEL_CONFIG_ENV_KEY`), then from `System.getProperties()`. All these are applied in that order to a map of properties.

SentinelConfig#loadProps(): it will set all properties to the private map `props`.

TransportConfig#getConsoleServerList(): it will load the server(s) from `SentinelConfig.getConfig(CONSOLE_SERVER)` (which will fetch from its internal `props` map), where as `CONSOLE_SERVER = "csp.sentinel.dashboard.server"`. This comma-separated list will be assigned to a SimpleHttpHeartbeatSender via its constructor.

```java
    public SimpleHttpHeartbeatSender() {
        // Retrieve the list of default addresses.
        List<Tuple2<String, Integer>> newAddrs = TransportConfig.getConsoleServerList();
        if (newAddrs.isEmpty()) {
            RecordLog.warn("[SimpleHttpHeartbeatSender] Dashboard server address not configured or not available");
        } else {
            RecordLog.info("[SimpleHttpHeartbeatSender] Default console address list retrieved: " + newAddrs);
        }
        this.addressList = newAddrs;
    }
```

Turned out SimpleHttpHeartbeatSender only picks the first (as of now) from the csv and use that in #sendHeartbeat().

#### How is the data source read from config file and loaded

SentinelDataSourceHandler#afterSingletonsInstantiated(): called from DefaultListableBeanFactory#preInstantiateSingletons(). It then calls #registerBean() with dataSourceName = `ds-sentinel-file-datasource`. Then a bean with the same name of type FileRefreshableDataSourceFactoryBean is registered to DefaultListableBeanFactory.

Question: how does the mapping of config file with multiple-level properties classes work?

```
spring.cloud.sentinel:
      datasource.ds2.file:
        file: "classpath: gateway.json"
        ruleType: gw-flow
```

* Seems that spring-boot recognizes that by property name. The class name or whether this second-level class is an inner does not matter (anyway static inner class is just another way to organize code) .See https://github.com/micronaut-projects/micronaut-core/issues/2373 for discussion in comparison with another framework named [micronaut](https://github.com/micronaut-projects). This makes sense as the datasource field is declared as `private Map<String, DataSourcePropertiesConfiguration> datasource`.
* Now for the second bit, it's for a map. If we change ds to ds2, everything works as usual; nothing breaks. This is because the key string is has no special meaning and is never used internally.
* And the 3rd level `file` is mapped to private field `FileDataSourceProperties file` of DataSourcePropertiesConfiguration. Then the FileDataSourceProperties class defines file and ruleType fields.

### 5.7 使用 Sentinel 保护应用防止服务雪崩

#### OpenFeign (via spring cloud Hoxton.SR10) + Sentinel (via spring cloud alibaba 2.2.5-RELEASE) that causes BeanCurrentlyInCreationException

Change of bean creation in FeignClientFactoryBean and FeignClientsRegistrar:

```
commit 7ef5c0ce96f323c1edddf4d23899fc201a9b098d
Author: Marcin Grzejszczak <marcin@grzejszczak.pl>
Date:   Wed Jan 13 10:15:05 2021 +0100

    Lazy openfeign bean registration (#455)

    without this change we're eagerly resolving placeholder properties in passed URLs / names. Since this happens at bean definition level it's extremely early e.g. Spring Cloud Contract has not yet registered any placeholders that Feign could try to consume.

    with this change we're changing the bean to become lazy initialized and we resolve the placeholder values at runtime - as late as possible.

    Fixes gh-441.
    # Conflicts:
    #       spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignClientFactoryBean.java
    #       spring-cloud-openfeign-core/src/main/java/org/springframework/cloud/openfeign/FeignClientsRegistrar.java
```

And interesting bits:

* FeignClientsRegistrar#registerFeignClient()

Spring Cloud Alibaba fix is at:

```
commit da197502688eb959f3256edf586fc37e0020dc7d
Author: theonefx <chenxilzx1@gmail.com>
Date:   Tue Feb 23 21:37:43 2021 +0800

    sentinel unit test pass
```

It changes the way to create the bean fom

```
Builder.this.applicationContext.getBean("&" + target.type().getName())
```

to

```
(FeignClientFactoryBean) def.getAttribute("feignClientsRegistrarFactoryBean")
```

Interesting bits:

* SentinelFeign#build()

* SentinelInvocationHandler#invoke(): every method call will be intercepted and arrive here.

#### How is the degrade rule loaded and triggered

The file is defined via config file:

```properties
spring.cloud.sentinel.datasource.ds.file.file=classpath: degraderule-openfeign.json
spring.cloud.sentinel.datasource.ds.file.data-type=json
spring.cloud.sentinel.datasource.ds.file.rule-type=degrade
```

The contents is:

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

Loading of the data is during #afterSingletonsInstantiated() of bean named of `12` type `SentinelDataSourceHandler`

The contents are read and converted to list of DegradeRule objects in SentinelConverter#convert(), then updated into value of a DynamicSentinelProperty (part of the bean named `dataSourceName` of type FileRefreshableDataSource).

The design of DynamicSentinelProperty is such that its internal value is exposed by adding external listerners. And because it is a degrade rule, a DegradeRuleManager$RulePropertyListener is registered.

According to DegradeRuleManager#isValidRule() and definitions in DegradeRule, the `grade` field has values:

```java
    public static final int DEGRADE_GRADE_RT = 0;
    /**
     * Degrade by biz exception ratio in the current {@link IntervalProperty#INTERVAL} second(s).
     */
    public static final int DEGRADE_GRADE_EXCEPTION_RATIO = 1;
    /**
     * Degrade by biz exception count in the last 60 seconds.
     */
    public static final int DEGRADE_GRADE_EXCEPTION_COUNT = 2;
```

And the three grade values have different `CircuitBreaker` implementations: for the first one, ResponseTimeCircuitBreaker; for the latter two, ExceptionCircuitBreaker.

Finally, DegradeRuleManager stores the rules internally in `private static volatile Map<String, List<CircuitBreaker>> circuitBreakers` and `private static volatile Map<String, Set<DegradeRule>> ruleMap` keyed by the resource (`GET:http://sms-service/send` in this case). 

When new connections are incoming, DegradeRuleManager#getCircuitBreakers() will be called to get the `CircuitBreaker` instances, twice for each request.

* The first request is part of DegradeSlot#entry() to decide whether to allow this request pass. The second one is part of DegradeSlot#exit(), when the request has passed, to decide whether to call CircuitBreaker#onRequestComplete() to decide whether there are too many slow requests and that it shall change the status.
* In between, `private SMSService smsService` has actual type of proxy to a SentinelInvocationHandler, whose #invoke() method will do the job. It knows its target as an instance of Target.HardCodedTarget. Its internal handling implements fall-back logic.
* Internally, sentinel would maintain a chain of ProcessorSlot. Upon enter and exit events, they are called one after another like a servlet filter chain or a netty handler chain.

#### How is the slide windowing mechanism implemented in class LeapArray

The array maintains a circular array of slots with each record of type T.

* The overall length of the time span covered by the whole array is intervalInMs.
* sampleCount is the number of units the whole time window is divided.
* So each time window covers time length of windowLengthInMs.
* Sliding window policy: If sampleCount > 1, it implements a overlapping sliding windowing strategy. The windowLengthInMs is length of each step sliding forward. If sampleCount = 1, it degenerates into a non-overlapping windowing.

For ResponseTimeCircuitBreaker, the slot type is SlowRequestCounter. And sampleCount = 1 (this is the case for ExceptionCircuitBreaker as well).

How to determine the slot for the current time? Simple math:

```java
    private int calculateTimeIdx(/*@Valid*/ long timeMillis) {
        long timeId = timeMillis / windowLengthInMs;
        // Calculate current index so we can map the timestamp to the leap array.
        return (int)(timeId % array.length());
    }

    protected long calculateWindowStart(/*@Valid*/ long timeMillis) {
        return timeMillis - timeMillis % windowLengthInMs;
    }
```

In #onRequestComplete(): it fetches the slot value of the current window (created if it does not exist):

```java
SlowRequestCounter counter = slidingCounter.currentWindow().value();
```

Then in the slot value, it updates the counts of total and slow requests.

And in #handleStateChangeWhenThresholdExceeded(), it fetches all the counters.

```java
        List<SlowRequestCounter> counters = slidingCounter.values();
```

#### The windowing mechanism of ResponseTimeCircuitBreaker

What's stated in DegradeRule may be inappropriate: it does not implement any of them. In particular, it states:

```
When the average RT exceeds the threshold ('count' in 'DegradeRule', in milliseconds), the resource enters a quasi-degraded state. If the RT of next coming 5 requests still exceed this threshold, this resource will be downgraded, which means that in the next time window (defined in 'timeWindow', in seconds) all the access to this resource will be blocked.
```

Quite a few guide articles would literally quote it. But we haven't encounter any implementation of this "quasi-degraded" logic.

There are two windowing logics. The first is in LeapArray (intervalInMs), to determine whether there are too many slow requests in the time window and change the breaker status; the other in recoveryTimeoutMs (assigned from DegradeRule.timeWindow), the time that a CircuitBreaker in OPEN status has to wait before it may switch to HALF_OPEN and try another request.

See in AbstractCircuitBreaker:

```java
    @Override
    public boolean tryPass(Context context) {
        // Template implementation.
        if (currentState.get() == State.CLOSED) {
            return true;
        }
        if (currentState.get() == State.OPEN) {
            // For half-open state we allow a request for probing.
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }
        return false;
    }
```

### 5.5 Hystrix 与 OpenFeign，Spring Cloud Circuit Breaker 的整合

#### Hystrix circuit-breaker key logic

##### HystrixOpenFeignApplication (with profile openfeign)

OnSubscribeThrow#call() called; the internal field exception is ```feign.RetryableException: httpbin.org executing GET https://httpbin.org/delay/3```

HystrixInvocationHandler#invoke(): to fetch fallback method and invoke it:

```
              Object fallback = fallbackFactory.create(getExecutionException());
              Object result = fallbackMethodMap.get(method).invoke(fallback, args);
```

