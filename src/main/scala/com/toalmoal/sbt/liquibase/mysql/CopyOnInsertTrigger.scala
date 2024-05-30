package com.toalmoal.sbt.liquibase.mysql

import com.toalmoal.sbt.liquibase.Utils._

import liquibase.database.Database
import liquibase.statement.SqlStatement
import liquibase.database.jvm.JdbcConnection
import liquibase.statement.core.RawSqlStatement

class CopyOnInsertTrigger extends CustomTrigger {

  var insertTriggerName: String = _

  override def generateStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollForwardColumns = (columns ++ columnsAdded).diff(columnsRemoved ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getInsertTriggerName(), "INSERT", rollForwardColumns)),
    )
  }

  override def generateRollbackStatements(database: Database): Array[SqlStatement] = {
    val connection: JdbcConnection = database.getConnection.asInstanceOf[JdbcConnection]
    val columns: Set[String] = getColumns(connection, tableName, getCatalogName(), getSchemaName()).toSet
    val rollBackwardColumns = (columns ++ columnsRemoved).diff(columnsAdded ++ columnsExcluded)

    Array(
      new RawSqlStatement(triggerSql(getInsertTriggerName(), "INSERT", rollBackwardColumns)),
    )
  }

  def getInsertTriggerName(): String = Option(insertTriggerName).getOrElse(s"${getTableName()}_insert")
  def setInsertTriggerName(name: String): Unit = insertTriggerName = Option(name).orNull

}
