package dk.via.partay.app.web;

import dk.via.partay.app.error.BadRequestException;
import dk.via.partay.app.error.ConflictException;
import dk.via.partay.app.error.ForbiddenException;
import dk.via.partay.app.error.NotFoundException;
import dk.via.partay.app.error.UpstreamException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.stream.Collectors;

/** Every error leaves T2 as RFC 9457 ProblemDetail JSON. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(NotFoundException e, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> forbidden(ForbiddenException e, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(ConflictException e, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), req);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> badRequest(BadRequestException e, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), req);
    }

    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ProblemDetail> upstream(UpstreamException e, HttpServletRequest req) {
        return problem(HttpStatus.valueOf(e.getStatus()), e.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException e, HttpServletRequest req) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, detail, req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException e, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Header " + e.getHeaderName() + " is required", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, "'" + e.getValue() + "' is not a valid " + e.getName(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "No endpoint " + req.getMethod() + " " + req.getRequestURI(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> method(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest req) {
        log.error("[T2] unexpected error on {} {}", req.getMethod(), req.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), req);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, HttpServletRequest req) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setInstance(URI.create(req.getRequestURI()));
        return ResponseEntity.status(status).body(body);
    }
}
