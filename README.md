# surrogate-service
The POC for surrogate service backed by Reactive Kafka and Akka/Akka Stream

# Todo-list
* Create an AccountRouter for each partition
* Different streams are used to digest different event ID.
* Hibernate one account actor as long as no corresponding events for long time.
* Auto-generate account actors according to Node table during warm-up period.

