# Set Up a WavefrontSender
You can choose to send metrics, histograms, or trace data from your application to the Wavefront service using one of the following techniques:
* Use [direct ingestion](https://docs.wavefront.com/direct_ingestion.html) to send the data directly to the Wavefront service. This is the simplest way to get up and running quickly.
* Use a [Wavefront proxy](https://docs.wavefront.com/proxies.html), which then forwards the data to the Wavefront service. This is the recommended choice for a large-scale deployment that needs resilience to internet outages, control over data queuing and filtering, and more. 

The `WavefrontSender` interface has two implementations. Instantiate the implementation that corresponds to your choice:
* Option 1: [Create a `WavefrontDirectIngestionClient`](#option-1-create-a-wavefrontdirectingestionclient) to send data directly to a Wavefront service.
* Option 2: [Create a `WavefrontProxyClient`](#option-2-create-a-wavefrontproxyclient) to send data to a Wavefront proxy.

## Option 1: Create a WavefrontDirectIngestionClient
To create a `WavefrontDirectIngestionClient`, you build it with the information it needs to send data directly to Wavefront.

### Step 1. Obtain Wavefront Access Information
Gather the following access information:

* Identify the URL of your Wavefront instance. This is the URL you connect to when you log in to Wavefront, typically something like `https://<domain>.wavefront.com`.
* In Wavefront, verify that you have Direct Data Ingestion permission, and [obtain an API token](http://docs.wavefront.com/wavefront_api.html#generating-an-api-token).

### Step 2. Initialize the WavefrontDirectIngestionClient
You initialize a `WavefrontDirectIngestionClient` by building it with the access information you obtained in Step 1.

You can optionally call builder methods to tune the following ingestion properties:

* Max queue size - Internal buffer capacity of the `WavefrontSender`. Any data in excess of this size is dropped.
* Flush interval - Interval for flushing data from the `WavefrontSender` directly to Wavefront.
* Batch size - Amount of data to send to Wavefront in each flush interval.

Together, the batch size and flush interval control the maximum theoretical throughput of the `WavefrontSender`. You should override the defaults _only_ to set higher values.

```java
// Create a builder with the Wavefront URL and a Wavefront API token
// that was created with direct ingestion permission.
WavefrontDirectIngestionClient.Builder wfDirectIngestionClientBuilder =
  new WavefrontDirectIngestionClient.Builder(wavefrontURL, token);

// Optional: Override the max queue size (in data points). Default: 50,000
wfDirectIngestionClientBuilder.maxQueueSize(100_000);

// Optional: Override the batch size (in data points). Default: 10,000
wfDirectIngestionClientBuilder.batchSize(20_000);

// Optional: Override the flush interval (in seconds). Default: 1 second
wfDirectIngestionClientBuilder.flushIntervalSeconds(2);

// Create a WavefrontDirectIngestionClient.
WavefrontSender wavefrontSender = wfDirectIngestionClientBuilder.build();
 ```

## Option 2: Create a WavefrontProxyClient

**Note:** Before your application can use a `WavefrontProxyClient`, you must [set up and start a Wavefront proxy](https://github.com/wavefrontHQ/java/tree/master/proxy#set-up-a-wavefront-proxy).

To create a `WavefrontProxyClient`, you build it with the information it needs to send data to a Wavefront proxy, including:

* The name of the host that will run the Wavefront proxy.
* One or more proxy listening ports to send data to. The ports you specify depend on the kinds of data you want to send (metrics, histograms, and/or trace data). You must specify at least one listener port. 
* Optional settings for tuning communication with the proxy.


```java
// Create the builder with the proxy hostname or address
WavefrontProxyClient.Builder wfProxyClientBuilder = new WavefrontProxyClient.Builder(proxyHostName);

// Set the proxy port to send metrics to. Default: 2878
wfProxyClientBuilder.metricsPort(2878);

// Set a proxy port to send histograms to.  Recommended: 40000
wfProxyClientBuilder.distributionPort(40_000);

// Set a proxy port to send trace data to. Recommended: 30000
wfProxyClientBuilder.tracingPort(30_000);

// Optional: Set a custom socketFactory to override the default SocketFactory
wfProxyClientBuilder.socketFactory(<SocketFactory>);

// Optional: Set a nondefault interval (in seconds) for flushing data from the sender to the proxy. Default: 5 seconds
wfProxyClientBuilder.flushIntervalSeconds(2);

// Create the WavefrontProxyClient
WavefrontSender wavefrontSender = wfProxyClientBuilder.build();
 ```
**Note:** When you [set up a Wavefront proxy](https://github.com/wavefrontHQ/java/tree/master/proxy#set-up-a-wavefront-proxy) on the specified proxy host, you specify the port it will listen to for each type of data to be sent. The `WavefrontProxyClient` must send data to the same ports that the Wavefront proxy listens to. Consequently, the port-related builder methods must specify the same port numbers as the corresponding proxy configuration properties: 

| `WavefrontProxyClient` builder method | Corresponding property in `wavefront.conf` |
| ----- | -------- |
| `metricsPort()` | `pushListenerPorts=` |
| `distributionPort()` | `histogramDistListenerPorts=` |
| `tracingPort()` | `traceListenerPorts=` |
 
# Share a WavefrontSender

Various Wavefront SDKs for Java use this library and require a `WavefrontSender` instance.

If you are using multiple Wavefront Java SDKs within the same JVM process, you can instantiate the `WavefrontSender` just once and share it among the SDKs. 
 
For example, the following snippet shows how to use the same `WavefrontSender` when setting up the [wavefront-opentracing-sdk-java](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java) and  [wavefront-dropwizard-metrics-sdk-java](https://github.com/wavefrontHQ/wavefront-dropwizard-metrics-sdk-java) SDKs.

```java

// Create a WavefrontSender
WavefrontSender wavefrontSender = buildProxyOrDirectSender(); // pseudocode

// Create a WavefrontSpanReporter for the OpenTracing SDK
Reporter spanReporter = new WavefrontSpanReporter.Builder().
   withSource("wavefront-tracing-example").
   build(wavefrontSender);

// Create a Wavefront reporter for the Dropwizard Metrics SDK
MetricRegistry registry = new MetricRegistry();
DropwizardMetricsReporter.Builder builder =   
DropwizardMetricsReporter metricsReporter =
   DropwizardMetricsReporter.forRegistry(registry).
   build(wavefrontSender);
...
```

**Note:** If you use SDKs in different JVM processes, you must instantiate one `WavefrontSender` instance per JVM process.


