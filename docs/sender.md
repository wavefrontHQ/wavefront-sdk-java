# Set Up a WavefrontSender
You can choose to send data to Wavefront using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

The `WavefrontSender` interface has two implementations. Instantiate the implementation that corresponds to your choice:
* Option 1: [Create a `WavefrontProxyClient`](#option-1-create-a-wavefrontproxyclient) to send data to a Wavefront proxy
* Option 2: [Create a `WavefrontDirectIngestionClient`](#option-2-create-a-wavefrontdirectingestionclient) to send data directly to a Wavefront service

### Option 1: Create a WavefrontProxyClient
To create a `WavefrontProxyClient`, you initialize it with the proxy host and port information. You must also [set up a Wavefront proxy](#set-up-a-wavefront-proxy) on the specified proxy host. 

You can do these tasks in either order, as long as you specify the same proxy host and port information in both places.

#### Initialize the WavefrontProxyClient

You initialize a `WavefrontProxyClient` with the host that will run the proxy, one or more proxy ports to send data to, and optional settings for tuning communication with the proxy.

You must explicitly specify a port number for each kind of data (metrics, histograms, trace data) you plan to send. At least one port is required. When you [set up the Wavefront proxy](#set-up-a-wavefront-proxy), you will enable each port you specify here.


```java
// Create the builder with the proxy hostname or address
WavefrontProxyClient.Builder wfProxyClientBuilder = new WavefrontProxyClient.Builder(proxyHostName);

// Set the proxy port to send metrics to. Must match the proxy's pushListenerPort property. Default: 2878
wfProxyClientBuilder.metricsPort(2878);

// Set a proxy port to send histograms to. Must match the proxy's histogramDistListenerPort property. Recommended: 40000
wfProxyClientBuilder.distributionPort(40_000);

// Set a proxy port to send trace data to. Must match the proxy's traceListenerPort property. Recommended: 30000
wfProxyClientBuilder.tracingPort(30_000);

// Optional: Set a custom socketFactory to override the default SocketFactory
wfProxyClientBuilder.socketFactory(<SocketFactory>);

// Optional: Set a nondefault interval (in seconds) for flushing data from the sender to the proxy. Default: 5 seconds
wfProxyClientBuilder.flushIntervalSeconds(2);

// Create the WavefrontProxyClient
WavefrontSender wavefrontSender = wfProxyClientBuilder.build();
 ```

#### Set Up a Wavefront Proxy
Make sure that a Wavefront proxy is configured and running on the expected proxy host:
 * [Install](http://docs.wavefront.com/proxies_installing.html) a Wavefront proxy on the proxy host. You must use Version 4.32 or later.
 * On the proxy host, open the proxy configuration file `wavefront.conf` in the installed [file path](http://docs.wavefront.com/proxies_configuring.html#paths), for example, `/etc/wavefront/wavefront-proxy/wavefront.conf`.
 * In the `wavefront.conf` file, find and uncomment the properties for the ports you want to enable. Set each of these properties to the corresponding value you specified in the `WavefrontProxyClient`: 
   ```
   # Required for metric data. Set to the same value as metricsPort().
   pushListenerPort=2878
   ...
   # Required for histogram distributions.  Set to the same value as distributionPort().
   histogramDistListenerPort=40000
   ...
   # Required for trace data.  Set to the same value as tracingPort().
   traceListenerPort=30000
   ```
 * Save the `wavefront.conf` file.
 * Start (or restart) the proxy.


### Option 2: Create a WavefrontDirectIngestionClient
To create a `WavefrontDirectIngestionClient`, you initialize it with settings that enable (and optionally tune) direct ingestion of data into Wavefront.

#### Obtain Wavefront Access Information
Gather the following access information:

* Identify the URL of your Wavefront instance. This is the URL you connect to when you log in to Wavefront, typically something like `https://<domain>.wavefront.com`.
* In Wavefront, verify that you have Direct Data Ingestion permission, and [obtain an API token](http://docs.wavefront.com/wavefront_api.html#generating-an-api-token).

#### Initialize the WavefrontDirectIngestionClient
You initialize a `WavefrontDirectIngestionClient` by building it with the access information you obtained above.

You can call builder methods to optionally tune the following ingestion properties:

* Max queue size - Internal buffer capacity of the `WavefrontSender`. Any data in excess of this size is dropped.
* Batch size - Amount of data to send to Wavefront in each flush interval.
* Flush interval - Interval for flushing data from the `WavefrontSender` directly to Wavefront.

Together, the batch size and flush interval control the maximum theoretical throughput of the `WavefrontSender`.

**Note:** You should override the defaults _only_ to set higher values.


```java
// Create a builder with the Wavefront URL and a Wavefront API token
// that was created with direct ingestion permission
WavefrontDirectIngestionClient.Builder wfDirectIngestionClientBuilder =
  new WavefrontDirectIngestionClient.Builder(wavefrontURL, token);

// Optional: Override the max queue size (in data points). Default: 50,000
wfDirectIngestionClientBuilder.maxQueueSize(100_000);

// Optional: Override the batch size (in data points). Default: 10,000
wfDirectIngestionClientBuilder.batchSize(20_000);

// Optional: Override the flush interval (in seconds). Default: 1 second
wfDirectIngestionClientBuilder.flushIntervalSeconds(2);

// Create a WavefrontDirectIngestionClient
WavefrontSender wavefrontSender = wfDirectIngestionClientBuilder.build();
 ```
 
 
# Share a WavefrontSender

Various Wavefront SDKs for Java use this library and require a `WavefrontSender` instance.

If you are using multiple Wavefront Java SDKs within the same JVM process, you can instantiate the WavefrontSender just once and share it among the SDKs. 
 
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
