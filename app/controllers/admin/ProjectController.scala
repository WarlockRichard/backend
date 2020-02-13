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

package controllers.admin

import javax.inject.{Inject, Singleton}

import com.mohiva.play.silhouette.api.Silhouette
import controllers.BaseController
import controllers.api.Response
import controllers.api.project.{ApiPartialProject, ApiProject}
import controllers.authorization.AllowedRole
import play.api.mvc.ControllerComponents
import services.ProjectService
import silhouette.DefaultEnv
import utils.implicits.FutureLifting._
import utils.listmeta.actions.ListActions
import utils.listmeta.sorting.Sorting

import scala.concurrent.ExecutionContext

/**
  * Project controller.
  */
@Singleton
class ProjectController @Inject() (
  protected val silhouette: Silhouette[DefaultEnv],
  protected val projectService: ProjectService,
  val controllerComponents: ControllerComponents,
  implicit val ec: ExecutionContext
) extends BaseController
  with ListActions {

  implicit val sortingFields = Sorting.AvailableFields("id", "name", "description")

  /**
    * Returns list of projects.
    */
  def getList(eventId: Option[Long], groupId: Option[Long]) =
    (silhouette.SecuredAction(AllowedRole.admin) andThen ListAction).async { implicit request =>
      toResult(Ok) {
        projectService
          .getList(eventId, groupId)
          .map { projects =>
            Response.List(projects)(ApiProject(_))
          }
      }
    }

  /**
    * Returns project.
    */
  def getById(id: Long) = silhouette.SecuredAction(AllowedRole.admin).async { implicit request =>
    toResult(Ok) {
      projectService
        .getById(id)
        .map(ApiProject(_))
    }
  }

  /**
    * Creates project.
    */
  def create = silhouette.SecuredAction(AllowedRole.admin).async(parse.json[ApiPartialProject]) { implicit request =>
    toResult(Created) {
      val project = request.body.toModel()
      projectService
        .create(project)
        .map(ApiProject(_))
    }
  }

  /**
    * Updates project.
    */
  def update(id: Long) = silhouette.SecuredAction(AllowedRole.admin).async(parse.json[ApiPartialProject]) {
    implicit request =>
      toResult(Ok) {
        val project = request.body.toModel(id)
        projectService
          .update(project)
          .map(ApiProject(_))
      }
  }

  /**
    * Removes project.
    */
  def delete(id: Long) = silhouette.SecuredAction(AllowedRole.admin).async { implicit request =>
    projectService
      .delete(id)
      .fold(
        error => toResult(error),
        _ => NoContent
      )
  }
}
