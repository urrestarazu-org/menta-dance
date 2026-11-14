package com.menta.virtual.infrastructure.cdn;

import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in developer diagnostic (issue #751, {@code app.cdn.bunny-net.diagnostic-probe}, off by default): decorates the
 * real {@link BunnyNetSignatureService} and, after each signed URL, sends ONE asynchronous {@code HEAD} request to it
 * and logs the status the CDN answers. The browser fetches the MP4 straight from Bunny.net, so a 403/404 never shows
 * up in the API or BFF logs; this surfaces it.
 *
 * <p><strong>It cannot affect the lesson response:</strong> the URL is returned as soon as the delegate signs it; the
 * request is fire-and-forget ({@code sendAsync}, 5 s timeout, no redirects, no retries), every failure is caught and
 * logged, and the probe's own code never throws into the caller.</p>
 *
 * <p><strong>Referer:</strong> a browser {@code <video>} sends one, and the library option "Block Direct URL File
 * Access" rejects requests without it (403 even with a perfect token). The optional
 * {@code app.cdn.bunny-net.diagnostic-probe-referer} is sent as the {@code Referer} header so the probe sees what the
 * browser would; blank (default) sends none. The Referer is not a secret and is never combined with the URL in a log
 * line.</p>
 *
 * <p><strong>Secret handling:</strong> the signed URL carries the token, so it is sent only to the pull-zone host it
 * already points at and is never logged. Failures log only the exception class name, because some exceptions embed
 * the URI in their message.</p>
 */
public final class ProbingBunnyNetSignatureService implements BunnyNetSignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(ProbingBunnyNetSignatureService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String HINT_PREFIX = "Bunny.net CDN probe hint: ";

    private final BunnyNetSignatureService delegate;
    private final String fileName;
    private final String referer;
    private final UnaryOperator<String> targetMapper;
    private final Duration timeout;
    private final HttpClient client;

    public ProbingBunnyNetSignatureService(BunnyNetSignatureService delegate, BunnyNetProperties properties) {
        this(delegate, properties, UnaryOperator.identity(), TIMEOUT);
    }

    /** Test seam: {@code targetMapper} redirects the probe (e.g. to a local fake server); production keeps the URL. */
    ProbingBunnyNetSignatureService(
        BunnyNetSignatureService delegate, BunnyNetProperties properties,
        UnaryOperator<String> targetMapper, Duration timeout
    ) {
        this.delegate = delegate;
        this.fileName = properties.getFileName();
        this.referer = properties.probeReferer();
        this.targetMapper = targetMapper;
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public String generateSignedUrl(String videoId, long expirationTimeInSeconds) {
        String signedUrl = delegate.generateSignedUrl(videoId, expirationTimeInSeconds);
        probe(videoId, signedUrl);
        return signedUrl;
    }

    private void probe(String videoId, String signedUrl) {
        long startedAt = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetMapper.apply(signedUrl)))
                .timeout(timeout)
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
            if (referer != null) {
                builder.header("Referer", referer);
            }
            HttpRequest request = builder.build();
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                if (failure != null) {
                    logFailure(videoId, failure);
                } else {
                    logStatus(videoId, response.statusCode(), (System.nanoTime() - startedAt) / 1_000_000L);
                }
            });
        } catch (RuntimeException failure) {
            logFailure(videoId, failure);
        }
    }

    private void logStatus(String videoId, int status, long elapsedMillis) {
        LOG.info("Bunny.net CDN probe: videoId={} file={} status={} in {}ms", videoId, fileName, status, elapsedMillis);
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 403) {
            LOG.warn(HINT_PREFIX + "403 = token rejected (wrong key: use the library Security page 'Token "
                + "authentication key'), OR the request has no/unallowed Referer while 'Block direct URL file "
                + "access' / allowed domains are on (set APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER="
                + "http://localhost:8080/)");
        } else if (status == 404) {
            LOG.warn(HINT_PREFIX + "404 = file not found: MP4 Fallback not active for this video (only videos "
                + "uploaded after enabling it get an MP4) or the file name is different "
                + "(APP_CDN_BUNNYNET_FILENAME)");
        } else {
            LOG.warn(HINT_PREFIX + "unexpected status {}; compare with the same request in the browser Network tab",
                status);
        }
    }

    private void logFailure(String videoId, Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
            && cause.getCause() != null) {
            cause = cause.getCause();
        }
        // Class name only: URI-bearing exceptions would leak the token-carrying URL through getMessage().
        LOG.warn("Bunny.net CDN probe failed: videoId={} file={} error={}",
            videoId, fileName, cause.getClass().getSimpleName());
    }
}
