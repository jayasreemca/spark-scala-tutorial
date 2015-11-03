// Adapted from SparkSQL9, but written as a script for easy use with the
// spark-shell command.
import com.typesafe.sparkworkshop.util.Verse
import org.apache.spark.sql.DataFrame

// For HDFS:
// val inputRoot = "hdfs://my_name_node_server:8020"
val inputRoot = "."
val inputPath = s"$inputRoot/data/kjvdat.txt"

// The following are already invoked when we start sbt `console` or `spark-shell`
// in the Spark distribution:
// val sqlContext = new SQLContext(sc)
// import sqlContext.implicits._

// Regex to match the fields separated by "|".
// Also strips the trailing "~" in the KJV file.
val lineRE = """^\s*([^|]+)\s*\|\s*([\d]+)\s*\|\s*([\d]+)\s*\|\s*(.*)~?\s*$""".r
// Use flatMap to effectively remove bad lines.
val versesRDD = sc.textFile(inputPath).flatMap {
  case lineRE(book, chapter, verse, text) =>
    Seq(Verse(book, chapter.toInt, verse.toInt, text))
  case line =>
    Console.err.println(s"Unexpected line: $line")
    Seq.empty[Verse]  // Will be eliminated by flattening.
}

// Create a DataFrame and register as a temporary "table".
val verses = sqlContext.createDataFrame(versesRDD)
verses.registerTempTable("kjv_bible")
verses.cache()
// print the 1st 20 lines. Pass an integer argument to show a different number
// of lines:
verses.show()
verses.show(100)

import sqlContext.sql  // for convenience

val godVerses = sql("SELECT * FROM kjv_bible WHERE text LIKE '%God%'")
println("The query plan:")
godVerses.queryExecution   // Compare with godVerses.explain(true)
println("Number of verses that mention God: "+godVerses.count())
godVerses.show()

// Use the DataFrame API:
val godVersesDF = verses.filter(verses("text").contains("God"))
println("The query plan:")
godVersesDF.queryExecution
println("Number of verses that mention God: "+godVersesDF.count())
godVersesDF.show()

// Use GroupBy and column aliasing.
val counts = sql("SELECT book, COUNT(*) as count FROM kjv_bible GROUP BY book")
counts.show(100)  // print the 1st 100 lines, but there are only 66 books/records...

// Exercise: Sort the output by the book names. Sort by the counts.

// Use "coalesce" when you have too many small partitions. The integer
// passed to "coalesce" is the number of output partitions (1 in this case).
val counts1 = counts.coalesce(1)
val nPartitions  = counts.rdd.partitions.size
val nPartitions1 = counts1.rdd.partitions.size
println(s"counts.count (can take a while, # partitions = $nPartitions):")
println(s"result: ${counts.count}")
println(s"counts1.count (usually faster, # partitions = $nPartitions1):")
println(s"result: ${counts1.count}")

// DataFrame version:
val countsDF = verses.groupBy("book").count()
countsDF.show(100)
countsDF.count

// Exercise: Sort the last output by the words, by counts. How much overhead does this add?
// Exercise: Try a JOIN with the "abbrevs_to_names" data to convert the book
//   abbreviations to full titles. (See solns/SparkSQL-..-script.scala)
// Exercise: Play with the DataFrame DSL.
// Exercise: Try some of the other sacred text data files.
