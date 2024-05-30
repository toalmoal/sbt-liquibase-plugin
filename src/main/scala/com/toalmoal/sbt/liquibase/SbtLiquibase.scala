package com.toalmoal.sbt.liquibase

import java.net.URLClassLoader
import java.text.SimpleDateFormat
import java.io.{FileWriter, OutputStreamWriter, PrintStream}

import liquibase.resource._
import liquibase.logging.core.JavaLogService
import liquibase.diff.output.DiffOutputControl
import liquibase.{Liquibase, Scope => LiqubaseScope}
import liquibase.integration.commandline.CommandLineUtils

import sbt.Keys._
import sbt.{Def, Setting, _}

import sbt.complete.DefaultParsers._

import java.nio.file.FileSystems

import scala.util.Try
import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Import {
  val liquibaseUpdate = TaskKey[Unit]("liquibase-update", "Run a liquibase migration")
  val liquibaseUpdateSql = TaskKey[Unit]("liquibase-update-sql", "Writes SQL to update database to current version")
  val liquibaseStatus = TaskKey[Unit]("liquibase-status", "Print count of un-run change sets")
  val liquibaseClearChecksums = TaskKey[Unit]("liquibase-clear-checksums", "Removes all saved checksums from database log. Useful for 'MD5Sum Check Failed' errors")
  val liquibaseListLocks = TaskKey[Unit]("liquibase-list-locks", "Lists who currently has locks on the database changelog")
  val liquibaseReleaseLocks = TaskKey[Unit]("liquibase-release-locks", "Releases all locks on the database changelog")
  val liquibaseValidateChangelog = TaskKey[Unit]("liquibase-validate-changelog", "Checks changelog for errors")
  val liquibaseTag = InputKey[Unit]("liquibase-tag", "Tags the current database state for future rollback")
  val liquibaseDbDiff = TaskKey[Unit]("liquibase-db-diff", "( this isn't implemented yet ) Generate changeSet(s) to make Test DB match Development")
  val liquibaseDbDoc = TaskKey[Unit]("liquibase-db-doc", "Generates Javadoc-like documentation based on current database and change log")
  val liquibaseGenerateChangelog = TaskKey[Unit]("liquibase-generate-changelog", "Writes Change Log XML to copy the current state of the database to standard out")
  val liquibaseChangelogSyncSql = TaskKey[Unit]("liquibase-changelog-sync-sql", "Writes SQL to mark all changes as executed in the database to STDOUT")
  val liquibaseDropAll = TaskKey[Unit]("liquibase-drop-all", "Drop all database objects owned by user")

  val liquibaseRollback = InputKey[Unit]("liquibase-rollback", "<tag> Rolls back the database to the the state is was when the tag was applied")
  val liquibaseRollbackSql = InputKey[Unit]("liquibase-rollback-sql", "<tag> Writes SQL to roll back the database to that state it was in when the tag was applied to STDOUT")
  val liquibaseRollbackCount = InputKey[Unit]("liquibase-rollback-count", "<num>Rolls back the last <num> change sets applied to the database")
  val liquibaseRollbackCountSql = InputKey[Unit]("liquibase-rollback-count-sql", "<num> Writes SQL to roll back the last <num> change sets to STDOUT applied to the database")
  val liquibaseRollbackToDate = InputKey[Unit]("liquibase-rollback-to-date", "<date> Rolls back the database to the the state is was at the given date/time. Date Format: yyyy-MM-dd HH:mm:ss")
  val liquibaseRollbackToDateSql = InputKey[Unit]("liquibase-rollback-to-date-sql", "<date> Writes SQL to roll back the database to that state it was in at the given date/time version to STDOUT")
  val liquibaseFutureRollbackSql = InputKey[Unit]("liquibase-future-rollback-sql", " Writes SQL to roll back the database to the current state after the changes in the changelog have been applied")

  val liquibaseDataDir = SettingKey[File]("liquibase-data-dir", "This is the liquibase migrations directory.")
  val liquibaseChangelog = SettingKey[File]("liquibase-changelog", "This is your liquibase changelog file to run.")
  val liquibaseUrl = TaskKey[String]("liquibase-url", "The url for liquibase")
  val liquibaseUsername = TaskKey[String]("liquibase-username", "username yo.")
  val liquibasePassword = TaskKey[String]("liquibase-password", "password")
  val liquibaseDriver = SettingKey[String]("liquibase-driver", "driver")
  val liquibaseDefaultCatalog = SettingKey[Option[String]]("liquibase-default-catalog", "default catalog")
  val liquibaseDefaultSchemaName = SettingKey[Option[String]]("liquibase-default-schema-name", "default schema name")
  val liquibaseChangelogCatalog = SettingKey[Option[String]]("liquibase-changelog-catalog", "changelog catalog")
  val liquibaseChangelogSchemaName = SettingKey[Option[String]]("liquibase-changelog-schema-name", "changelog schema name")
  val liquibaseContext = SettingKey[String]("liquibase-context", "changeSet contexts to execute")
  val liquibaseOutputDefaultCatalog = SettingKey[Boolean]("liquibase-output-default-catalog", "Whether to ignore the schema name.")
  val liquibaseOutputDefaultSchema = SettingKey[Boolean]("liquibase-output-default-schema", "Whether to ignore the schema name.")

  val liquibaseSqlOutputFile = TaskKey[Option[File]]("liquibase-sql-output-file", "Filename for SQL output")
  val liquibaseResourceAccessor = TaskKey[ResourceAccessor]("liquibase-resource-accessor", "Resource accessor for finding changelog files and plugins")

  lazy val liquibaseInstance = TaskKey[() => Liquibase]("liquibase", "liquibase object")
}

