package ru.practice.support.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI supportApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Support Ticket API")
                .version("1.0.0")
                .description(
                    "Учебный сервис обработки заявок. closedAt заполнен только для RESOLVED; для NEW, IN_PROGRESS и CANCELLED он равен null. "));
  }
}
