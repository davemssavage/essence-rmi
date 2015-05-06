Essence RMI is designed to be a simple low latency, high throughput messaging and RMI framework.

The framework supports messaging latency as low as 9-30 µs over network latency and synchronous RMI calls with a latency of 20-60 µs (over the ping time of the network), depending on the number and type of arguments. In tests, 98% of calls took less than 60 µs longer than the ping time. For example, on a network with a ping latency of 90 µs, the RMI latency was around 110-150 µs.

The framework supports asynchronous or non-blocking RMI calls for high throughput. A single client-server connection can sustain 100K calls/second. However even modest PCs can achieve a throughput of 50K calls/second or more. Essence RMI has an efficient custom POJO serialization which is faster than Java Serialization (by about 5x or more).
With batching, throughputs of 500K calls/second can be achieved over a network.