object SbtLiquibase extends AutoPlugin {

  import Import._

  val autoImport: Import.type = Import

  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  private lazy val DateParser = mapOrFail(StringBasic) { date =>
    dateFormat.parse(date)
  }

  // getCurrentScope throws exception the first time because logging service is not set
  Try(LiqubaseScope.getCurrentScope)

  implicit class RichLiquibase(val liquibase: Liquibase) extends AnyVal {
    def execAndClose(f: Liquibase => Unit): Unit = {
      try {
        f(liquibase)
      } finally {
        liquibase.getDatabase.close()
      }
    }
  }

  override def projectSettings: Seq[Setting[_]] = liquibaseBaseSettings(Compile) ++ inConfig(Test)(liquibaseBaseSettings(Test))

  private def liquibaseBaseSettings(conf: Configuration): Seq[Setting[_]] = {

    lazy val rootLoader: ClassLoader = {
      @tailrec
      def parent(loader: ClassLoader): ClassLoader = {
        val p = loader.getParent
        if (p eq null) loader else parent(p)
      }

      val systemLoader = ClassLoader.getSystemClassLoader
      if (systemLoader ne null) parent(systemLoader)
      else parent(getClass.getClassLoader)
    }

    def withinScope[T](resourceAccessor: ResourceAccessor, classLoader: ClassLoader)(f: => T): T = {
      val attributes = Map(
        LiqubaseScope.Attr.logService.name() -> new JavaLogService(),
        LiqubaseScope.Attr.resourceAccessor.name() -> resourceAccessor,
        LiqubaseScope.Attr.classLoader.name() -> classLoader
      ).asJava
      LiqubaseScope.child(attributes, () => f)
    }

    Seq[Setting[_]](
      liquibaseDefaultCatalog := None,
      liquibaseDefaultSchemaName := None,
      liquibaseChangelogCatalog := None,
      liquibaseChangelogSchemaName := None,
      liquibaseDataDir := baseDirectory.value / "src" / "main" / "migrations",
      liquibaseChangelog := liquibaseDataDir.value / "changelog.xml",
      liquibaseContext := "",
      liquibaseOutputDefaultCatalog := true,
      liquibaseOutputDefaultSchema := true,

      liquibaseSqlOutputFile := Some(file("liquibase-out.sql")),

      liquibaseResourceAccessor := {
        Try(LiqubaseScope.getCurrentScope)
        withinScope(new ClassLoaderResourceAccessor(getClass.getClassLoader), rootLoader) {
          val rootFolders = FileSystems.getDefault.getRootDirectories.asScala.map(_.toFile).toSeq
          val fsAccessors = rootFolders.map(new DirectoryResourceAccessor(_))
          val loader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), rootLoader)
          val clAccessor = new ClassLoaderResourceAccessor(loader)
          val pluginClAccessor = new ClassLoaderResourceAccessor(getClass.getClassLoader)
          new CompositeResourceAccessor(fsAccessors ++ Seq(clAccessor, pluginClAccessor): _*)
        }
      },

      liquibaseInstance := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        def database() = CommandLineUtils.createDatabaseObject(
            resourceAccessor,
            liquibaseUrl.value,
            liquibaseUsername.value,
            liquibasePassword.value,
            liquibaseDriver.value,
            liquibaseDefaultCatalog.value.orNull,
            liquibaseDefaultSchemaName.value.orNull,
            false, // outputDefaultCatalog
            true, // outputDefaultSchema
            null, // databaseClass
            null, // driverPropertiesFile
            null, // propertyProviderClass
            liquibaseChangelogCatalog.value.orNull,
            liquibaseChangelogSchemaName.value.orNull,
            null, // databaseChangeLogTableName
            null // databaseChangeLogLockTableName
          )

