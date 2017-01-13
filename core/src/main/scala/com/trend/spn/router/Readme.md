I would describe the routing mechanism here

Basically, I use the ConsistentHashingPool as routing pool so that we can send messages to the same actors. The brief as below:

## ConsistentHashingPool and ConsistentHashingGroup

The ConsistentHashingPool uses consistent hashing to select a routee based on the sent message. This article gives good insight into how consistent hashing is implemented.

There is 3 ways to define what data to use for the consistent hash key.

* You can define hashMapping of the router to map incoming messages to their consistent hash key. This makes the decision transparent for the sender.
* The messages may implement akka.routing.ConsistentHashingRouter.ConsistentHashable. The key is part of the message and it's convenient to define it together with the message definition.
* The messages can be wrapped in a akka.routing.ConsistentHashingRouter.ConsistentHashableEnvelope to define what data to use for the consistent hash key. The sender knows the key to use.

The second requirement is that the size of pool is dynamically resizeable.
There are two types of resizers: the **default Resizer** and the **OptimalSizeExploringResizer**.


