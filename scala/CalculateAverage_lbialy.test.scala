//> using scala 3.3.1
//> using jvm graalvm-java21:21.0.1
//> using toolkit default

import java.nio.*
import java.nio.file.*
import java.io.RandomAccessFile
import scala.util.Using
import java.util.HashMap
import scala.jdk.CollectionConverters.*
import scala.concurrent.*
import scala.concurrent.*, duration.Duration
import java.util.concurrent.Executors
import java.nio.channels.FileChannel
import scala.collection.mutable

class TestSuite extends munit.FunSuite:

  val smallSample = """Cracow;5.1
                      |Blantyre;37.9
                      |Tromsø;10.9
                      |""".stripMargin.getBytes()

  val sample = """Cracow;5.1
                 |Blantyre;37.9
                 |Tromsø;10.9
                 |Brisbane;20.0
                 |Jos;33.0
                 |Stockholm;16.7
                 |Hobart;11.3
                 |Villahermosa;40.7
                 |Tabora;29.8
                 |Niamey;21.0
                 |Bujumbura;36.9
                 |Muscat;40.7
                 |Malé;29.0
                 |Suva;19.3
                 |Dushanbe;17.8
                 |Abéché;42.1
                 |Kumasi;35.3
                 |Malé;39.7
                 |Warsaw;-0.9
                 |Anchorage;7.3
                 |Kankan;34.8
                 |Gaborone;11.2
                 |Miami;37.1
                 |Hanga Roa;29.2
                 |Harbin;9.5
                 |Mumbai;26.7
                 |Vancouver;15.9
                 |Barcelona;29.9
                 |Napoli;12.5
                 |Warsaw;14.2
                 |Seville;12.1
                 |Roseau;21.5
                 |San Diego;18.0
                 |Sapporo;15.4
                 |""".stripMargin.getBytes()

  def makeSmallSampleBB = ByteBuffer.wrap(smallSample)
  def makeSampleBB      = ByteBuffer.wrap(sample)

  def writeTempFile: Path = Files.write(Files.createTempFile("test", "txt"), sample)

  test("test doubles parsing") {
    val sample1 = ByteBuffer.wrap("1.0\n".getBytes())
    val sample2 = ByteBuffer.wrap("10.1\n".getBytes())
    val sample3 = ByteBuffer.wrap("99.9\n".getBytes())
    val sample4 = ByteBuffer.wrap("-1.0\n".getBytes())
    val sample5 = ByteBuffer.wrap("-10.1\n".getBytes())
    val sample6 = ByteBuffer.wrap("-99.9\n".getBytes())
    val sample7 = ByteBuffer.wrap("0.0\n".getBytes())
    val sample8 = ByteBuffer.wrap("-0.0\n".getBytes())

    assertEquals(Parsing.parseTemperature(sample1, 0) / 10.0, 1.0)
    assertEquals(Parsing.parseTemperature(sample2, 0) / 10.0, 10.1)
    assertEquals(Parsing.parseTemperature(sample3, 0) / 10.0, 99.9)
    assertEquals(Parsing.parseTemperature(sample4, 0) / 10.0, -1.0)
    assertEquals(Parsing.parseTemperature(sample5, 0) / 10.0, -10.1)
    assertEquals(Parsing.parseTemperature(sample6, 0) / 10.0, -99.9)
    assertEquals(Parsing.parseTemperature(sample7, 0) / 10.0, 0.0)
    assertEquals(Parsing.parseTemperature(sample8, 0) / 10.0, -0.0)

    assertEquals(sample1.get().toChar, '\n')
    assertEquals(sample2.get().toChar, '\n')
    assertEquals(sample3.get().toChar, '\n')
    assertEquals(sample4.get().toChar, '\n')
    assertEquals(sample5.get().toChar, '\n')
    assertEquals(sample6.get().toChar, '\n')
    assertEquals(sample7.get().toChar, '\n')
    assertEquals(sample8.get().toChar, '\n')
  }

  test("test faster doubles parsing") {
    val sample1 = ByteBuffer.wrap("1.0\n".getBytes())
    val sample2 = ByteBuffer.wrap("10.1\n".getBytes())
    val sample3 = ByteBuffer.wrap("99.9\n".getBytes())
    val sample4 = ByteBuffer.wrap("-1.0\n".getBytes())
    val sample5 = ByteBuffer.wrap("-10.1\n".getBytes())
    val sample6 = ByteBuffer.wrap("-99.9\n".getBytes())
    val sample7 = ByteBuffer.wrap("0.0\n".getBytes())
    val sample8 = ByteBuffer.wrap("-0.0\n".getBytes())

    assertEquals(Parsing.parseTemperature(sample1, 0) / 10.0, 1.0)
    assertEquals(Parsing.parseTemperature(sample2, 0) / 10.0, 10.1)
    assertEquals(Parsing.parseTemperature(sample3, 0) / 10.0, 99.9)
    assertEquals(Parsing.parseTemperature(sample4, 0) / 10.0, -1.0)
    assertEquals(Parsing.parseTemperature(sample5, 0) / 10.0, -10.1)
    assertEquals(Parsing.parseTemperature(sample6, 0) / 10.0, -99.9)
    assertEquals(Parsing.parseTemperature(sample7, 0) / 10.0, 0.0)
    assertEquals(Parsing.parseTemperature(sample8, 0) / 10.0, -0.0)

    assertEquals(sample1.get().toChar, '\n')
    assertEquals(sample2.get().toChar, '\n')
    assertEquals(sample3.get().toChar, '\n')
    assertEquals(sample4.get().toChar, '\n')
    assertEquals(sample5.get().toChar, '\n')
    assertEquals(sample6.get().toChar, '\n')
    assertEquals(sample7.get().toChar, '\n')
    assertEquals(sample8.get().toChar, '\n')
  }

  test("linear parsing") {
    val bb  = makeSmallSampleBB
    val map = Parsing.parseWholeBuffer(bb).toHashMap.asScala

    assert(map.contains("Cracow"))
    assert(map.contains("Blantyre"))
    assert(map.contains("Tromsø"))

    assertEquals(map("Cracow").sum, 5.1)
    assertEquals(map("Blantyre").sum, 37.9)
    assertEquals(map("Tromsø").sum, 10.9)
  }

  test("large linear parsing") {
    val bb  = makeSampleBB
    val map = Parsing.parseWholeBuffer(bb).toHashMap.asScala

    assertCorrectResult(map)
  }

  test("find segments") {
    val segmentCount = 8
    val path         = writeTempFile
    val raf          = RandomAccessFile(path.toFile, "r")
    val fileSize     = raf.length()

    val segments = Parsing.findSegments(raf, fileSize, 8)

    assertEquals(segments.length, segmentCount + 1)
    assertEquals(segments.toVector, Vector(0L, 61, 118, 168, 218, 281, 330, 384, 437))
  }

  test("parallel parsing from file") {
    val segmentCount = 6
    val path         = writeTempFile

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

      val map = Await.result(maps, Duration.Inf).map(_.toHashMap).reduce(HashMapOps.merge(_, _)).asScala

      assertCorrectResult(map)
    }
  }

  def assertCorrectResult(map: mutable.Map[String, Result]): Unit =
    assert(map.contains("Cracow"))
    assert(map.contains("Blantyre"))
    assert(map.contains("Tromsø"))
    assert(map.contains("Brisbane"))
    assert(map.contains("Jos"))
    assert(map.contains("Stockholm"))
    assert(map.contains("Hobart"))
    assert(map.contains("Villahermosa"))
    assert(map.contains("Tabora"))
    assert(map.contains("Niamey"))
    assert(map.contains("Bujumbura"))
    assert(map.contains("Muscat"))
    assert(map.contains("Malé"))
    assert(map.contains("Suva"))
    assert(map.contains("Dushanbe"))
    assert(map.contains("Abéché"))
    assert(map.contains("Kumasi"))
    assert(map.contains("Warsaw"))
    assert(map.contains("Anchorage"))
    assert(map.contains("Kankan"))
    assert(map.contains("Gaborone"))
    assert(map.contains("Miami"))
    assert(map.contains("Hanga Roa"))
    assert(map.contains("Harbin"))
    assert(map.contains("Mumbai"))
    assert(map.contains("Vancouver"))
    assert(map.contains("Barcelona"))
    assert(map.contains("Napoli"))
    assert(map.contains("Seville"))
    assert(map.contains("Roseau"))
    assert(map.contains("San Diego"))
    assert(map.contains("Sapporo"))

    assertEquals(map("Cracow").sum, 5.1)
    assertEquals(map("Blantyre").sum, 37.9)
    assertEquals(map("Tromsø").sum, 10.9)
    assertEquals(map("Brisbane").sum, 20.0)
    assertEquals(map("Jos").sum, 33.0)
    assertEquals(map("Stockholm").sum, 16.7)
    assertEquals(map("Hobart").sum, 11.3)
    assertEquals(map("Villahermosa").sum, 40.7)
    assertEquals(map("Tabora").sum, 29.8)
    assertEquals(map("Niamey").sum, 21.0)
    assertEquals(map("Bujumbura").sum, 36.9)
    assertEquals(map("Muscat").sum, 40.7)
    assertEquals(map("Malé").sum, 68.7)
    assertEquals(map("Suva").sum, 19.3)
    assertEquals(map("Dushanbe").sum, 17.8)
    assertEquals(map("Abéché").sum, 42.1)
    assertEquals(map("Kumasi").sum, 35.3)
    assertEquals(map("Warsaw").sum, 13.299999999999999)
    assertEquals(map("Anchorage").sum, 7.3)
    assertEquals(map("Kankan").sum, 34.8)
    assertEquals(map("Gaborone").sum, 11.2)
    assertEquals(map("Miami").sum, 37.1)
    assertEquals(map("Hanga Roa").sum, 29.2)
    assertEquals(map("Harbin").sum, 9.5)
    assertEquals(map("Mumbai").sum, 26.7)
    assertEquals(map("Vancouver").sum, 15.9)
    assertEquals(map("Barcelona").sum, 29.9)
    assertEquals(map("Napoli").sum, 12.5)
    assertEquals(map("Seville").sum, 12.1)
    assertEquals(map("Roseau").sum, 21.5)
    assertEquals(map("San Diego").sum, 18.0)
    assertEquals(map("Sapporo").sum, 15.4)
