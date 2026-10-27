package dk.via.partay.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** T2: REST API, all business rules, gRPC client to T3 and REST client to Spotify. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AppServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppServerApplication.class, args);
    }
}
