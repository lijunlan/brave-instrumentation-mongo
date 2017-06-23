# brave-instrumentation-mongo
This module contains a tracing listener for mongodb-driver

## Configuration

Tracing always needs a bean of type `Tracing` configured. Make sure
it is in place before proceeding.

Then, configure `MongoTracingListener` in Java.

```java
@Configuration
@Import(MongoTracingListener.class)
class MongoConfiguration {
  
  @Bean
    public MongoClient mongoClient() {
        List<ServerAddress> seeds = new ArrayList<>();
        // add some address..
        List<MongoCredential> credentialsList = new ArrayList<>();
        credentialsList.add(MongoCredential.createCredential(username, db, password.toCharArray()));
        // add listener
        MongoClientOptions mongoClientOptions = MongoClientOptions.builder().
                addCommandListener(mongoTracingListener).build();
        MongoClient mongoClient = new MongoClient(seeds, credentialsList, mongoClientOptions);
        return mongoClient;
    }

}
```
