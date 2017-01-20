# surrogate-service
The POC for surrogate service backed by Reactive Kafka and Akka/Akka Stream

# Todo-list
* Create an Supervisor actor for partitioning, using the built-in ConsistenHashingRoutingLogic can fulfill this requirement. 
* Different streams are used to digest different event ID.
* Hibernate one account actor as long as no corresponding events for long time.
* Auto-generate account actors according to Node table during warm-up period.
* Create own RoutingLogic to make sure every account actor only handle its own events.

