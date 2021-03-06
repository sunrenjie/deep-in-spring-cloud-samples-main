/*
 * Copyright (C) 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package deep.in.spring.cloud;

import java.util.List;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.dubbo.annotation.DubboTransported;

/**
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
@SpringBootApplication
@EnableFeignClients
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }

    @RestController
    static class ConsumerController {

        @DubboReference(version = "1.0.0", protocol = "dubbo")
        private OrderService orderService;

        @Autowired
        private FeignOrderService feignOrderService;

        @Autowired
        private DubboFeignOrderService dubboFeignOrderService;

        @GetMapping("/rawDubbo/{userId}")
        public List<Order> rawDubbo(@PathVariable String userId) {
            return orderService.getAllOrders(userId);
        }

        @GetMapping("/feignDubbo/{userId}")
        public List<Order> feignDubbo(@PathVariable String userId) {
            return dubboFeignOrderService.getAllOrders(userId);
        }

        @GetMapping("/rawFeign/{userId}")
        public List<Order> rawFeign(@PathVariable String userId) {
            return feignOrderService.getAllOrders(userId);
        }

    }

    @FeignClient("sc-dubbo-provider")
    public interface FeignOrderService {

        @GetMapping("/allOrders")
        List<Order> getAllOrders(@RequestParam("userId") final String userId);

        @GetMapping("/findOrder")
        Order findOrder(@RequestParam("orderId") String orderId);

    }

    @FeignClient("sc-dubbo-provider")
    @DubboTransported(protocol = "dubbo")
    public interface DubboFeignOrderService {

        @GetMapping("/allOrders")
        List<Order> getAllOrders(@RequestParam("userId") final String userId);

        @GetMapping("/findOrder")
        Order findOrder(@RequestParam("orderId") String orderId);

    }


}
