### #1BRC in Scala & Scala-Native
---

Hello, this fork of main #1BRC repo is a small playground that I've used to see how far Scala Native has gone. For the longest time the biggest complain about Scala Native was that it doesn't support multithreading and yeah, it's fair, you can't really do a lot of stuff without multithreading, especially in Scala, the language that historically excelled in safe, performant, multi-threaded applications.

But things are changing - Scala Native is nearing the 0.5.0 release that introduces multithreading model that respects Java Memory Model and makes it possible to use familiar abstractions like Future and ExecutionContext and compile your application to native binaries via LLVM.

So here's my entry to 1BRC. It's definitely not a top 10 tier in these last two weeks as I did not use Panama's MemorySegment and Unsafe, mostly because these APIs are not implemented by Scala Native. This solution is mostly inspired by awesome work done by Roy van Rijn, Sam Pullara and Yavuz Tas and is relatively simple while still going under 10s easily. I *could* go to bare knuckles level and just leverage the fact that Scala Native integrates with libc directly, grab mmap and instead of hacking via Unsafe just read the memory directly. There's possibly a lot more weird stuff that could be done using this capability! I didn't do that but hey - You could! But I didn't because this was not my point here. 

I was interested in how close would Scala Native 0.5.0-SNAPSHOT be to a miracle of engineering that GraalVM is. It turned out to be a bumpy road. My initial solution was done mid-january but 0.5.0-SNAPSHOT was not optimized at all at the time and it was easily 10x slower than a heavily optimized JVM (100x slower on linux/amd64 for some time for some reason). [Wojtek Mazur](https://twitter.com/WojciechM_dev) took on the challenge to bring Scala Native to the table and what a job he did in these two weeks! 

What this means is that Scala Native is now very near to being an actual business-ready solution that can yield fast apps that require much less memory than equivalent JVM deployment. The ecosystem of pure Scala libs that do not leverage any java libs underneath is growing steadily and these libs *do work with Scala Native*. This means we should soon be able to write once and deploy either to JVMs (still a massively useful platform!) or as native binaries.

Anyway, you're probably bored by all that yapping so here are the final scores:

#### Final results:
  - Scala JVM: 3.564s (16.118s on Apple M1 Pro)
  - Scala Native (immix): 4.494s (17.442s on Apple M1 Pro)
  - Scala Native (no gc): 4.058s (17.064s on Apple M1 Pro)

From 100x slower to 1.14x slower in two weeks. Bravo, [Wojtek](https://twitter.com/WojciechM_dev)! Check out the changelog in the [source](CalculateAverage_lbialy.scala).

#### You can run it too! 

In root folder of the repo there's `./prepare_lbialy.sh` file. It expects you to have [sdkman](https://sdkman.io/) as many other entries to #1BRC and [scala-cli](https://scala-cli.virtuslab.org/) too. Scala-cli is self-contained, it will download all the stuff necessary to compile things provided you're not on an antique linux box (or Windows, I did not check if it would work there but it possibly could work just fine in WSL2). 

There's a `benchmark.sc` Scala script in the root of the repo that you can use to run your artifacts. Syntax is:

`scala-cli run benchmark.sc -- ./scala/calculate_average_lbialy_native`

or

`scala-cli run benchmark.sc -- ./scala/calculate_average_lbialy.jar`

**It requires a `correct.txt` file present in the root of the repo that contains, duh, correct results for your version of the `measurements.txt` calculated using another correct solution. Roy von Rijn's is fine, he's topping the table most of the time.**

#### Changes in Scala Native that led to this result:

In order of introduction:
* https://github.com/scala-native/scala-native/pull/3679 Fixed bugs in implementation of Mapped Byte Buffers when reading in non-page aligned segments
* https://github.com/scala-native/scala-native/pull/3681 Added absolute get/put bulk operations and most of other methods introduced after JDK 8
* https://github.com/scala-native/scala-native/pull/3713 A group of multiple improvements with goal of making GC more stable and performant
* https://github.com/scala-native/scala-native/pull/3718 Improved performance of all NIO Buffers non-bulk operations by removing unnecessary indirection and GC overhead inherited from Scala.js implementation. Generalisation of non-bulk operation between all ByteBuffer implementations.
* https://github.com/scala-native/scala-native/pull/3719 Introduces relaxed memory model to Scala Native removing redundant Java Memory Model overhead for final fields
* https://github.com/scala-native/scala-native/pull/3722 Improved default tuning of optimiser and caching of state for inlining decisions

#### Fun tweaking stuff: 
  * commix is supposedly ~10% faster than immix but it crashes on linux for me, it works fine on MBP M1 Pro but is slower than no gc at all (the `--native-gc=none` #FAFO option).
  * I've seen the same native code hit 13s on MBP M1 Pro with `--native-mode=release-fast` once. No idea how that happened, maybe the stars were aligned or something.

