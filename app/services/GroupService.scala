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

package services

import javax.inject.{Inject, Singleton}

import models.ListWithTotal
import models.dao.{GroupDao, ProjectDao, ProjectRelationDao, UserGroupDao}
import models.group.{Group => GroupModel}
import models.project.Project
import org.davidbild.tristate.Tristate
import utils.errors.{ConflictError, ExceptionHandler, NotFoundError}
import utils.implicits.FutureLifting._
import utils.listmeta.ListMeta

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scalaz.EitherT

/**
  * Group service.
  */
@Singleton
class GroupService @Inject() (
  protected val groupDao: GroupDao,
  protected val userGroupDao: UserGroupDao,
  protected val relationDao: ProjectRelationDao,
  protected val projectDao: ProjectDao,
  implicit val ec: ExecutionContext
) extends ServiceResults[GroupModel] {

  /**
    * Returns group by ID
    */
  def getById(id: Long): SingleResult = {
    groupDao
      .findById(id)
      .liftRight {
        NotFoundError.Group(id)
      }
  }

  /**
    * Returns groups list filtered by given criteria.
    *
    * @param parentId parent ID
    * @param userId   only groups of user
    * @param name     part of group name
    */
  def list(
    parentId: Tristate[Long],
    userId: Option[Long],
    name: Option[String],
    levels: Option[String]
  )(implicit meta: ListMeta): ListResult = {

    val levelsParsed = levels.flatMap { l =>
      Try(l.split(",")).map(_.toSeq.map(_.toInt)).toOption
    }

    groupDao
      .getList(
        optId = None,
        optParentId = parentId,
        optUserId = userId,
        optName = name,
        optLevels = levelsParsed
      )
      .lift
  }

  def listByUserId(userId: Long): ListResult = {

    def getParents(group: GroupModel): Future[Seq[GroupModel]] = {
      group.parentId match {
        case None => Nil.toFuture
        case Some(parentId) =>
          groupDao.findById(parentId).flatMap { maybeParent =>
            maybeParent.fold(Seq.empty[GroupModel].toFuture)(parent => getParents(parent).map(_ :+ parent))
          }
      }
    }

    EitherT.rightT {
      groupDao.getList(optUserId = Some(userId)).flatMap { groups =>
        Future
          .sequence(groups.data.map(getParents))
          .map { result =>
            ListWithTotal((result.flatten ++ groups.data).distinct)
          }
      }
    }
  }

  /**
    * Creates new group.
    *
    * @param group group model
    */
  def create(group: GroupModel): SingleResult = {
    for {
      _ <- validateParentId(group)
      created <- groupDao.create(group).lift(ExceptionHandler.sql)
    } yield created
  }

  /**
    * Updates group.
    *
    * @param draft group draft
    */
  def update(draft: GroupModel): SingleResult = {
    for {
      _ <- getById(draft.id)
      _ <- validateParentId(draft)

      updated <- groupDao.update(draft).lift(ExceptionHandler.sql)
    } yield updated
  }

  /**
    * Removes group.
    *
    * @param id group ID
    */
  def delete(id: Long): UnitResult = {

    def getConflictedEntities = {
      def getForGroup(groupId: Long) = {
        for {
          projects <- projectDao.getList(optGroupAuditorId = Some(groupId))
          relationsWithGroupFrom <- relationDao.getList(optGroupFromId = Some(groupId))
          relationsWithGroupTo <- relationDao.getList(optGroupToId = Some(groupId))
        } yield {
          projects.data.map(_.toNamedEntity) ++
            relationsWithGroupFrom.data.map(_.project) ++
            relationsWithGroupTo.data.map(_.project)
        }
      }

      for {
        childrenIds <- groupDao.findChildrenIds(id)
        allProjects <- Future.traverse(childrenIds :+ id)(getForGroup)
      } yield {
        ConflictError.getConflictedEntitiesMap(
          Project.namePlural -> allProjects.flatten.distinct
        )
      }
    }

    for {
      _ <- getById(id)

      conflictedEntities <- getConflictedEntities.lift
      _ <- ensure(conflictedEntities.isEmpty) {
        ConflictError.General(Some(GroupModel.nameSingular), conflictedEntities)
      }

      _ <- groupDao.delete(id).lift
    } yield ()
  }

  /**
    * Check if can set parent ID for group.
    * Possible errors: missed parent, self-reference, circular reference.
    *
    * @param group group model
    * @return either error or unit
    */
  private def validateParentId(group: GroupModel): UnitResult = {
    val groupIsNew = group.id == 0

    group.parentId match {
      case None => ().lift
      case Some(parentId) =>
        for {
          _ <- ensure(groupIsNew || parentId != group.id) {
            ConflictError.Group.ParentId(group.id)
          }

          _ <- getById(parentId)

          isCircularReference <- {
            !groupIsNew.toFuture && groupDao.findChildrenIds(group.id).map(_.contains(parentId))
          }.lift

          _ <- ensure(!isCircularReference) {
            ConflictError.Group.CircularReference(group.id, parentId)
          }
        } yield ()
    }
  }
}
