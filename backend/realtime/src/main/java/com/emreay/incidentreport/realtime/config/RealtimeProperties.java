package com.emreay.incidentreport.realtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tunables for the live stream (TC-10).
 *
 * <p>Both values exist because a Server-Sent Events connection is a held resource, not a request
 * that ends. They are configuration rather than constants so an operator can trade reconnect churn
 * against how long a dead connection may sit around, without a rebuild.
 *
 * @param streamTimeout     how long a single subscription may stay open before the server closes
 *                          it. Not a limit on watching — the browser's {@code EventSource}
 *                          reconnects on its own and nothing is lost, because the stream carries no
 *                          data (ADR-021). It is a ceiling on how long anything the server forgot
 *                          to clean up can survive.
 * @param heartbeatInterval how often a comment is written to every open subscription. It keeps
 *                          proxies from closing an idle connection, and it is how the server finds
 *                          out that a client has gone: a browser tab that was closed abruptly leaves
 *                          a socket that looks alive until something is written to it.
 */
@ConfigurationProperties(prefix = "incident-report.realtime")
public record RealtimeProperties(@DefaultValue("30m") Duration streamTimeout,
                                 @DefaultValue("20s") Duration heartbeatInterval) {
}
