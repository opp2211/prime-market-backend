package ru.maltsev.primemarketbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI primeMarketOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Prime Market Backend API")
                .version("0.0.1-SNAPSHOT")
                .description("Generated API contract for frontend/backend synchronization."))
            .servers(List.of(new Server().url("/").description("Relative to the current backend base URL")))
            .components(new Components().addSecuritySchemes(
                "bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            ));
    }
}
