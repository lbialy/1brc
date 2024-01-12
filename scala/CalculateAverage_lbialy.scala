//> using scala 3.3.1
//> using jvm graalvm-java21:21.0.1

import scala.util.Using
import java.util.TreeMap
import java.util.HashMap
import scala.io.Source

val file = "measurements.txt"

case class Measurement(station: String, value: Double)
object Measurement:
  def apply(parts: Array[String]) = new Measurement(parts(0), parts(1).toDouble)

case class ResultRow(min: Double, mean: Double, max: Double):
  private def round(value: Double): Double = math.round(value * 10.0) / 10.0
  override def toString(): String          = s"${round(min)}/${round(mean)}/${round(max)}"

class MeasurementAggregator(
  var min: Double = Double.MaxValue,
  var max: Double = Double.MinValue,
  var sum: Double = 0.0,
  var count: Long = 0L
):
  def toResult: ResultRow = ResultRow(min, sum / count, max)

/** Changelog:
  *
  * Baseline - Java: 211200ms (Oracle GraalVM 21.0.1)
  * Baseline - Scala JVM: 209240ms (Oracle GraalVM 21.0.1)
  * Baseline - Scala Native: 194560ms 
  */
@main def calculateAverage(): Unit = Using(Source.fromFile(file)) { source =>
  val measurements = source.getLines().map(line => Measurement(line.split(";")))

  val toAggregate = measurements.foldLeft(HashMap[String, MeasurementAggregator]()) { (map, measurement) =>
    val aggregator = map.computeIfAbsent(measurement.station, _ => MeasurementAggregator())
    aggregator.min = math.min(aggregator.min, measurement.value)
    aggregator.max = math.max(aggregator.max, measurement.value)
    aggregator.sum += measurement.value
    aggregator.count += 1
    map
  }

  val finalMap = TreeMap[String, ResultRow]()

  toAggregate.forEach { (station, aggregator) =>
    finalMap.put(station, aggregator.toResult)
  }

  println(finalMap)

}.get
