package com.emreay.incidentreport.web;

import com.emreay.incidentreport.shared.error.DomainValidationException;
import com.emreay.incidentreport.shared.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * Turns every failure into an RFC 7807 {@code application/problem+json} response.
 *
 * <p>Lives in {@code app} rather than in a module because it answers for all of them: the exception
 * types it maps come from the shared kernel, and there should be exactly one description of what an
 * error looks like on the wire. Extending {@link ResponseEntityExceptionHandler} means Spring's own
 * failures — malformed JSON, wrong method, unsupported media type — come out in the same shape
 * instead of the default HTML-ish body.
 *
 * <p>Nothing internal is allowed into a response. Validation problems carry the domain's stable
 * code; anything unexpected becomes a plain 500 with a reference id that also appears in the log,
 * so an operator can find the stack trace without it ever being sent to the caller.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Problem types are documented under this prefix. A URI here is an identifier, not a link the
     * client must fetch — which is what RFC 7807 intends.
     */
    private static final String PROBLEM_TYPE_PREFIX = "https://incident-report/problems/";

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail handleValidation(DomainValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + exception.getCode()));
        // The stable, machine-readable half of the contract - clients branch on this, not on the
        // human-readable detail, which is free to be reworded.
        problem.setProperty("code", exception.getCode());
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Not found");
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + "resource.not-found"));
        problem.setProperty("code", "resource.not-found");
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }

    /**
     * Last resort. The caller learns only that something broke and gets a reference to quote; the
     * cause goes to the log. Echoing an exception message back is how internal detail — table
     * names, connection strings, library internals — ends up in front of whoever asked.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        String reference = UUID.randomUUID().toString();
        log.error("unexpected failure, reference {}", reference, exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The request could not be completed. Quote the reference when reporting this.");
        problem.setTitle("Internal error");
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + "internal"));
        problem.setProperty("code", "internal");
        problem.setProperty("reference", reference);
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }

    /**
     * Spring's own web failures reach here. The body is already a {@code ProblemDetail}; this adds
     * the same {@code code}, {@code type} and {@code timestamp} the handlers above set, so a client
     * sees one consistent shape whatever went wrong.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                            @Nullable Object body,
                                                            HttpHeaders headers,
                                                            HttpStatusCode statusCode,
                                                            WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            String code = codeFor(statusCode);
            problem.setType(URI.create(PROBLEM_TYPE_PREFIX + code));
            problem.setProperty("code", code);
            problem.setProperty("timestamp", clock.instant());
        }
        return response;
    }

    /**
     * Derives the code from the status rather than labelling everything the same thing.
     *
     * <p>An earlier version called every Spring-level failure {@code request.malformed}, which told
     * a client asking with the wrong HTTP method that its JSON was broken. The code is the half of
     * this contract clients branch on; being vague there is worse than being verbose.
     */
    private static String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String name = status != null ? status.name() : "status-" + statusCode.value();
        return "request." + name.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
