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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author <a href="mailto:fangjian0423@gmail.com">Jim</a>
 */
@SpringBootApplication
public class ConfigClientRefreshApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigClientRefreshApplication.class, args);
    }

    @RestController
    @RefreshScope
    static class ConfigurationController {

        @Value("${book.author:unknown}")
        String bookAuthor;

        @Value("${book.name:unknown}")
        String bookName;

        @Value("${book.category:unknown}")
        String bookCategory;

        @GetMapping("/config")
        public String config() {
            StringBuilder sb = new StringBuilder();
            sb.append("bookAuthor=" + bookAuthor)
                .append("<br/>bookName=" + bookName)
                .append("<br/>bookCategory=" + bookCategory);
            return sb.toString();
        }

    }

}
