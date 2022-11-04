package com.wavefront.sdk.proxy;

import com.wavefront.sdk.common.WavefrontSender;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;

public class ProxyTest {
    private static final int NUM_THREADS = 10;
    private static final int NUM_ITERATIONS = 1000;
    private static final int THREAD_WAIT_ITERATIONS = 10;
    private static final List<String> EXPECTED_METRIC_LINE =
            Collections.singletonList("^\"dummy\" 1\\.0 [0-9]+ source=\"a-host\"$");

    private static class MockServer implements Runnable, Closeable {
        public static final int MOCK_PROXY_PORT = 12345;
        private ServerSocket s;

        static MockServer start() throws InterruptedException {
            MockServer server = new MockServer();
            Thread thread = new Thread(server);
            thread.setDaemon(false);
            thread.start();
            for (int i = 0; i < 10; i++) {
                if (server.isBound())
                    return server;
                Thread.sleep(100);
            }
            fail("Timed out waiting for MockServer to bind on port " + MOCK_PROXY_PORT);
            return null;
        }

        public void run() {
            try {
                s = new ServerSocket(MOCK_PROXY_PORT);

                Socket cs = s.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(cs.getInputStream()));
                int n = 0;
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.charAt(1) == '~' || line.charAt(2) == '~') {
                        System.out.println("Ignoring internal metric: " + line);
                        continue;
                    }
                    assertLinesMatch(EXPECTED_METRIC_LINE, Collections.singletonList(line));
                    n++;
                }
                assertEquals(NUM_ITERATIONS * NUM_THREADS, n, "Wrong number of messages received");
            } catch(IOException e) {
                fail(e);
            }
        }

        public boolean isBound() {
            return (s != null) && s.isBound() && !s.isClosed();
        }

        @Override
        public void close() throws IOException {
            s.close();
        }
    }

    private static Thread[] getAllThreads() {
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parentGroup;
        while ((parentGroup = rootGroup.getParent()) != null) {
            rootGroup = parentGroup;
        }
        Thread[] threads = new Thread[rootGroup.activeCount()];
        while (rootGroup.enumerate(threads, true ) == threads.length) {
            threads = new Thread[threads.length * 2];
        }
        return threads;
    }

    @Test
    public void testProxyRoundtrip() {
        try (MockServer mockProxy = MockServer.start()) {
            // Take a snapshot of active threads before we start the client
            Thread[] threads = getAllThreads();
            Map<Long, Thread> tMap = new HashMap<>();
            for(Thread t : threads) {
                if (t == null) {
                    continue;
                }
                tMap.put(t.getId(), t);
            }

            WavefrontProxyClient.Builder b = new WavefrontProxyClient.Builder("localhost");
            b.metricsPort(MockServer.MOCK_PROXY_PORT);
            final WavefrontSender wfSender = b.build();
            final Semaphore semaphore = new Semaphore(0);
            for(int i = 0; i < NUM_THREADS; ++i) {
                new Thread(() -> {
                    for (int j = 0; j < NUM_ITERATIONS; ++j) {
                        try {
                            wfSender.sendMetric("dummy", 1.0, System.currentTimeMillis(), "a-host", Collections.emptyMap());
                        } catch(IOException e) {
                            fail(e);
                        }
                    }
                    semaphore.release();
                }).start();
            }

            semaphore.acquire(NUM_THREADS);
            wfSender.close();

            // Wait for all new non-daemon threads to terminate (or timeout)
            int n = 0;
            for(;;) {
                threads = getAllThreads();
                int newT = 0;
                for(Thread t : threads) {
                    if (t == null) {
                        break;
                    }
                    if (!tMap.containsKey(t.getId()) && !t.isDaemon()) {
                        ++newT;
                        System.out.println("Non-daemon thread still running: " + t + ". Waiting for it to finish");
                    }
                }
                if (newT > 0) {
                    n++;
                    if (n >= THREAD_WAIT_ITERATIONS) {
                        fail("Gave up waiting for threads to exit");
                    }
                    Thread.sleep(1000);
                } else {
                    break;
                }
            }
        } catch(IOException | InterruptedException e) {
            fail(e);
        }
    }
}
