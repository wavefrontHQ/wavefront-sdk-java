# Internal Diagnostic Metrics

This SDK automatically collects a suite of diagnostic metrics that allow you to monitor the performance of your `WavefrontSender` instance. These metrics are collected once per minute and are reported to Wavefront using your `WavefrontSender` instance. The type of instance determines what metrics are collected.

## WavefrontDirectIngestionClient
If you are using a `WavefrontDirectIngestionClient` instance, the following diagnostic metrics are reported:

### Metric Points Ingestion
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.direct.points.queue.size                 |Gauge      |Points in ingestion buffer|
|~sdk.java.core.sender.direct.points.queue.remaining_capacity   |Gauge      |Remaining capacity of ingestion buffer|
|~sdk.java.core.sender.direct.points.valid.count                |Counter    |Valid points received|
|~sdk.java.core.sender.direct.points.invalid.count              |Counter    |Invalid points received|
|~sdk.java.core.sender.direct.points.dropped.count              |Counter    |Valid points that are dropped during ingestion|
|~sdk.java.core.sender.direct.points.report.errors.count        |Counter    |Exceptions encountered while reporting points|
|~sdk.java.core.sender.direct.points.report.202.count           |Counter    |Report requests by response status code|

### Histogram Distributions Ingestion
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.direct.histograms.queue.size                 |Gauge      |Distributions in ingestion buffer|
|~sdk.java.core.sender.direct.histograms.queue.remaining_capacity   |Gauge      |Remaining capacity of ingestion buffer|
|~sdk.java.core.sender.direct.histograms.valid.count                |Counter    |Valid distributions received|
|~sdk.java.core.sender.direct.histograms.invalid.count              |Counter    |Invalid distributions received|
|~sdk.java.core.sender.direct.histograms.dropped.count              |Counter    |Valid distributions that are dropped during ingestion|
|~sdk.java.core.sender.direct.histograms.report.errors.count        |Counter    |Exceptions encountered while reporting distributions|
|~sdk.java.core.sender.direct.histograms.report.202.count           |Counter    |Report requests by response status code|

### Tracing Spans Ingestion
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.direct.spans.queue.size                  |Gauge      |Spans in ingestion buffer|
|~sdk.java.core.sender.direct.spans.queue.remaining_capacity    |Gauge      |Remaining capacity of ingestion buffer|
|~sdk.java.core.sender.direct.spans.valid.count                 |Counter    |Valid spans received|
|~sdk.java.core.sender.direct.spans.invalid.count               |Counter    |Invalid spans received|
|~sdk.java.core.sender.direct.spans.dropped.count               |Counter    |Valid spans that are dropped during ingestion|
|~sdk.java.core.sender.direct.spans.report.errors.count         |Counter    |Exceptions encountered while reporting spans|
|~sdk.java.core.sender.direct.spans.report.202.count            |Counter    |Report requests by response status code|


## WavefrontProxyClient
If you are using a `WavefrontProxyClient` instance, the following diagnostic metrics are reported:

### Metric Points Handler
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.proxy.points.discarded.count                     |Counter    |Points discarded due to unconfigured port|
|~sdk.java.core.sender.proxy.points.valid.count                         |Counter    |Valid points received|
|~sdk.java.core.sender.proxy.points.invalid.count                       |Counter    |Invalid points received|
|~sdk.java.core.sender.proxy.points.dropped.count                       |Counter    |Points dropped due to failure to write to socket|
|~sdk.java.core.sender.proxy.metricHandler.errors.count                 |Counter    |Errors encountered, excluding connection errors |
|~sdk.java.core.sender.proxy.metricHandler.connect.errors.count         |Counter    |Errors encountered connecting to remote socket|
|~sdk.java.core.sender.proxy.metricHandler.socket.write.success.count   |Counter    |Successful writes to socket|
|~sdk.java.core.sender.proxy.metricHandler.socket.write.errors.count    |Counter    |Unsuccessful writes to socket|
|~sdk.java.core.sender.proxy.metricHandler.socket.flush.success.count   |Counter    |Successful socket flushes|
|~sdk.java.core.sender.proxy.metricHandler.socket.flush.errors.count    |Counter    |Unsuccessful socket flushes|
|~sdk.java.core.sender.proxy.metricHandler.socket.reset.success.count   |Counter    |Successful connection resets|
|~sdk.java.core.sender.proxy.metricHandler.socket.reset.errors.count    |Counter    |Unsuccessful connection resets|

### Histogram Distributions Handler
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.proxy.histograms.discarded.count                     |Counter    |Distributions discarded due to unconfigured port|
|~sdk.java.core.sender.proxy.histograms.valid.count                         |Counter    |Valid distributions received|
|~sdk.java.core.sender.proxy.histograms.invalid.count                       |Counter    |Invalid distributions received|
|~sdk.java.core.sender.proxy.histograms.dropped.count                       |Counter    |Distributions dropped due to failure to write to socket|
|~sdk.java.core.sender.proxy.histogramHandler.errors.count                  |Counter    |Errors encountered, excluding connection errors |
|~sdk.java.core.sender.proxy.histogramHandler.connect.errors.count          |Counter    |Errors encountered connecting to remote socket|
|~sdk.java.core.sender.proxy.histogramHandler.socket.write.success.count    |Counter    |Successful writes to socket|
|~sdk.java.core.sender.proxy.histogramHandler.socket.write.errors.count     |Counter    |Unsuccessful writes to socket|
|~sdk.java.core.sender.proxy.histogramHandler.socket.flush.success.count    |Counter    |Successful socket flushes|
|~sdk.java.core.sender.proxy.histogramHandler.socket.flush.errors.count     |Counter    |Unsuccessful socket flushes|
|~sdk.java.core.sender.proxy.histogramHandler.socket.reset.success.count    |Counter    |Successful connection resets|
|~sdk.java.core.sender.proxy.histogramHandler.socket.reset.errors.count     |Counter    |Unsuccessful connection resets|

### Tracing Handler
|Metric Name|Metric Type|Description|
|:---|:---:|:---|
|~sdk.java.core.sender.proxy.spans.discarded.count                      |Counter    |Spans discarded due to unconfigured port|
|~sdk.java.core.sender.proxy.spans.valid.count                          |Counter    |Valid spans received|
|~sdk.java.core.sender.proxy.spans.invalid.count                        |Counter    |Invalid spans received|
|~sdk.java.core.sender.proxy.spans.dropped.count                        |Counter    |Spans dropped due to failure to write to socket|
|~sdk.java.core.sender.proxy.tracingHandler.errors.count                |Counter    |Errors encountered, excluding connection errors |
|~sdk.java.core.sender.proxy.tracingHandler.connect.errors.count        |Counter    |Errors encountered connecting to remote socket|
|~sdk.java.core.sender.proxy.tracingHandler.socket.write.success.count  |Counter    |Successful writes to socket|
|~sdk.java.core.sender.proxy.tracingHandler.socket.write.errors.count   |Counter    |Unsuccessful writes to socket|
|~sdk.java.core.sender.proxy.tracingHandler.socket.flush.success.count  |Counter    |Successful socket flushes|
|~sdk.java.core.sender.proxy.tracingHandler.socket.flush.errors.count   |Counter    |Unsuccessful socket flushes|
|~sdk.java.core.sender.proxy.tracingHandler.socket.reset.success.count  |Counter    |Successful connection resets|
|~sdk.java.core.sender.proxy.tracingHandler.socket.reset.errors.count   |Counter    |Unsuccessful connection resets|