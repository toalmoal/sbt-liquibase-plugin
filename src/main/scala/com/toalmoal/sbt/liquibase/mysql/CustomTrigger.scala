package com.toalmoal.sbt.liquibase.mysql

import com.toalmoal.sbt.liquibase.Utils._

import liquibase.change.custom._
import liquibase.database.Database
import liquibase.resource.ResourceAccessor
import liquibase.exception.ValidationErrors

abstract class CustomTrigger extends CustomSqlChange with CustomSqlRollback {

  var catalogName: String = _
  var schemaName: String = _
  var tableName: String = _
  var copyTableName: String = _
  var columnsAdded: Set[String] = Set.empty
  var columnsRemoved: Set[String] = Set.empty
  var columnsExcluded: Set[String] = Set.empty

  def getCatalogName(): String = catalogName
  def setCatalogName(name: String): Unit = catalogName = Option(name).orNull

  def getSchemaName(): String = schemaName
  def setSchemaName(name: String): Unit = schemaName = Option(name).orNull

  def getTableName(): String = tableName
  def setTableName(name: String): Unit = tableName = Option(name).orNull

  def getCopyTableName(): String = Option(copyTableName).getOrElse(s"${getTableName()}_log")
  def setCopyTableName(name: String): Unit = copyTableName = Option(name).orNull

  def getColumnsAdded(): String = columnsAdded.mkString(", ")
  def setColumnsAdded(columns: String): Unit = columnsAdded = Option(columns).getOrElse("").split("\\s*,\\*").toSet

  def getColumnsRemoved(): String = columnsRemoved.mkString(", ")
  def setColumnsRemoved(columns: String): Unit = columnsRemoved = Option(columns).getOrElse("").split("\\s*,\\*").toSet

  def getColumnsExcluded(): String = columnsExcluded.mkString(", ")
  def setColumnsExcluded(columns: String): Unit = columnsExcluded = Option(columns).getOrElse("").split("\\s*,\\*").toSet

  override def getConfirmationMessage: String = null

  override def setUp(): Unit = ()

  override def setFileOpener(resourceAccessor: ResourceAccessor): Unit = ()

  override def validate(database: Database): ValidationErrors = new ValidationErrors()

  protected def triggerSql(triggerName: String, operation: String, columns: Set[String]): String =
    s"""CREATE TRIGGER `$triggerName`
       |AFTER $operation
       |ON `${getTableName()}`
       |FOR EACH ROW
       |  BEGIN
       |    INSERT INTO `${getCopyTableName()}` (`log_time`,
       |      ${multilineJoin(columns.map(v => s"`$v`").toList.sorted, 200)})
       |    VALUES (UTC_TIMESTAMP(),
       |      ${multilineJoin(columns.map(v => s"NEW.`$v`").toList.sorted, 200)});
       |END;""".stripMargin

}