        () => withinScope(resourceAccessor, classLoader) { new Liquibase(liquibaseChangelog.value.absolutePath, resourceAccessor, database()) }
      },

      liquibaseUpdate := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) { liquibaseInstance.value().execAndClose(_.update(liquibaseContext.value)) }
      },

      liquibaseUpdateSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose(_.update(liquibaseContext.value, outputWriter.value))
        }
      },

      liquibaseStatus := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose {
            _.reportStatus(true, liquibaseContext.value, new OutputStreamWriter(System.out))
          }
        }
      },

      liquibaseClearChecksums := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose(_.clearCheckSums())
        }
      },

      liquibaseListLocks := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose(_.reportLocks(new PrintStream(System.out)))
        }
      },

      liquibaseReleaseLocks := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose(_.forceReleaseLocks())
        }
      },

      liquibaseValidateChangelog := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose(_.validate())
        }
      },

      liquibaseDbDoc := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          {
            val path = (target.value / "liquibase-doc").absolutePath
            liquibaseInstance.value().execAndClose(_.generateDocumentation(path))
            streams.value.log.info(s"Documentation generated in $path")
          }
        }
      },

      liquibaseRollback := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val tag = token(Space ~> StringBasic, "<tag>").parsed
          liquibaseInstance.value().execAndClose(_.rollback(tag, liquibaseContext.value))
          streams.value.log.info("Rolled back to tag %s".format(tag))
        }
      },

      liquibaseRollbackCount := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val count = token(Space ~> IntBasic, "<count>").parsed
          liquibaseInstance.value().execAndClose(_.rollback(count, liquibaseContext.value))
          streams.value.log.info("Rolled back to count %s".format(count))
        }
      },

      liquibaseRollbackSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val tag = token(Space ~> StringBasic, "<tag>").parsed
          liquibaseInstance.value().execAndClose {
            _.rollback(tag, liquibaseContext.value, outputWriter.value)
          }
        }
      },

      liquibaseRollbackCountSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val count = token(Space ~> IntBasic, "<count>").parsed
          liquibaseInstance.value().execAndClose {
            _.rollback(count, liquibaseContext.value, outputWriter.value)
          }
        }
      },

      liquibaseRollbackToDate := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val date = token(Space ~> DateParser, "<date/time>").parsed
          liquibaseInstance.value().execAndClose(_.rollback(date, liquibaseContext.value))
        }
      },

      liquibaseRollbackToDateSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val date = token(Space ~> DateParser, "<date/time>").parsed
          liquibaseInstance.value().execAndClose {
            _.rollback(date, liquibaseContext.value, outputWriter.value)
          }
        }
      },

      liquibaseFutureRollbackSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose {
            _.futureRollbackSQL(liquibaseContext.value, outputWriter.value)
          }
        }
      },

      liquibaseTag := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          val tag = token(Space ~> StringBasic, "<tag>").parsed
          liquibaseInstance.value().execAndClose(_.tag(tag))
          streams.value.log.info(s"Tagged db with $tag for future rollback if needed")
        }
      },

      liquibaseChangelogSyncSql := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) {
          liquibaseInstance.value().execAndClose {
            _.changeLogSync(liquibaseContext.value, outputWriter.value)
          }
        }
      },

      liquibaseDropAll := {
        val resourceAccessor: ResourceAccessor = liquibaseResourceAccessor.value
        val classLoader = new URLClassLoader(Path.toURLs(liquibaseDataDir.value +: (conf / dependencyClasspath).value.map(_.data)), getClass.getClassLoader)

        withinScope(resourceAccessor, classLoader) { liquibaseInstance.value().execAndClose(_.dropAll()) }
      }
    )
  }

  def outputWriter: Def.Initialize[Task[OutputStreamWriter]] = liquibaseSqlOutputFile.map {
    case None => new OutputStreamWriter(System.out)
    case Some(file) => new FileWriter(file)
  }

  def generateChangeLog = {
    (liquibaseInstance, liquibaseChangelog)
    liquibaseInstance.map { liquibase =>
      liquibaseChangelog.map { clog =>
        liquibaseDefaultCatalog.map { defaultCatalog =>
          liquibaseDefaultSchemaName.map { defaultSchemaName =>
            liquibaseDataDir.map { dataDir =>
              val instance = liquibase()
              try {
                CommandLineUtils.doGenerateChangeLog(
                  clog.absolutePath,
                  instance.getDatabase,
                  defaultCatalog.orNull,
                  defaultSchemaName.orNull,
                  null, // snapshotTypes
                  null, // author
                  null, // context
                  dataDir.absolutePath,
                  new DiffOutputControl())
              } finally {
                instance.getDatabase.close()
              }
            }
          }
        }
      }
    }
  }
}
