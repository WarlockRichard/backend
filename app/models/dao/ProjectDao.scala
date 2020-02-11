package models.dao

import javax.inject.{Inject, Singleton}

import models.event.Event
import models.{ListWithTotal, NamedEntity}
import models.project.{Project, TemplateBinding}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import utils.implicits.FutureLifting._
import utils.listmeta.ListMeta
import io.scalaland.chimney.dsl._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Component for project table.
  */
trait ProjectComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>

  import profile.api._

  /**
    * Project db model.
    */
  case class DbProject(
    id: Long,
    name: String,
    description: Option[String],
    groupAuditorId: Long,
    formsOnSamePage: Boolean,
    canRevote: Boolean,
    isAnonymous: Boolean,
    machineName: String
  ) {

    def toModel(groupAuditorName: String, templates: Seq[TemplateBinding], hasInProgressEvent: Boolean): Project =
      this
        .into[Project]
        .withFieldConst(_.groupAuditor, NamedEntity(groupAuditorId, groupAuditorName))
        .withFieldConst(_.templates, templates)
        .withFieldConst(_.hasInProgressEvents, hasInProgressEvent)
        .transform
  }

  object DbProject {
    def fromModel(p: Project) =
      p.into[DbProject]
        .withFieldComputed(_.groupAuditorId, _.groupAuditor.id)
        .transform
  }

  class ProjectTable(tag: Tag) extends Table[DbProject](tag, "project") {

    def id = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def name = column[String]("name")
    def description = column[Option[String]]("description")
    def groupAuditorId = column[Long]("group_auditor_id")
    def formsOnSamePage = column[Boolean]("forms_on_same_page")
    def canRevote = column[Boolean]("can_revote")
    def isAnonymous = column[Boolean]("is_anonymous")
    def machineName = column[String]("machine_name")

    def * =
      (id, name, description, groupAuditorId, formsOnSamePage, canRevote, isAnonymous, machineName) <> ((DbProject.apply _).tupled, DbProject.unapply)
  }

  val Projects = TableQuery[ProjectTable]

}

/**
  * Project DAO.
  */
