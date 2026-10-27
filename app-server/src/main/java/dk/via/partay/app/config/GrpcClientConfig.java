package dk.via.partay.app.config;

import dk.via.partay.app.grpc.LoggingClientInterceptor;
import dk.via.partay.grpc.PartayDataServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel dataServerChannel(@Value("${data-server.host}") String host,
                                            @Value("${data-server.port}") int port,
                                            LoggingClientInterceptor loggingInterceptor) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .intercept(loggingInterceptor)
                .build();
    }

    /**
     * No deadline here on purpose: a deadline is absolute, so on a singleton stub it would expire for
     * every later call. DataClient sets one per call.
     */
    @Bean
    public PartayDataServiceGrpc.PartayDataServiceBlockingStub dataServerStub(ManagedChannel dataServerChannel) {
        return PartayDataServiceGrpc.newBlockingStub(dataServerChannel);
    }
}
