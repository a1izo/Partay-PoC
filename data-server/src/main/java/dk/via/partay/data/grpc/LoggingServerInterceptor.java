package dk.via.partay.data.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Logs one line per incoming gRPC call: method, status and time. */
@Component
public class LoggingServerInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingServerInterceptor.class);

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        long start = System.nanoTime();
        String method = call.getMethodDescriptor().getBareMethodName();
        ServerCall<Q, R> logging = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long ms = (System.nanoTime() - start) / 1_000_000;
                String detail = status.getDescription() == null ? "" : " " + status.getDescription();
                log.info("[T3] gRPC <- {} {}{}  ({} ms)", String.format("%-18s", method), status.getCode(), detail, ms);
                super.close(status, trailers);
            }
        };
        return next.startCall(logging, headers);
    }
}
