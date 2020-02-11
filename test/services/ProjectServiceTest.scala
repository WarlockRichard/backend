package services

import java.sql.SQLException

import models.ListWithTotal
import models.dao.{EventDao, GroupDao, ProjectDao}
import models.event.Event
import models.project.Project
import org.mockito.Mockito._
import testutils.fixture.{ProjectFixture, UserFixture}
import testutils.generator.ProjectGenerator
import utils.errors.{ConflictError, NotFoundError}
import utils.listmeta.ListMeta

import scala.concurrent.Future

/**
  * Test for project service.
  */
class ProjectServiceTest extends BaseServiceTest with ProjectGenerator with ProjectFixture {

  private val admin = UserFixture.admin

  private case class TestFixture(
    projectDaoMock: ProjectDao,
    eventDaoMock: EventDao,
    groupDao: GroupDao,
    service: ProjectService
  )

  private def getFixture = {
    val daoMock = mock[ProjectDao]
    val eventDaoMock = mock[EventDao]
    val groupDao = mock[GroupDao]
    val service = new ProjectService(daoMock, eventDaoMock, groupDao, ec)
    TestFixture(daoMock, eventDaoMock, groupDao, service)
  }

  "getById" should {

    "return not found if project not found" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.getById(id).run)

        result mustBe left
        result.swap.toOption.get mustBe a[NotFoundError]

        verify(fixture.projectDaoMock, times(1)).findById(id)
        verifyNoMoreInteractions(fixture.projectDaoMock)
      }
    }

    "return project from db" in {
      forAll { (project: Project, id: Long) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(id)).thenReturn(toFuture(Some(project)))
        val result = wait(fixture.service.getById(id).run)

        result mustBe right
        result.toOption.get mustBe project

        verify(fixture.projectDaoMock, times(1)).findById(id)
        verifyNoMoreInteractions(fixture.projectDaoMock)
      }
    }
  }

  "list" should {
    "return list of projects from db" in {
      forAll {
        (
          eventId: Option[Long],
          groupId: Option[Long],
          projects: Seq[Project],
          total: Int
        ) =>
          val fixture = getFixture
          when(
            fixture.projectDaoMock.getList(
              optId = *,
              optEventId = eqTo(eventId),
              optGroupFromIds = *,
              optFormId = *,
              optGroupAuditorId = *,
              optEmailTemplateId = *,
              optAnyRelatedGroupId = eqTo(groupId)
            )(eqTo(ListMeta.default))
          ).thenReturn(toFuture(ListWithTotal(total, projects)))
          val result = wait(fixture.service.getList(eventId, groupId)(ListMeta.default).run)

          result mustBe right
          result.toOption.get mustBe ListWithTotal(total, projects)
      }
    }
  }

  "create" should {
    "return conflict if db exception" in {
      forAll { (project: Project) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.create(*)).thenReturn(Future.failed(new SQLException("", "2300")))
        val result = wait(fixture.service.create(project.copy(id = 0)).run)

        result mustBe left
        result.swap.toOption.get mustBe a[ConflictError]
      }
    }

    "create project in db" in {
      val project = Projects(0)

      val fixture = getFixture
      when(fixture.projectDaoMock.create(project.copy(id = 0))).thenReturn(toFuture(project))
      val result = wait(fixture.service.create(project.copy(id = 0)).run)

      result mustBe right
      result.toOption.get mustBe project
    }
  }

  "update" should {
    "return conflict if db exception" in {
      forAll { (project: Project) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(project.id)).thenReturn(toFuture(Some(project)))
        when(
          fixture.eventDaoMock.getList(
            optId = *,
            optStatus = eqTo(Some(Event.Status.InProgress)),
            optProjectId = eqTo(Some(project.id)),
            optFormId = *,
            optGroupFromIds = *,
            optUserId = *
          )(*)
        ).thenReturn(toFuture(ListWithTotal[Event](0, Nil)))
        when(fixture.projectDaoMock.update(*)).thenReturn(Future.failed(new SQLException("", "2300")))
        val result = wait(fixture.service.update(project).run)

        result mustBe left
        result.swap.toOption.get mustBe a[ConflictError]
      }
    }

    "return conflict if exists events in progress" in {
      forAll { (project: Project) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(project.id)).thenReturn(toFuture(Some(project)))
        when(
          fixture.eventDaoMock.getList(
            optId = *,
            optStatus = eqTo(Some(Event.Status.InProgress.asInstanceOf[Event.Status])),
            optProjectId = eqTo(Some(project.id)),
            optFormId = *,
            optGroupFromIds = *,
            optUserId = *
          )(*)
        ).thenReturn(toFuture(ListWithTotal[Event](1, Nil)))
        val result = wait(fixture.service.update(project).run)

        result mustBe left
        result.swap.toOption.get mustBe a[ConflictError]
      }
    }

    "return not found if project not found" in {
      forAll { (project: Project) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(project.id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.update(project).run)

        result mustBe left
        result.swap.toOption.get mustBe a[NotFoundError]

        verify(fixture.projectDaoMock, times(1)).findById(project.id)
        verifyNoMoreInteractions(fixture.projectDaoMock)
      }
    }

    "update project in db" in {
      val project = Projects(0)
      val fixture = getFixture
      when(fixture.projectDaoMock.findById(project.id)).thenReturn(toFuture(Some(project)))
      when(
        fixture.eventDaoMock.getList(
          optId = *,
          optStatus = eqTo(Some(Event.Status.InProgress)),
          optProjectId = eqTo(Some(project.id)),
          optFormId = *,
          optGroupFromIds = *,
          optUserId = *
        )(*)
      ).thenReturn(toFuture(ListWithTotal[Event](0, Nil)))
      when(fixture.projectDaoMock.update(project)).thenReturn(toFuture(project))
      val result = wait(fixture.service.update(project).run)

      result mustBe right
      result.toOption.get mustBe project
    }
  }

  "delete" should {
    "return not found if project not found" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(id)).thenReturn(toFuture(None))
        val result = wait(fixture.service.delete(id)(admin).run)

        result mustBe left
        result.swap.toOption.get mustBe a[NotFoundError]
      }
    }

    "delete project from db" in {
      forAll { (id: Long) =>
        val fixture = getFixture
        when(fixture.projectDaoMock.findById(id)).thenReturn(toFuture(Some(Projects(0))))
        when(
          fixture.eventDaoMock.getList(
            optId = *,
            optStatus = *,
            optProjectId = eqTo(Some(id)),
            optFormId = *,
            optGroupFromIds = *,
            optUserId = *
          )(*)
        ).thenReturn(toFuture(ListWithTotal[Event](0, Nil)))
        when(fixture.projectDaoMock.delete(id)).thenReturn(toFuture(1))

        val result = wait(fixture.service.delete(id)(admin).run)

        result mustBe right
      }
    }
  }
}
