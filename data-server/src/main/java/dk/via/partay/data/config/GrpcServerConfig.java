package dk.via.partay.data.config;

import dk.via.partay.data.grpc.LoggingServerInterceptor;
import dk.via.partay.data.grpc.PartayDataGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Starts the plain grpc-java server with the Spring context and stops it on shutdown.
 * Reflection is on, so Postman (gRPC request) or grpcurl can list and call the service.
 */
@Component
public class GrpcServerConfig implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerConfig.class);

    private final PartayDataGrpcService service;
    private final LoggingServerInterceptor loggingInterceptor;
    private final int port;
    private volatile Server server;

    public GrpcServerConfig(PartayDataGrpcService service, LoggingServerInterceptor loggingInterceptor,
                            @Value("${grpc.server.port}") int port) {
        this.service = service;
        this.loggingInterceptor = loggingInterceptor;
        this.port = port;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(ServerInterceptors.intercept(service, loggingInterceptor))
                    .addService(ProtoReflectionService.newInstance())
                    .build()
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start gRPC server on port " + port, e);
        }
        log.info("[T3] gRPC data-server listening on port {} (reflection enabled)", port);

        // The app has no web server, so without a non-daemon thread the JVM exits right after startup.
        Thread awaiter = new Thread(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "grpc-await-termination");
        awaiter.setDaemon(false);
        awaiter.start();
    }

    @Override
    public void stop() {
        Server s = server;
        if (s != null) {
            s.shutdown();
            try {
                if (!s.awaitTermination(5, TimeUnit.SECONDS)) {
                    s.shutdownNow();
                }
            } catch (InterruptedException e) {
                s.shutdownNow();
                Thread.currentThread().interrupt();
            }
            server = null;
            log.info("[T3] gRPC data-server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return server != null && !server.isShutdown();
    }
}
