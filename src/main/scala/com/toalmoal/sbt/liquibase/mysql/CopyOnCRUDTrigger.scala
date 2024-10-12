package com.toalmoal.sbt.liquibase.mysql

import com.toalmoal.sbt.liquibase.Utils._
import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.statement.SqlStatement
import liquibase.statement.core.RawSqlStatement

class CopyOnCRUDTrigger extends CustomTrigger {

  var insertTriggerName: String = _
  var updateTriggerName: String = _
  var deleteTriggerName: String = _

  override def generateStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollForwardColumns = (columns ++ columnsAdded).diff(columnsRemoved ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getInsertTriggerName(), "INSERT", rollForwardColumns)),
      new RawSqlStatement(triggerSql(getUpdateTriggerName(), "UPDATE", rollForwardColumns)),
      new RawSqlStatement(triggerSql(getDeleteTriggerName(), "DELETE", rollForwardColumns, deleted = true)),
    )
  }

  override def generateRollbackStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollBackwardColumns = (columns ++ columnsRemoved).diff(columnsAdded ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getInsertTriggerName(), "INSERT", rollBackwardColumns)),
      new RawSqlStatement(triggerSql(getUpdateTriggerName(), "UPDATE", rollBackwardColumns)),
      new RawSqlStatement(triggerSql(getDeleteTriggerName(), "DELETE", rollBackwardColumns, deleted = true)),
    )
  }

  def getInsertTriggerName(): String = Option(insertTriggerName).getOrElse(s"${getTableName()}_insert")
  def setInsertTriggerName(name: String): Unit = insertTriggerName = Option(name).orNull

  def getUpdateTriggerName(): String = Option(updateTriggerName).getOrElse(s"${getTableName()}_update")
  def setUpdateTriggerName(name: String): Unit = updateTriggerName = Option(name).orNull

  def getDeleteTriggerName(): String = Option(deleteTriggerName).getOrElse(s"${getTableName()}_delete")
  def setDeleteTriggerName(name: String): Unit = deleteTriggerName = Option(name).orNull

}
