package dk.via.partay.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI partayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Partay PoC - T2 API")
                .version("0.1.0")
                .description("""
                        Three tiers: T1 (this Swagger UI, the demo page at /, PowerShell scripts) calls \
                        T2 app-server over REST. T2 owns every business rule, calls T3 data-server over gRPC \
                        and the Spotify Web API over REST. T3 stores rows in MySQL.

                        Guests identify themselves with the X-Guest-Id header (the guestId returned when \
                        creating or joining a party). No accounts."""));
    }
}
