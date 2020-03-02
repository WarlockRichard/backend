/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package demo

import com.google.inject.AbstractModule
import javax.inject.{Inject, Singleton}
import models.NamedEntity
import models.form.Form
import models.form.element.{Radio, TextArea}
import models.group.Group
import models.project.{Project, Relation}
import utils.implicits.FutureLifting._
import utils.{Config, Logger, RandomGenerator}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Failure

/**
  * Demo initialization module.
  */
class DemoModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[DemoInitializer]).asEagerSingleton()
  }
}

/**
  * Adds demo entities if project database is empty and 'clean' option isn't enabled.
  */
@Singleton
class DemoInitializer @Inject() (
  protected val config: Config,
  protected val parser: DemoParser,
  protected val service: DemoService,
  implicit val ec: ExecutionContext
) extends Logger {

  import DemoInitializer._

  val demoData = if (!config.cleanStart) {

    service.checkDbEmpty
      .map { dbClear =>
        if (dbClear) {
          val maybeDemo = parser
            .parse(config.demoPath)
            .recoverWith { exception =>
              log.error(s"Unable to parse demo entities", exception)
              Failure(exception)
            }
            .toOption

          maybeDemo.map { demo =>
            for {
              groups <- service.createGroups(demo.groups.map(toGroupModel))
              groupMap = groups.map(group => group.name -> group.id).toMap
              projects <- service.createProjects(demo.projects.map(toProjectModel(_, groupMap)))
              projectMap = projects.map(project => project.name -> project.id).toMap
              forms <- service.createForms(demo.forms.asScala.toSeq.map {
                case (name, elements) =>
                  toFormModel(name, elements)
              })
              formMap = forms.map(form => form.name -> form.id).toMap
              _ <- service.createRelations(demo.relations.map(toRelationModel(_, groupMap, projectMap, formMap)))
            } yield ()
          }
        } else {
          log.debug("Skipping creating demo entities: DB is not empty")
          None
        }
      }
      .recover { exception =>
        log.error(s"Unable to check database on emptiness", exception)
        None
      }
  } else {
    None.toFuture
  }

}

object DemoInitializer {

  private def toGroupModel(groupName: String): Group =
    Group(id = 0, parentId = None, groupName, hasChildren = false, level = 0)

  private def toFormModel(formName: String, elementMap: java.util.HashMap[String, Array[String]]): Form = {
    val elements = elementMap.asScala.toSeq.map {
      case (caption, options) =>
        val details = if (options.nonEmpty) {
          (Radio, true, options.map(Form.ElementValue(id = 0, _)).toSeq)
        } else {
          (TextArea, false, Seq())
        }
        Form.Element(id = 0, details._1, caption, details._2, details._3, Seq(), randomMachineName, None)
    }
    Form(id = 0, formName, elements, Form.Kind.Active: Form.Kind, showInAggregation = true, randomMachineName)
  }

  private def randomMachineName = RandomGenerator.generateMachineName

  private def toProjectModel(project: DemoProject, groupMap: Map[String, Long]) = Project(
    id = 0,
    project.name,
    description = None,
    NamedEntity(groupMap.getOrElse(project.auditor, 0), Some(project.auditor)),
    templates = Seq(),
    formsOnSamePage = true,
    canRevote = true,
    isAnonymous = false,
    hasInProgressEvents = false,
    randomMachineName
  )

  private def toRelationModel(
    relation: DemoRelation,
    groupMap: Map[String, Long],
    projectMap: Map[String, Long],
    formMap: Map[String, Long]
  ) = {
    val toGroup = Option(relation.to)
    Relation(
      id = 0,
      project = NamedEntity(projectMap.getOrElse(relation.project, 0), Some(relation.project)),
      groupFrom = NamedEntity(groupMap.getOrElse(relation.from, 0), Some(relation.from)),
      groupTo = toGroup.map(to => NamedEntity(groupMap.getOrElse(to, 0), Some(to))),
      form = NamedEntity(formMap.getOrElse(relation.form, 0), Some(relation.form)),
      kind = if (toGroup.isEmpty) Relation.Kind.Survey else Relation.Kind.Classic,
      templates = Seq(),
      hasInProgressEvents = false,
      canSelfVote = false,
      canSkipAnswers = true
    )
  }
}
