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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bootstrap.config.PropertySourceBootstrapProperties;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.cloud.config.client.ConfigServicePropertySourceLocator;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ConfigClientServiceRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientServiceRegistryApplication.class);
    }

    @RestController
    @RefreshScope
    static class ConfigurationController {
        /**
         * To insert PropertySource<?> into environment such that they may be fetched later.
         * TODO we must be doing it the harder way to copy framework code; figure out a smarter way
         * @param propertySources MutablePropertySources from ConfigurableEnvironment environment
         * @param composite PropertySource<?> as returned by locator.locate().
         * @see org.springframework.cloud.bootstrap.config.PropertySourceBootstrapConfiguration#insertPropertySources()
         */
        private void insertPropertySources(MutablePropertySources propertySources,
                                           List<PropertySource<?>> composite) {
            MutablePropertySources incoming = new MutablePropertySources();
            List<PropertySource<?>> reversedComposite = new ArrayList<>(composite);
            // Reverse the list so that when we call addFirst below we are maintaining the
            // same order of PropertySources
            // Wherever we call addLast we can use the order in the List since the first item
            // will end up before the rest
            Collections.reverse(reversedComposite);
            for (PropertySource<?> p : reversedComposite) {
                incoming.addFirst(p);
            }
            PropertySourceBootstrapProperties remoteProperties = new PropertySourceBootstrapProperties();
            if (!remoteProperties.isAllowOverride() || (!remoteProperties.isOverrideNone()
                    && remoteProperties.isOverrideSystemProperties())) {
                for (PropertySource<?> p : reversedComposite) {
                    propertySources.addFirst(p);
                }
                return;
            }
            if (remoteProperties.isOverrideNone()) {
                for (PropertySource<?> p : composite) {
                    propertySources.addLast(p);
                }
                return;
            }
            if (propertySources.contains(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                if (!remoteProperties.isOverrideSystemProperties()) {
                    for (PropertySource<?> p : reversedComposite) {
                        propertySources.addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
                    }
                }
                else {
                    for (PropertySource<?> p : composite) {
                        propertySources.addBefore(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, p);
                    }
                }
            }
            else {
                for (PropertySource<?> p : composite) {
                    propertySources.addLast(p);
                }
            }
        }

        // TODO define behavior of refreshing.
        // ConfigClientProperties config will be auto refreshed by DiscoveryClientConfigServiceBootstrapConfiguration.
        // First, the config server info from nacos may be refreshed; second, config data from config server may change.
        private void fetchAndUpdateEnvironment(ConfigClientProperties config) {
            PropertySourceLocator locator =  new ConfigServicePropertySourceLocator(config);
            PropertySource<?> composite = locator.locate(environment);
            MutablePropertySources propertySources = environment.getPropertySources();
            insertPropertySources(propertySources, Collections.singletonList(composite));
        }

/*        @Value("${book.author:unknown}")
        String bookAuthor;

        @Value("${book.name:unknown}")
        String bookName;

        @Value("${book.category:unknown}")
        String bookCategory;*/

        @Autowired
        ConfigurableEnvironment environment;

        @Autowired
        private ConfigClientProperties config;

        @GetMapping("/config")
        public String config() {
            fetchAndUpdateEnvironment(config);
            String bookCategory = environment.getProperty("book.category", "unknown");
            String bookName = environment.getProperty("book.name", "unknown");
            String bookAuthor = environment.getProperty("book.author", "unknown");
            StringBuilder sb = new StringBuilder();
            sb.append("bookAuthor=" + bookAuthor)
                    .append("<br/>bookName=" + bookName)
                    .append("<br/>bookCategory=" + bookCategory);
            return sb.toString();
        }

    }
}
