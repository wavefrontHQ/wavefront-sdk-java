# Application Tags

Many Wavefront SDKs require you to specify _application tags_ that describe the architecture of your application as it is deployed. These tags are associated with the metrics and trace data sent from the instrumented microservices in your application. You specify a separate set of application tags for each microservice you instrument. Wavefront uses these tags to aggregate and filter data at different levels of granularity.

**Required tags** enable you to drill down into the data for a particular service:
* `application` - Name that identifies your Java application, for example: `OrderingApp`. All microservices in the same application should share the same application name.
* `service` - Name that identifies the microservice within your application, for example: `inventory`. Each microservice should have its own service name.

**Optional tags** enable you to use the physical topology of your application to further filter your data:
* `cluster` - Name of a group of related hosts that serves as a cluster or region in which the application will run, for example: `us-west-2`. Defaults to value of "none".
* `shard` - Name of a subgroup of hosts within a cluster that serve as a partition, replica, shard, or mirror, for example: `secondary`. Defaults to value of "none".

You can also optionally add custom tags specific to your application in the form of a `Map` (see snippet below).

Application tags and their values are encapsulated in an `ApplicationTags` object in your microservice’s code. Because the tags describe the application’s architecture as it is deployed, your code typically obtains values for the tags from a YAML configuration file, either provided by the SDK or through a custom mechanism implemented by your application.

To create an `ApplicationTags` instance:
```java
String application = "OrderingApp";
String service = "inventory";
String cluster = "us-west-2";
String shard = "secondary";

Map<String, String> customTags = new HashMap<String, String>() {{
  put("location", "Oregon");
  put("env", "Staging");
}};

ApplicationTags applicationTags = new ApplicationTags.Builder(application, service).
    cluster(cluster).       // optional
    shard(shard).           // optional
    customTags(customTags). // optional
    build();
```
