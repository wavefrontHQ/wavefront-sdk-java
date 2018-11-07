# Application Tags

Several Wavefront Java SDKs such as `wavefront-jersey-sdk-java`, `wavefront-opentracing-sdk-java` etc. require `ApplicationTags`.

Application tags determine the metadata (aka point/span tags) that are included with any metrics/histograms/spans reported to Wavefront.

The following tags are mandatory:
* `application`: The name of your Java application, for example: `OrderingApp`.
* `service`: The name of the microservice within your application, for example: `inventory`.

The following tags are optional:
* `cluster`: For example: `us-west-2`.
* `shard`: The shard (aka mirror), for example: `secondary`.

You can also optionally add custom tags specific to your application in the form of a `Map` (see snippet below).

To create the application tags:
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

You would typically define the above metadata in your application's YAML config file and create the `ApplicationTags`.
