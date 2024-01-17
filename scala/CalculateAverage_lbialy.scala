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
import scala.util.boundary, boundary.break
import scala.util.Using.Releasable
import java.util.concurrent.ExecutorService

val path = Paths.get("measurements.txt")

class Result(
  private val bb: ByteBuffer,
  val offset: Int,
  val length: Int,
  val hash: Int,
  private var min: Double = Double.MaxValue,
  private var max: Double = Double.MinValue,
  private var _sum: Double = 0.0,
  private var count: Long = 0L
):

  def sum: Double = _sum

  def station: String =
    // var idx = offset
    // bb.position(idx)
    // val arr  = Array.ofDim[Byte](100) // max station name length
    // var char = bb.get() // at least one char name
    // while char != ';' do
    //   arr(idx - offset) = char
    //   idx += 1
    //   char = bb.get()

    // new String(arr, 0, idx - offset)

    // bulk method
    val arr2 = Array.ofDim[Byte](length)
    bb.get(offset, arr2)
    new String(arr2)

  def merge(other: Result): Unit =
    min = math.min(min, other.min)
    max = math.max(max, other.max)
    _sum += other._sum
    count += other.count

  def update(measurement: Double): Unit =
    this.min = math.min(this.min, measurement)
    this.max = math.max(this.max, measurement)
    this._sum += measurement
    this.count += 1

  def isEqualTo(thatHash: Int, thatBB: ByteBuffer, thatOffset: Int, thatLength: Int): Boolean =
    if hash != thatHash || length != thatLength then false
    else
      boundary:
        var i = 0
        while i < length do // todo could this use getLong/getInt/get combo?
          if bb.get(offset + i) != thatBB.get(thatOffset + i) then break(false)
          i += 1
        true

  override def equals(x: Any): Boolean = x match
    case that: Result => isEqualTo(that.hash, that.bb, that.offset, that.length)
    case _            => false

  private def round(value: Double): Double = math.round(value * 10.0) / 10.0

  override def hashCode(): Int    = hash
  override def toString(): String = s"${min}/${round(_sum / count)}/${max}"

object HashMapOps:
  def merge(map: HashMap[String, Result], other: HashMap[String, Result]): HashMap[String, Result] =
    other.forEach { (station, result) =>
      val existing = map.get(station)
      if existing == null then map.put(station, result)
      else existing.merge(result)
    }
    map

object Parsing:

  def parseWholeBuffer(bb: ByteBuffer): FastMap =
    val max = bb.capacity()
    val map = FastMap()
    var idx = 0
    while idx + 1 < max do
      val stationOffset = idx
      var stationIdx    = idx
      var hash          = 0
      bb.position(stationIdx)

      // this could be done using SWAR or at least unrolled with 64 bytes
      var char = bb.get() // at least one char name
      while char != ';' do
        stationIdx += 1
        hash = 31 * hash + char
        char = bb.get()

      val length = stationIdx - stationOffset

      bb.position(stationIdx + 1)
      idx = stationIdx + 1

      val temp = parseTemperature(bb, idx) / 10.0
      idx = bb.position()

      idx += 1 // skip newline

      map.updateOrRecord(hash, bb, stationOffset, length, temp)
    end while

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

class FastMap():
  private val keys = Array.ofDim[Result](FastMap.Size)

  def updateOrRecord(hash: Int, bb: ByteBuffer, offset: Int, length: Int, temp: Double): Unit =
    var slot     = FastMap.findSlot(hash)
    var existing = keys(slot)

    if existing == null then
      existing = Result(bb, offset, length, hash)
      existing.update(temp)
      keys(slot) = existing
    else if existing.isEqualTo(hash, bb, offset, length) then existing.update(temp)
    else // exists but is not equal, collision
      // linear search
      while {
        slot += 1
        existing = keys(slot & FastMap.Mask)
        existing != null && !existing.isEqualTo(hash, bb, offset, length)
      } do ()

      existing = Result(bb, offset, length, hash)
      keys(slot & FastMap.Mask) = existing

      existing.update(temp)

  def toHashMap: HashMap[String, Result] =
    val map = HashMap[String, Result]()
    keys.foreach { result =>
      if result != null then map.put(result.station, result)
    }
    map

object FastMap:
  val Size = 32768
  val Mask = Size - 1

  private val _collisions            = java.util.concurrent.atomic.AtomicInteger(0)
  inline def recordCollision(): Unit = _collisions.incrementAndGet()
  inline def collisions: Int         = _collisions.get()
  inline def resetCollisions(): Unit = _collisions.set(0)

  inline def findSlot(hash: Int): Int =
    val newHash = hash ^ (hash >>> 16)
    newHash & Mask

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
  * fix for SN MappedByteBuffer's missing apis - Scala JVM: 10.871s (using get(idx: Int) instead of get(idx: Int, arr: Array[Byte]) is painful)
  * fix for SN MappedByteBuffer's missing apis - Scala Native: crashes
  * fast hashmap implementation - Scala JVM: 3.617s
  * fast hashmap implementation - Scala Native: 307.060s (bug on Ryzen?, 38.76s on Apple M1 Pro)
  * bulk BB APIs restored - Scala JVM: 3.564s
  * bulk BB APIs restored - Scala Native: 242.387s (bug on Ryzen?, 38.76s on Apple M1 Pro)
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

    val map = Await.result(maps, Duration.Inf).map(_.toHashMap).reduce(HashMapOps.merge(_, _))

    val finalMap = TreeMap[String, Result](map)

    println(finalMap)
  }.get
