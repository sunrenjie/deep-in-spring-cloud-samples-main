#### How should the Nacos-to-Eureka migration experiment be carried out

Preliminaries: how to run the two servers:

* Build and run chapter02/spring-cloud-netflix-eureka-server. Its server host is defined as "eureka" by all hosts.
* Download Nacos, run it via nacos/bin/startup -m standalone. Its server host is defined as "nacos" by all hosts.

Port definition matrix:

| Type         | Provider | Consumer |
| ------------ | -------- | -------- |
| eureka-only  | 18080    | 18081    |
| eureka-nacos | 28080    | 28081    |
| nacos-only   | 38080    | 38081    |

Steps

* Run the Eureka server.
* Have eureka-provider@18080 and eureka-consumer@18081 running.
* Run the Nacos server. Actually this step may be performed now or be delayed at most to the point before eureka server is taken down.
* Have in addition eureka-nacos-provider@28080 running.
* Take down eureka-provider@18080.
* Have in addition eureka-nacos-consumer@28081 running.
* Take down eureka-consumer@18081.
* Take down Eureka server.
* Optionally run nacos-provider@38080 and nacos-consumer@38081; after that take down eureka-nacos-provider@28080 and eureka-nacos-consumer@28081.

In addition, try to run the eureka/nacos servers, providers, consumers in different OS hosts to ensure the services are not localhost-only.
