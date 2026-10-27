package dk.via.partay.app.error;

/** A system T2 depends on (T3 or Spotify) failed. 503 when it is down or rate limited, 502 otherwise. */
public class UpstreamException extends RuntimeException {

    private final int status;

    public UpstreamException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
