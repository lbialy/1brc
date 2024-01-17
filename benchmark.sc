//> using toolkit default
//> using scala 3.3.1
//> using jvm graalvm-java21:21.0.1

import java.time.Duration

enum Variant:
  case JVM(file: String)
  case Native(file: String)
  case BashScript(file: String)

  def command: Vector[String] =
    this match
      case JVM(file)        => Vector("java", "-jar", file)
      case Native(file)     => Vector(file)
      case BashScript(file) => Vector("bash", file)

def runBenchmark(variant: Variant, iterations: Int = 10): Unit =
  val sum = (1 to iterations)
    .map { idx =>
      println(s"Run $idx starting")
      val start  = System.nanoTime()
      val result = os.proc(variant.command).call()
      val end    = System.nanoTime()

      val correctResult = result.out.text() == correct
      println(s"Run $idx finished with ${if correctResult then "correct result" else "incorrect result"}")

      if !correctResult then
        val x = os.proc("diff", "-u", "-", "correct.txt").call(stdin = result.out.text())
        println(x.out.text())

      end - start
    }
    .sorted
    .drop(1)
    .dropRight(1)
    .sum

  println(s"${Duration.ofNanos(sum / 8).toMillis()} ms")

lazy val correct = os.read(os.pwd / "correct.txt")

args.toList match
  case command :: file :: Nil if command == "verify" && file.endsWith(".jar") => runBenchmark(Variant.JVM(file), 1)
  case command :: file :: Nil if command == "verify" && file.endsWith(".sh")  => runBenchmark(Variant.BashScript(file), 1)
  case command :: file :: Nil if command == "verify"                          => runBenchmark(Variant.Native(file), 1)
  case file :: Nil if file.endsWith(".jar")                                   => runBenchmark(Variant.JVM(file))
  case file :: Nil if file.endsWith(".sh")                                    => runBenchmark(Variant.BashScript(file))
  case file :: Nil                                                            => runBenchmark(Variant.Native(file))
  case _                                                                      => println("Usage: benchmark <file>")
