# wavefront-sdk-java [![build status][ci-img]][ci] [![Released Version][maven-img]][maven]

Wavefront by VMware SDK for Java is the core library for sending metrics, histograms and trace data from your Java application to Wavefront using a `WavefrontSender` interface.

## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-sdk-java</artifactId>
    <version>$releaseVersion</version>
</dependency>
```
Replace `$releaseVersion` with the latest version available on [maven].

## Set Up a WavefrontSender
You can choose to send data to Wavefront using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

The `WavefrontSender` interface has two implementations. Instantiate the implementation that corresponds to your choice:
* [Create a `WavefrontProxyClient`](#create-a-wavefrontproxyclient) to send data to a Wavefront proxy
* [Create a `WavefrontDirectIngestionClient`](#create-a-wavefrontdirectingestionclient) to send data directly to a Wavefront service

### Create a WavefrontProxyClient
To create a WavefrontProxyClient, you specify the proxy host and one or more ports for the proxy to listen on.

Before data can be sent from your application, you must ensure the Wavefront proxy is configured and running:
* [Install](http://docs.wavefront.com/proxies_installing.html) a Wavefront proxy on the specified proxy host if necessary.
* [Configure](http://docs.wavefront.com/proxies_configuring.html) the proxy to listen on the specified port(s) by setting the corresponding properties: `pushListenerPort`, `histogramDistListenerPort`, `traceListenerPort`
* Start (or restart) the proxy.

```java
// Create the builder with the proxy hostname or address
WavefrontProxyClient.Builder builder = new WavefrontProxyClient.Builder(proxyHost);

// Note: At least one of metrics/histogram/tracing port is required.
// Only set a port if you wish to send that type of data to Wavefront and you
// have the port enabled on the proxy.

// Set the proxy metrics port (example: 2878) to send metrics to Wavefront
builder.metricsPort(2878);

// Set the proxy distribution port (example: 40,000) to send histograms to Wavefront
builder.distributionPort(40_000);

// Set the trace port (example: 30,000) to send opentracing spans to Wavefront
builder.tracingPort(30_000);

// Optional: Set a custom socketFactory to override the default SocketFactory
builder.socketFactory(<SocketFactory>);

// Optional: Set this to override the default flush interval of 5 seconds
builder.flushIntervalSeconds(2);

// Finally create a WavefrontProxyClient
WavefrontSender wavefrontSender = builder.build();
 ```

### Create a WavefrontDirectIngestionClient
To create a `WavefrontDirectIngestionClient`, you must have access to a Wavefront instance with direct data ingestion permission:
```java
// Create a builder with the URL of the form "https://DOMAIN.wavefront.com"
// and a Wavefront API token with direct ingestion permission
WavefrontDirectIngestionClient.Builder builder =
  new WavefrontDirectIngestionClient.Builder(wavefrontURL, token);

// Optional configuration properties.
// Only override the defaults to set higher values.

// This is the size of internal buffer beyond which data is dropped
// Optional: Set this to override the default max queue size of 50,000
builder.maxQueueSize(100_000);

// This is the max batch of data sent per flush interval
// Optional: Set this to override the default batch size of 10,000
builder.batchSize(20_000);

// Together with batch size controls the max theoretical throughput of the sender
// Optional: Set this to override the default flush interval value of 1 second
builder.flushIntervalSeconds(2);

// Finally create a WavefrontDirectIngestionClient
WavefrontSender wavefrontSender = builder.build();
 ```

## Send Data to Wavefront

 To send data to Wavefront using the `WavefrontSender` you instantiated:

### Metrics and Delta Counters

 ```java
// Wavefront Metrics Data format
// <metricName> <metricValue> [<timestamp>] source=<source> [pointTags]
// Example: "new-york.power.usage 42422 1533529977 source=localhost datacenter=dc1"
wavefrontSender.sendMetric("new-york.power.usage", 42422.0, 1533529977L,
    "localhost", ImmutableMap.<String, String>builder().put("datacenter", "dc1").build());

// Wavefront Delta Counter format
// <metricName> <metricValue> source=<source> [pointTags]
// Example: "lambda.thumbnail.generate 10 source=lambda_thumbnail_service image-format=jpeg"
wavefrontSender.sendDeltaCounter("lambda.thumbnail.generate", 10,
    "lambda_thumbnail_service",
    ImmutableMap.<String, String>builder().put("image-format", "jpeg").build());
```

### Distributions (Histograms)

```java
// Wavefront Histogram Data format
// {!M | !H | !D} [<timestamp>] #<count> <mean> [centroids] <histogramName> source=<source>
// [pointTags]
// Example: You can choose to send to at most 3 bins: Minute, Hour, Day
// "!M 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
// "!H 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
// "!D 1533529977 #20 30.0 #10 5.1 request.latency source=appServer1 region=us-west"
wavefrontSender.sendDistribution("request.latency",
    ImmutableList.<Pair<Double, Integer>>builder().add(new Pair<>(30.0, 20)).
      add(new Pair<>(5.1, 10)).build(),
    ImmutableSet.<HistogramGranularity>builder().add(HistogramGranularity.MINUTE).
      add(HistogramGranularity.HOUR).
      add(HistogramGranularity.DAY).build(),
    1533529977L, "appServer1",
    ImmutableMap.<String, String>builder().put("region", "us-west").build());
```

### Tracing Spans

```java
 // Wavefront Tracing Span Data format
 // <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_milliseconds>
 // Example: "getAllUsers source=localhost
 //           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
 //           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
 //           parent=2f64e538-9457-11e8-9eb6-529269fb1459
 //           application=Wavefront http.method=GET
 //           1533529977 343500"
wavefrontSender.sendSpan("getAllUsers",1533529977L, 343500L, "localhost",
      UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"),
      UUID.fromString("0313bafe-9457-11e8-9eb6-529269fb1459"),
      ImmutableList.<UUID>builder().add(UUID.fromString(
        "2f64e538-9457-11e8-9eb6-529269fb1459")).build(), null,
      ImmutableList.<Pair<String, String>>builder().
        add(new Pair<>("application", "Wavefront")).
        add(new Pair<>("http.method", "GET")).build(), null);
```

## Close the WavefrontSender
Remember to flush the buffer and close the sender before shutting down your application.
```java
// If there are any failures observed while sending metrics/histograms/tracing-spans above,
// you get the total failure count using the below API
int totalFailures = wavefrontSender.getFailureCount();

// on-demand buffer flush (may want to do this if you are shutting down your application)
wavefrontSender.flush();

// close the sender connection before shutting down application
// this will flush in-flight buffer and close connection
wavefrontSender.close();
```

[ci-img]: https://travis-ci.com/wavefrontHQ/wavefront-sdk-java.svg?branch=master
[ci]: https://travis-ci.com/wavefrontHQ/wavefront-sdk-java
[maven-img]: https://img.shields.io/maven-central/v/com.wavefront/wavefront-sdk-java.svg?maxAge=2592000
[maven]: http://search.maven.org/#search%7Cga%7C1%7Cwavefront-sdk-java
