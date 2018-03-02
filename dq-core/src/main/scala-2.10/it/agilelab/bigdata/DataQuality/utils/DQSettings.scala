package it.agilelab.bigdata.DataQuality.utils

import com.typesafe.config._
import it.agilelab.bigdata.DataQuality.exceptions.IllegalParameterException
import it.agilelab.bigdata.DataQuality.sources.DatabaseConfig
import it.agilelab.bigdata.DataQuality.targets.HdfsTargetConfig
import it.agilelab.bigdata.DataQuality.utils
import org.joda.time
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.util.Try

/**
  * Created by Paolo on 20/01/2017.
  */
case class DQSettings(
    commandLineOpts: DQcommandLineOptions,
    configObj: Config
) {

  val inputArgDateFormat: DateTimeFormatter =
    DateTimeFormat.forPattern("yyyy-MM-dd")

  val ref_date: DateTime = new time.DateTime(commandLineOpts.refDate.getTime)

  lazy val refDateString: String = ref_date.toString(inputArgDateFormat)

  /* application.conf parameters */

  val vsDumpConfig: Option[HdfsTargetConfig] = Try {
    val obj: Config = configObj.getConfig("vsDumpConfig")

    utils.parseTargetConfig(obj).get
  }.toOption

  val appName: String =
    Try(configObj.getString("appName")).toOption.getOrElse("")

  val appDir: String =
    Try(configObj.getString("appDirectory")).toOption.getOrElse("")
  val errorDumpSize: Int =
    Try(configObj.getInt("errorDumpSize")).toOption.getOrElse(1000)
  val errorFolderPath: Option[String] = Try(
    configObj.getString("errorFolderPath")).toOption

  val hiveDir: String =
    Try(configObj.getString("hiveDir")).toOption.getOrElse("")
  val hadoopConfDir: String =
    Try(configObj.getString("hadoopConfDir")).toOption.getOrElse("")

  val mailingMode: String =
    Try(configObj.getString("mailing.mode").toLowerCase).getOrElse("internal")
  val mailingConfig: Option[Mailer] = {
    val monfig = Try(configObj.getConfig("mailing.conf")).toOption
    monfig match {
      case Some(conf) => Try(new Mailer(conf)).toOption
      case None       => None
    }
  }

  private val storageType: String = configObj.getString("storage.type")
  private val storageConfig: Config = configObj.getConfig("storage.config")
  // todo add new storage types
  val resStorage: Product = storageType match {
    case "DB" => new DatabaseConfig(storageConfig)
    case x    => throw IllegalParameterException(x)
  }
}

case class Mailer(
    address: String,
    hostName: String,
    username: String,
    password: String,
    smtpPortSSL: Int,
    sslOnConnect: Boolean
) {
  def this(config: Config) = {
    this(
      config.getString("address"),
      config.getString("hostname"),
      Try(config.getString("username")).getOrElse(""),
      Try(config.getString("password")).getOrElse(""),
      Try(config.getInt("smtpPort")).getOrElse(465),
      Try(config.getBoolean("sslOnConnect")).getOrElse(true)
    )
  }
}
