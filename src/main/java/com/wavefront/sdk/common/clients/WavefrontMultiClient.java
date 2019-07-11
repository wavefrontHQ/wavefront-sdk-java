package com.wavefront.sdk.common.clients;

import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.annotation.Nullable;
import com.wavefront.sdk.common.clients.exceptions.MultiClientIOException;
import com.wavefront.sdk.entities.histograms.HistogramGranularity;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * WavefrontMultiClient supports multicasting metrics/distributions/spans
 * onto multiple endpoints for either Proxy or Direct Ingestion.
 * User should probably attempt to reconnect when exceptions are thrown from any methods.
 *
 * @author Mike McMahon (mike.mcmahon@wavefront.com).
 */
public class WavefrontMultiClient<T extends WavefrontSender & Runnable> implements WavefrontSender, Runnable {
    private static final Logger logger = Logger.getLogger(
            WavefrontProxyClient.class.getCanonicalName());

    private final ConcurrentHashMap<String, T> wavefrontSenders = new ConcurrentHashMap<>();

    public static class Builder<T extends WavefrontSender & Runnable> {
        private final ConcurrentHashMap<String, T> wavefrontSenders = new ConcurrentHashMap<>();

        public Builder withWavefrontSender(T sender) {
            if (wavefrontSenders.containsKey(sender.getClientId()))
                throw new IllegalArgumentException("Duplicate Client specified");

            wavefrontSenders.put(sender.getClientId(), sender);
            return this;
        }

        public WavefrontMultiClient<T> build() {
            return new WavefrontMultiClient<T>(this);
        }
    }

    private WavefrontMultiClient(Builder<T> builder) {
        this.wavefrontSenders.putAll(builder.wavefrontSenders);
    }

    /**
     * Provide direct access to a specific client by id.
     *
     * @param clientId the unique client ID generated when a Proxy or DirectIngestion Client is instantiated.
     * @return The client found or null if no client is found matching the supplied clientId.
     */
    public T getClient(String clientId) {
        return wavefrontSenders.getOrDefault(clientId, null);
    }

    @Override
    public void flush() throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.flush();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + getClientId() + " failed to flush.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public int getFailureCount() {
        int failureCount = 0;
        for (T client : wavefrontSenders.values()) {
            failureCount += client.getFailureCount();
        }
        return failureCount;
    }

    /**
     * Obtain the failure counts per endpoint.
     *
     * @return a map of clientId's to failures per endpoint.
     */
    public Map<String, Integer> getFailureCountPerSender() {
        final ConcurrentHashMap<String, Integer> failuresPerSender = new ConcurrentHashMap<>();
        for (Map.Entry<String, T> e : wavefrontSenders.entrySet()) {
            failuresPerSender.put(e.getKey(), e.getValue().getFailureCount());
        }

        return failuresPerSender;
    }

    @Override
    public void sendMetric(String name, double value, @Nullable Long timestamp,
                           @Nullable String source, @Nullable Map<String, String> tags)
            throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.sendMetric(name, value, timestamp, source, tags);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + client.getClientId() + " failed to send metric.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public void sendFormattedMetric(String point) throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.sendFormattedMetric(point);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + client.getClientId() + " failed to send formatted metric.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public void sendDistribution(String name, List<Pair<Double, Integer>> centroids,
                                 Set<HistogramGranularity> histogramGranularities,
                                 @Nullable Long timestamp, @Nullable String source,
                                 @Nullable Map<String, String> tags)
            throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.sendDistribution(name, centroids, histogramGranularities, timestamp, source, tags);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + client.getClientId() + " failed to send distribution.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public void sendSpan(String name, long startMillis, long durationMillis,
                         @Nullable String source, UUID traceId, UUID spanId,
                         @Nullable List<UUID> parents, @Nullable List<UUID> followsFrom,
                         @Nullable List<Pair<String, String>> tags, @Nullable List<SpanLog> spanLogs)
            throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.sendSpan(name, startMillis, durationMillis, source, traceId, spanId, parents, followsFrom, tags, spanLogs);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + client.getClientId() + " failed to send span.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public void close() throws IOException {
        MultiClientIOException exceptions = new MultiClientIOException();
        for (T client : wavefrontSenders.values()) {
            try {
                client.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Client " + client.getClientId() + " failed to close.", ex);
                exceptions.add(ex);
            }
        }

        exceptions.checkAndThrow();
    }

    @Override
    public void run() {
        for (T client : wavefrontSenders.values()) {
            client.run();
        }
    }

    @Override
    public String getClientId() {
        return "MultiClient";
    }
}