@Singleton
class ProjectDao @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider,
  implicit val ec: ExecutionContext
) extends HasDatabaseConfigProvider[JdbcProfile]
  with ProjectComponent
  with EventProjectComponent
  with GroupComponent
  with TemplateBindingComponent
  with TemplateComponent
  with ProjectRelationComponent
  with EventComponent
  with DaoHelper {

  import profile.api._

  /**
    * Returns list of projects with relations.
    *
    * @param meta sorting and pagination
    */
  def getList(
    optId: Option[Long] = None,
    optEventId: Option[Long] = None,
    optGroupFromIds: Option[Seq[Long]] = None,
    optFormId: Option[Long] = None,
    optGroupAuditorId: Option[Long] = None,
    optEmailTemplateId: Option[Long] = None,
    optAnyRelatedGroupId: Option[Long] = None
  )(implicit meta: ListMeta = ListMeta.default): Future[ListWithTotal[Project]] = {

    def sortMapping(project: ProjectTable): PartialFunction[String, Rep[_]] = {
      case "id"          => project.id
      case "name"        => project.name
      case "description" => project.description
    }

    def emailTemplateFilter(project: ProjectTable) = optEmailTemplateId.map { emailTemplateId =>
      project.id in ProjectTemplates.filter(_.templateId === emailTemplateId).map(_.projectId)
    }

    def eventFilter(project: ProjectTable) = optEventId.map { eventId =>
      project.id in EventProjects.filter(_.eventId === eventId).map(_.projectId)
    }

    def groupFromFilter(project: ProjectTable) = optGroupFromIds.map { groupFromIds =>
      project.id in Relations.filter(_.groupFromId.inSet(groupFromIds)).map(_.projectId)
    }

    def formFilter(project: ProjectTable) = optFormId.map { formId =>
      project.id in Relations.filter(_.formId === formId).map(_.projectId)
    }

    def anyRelatedGroupFilter(project: ProjectTable) = optAnyRelatedGroupId.map { anyRelatedGroup =>
      project.groupAuditorId === anyRelatedGroup ||
      (project.id in Relations
        .filter(x => x.groupToId === anyRelatedGroup || x.groupFromId === anyRelatedGroup)
        .map(_.projectId))
    }

    val baseQuery = Projects
      .applyFilter { x =>
        Seq(
          optId.map(x.id === _),
          eventFilter(x),
          groupFromFilter(x),
          formFilter(x),
          optGroupAuditorId.map(x.groupAuditorId === _),
          emailTemplateFilter(x),
          anyRelatedGroupFilter(x)
        )
      }

    val countQuery = baseQuery.length

    val templatesQuery = ProjectTemplates.join(Templates).on(_.templateId === _.id)

    val resultQuery = baseQuery
      .applySorting(meta.sorting)(sortMapping)
      .applyPagination(meta.pagination)
      .join(Groups)
      .on(_.groupAuditorId === _.id)
      .joinLeft(templatesQuery)
      .on { case ((project, _), (template, _)) => project.id === template.projectId }
      .map {
        case ((project, auditorGroup), templateOpt) =>
          val eventsIds = EventProjects.filter(_.projectId === project.id).map(_.eventId)
          val isEventsExists = Events
            .filter(event => event.id.in(eventsIds) && statusFilter(event, Event.Status.InProgress))
            .exists
          ((project, auditorGroup, isEventsExists), templateOpt)
      }
      .applySorting(meta.sorting) { case ((project, _, _), _) => sortMapping(project) } // sort one page (order not preserved after join)

    for {
      count <- db.run(countQuery.result)
      result <- if (count > 0) db.run(resultQuery.result) else Nil.toFuture
    } yield {
      val data = result
        .groupByWithOrder {
          case ((project, auditorGroup, isEventsExists), _) => (project, auditorGroup, isEventsExists)
        }
        .map {
          case ((project, auditorGroup, isEventsExists), flatTemplates) =>
            val templates = flatTemplates
              .collect { case (_, Some((templateBinding, template))) => templateBinding.toModel(template.name) }

            project.toModel(auditorGroup.name, templates, isEventsExists)
        }
      ListWithTotal(count, data)
    }
  }

  /**
    * Returns project by ID
    *
    * @param id project ID
    */
  def findById(id: Long): Future[Option[Project]] = {
    getList(optId = Some(id)).map(_.data.headOption)
  }

  /**
    * Creates project.
    *
    * @param project project model
    * @return created project with ID
    */
  def create(project: Project): Future[Project] = {
    val action = for {
      projectId <- Projects.returning(Projects.map(_.id)) += DbProject.fromModel(project)
      _ <- DBIO.seq(ProjectTemplates ++= project.templates.map(DbTemplateBinding.fromModel(_, projectId)))
    } yield projectId

    db.run(action.transactionally)
      .flatMap(findById(_).map(_.getOrElse(throw new NoSuchElementException("project not found"))))
  }

  /**
    * Updates project.
    *
    * @param project project model
    * @return updated project
    */
  def update(project: Project): Future[Project] = {
    val action = for {
      _ <- Projects.filter(_.id === project.id).update(DbProject.fromModel(project))
      _ <- ProjectTemplates.filter(_.projectId === project.id).delete
      _ <- DBIO.seq(ProjectTemplates ++= project.templates.map(DbTemplateBinding.fromModel(_, project.id)))
    } yield ()

    db.run(action.transactionally)
      .flatMap(_ => findById(project.id).map(_.getOrElse(throw new NoSuchElementException("project not found"))))
  }

  /**
    * Removes project with relations.
    *
    * @param projectId project ID
    * @return number of rows affected
    */
  def delete(projectId: Long): Future[Int] = db.run {
    Projects.filter(_.id === projectId).delete
  }
}
