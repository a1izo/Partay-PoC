package dk.via.partay.data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** T3: stores and returns rows over gRPC. No business rules beyond database constraints. */
@SpringBootApplication
public class DataServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataServerApplication.class, args);
    }
}
