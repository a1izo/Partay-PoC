package dk.via.partay.app.grpc;

import dk.via.partay.app.trace.TraceRecorder;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs one line per outgoing gRPC call and adds it as a hop to the request trace.
 * With the blocking stub, onClose runs on the request thread, so the trace ThreadLocal is in scope.
 */
@Component
public class LoggingClientInterceptor implements ClientInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientInterceptor.class);

    private final TraceRecorder traces;

    public LoggingClientInterceptor(TraceRecorder traces) {
        this.traces = traces;
    }

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
        String name = method.getBareMethodName();
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<R> responseListener, Metadata headers) {
                long start = System.nanoTime();
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        long ms = (System.nanoTime() - start) / 1_000_000;
                        String detail = status.isOk() || status.getDescription() == null ? "" : " " + status.getDescription();
                        log.info("[T2] gRPC -> {} {}{}  ({} ms)", String.format("%-18s", name), status.getCode(), detail, ms);
                        traces.hop("GRPC", name, status.getCode().name(), ms);
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
