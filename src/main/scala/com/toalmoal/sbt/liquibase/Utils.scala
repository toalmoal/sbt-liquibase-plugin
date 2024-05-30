package com.toalmoal.sbt.liquibase

import liquibase.database.jvm.JdbcConnection

import java.sql.ResultSet

object Utils {

  def extract[T](rs: ResultSet, f: ResultSet => T): List[T] =
    if (rs.next()) {
      f(rs) +: extract(rs, f)
    } else List.empty

  def getColumns(connection: JdbcConnection, table: String, catalog: String, schema: String): Seq[String] = {
    val columnsResultSet = connection.getMetaData.getColumns(catalog, schema, table, null)
    extract(columnsResultSet, _.getString("COLUMN_NAME"))
  }

  def multilineJoin(parts: Seq[String], maxLength: Int, separator: String = ", "): String = {
    def format(line: String, rest: List[String]): String = rest match {
      case head :: tail =>
        val partsSeparator = if (line.nonEmpty) separator else ""
        if (line.length + head.length > maxLength) {
          line + partsSeparator.trim + "\n      " + format(head, tail)
        } else {
          format(line + partsSeparator + head, tail)
        }
      case Nil => line
    }

    format("", parts.toList)
  }

}
