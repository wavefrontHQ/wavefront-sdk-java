# Using Wavefront Sender in Wavefront SDKs

Several Wavefront Java SDKs such as [wavefront-dropwizard-metrics-sdk-java](https://github.com/wavefrontHQ/wavefront-dropwizard-metrics-sdk-java), [wavefront-opentracing-sdk-java](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java), [wavefront-jersey-sdk-java](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java) etc. use this library and require a `WavefrontSender` instance.

If you are using multiple Wavefront Java SDKs within the same JVM process, you can instantiate the WavefrontSender just once and share it amongst the SDKs. For example:

```java
// assuming you have a configuration file
WavefrontSender wavefrontSender = buildProxyOrDirectSender(config);

// Create a Wavefront open tracing reporter
Reporter spanReporter = new WavefrontSpanReporter.Builder().
  withSource("wavefront-tracing-example").
  build(wavefrontSender);

// Create a Wavefront dropwizard metrics reporter
MetricRegistry registry = new MetricRegistry();
DropwizardMetricsReporter.Builder builder =   
DropwizardMetricsReporter metricsReporter =
  DropwizardMetricsReporter.forRegistry(registry).
  build(wavefrontSender);
...
```

However, if you use the SDKs on different JVM processes, you would need to instantiate one WavefrontSender instance per JVM process.
