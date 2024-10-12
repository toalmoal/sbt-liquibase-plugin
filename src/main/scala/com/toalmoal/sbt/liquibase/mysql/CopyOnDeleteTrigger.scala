package com.toalmoal.sbt.liquibase.mysql

import com.toalmoal.sbt.liquibase.Utils._

import liquibase.database.Database
import liquibase.statement.SqlStatement
import liquibase.database.jvm.JdbcConnection
import liquibase.statement.core.RawSqlStatement

class CopyOnDeleteTrigger extends CustomTrigger {

  var deleteTriggerName: String = _

  override def generateStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollForwardColumns = (columns ++ columnsAdded).diff(columnsRemoved ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getDeleteTriggerName(), "DELETE", rollForwardColumns)),
    )
  }

  override def generateRollbackStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollBackwardColumns = (columns ++ columnsRemoved).diff(columnsAdded ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getDeleteTriggerName(), "DELETE", rollBackwardColumns, deleted = true)),
    )
  }

  def getDeleteTriggerName(): String = Option(deleteTriggerName).getOrElse(s"${getTableName()}_delete")
  def setDeleteTriggerName(name: String): Unit = deleteTriggerName = Option(name).orNull

}
