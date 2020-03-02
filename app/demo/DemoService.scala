package demo

import javax.inject.{Inject, Singleton}
import models.ListWithTotal
import models.dao.{FormDao, GroupDao, ProjectDao, ProjectRelationDao}
import models.form.Form
import models.group.Group
import models.project.{Project, Relation}
import utils.listmeta.ListMeta
import utils.listmeta.pagination.Pagination.WithPages
import utils.listmeta.sorting.Sorting

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DemoService @Inject() (
  protected val groupDao: GroupDao,
  protected val projectDao: ProjectDao,
  protected val projectRelationDao: ProjectRelationDao,
  protected val formDao: FormDao,
  implicit val ec: ExecutionContext
) {

  def checkDbEmpty: Future[Boolean] = {

    implicit val listMeta: ListMeta = ListMeta(WithPages(size = 1, number = 1), Sorting(Nil))

    def isListEmpty[A](list: ListWithTotal[A]) = list.total == 0

    for {
      noGroups <- groupDao.getList().map(isListEmpty)
      noProjects <- projectDao.getList().map(isListEmpty)
      noRelations <- projectRelationDao.getList().map(isListEmpty)
      noForms <- formDao.getList().map(isListEmpty)
    } yield noGroups && noProjects && noRelations && noForms
  }

  def createGroups(groups: Seq[Group]): Future[Seq[Group]] = {
    Future.sequence(groups.map(groupDao.create))
  }

  def createProjects(projects: Seq[Project]): Future[Seq[Project]] = {
    Future.sequence(projects.map(projectDao.create))
  }

  def createRelations(relations: Seq[Relation]): Future[Seq[Relation]] = {
    Future.sequence(relations.map(projectRelationDao.create))
  }

  def createForms(forms: Seq[Form]): Future[Seq[Form]] = {
    Future.sequence(forms.map(formDao.create))
  }
}
