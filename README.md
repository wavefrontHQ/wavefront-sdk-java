# wavefront-sdk-java [![build status][ci-img]][ci] [![Released Version][maven-img]][maven]

## Table of Content
* [Prerequisites](#Prerequisites)
* [Set Up a WavefrontSender](#set-up-a-wavefrontsender)
* [Send Data to Wavefront](#send-data-to-wavefront)
* [Close the WavefrontSender](#close-the-wavefrontsender)
* [Monitor the SDK](#monitor-the-sdk)
* [License](#License)
* [How to Contribute](#How-to-Contribute)

# Welcome to Wavefront's Java SDK

Wavefront by VMware Java SDK lets you send raw data from your Java application to Wavefront using a `WavefrontSender` interface. The data is then stored as metrics, histograms, and trace data. This SDK is also referred to as the Wavefront Sender SDK for Java. 

Although this library is mostly used by the other Wavefront Java SDKs to send data to Wavefront, you can also use this SDK directly. For example, you can send data directly from a data store or CSV file to Wavefront.

## Prerequisites

* Java 8 or above.
* Add dependencies:
  * **Maven** <br/>
    If you are using Maven, add the following maven dependency to your pom.xml:
    ```
    <dependency>
        <groupId>com.wavefront</groupId>
        <artifactId>wavefront-sdk-java</artifactId>
        <version>$releaseVersion</version>
    </dependency>
    ```
    Replace `$releaseVersion` with the latest version available on [maven].

  * **Gradle** <br/>
    If you are using Gradle, add the following dependency:
    ```
    compile group: 'com.wavefront', name: 'wavefront-sdk-java', version: '$releaseVersion'
    ```
    Replace `$releaseVersion` with the latest version available on [maven].

## Set Up a WavefrontSender

You can send metrics, histograms, or trace data from your application to the Wavefront service using a Wavefront proxy or direct ingestions.

* Use a [**Wavefront proxy**](https://docs.wavefront.com/proxies.html), which then forwards the data to the Wavefront service. This is the recommended choice for a large-scale deployment that needs resilience to internet outages, control over data queuing and filtering, and more.
* Use [**direct ingestion**](https://docs.wavefront.com/direct_ingestion.html) to send the data directly to the Wavefront service. This is the simplest way to get up and running quickly.

Let's [create a `WavefrontClient`](#Sending-Data-via-the-WavefrontClient) to send data to Wavefront either via Wavefront proxy or directly over HTTP.

> **Deprecated implementations**: *`WavefrontDirectIngestionClient` and `WavefrontProxyClient` are deprecated from proxy version 7.0 onwards. We recommend all new applications to use the `WavefrontClient`.*

### Sending Data via the WavefrontClient

Use `WavefrontClientFactory` to create a `WavefrontClient` instance, which can send data directly to a Wavefront service or send data using a Wavefront Proxy.

The `WavefrontClientFactory` supports multiple client bindings. If more than one client configuration is specified, you can create a `WavefrontMultiClient` to send multiple Wavefront services.

### Prerequisites  
* Sending data via Wavefront proxy? 
  <br/>Before your application can use a `WavefrontClient` you must [set up and start a Wavefront proxy](https://docs.wavefront.com/proxies_installing.html).  
* Sending data via direct ingestion? 
  * Verify that you have the Direct Data Ingestion permission. For details, see [Examine Groups, Roles, and Permissions](https://docs.wavefront.com/users_account_managing.html#examine-groups-roles-and-permissions).
  * The HTTP URL of your Wavefront instance. This is the URL you connect to when you log in to Wavefront, typically something like `http://<domain>.wavefront.com`.<br/> You can also use HTTP client with Wavefront Proxy version 7.0 or newer. Example: `http://proxy.acme.corp:2878`.
  * [Obtain the API token](http://docs.wavefront.com/wavefront_api.html#generating-an-api-token).

### Initialize the WavefrontClient

You initialize a `WavefrontClient` by building it with the information you obtained in the Prerequisites section.

Optionally, you can call factory methods to tune the following ingestion properties:

* Max queue size - Internal buffer capacity of the `WavefrontSender`. Data that exceeds this size is dropped.
* Flush interval - Interval for flushing data from the `WavefrontSender` directly to Wavefront.
* Batch size - Amount of data to send to Wavefront in each flush interval.

Together, the batch size and flush interval control the maximum theoretical throughput of the `WavefrontSender`. Override the defaults _only_ to set higher values.

**Example**: Use a factory class to create a WavefrontClient and send data to Wavefront via Wavefront Proxy. 

```java
// Add a client with the following URL format: "proxy://<your.proxy.load.balancer.com>:<somePort>"
// to send data to proxies
WavefrontClientFactory wavefrontClientFactory = new WavefrontClientFactory();
wavefrontClientFactory.addClient(wavefrontURL);

WavefrontSender wavefrontSender = wavefrontClientFactory.getClient();
```
**Example**: Use a builder to create a WavefrontClient and send data to Wavefront via Wavefront Proxy. 

```java
// Using the WavefrontClient.Builder directly with a url in the form of "http://your.proxy.load.blanacer:port"
// to send data to proxies.
WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(proxyURL);

//The maximum message size in bytes that is pushed with on each flush interval 
wfClientBuilder.messageSizeBytes(120);

// This is the size of internal buffer beyond which data is dropped
// Optional: Set this to override the default max queue size of 500,000
wfClientBuilder.maxQueueSize(100_000);

// This is the max batch of data sent per flush interval
// Optional: Set this to override the default batch size of 10,000
wfClientBuilder.batchSize(20_000);

// Together with batch size controls the max theoretical throughput of the sender
// Optional: Set this to override the default flush interval value of 1 second
wfClientBuilder.flushIntervalSeconds(2);

WavefrontSender wavefrontSender = wfClientBuilder.build();
``` 

**Example**: Use a factory class to create a WavefrontClient and send  data to Wavefront via direct ingestion.

```java
// Create a factory and add a client with the following URL format: "http://TOKEN@DOMAIN.wavefront.com"
// and a Wavefront API token with direct ingestion permission
WavefrontClientFactory wavefrontClientFactory = new WavefrontClientFactory();

// Add a new client that sends data directly to Wavefront services 
wavefrontClientFactory.addClient(wavefrontURL,
  20_000,           // This is the max batch of data sent per flush interval
  100_000,          // This is the size of internal buffer beyond which data is dropped
  2,                // Together with the batch size controls the max theoretical throughput of the sender
  Integer.MAX_VALUE // The maximum message size in bytes we will push with on each flush interval 
);

WavefrontSender wavefrontSender = wavefrontClientFactory.getClient();
```

**Example**: Use a builder to create a WavefrontClient and send  data to Wavefront via direct ingestion.

```java
// Using the WavefrontClient.Builder directly with a url in the form of "https://DOMAIN.wavefront.com"
// and a Wavefront API token with direct ingestion permission
WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(wavefrontURL, token);

//The maximum message size in bytes that is pushed with on each flush interval 
wfClientBuilder.messageSizeBytes(120);

// This is the size of internal buffer beyond which data is dropped
// Optional: Set this to override the default max queue size of 50,000
wfClientBuilder.maxQueueSize(100_000);

// This is the max batch of data sent per flush interval
// Optional: Set this to override the default batch size of 10,000
wfClientBuilder.batchSize(20_000);

// Together with batch size controls the max theoretical throughput of the sender
// Optional: Set this to override the default flush interval value of 1 second
wfClientBuilder.flushIntervalSeconds(2);

WavefrontSender wavefrontSender = wfClientBuilder.build();
``` 

### Sending data to multiple Wavefront services

Use `WavefrontMultiClient` to send data to multiple Wavefront services so you handle the data traffic.
The `addClient()` supports null for batch size, queue size, and push interval. The defaults values are used if nothing is specified.

**Example**: Creating a `WavefrontMultiClient` to send data to multiple Wavefront services.
```java
// Add multiple URLs to the Factory to obtain a multi-sender
WavefrontClientFactory wavefrontClientFactory = new WavefrontClientFactory();
wavefrontClientFactory.addClient("https://someToken@DOMAIN.wavefront.com");
wavefrontClientFactory.addClient("proxy://our.proxy.lb.com:2878");
// Send traces and spans to the tracing port. If you are directly using the sender SDK to send spans without using any other SDK, use the same port as the customTracingListenerPorts configured in the wavefront proxy. Assume you have installed and started the proxy on <proxyHostname>.
wavefrontClientFactory.addClient("http://<proxy_hostname>:30000/");

WavefrontSender wavefrontSender = wavefrontClientFactory.getClient();
```

## Send Data to Wavefront

 Wavefront supports different metric types, such as gauges, counters, delta counters, histograms, traces, and spans. See [Metrics](https://docs.wavefront.com/metric_types.html) for details. To send data to Wavefront using the `WavefrontSender` you need to instantiate the following:
 * [Metrics and Delta Counters](#Metrics-and-Delta-Counters)
 * [Distributions (Histograms)](#distributions-histograms)
 * [Tracing Spans](#Tracing-Spans)

#### Metrics and Delta Counters

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

***Note***: If your `metricName` has a bad character, that character is replaced with a `-`.

#### Distributions (Histograms)

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

#### Tracing Spans

<!-- Commenting this section out for now. We need to uncomment this section after https://github.com/wavefrontHQ/wavefront-sdk-java/pull/201 is published.
If you are directly using the Sender SDK to send data to Wavefront, you won’t see span-level RED metrics by default unless you use the Wavefront proxy and define a custom tracing port (`tracingPort`). See [Instrument Your Application with Wavefront Sender SDKs](https://docs.wavefront.com/tracing_instrumenting_frameworks.html#instrument-your-application-with-wavefront-sender-sdks) for details.

-->


```java
// Using the WavefrontClient.Builder directly with a url in the form of "http://your.proxy.load.blanacer:port"
// to send data to proxies.
WavefrontClient.Builder wfClientBuilder = new WavefrontClient.Builder(proxyURL);

// Now send distributed tracing spans as below
 // Wavefront tracing span data format
 // <tracingSpanName> source=<source> [pointTags] <start_millis> <duration_milliseconds>
 // Example: "getAllUsers source=localhost
 //           traceId=7b3bf470-9456-11e8-9eb6-529269fb1459
 //           spanId=0313bafe-9457-11e8-9eb6-529269fb1459
 //           parent=2f64e538-9457-11e8-9eb6-529269fb1459
 //           application=Wavefront http.method=GET
 //           1552949776000 343"
wavefrontSender.sendSpan("getAllUsers", 1552949776000L, 343, "localhost",
      UUID.fromString("7b3bf470-9456-11e8-9eb6-529269fb1459"),
      UUID.fromString("0313bafe-9457-11e8-9eb6-529269fb1459"),
      ImmutableList.<UUID>builder().add(UUID.fromString(
        "2f64e538-9457-11e8-9eb6-529269fb1459")).build(), null,
      ImmutableList.<Pair<String, String>>builder().
        add(new Pair<>("application", "Wavefront")).
        add(new Pair<>("service", "istio")).
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

## Monitor the SDK
See the [diagnostic metrics documentation](https://github.com/wavefrontHQ/wavefront-sdk-doc-sources/blob/master/java/internalmetrics.md#internal-diagnostic-metrics) for details on the internal metrics that the SDK collects and reports to Wavefront.

## License
[Apache 2.0 License](LICENSE).

## How to Contribute

* Reach out to us on our public [Slack channel](https://www.wavefront.com/join-public-slack).
* If you run into any issues, let us know by creating a GitHub issue.

[ci-img]: https://travis-ci.com/wavefrontHQ/wavefront-sdk-java.svg?branch=master
[ci]: https://travis-ci.com/wavefrontHQ/wavefront-sdk-java
[maven-img]: https://img.shields.io/maven-central/v/com.wavefront/wavefront-sdk-java.svg?maxAge=604800
[maven]: http://search.maven.org/#search%7Cga%7C1%7Cwavefront-sdk-java
