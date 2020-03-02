package utils

import demo.{DemoInitializer, DemoParser, DemoService}
import models.ListWithTotal
import models.dao.{FormDao, GroupDao, ProjectDao, ProjectRelationDao}
import models.form.FormShort
import models.group.Group
import models.project.{Project, Relation}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import testutils.AsyncHelper
import utils.implicits.FutureLifting._
import utils.listmeta.ListMeta
import utils.listmeta.pagination.Pagination.WithPages
import utils.listmeta.sorting.Sorting

import scala.concurrent.{ExecutionContext, Future}

class DemoInitializerTest extends PlaySpec with AsyncHelper with MockitoSugar {

  private def getFixture = {
    val config = mock[Config]
    val groupDao = mock[GroupDao]
    val projectDao = mock[ProjectDao]
    val projectRelationDao = mock[ProjectRelationDao]
    val formDao = mock[FormDao]
    Fixture(config, groupDao, projectDao, projectRelationDao, formDao)
  }

  private case class Fixture(
    config: Config,
    groupDao: GroupDao,
    projectDao: ProjectDao,
    projectRelationDao: ProjectRelationDao,
    formDao: FormDao
  ) {
    def initializer = new DemoInitializer(
      config,
      new DemoParser(),
      new DemoService(groupDao, projectDao, projectRelationDao, formDao, ExecutionContext.Implicits.global),
      ExecutionContext.Implicits.global
    )
  }

  "Demo Initializer" should {

    "parse YAML file" in {
      val fixture = getFixture
      def emptyList[A] = ListWithTotal[A](Seq.empty)

      implicit val listMeta: ListMeta = ListMeta(WithPages(size = 1, number = 1), Sorting(Nil))

      when(fixture.config.cleanStart).thenReturn(false)
      when(fixture.config.demoPath).thenReturn("demo/ru.yml")

      when(fixture.groupDao.getList()).thenReturn(Future.successful(emptyList[Group]))
      when(fixture.projectDao.getList()).thenReturn(emptyList[Project].toFuture)
      when(fixture.projectRelationDao.getList()).thenReturn(emptyList[Relation].toFuture)
      when(fixture.formDao.getList()).thenReturn(emptyList[FormShort].toFuture)

      wait(fixture.initializer.demoData)
    }

    "log DB error" in {
      val fixture = getFixture

      implicit val listMeta: ListMeta = ListMeta(WithPages(size = 1, number = 1), Sorting(Nil))

      when(fixture.config.cleanStart).thenReturn(false)
      when(fixture.config.demoPath).thenReturn("demo/ru.yml")

      when(fixture.groupDao.getList()).thenReturn(Future.failed(new RuntimeException("message")))

      wait(fixture.initializer.demoData)
    }
  }
}
