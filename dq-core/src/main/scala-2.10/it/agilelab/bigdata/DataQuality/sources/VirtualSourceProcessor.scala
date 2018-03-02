package it.agilelab.bigdata.DataQuality.sources

import it.agilelab.bigdata.DataQuality.metrics.SourceProcessor.FileId
import it.agilelab.bigdata.DataQuality.utils._
import org.apache.spark.sql.{DataFrame, SQLContext}

import scala.collection.JavaConversions.asJavaCollection

/**
  * Created by Egor Makhov on 12/10/17.
  */
object VirtualSourceProcessor {

  def getActualSources(initialVirutalSourcesMap: Map[FileId, VirtualFile],
                       initialsourceMap: Map[String, Source])(
      implicit sqlContext: SQLContext,
      settings: DQSettings): Map[String, Source] = {

    @scala.annotation.tailrec
    def loop(virtualSourcesMap: Map[FileId, VirtualFile],
             actualSourcesMapAccumulator: Map[String, Source])(
        implicit sqlContext: SQLContext): Map[String, Source] = {

      log.info(
        "VIRTUAL SOURCES MAP SIZE " + virtualSourcesMap.size + " keys " + virtualSourcesMap.keySet
          .mkString("-"))
      log.info(
        "ACTUAL SOURCES MAP SIZE " + actualSourcesMapAccumulator.size ++ " keys " + actualSourcesMapAccumulator.keySet
          .mkString("-"))
      if (virtualSourcesMap.isEmpty) {
        actualSourcesMapAccumulator
      } else {
        val firstLevelVirtualSources: Map[FileId, VirtualFile] =
          virtualSourcesMap.filter {
            case (sourceId, conf: VirtualFile) =>
              val parentIds = conf.parentSourceIds
              log.info(s" virutal source $sourceId   parerentIDS ${parentIds.mkString(
                "-")} sources ${actualSourcesMapAccumulator.keySet.mkString("-")}")
              actualSourcesMapAccumulator.keySet.containsAll(parentIds)
          }

        val otherSources: Map[String, Source] = firstLevelVirtualSources
          .map {
            case (vid, virutalFile) =>
              virutalFile match {
                case VirtualFileSelect(id,
                                       parentSourceIds,
                                       sqlCode,
                                       keyfields,
                                       _) =>
                  log.info("VIRTUAL SOURCE SELECT " + vid)
                  val firstParent = parentSourceIds.head
                  log.info("FIRST PARENT " + firstParent)
                  val dfSource =
                    actualSourcesMapAccumulator.get(firstParent).head

                  dfSource.df.registerTempTable(firstParent)
                  val virtualSourceDF = sqlContext.sql(sqlCode)

                  Source(vid,
                         settings.refDateString,
                         virtualSourceDF,
                         keyfields)
                case VirtualFileJoinSql(id,
                                        parentSourceIds,
                                        sqlCode,
                                        keyfields,
                                        _) =>
                  log.info("VIRUTAL JOIN " + sqlCode)
                  val leftParent = parentSourceIds.head
                  val rightParent = parentSourceIds(1)
                  log.info("LEFT PARENT " + leftParent)
                  log.info("RIGHT PARENT " + rightParent)
                  val dfSourceLeft: DataFrame =
                    actualSourcesMapAccumulator(leftParent).df
                  val dfSourceRight: DataFrame =
                    actualSourcesMapAccumulator(rightParent).df
                  val colLeft = dfSourceLeft.columns.toSeq.mkString(",")
                  val colRight = dfSourceRight.columns.toSeq.mkString(",")
                  dfSourceLeft.registerTempTable(leftParent)
                  dfSourceRight.registerTempTable(rightParent)
                  log.info(s"column left $colLeft")
                  log.info(s"column right $colRight")
                  val virtualSourceDF = sqlContext.sql(sqlCode)
                  log.info("VIRUTAL JOIN" + virtualSourceDF.explain())

                  Source(vid,
                         settings.refDateString,
                         virtualSourceDF,
                         keyfields)

                case VirtualFileJoin(id,
                                     parentSourceIds,
                                     joiningColumns,
                                     joinType,
                                     keyfields,
                                     _) =>
                  log.info("VIRUTAL JOIN " + joiningColumns.mkString("-"))

                  val leftParent = parentSourceIds.head
                  val rightParent = parentSourceIds(1)
                  log.info("LEFT PARENT " + leftParent)
                  log.info("RIGHT PARENT " + rightParent)
                  val dfSourceLeft = actualSourcesMapAccumulator(leftParent).df
                  val dfSourceRight =
                    actualSourcesMapAccumulator(rightParent).df

                  val colLeftRenamedLeft: Array[(String, String)] =
                    dfSourceLeft.columns
                      .filter(c => !joiningColumns.contains(c))
                      .map(colName => (colName, s"l_$colName"))
                  val colLeftRenamedRight: Array[(String, String)] =
                    dfSourceRight.columns
                      .filter(c => !joiningColumns.contains(c))
                      .map(colName => (colName, s"r_$colName"))

                  val dfLeftRenamed = colLeftRenamedLeft.foldLeft(dfSourceLeft)(
                    (dfAcc, cols) => dfAcc.withColumnRenamed(cols._1, cols._2))
                  val dfRightRenamed =
                    colLeftRenamedRight.foldLeft(dfSourceRight)((dfAcc, cols) =>
                      dfAcc.withColumnRenamed(cols._1, cols._2))

                  val colLeft = dfLeftRenamed.columns.toSeq.mkString(",")
                  val colRight = dfRightRenamed.columns.toSeq.mkString(",")

                  dfLeftRenamed.registerTempTable(leftParent)
                  dfRightRenamed.registerTempTable(rightParent)

                  log.info(s"column left $colLeft")
                  log.info(s"column right $colRight")

                  val virtualSourceDF =
                    dfLeftRenamed.join(dfRightRenamed, joiningColumns, joinType)

                  log.info("VIRUTAL JOIN" + virtualSourceDF.explain())

                  Source(vid,
                         settings.refDateString,
                         virtualSourceDF,
                         keyfields)
              }

          }
          .map(s => (s.id, s))
          .toMap
        val virutalSourcesToProcess = virtualSourcesMap -- firstLevelVirtualSources.keySet

        val processed = firstLevelVirtualSources.size

        val newActualSources = actualSourcesMapAccumulator ++ otherSources
        if (otherSources.isEmpty) {
          log.error("SOMETHING WRONG ")
          throw new Exception(
            s"processed $processed : ${firstLevelVirtualSources.keySet.mkString("-")} but haed only  addedSize ")
        }
        loop(virutalSourcesToProcess, newActualSources)

      }

    }

    loop(initialVirutalSourcesMap, initialsourceMap)
  }

}
