//> using scala 3.3.1
//> using jvm graalvm-java21:21.0.1

import scala.util.Using
import java.util.TreeMap
import java.util.HashMap
import scala.io.Source
import java.nio.ByteBuffer
import java.io.RandomAccessFile
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import java.io.File
import scala.concurrent.*, duration.*
import java.nio.channels.FileChannel
import java.nio.file.Paths

val path = Paths.get("measurements.txt")

class Result(
  var min: Double = Double.MaxValue,
  var max: Double = Double.MinValue,
  var sum: Double = 0.0,
  var count: Long = 0L
):
  def merge(other: Result): Result =
    min = math.min(min, other.min)
    max = math.max(max, other.max)
    sum += other.sum
    count += other.count
    this

  def update(measurement: Double): Result =
    this.min = math.min(this.min, measurement)
    this.max = math.max(this.max, measurement)
    this.sum += measurement
    this.count += 1
    this

  override def toString(): String = s"${min}/${sum / count}/${max}"

object HashMapOps:
  def merge(map: HashMap[String, Result], other: HashMap[String, Result]): HashMap[String, Result] =
    other.forEach { (station, result) =>
      val existing = map.get(station)
      if existing == null then map.put(station, result)
      else existing.merge(result)
    }
    map

object Parsing:

  def parseWholeBuffer(bb: ByteBuffer): HashMap[String, Result] =
    val max = bb.capacity()
    val map = HashMap[String, Result]()
    var idx = 0
    while idx + 1 < max do
      val station = parseStation(bb, idx)
      idx = bb.position()

      val temp = parseTemperature(bb, idx) / 10.0
      idx = bb.position()

      idx += 1 // skip newline

      var result = map.get(station)
      if result == null then
        result = Result()
        map.put(station, result)
      result.update(temp)

    map

  def findSegments(raf: RandomAccessFile, fileSize: Long, segmentCount: Int): Array[Long] =
    val segmentSize = fileSize / segmentCount
    val segments    = Array.ofDim[Long](segmentCount + 1)
    var i           = 1
    segments(0) = 0
    while i < segmentCount + 1 do
      segments(i) = findNextNewLine(raf, i * segmentSize)
      i += 1
    segments

  inline def findNextNewLine(raf: RandomAccessFile, start: Long): Long =
    var i = start
    raf.seek(i)
    while i < raf.length() && raf.readByte() != '\n' do i += 1
    raf.getFilePointer()

  def parseStation(bb: ByteBuffer, start: Int): String =
    var idx = start
    while bb.get(idx) != ';' do idx += 1
    val arr = Array.ofDim[Byte](idx - start)
    bb.get(start, arr)
    val station = new String(arr)
    bb.position(idx + 1)
    station

  def parseTemperature(bb: ByteBuffer, start: Int): Int =
    var idx    = start
    val first4 = bb.getInt(idx)
    idx += 3

    val b1 = ((first4 >> 24) & 0xff).toByte
    val b2 = ((first4 >> 16) & 0xff).toByte
    val b3 = ((first4 >> 8) & 0xff).toByte

    var result = 0

    if b1 == '-' then
      if b3 == '.' then // handles: -1.0
        idx += 1
        result -= 10 * (b2 - '0') + first4.toByte - '0'
      else // handles: -10.1
        idx += 1
        result -= 100 * (b2 - '0') + 10 * (b3 - '0') + bb.get(idx) - '0'
        idx += 1
    else if b2 == '.' then result += 10 * (b1 - '0') + b3 - '0' // handles: 1.0
    else // handles: 10.1
      idx += 1
      result += 100 * (b1 - '0') + 10 * (b2 - '0') + first4.toByte - '0'

    bb.position(idx)

    result
  end parseTemperature

/** Changelog:
  * JVM: Oracle GraalVM 21.0.1
  * Machine: AMD Ryzen 7 2700X Eight-Core @ 16x 3.7GHz
  * 
  * Notes:
  * - inlining parseStation and parseTemperature does not improve performance, on the contrary,
  *   most java implementations manually inline these methods, maybe that's a mistake?  
  * 
  * baseline - Java: 194.686s 
  * baseline - Scala JVM: 198.580s
  * baseline - Scala Native: 342.085s (bug on Ryzen?, 197s on Apple M1 Pro)
  * parallelism and unrolled Double parsing - Scala JVM: 7.667s
  * faster Double parsing - Scala JVM: 7.410s
  * 
  */
@main def calculateAverage(): Unit =
  val segmentCount = Runtime.getRuntime().availableProcessors()

  Using.Manager { use =>
    val executor           = use(Executors.newFixedThreadPool(segmentCount))
    given ExecutionContext = ExecutionContext.fromExecutor(executor)
    val raf                = use(RandomAccessFile(path.toFile, "r"))
    val fileSize           = raf.length()
    val channel            = use(raf.getChannel())

    val segments = Parsing.findSegments(raf, fileSize, segmentCount)

    val maps = Future.sequence {
      segments
        .sliding(2)
        .map { case Array(start, end) =>
          Future {
            val bb = channel.map(FileChannel.MapMode.READ_ONLY, start, end - start)
            Parsing.parseWholeBuffer(bb)
          }
        }
        .toVector
    }

    val map = Await.result(maps, Duration.Inf).reduce(HashMapOps.merge(_, _))

    val finalMap = TreeMap[String, Result]()

    map.forEach { (station, result) =>
      finalMap.put(station, result)
    }

    println(finalMap)
  }
