package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
/* WebClientConfig - конфигурация WebClient для взаимодействия с другими сервисами 
    WebClient - это класс, который используется для взаимодействия с другими сервисами.
    WebClient.Builder - это класс, который используется для построения WebClient.
    WebClient.Builder.build() - это метод, который используется для построения WebClient.
*/
@Configuration
public class WebClientConfig {
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                .build();
    }
}
