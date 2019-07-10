package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WavefrontProxyMultiClient that sends data directly via TCP to multiple Wavefront endpoints running a Proxy Agent.
 * User should probably attempt to reconnect when exceptions are thrown from any methods.
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com).
 */
public class WavefrontMultiProxyClient implements WavefrontSender, Runnable {

    private static final Logger logger = Logger.getLogger(
            WavefrontMultiProxyClient.class.getCanonicalName());
    private static final ConcurrentHashMap<String, WavefrontProxyClient> wavefrontProxyClients = new ConcurrentHashMap<>();

    public static class Builder {
        private final ConcurrentHashMap<String, WavefrontProxyClient> wavefrontProxyClients = new ConcurrentHashMap<>();

        public Builder withWavefrontProxyClient(String id, WavefrontProxyClient proxyClient) {
            wavefrontProxyClients.put(id, proxyClient);
            return this;
        }

        public WavefrontMultiProxyClient build() {
            return new WavefrontMultiProxyClient(this);
        }

    }

    private WavefrontMultiProxyClient(Builder builder) {
        wavefrontProxyClients.putAll(builder.wavefrontProxyClients);
    }

    /**
     * Provide direct access to a specific client by id
     * @param id
     * @return
     */
    public  WavefrontProxyClient getClient(String id) {
        return wavefrontProxyClients.getOrDefault(id, null);
    }

    @Override
    public void flush() throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.flush();
        }
    }

    @Override
    public int getFailureCount() {
        int failureCount = 0;
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            failureCount += client.getFailureCount();
        }
        return failureCount;
    }

    @Override
    public void sendMetric(String name, double value, @Nullable Long timestamp,
                           @Nullable String source, @Nullable Map<String, String> tags)
            throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.sendMetric(name, value, timestamp, source, tags);
        }
    }

    @Override
    public void sendFormattedMetric(String point) throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.sendFormattedMetric(point);
        }
    }

    @Override
    public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                                 Set<HistogramGranularity> histogramGranularities,
                                 @Nullable Long timestamp, @Nullable String source,
                                 @Nullable Map<String, String> tags)
            throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.sendDistribution(name, centroids, histogramGranularities, timestamp, source, tags);
        }
    }

    @Override
    public void sendSpan(String name, long startMillis, long durationMillis,
                         @Nullable String source, UUID traceId, UUID spanId,
                         @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                         @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
            throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.sendSpan(name, startMillis, durationMillis, source, traceId, spanId, parents, followsFrom, tags, spanLogs);
        }
    }

    @Override
    public void close() throws IOException {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.close();
        }
    }

    @Override
    public void run() {
        for (WavefrontProxyClient client : wavefrontProxyClients.values()) {
            client.run();
        }
    }
}
