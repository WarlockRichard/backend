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

import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.test.FakeEnvironment
import controllers.BaseControllerTest
import controllers.api.Response
import controllers.api.group.{ApiGroup, ApiPartialGroup}
import models.ListWithTotal
import models.group.Group
import models.user.User
import org.davidbild.tristate.Tristate
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.GroupService
import silhouette.DefaultEnv
import testutils.fixture.UserFixture
import testutils.generator.{GroupGenerator, TristateGenerator}
import utils.errors.NotFoundError
import utils.listmeta.ListMeta

import scala.concurrent.ExecutionContext

/**
  * Test for groups controller.
  */
class GroupControllerTest extends BaseControllerTest with GroupGenerator with TristateGenerator {

  private case class TestFixture(
    silhouette: Silhouette[DefaultEnv],
    groupServiceMock: GroupService,
    controller: GroupController
  )

  private def getFixture(environment: FakeEnvironment[DefaultEnv]) = {
    val silhouette = getSilhouette(environment)
    val groupServiceMock = mock[GroupService]
    val controller = new GroupController(silhouette, groupServiceMock, cc, ExecutionContext.Implicits.global)
    TestFixture(silhouette, groupServiceMock, controller)
  }

  private val admin = UserFixture.admin

  "GET /groups/id" should {
    "return not found if group not found" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.groupServiceMock.getById(id)).thenReturn(toErrorResult[Group](NotFoundError.Group(id)))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id).apply(request)
        status(response) mustBe NOT_FOUND
      }
    }

    "return json with group" in {
      forAll { (id: Long, group: Group) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.groupServiceMock.getById(id)).thenReturn(toSuccessResult(group))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id)(request)
        status(response) mustBe OK
        val groupJson = contentAsJson(response)
        groupJson mustBe Json.toJson(ApiGroup(group))
      }
    }
  }

  "GET /groups" should {
    "return groups list from service" in {
      forAll {
        (
          parentId: Tristate[Long],
          userId: Option[Long],
          name: Option[String],
          levels: Option[String],
          total: Int,
          groups: Seq[Group]
        ) =>
          val env = fakeEnvironment(admin)
          val fixture = getFixture(env)
          when(fixture.groupServiceMock.list(parentId, userId, name, levels)(ListMeta.default))
            .thenReturn(toSuccessResult(ListWithTotal(total, groups)))
          val request = authenticated(FakeRequest(), env)

          val response = fixture.controller.getList(parentId, userId, name, levels)(request)

          status(response) mustBe OK
          val groupsJson = contentAsJson(response)
          val expectedJson = Json.toJson(
            Response.List(Response.Meta(total, ListMeta.default), groups.map(ApiGroup(_)))
          )
          groupsJson mustBe expectedJson
      }
    }
    "return forbidden for non admin user" in {
      val env = fakeEnvironment(admin.copy(role = User.Role.User))
      val fixture = getFixture(env)
      val request = authenticated(FakeRequest(), env)
      val response = fixture.controller.getList(Tristate.Unspecified, None, None, None).apply(request)

      status(response) mustBe FORBIDDEN
    }
  }

  "PUT /groups" should {
    "update groups" in {
      forAll { (group: Group) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.groupServiceMock.update(group.copy(hasChildren = false, level = 0)))
          .thenReturn(toSuccessResult(group))

        val partialGroup = ApiPartialGroup(group.parentId, group.name)
        val request = authenticated(
          FakeRequest("PUT", "/groups")
            .withBody[ApiPartialGroup](partialGroup)
            .withHeaders(CONTENT_TYPE -> "application/json"),
          env
        )

        val response = fixture.controller.update(group.id).apply(request)
        val responseJson = contentAsJson(response)
        val expectedJson = Json.toJson(ApiGroup(group))

        status(response) mustBe OK
        responseJson mustBe expectedJson
      }
    }
  }

  "POST /groups" should {
    "create groups" in {
      forAll { (group: Group) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.groupServiceMock.create(group.copy(id = 0, hasChildren = false, level = 0)))
          .thenReturn(toSuccessResult(group))

        val partialGroup = ApiPartialGroup(group.parentId, group.name)
        val request = authenticated(
          FakeRequest("POST", "/groups")
            .withBody[ApiPartialGroup](partialGroup)
            .withHeaders(CONTENT_TYPE -> "application/json"),
          env
        )

        val response = fixture.controller.create.apply(request)
        val responseJson = contentAsJson(response)
        val expectedJson = Json.toJson(ApiGroup(group))

        status(response) mustBe CREATED
        responseJson mustBe expectedJson
      }
    }
  }

  "DELETE /groups" should {
    "delete groups" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.groupServiceMock.delete(id)).thenReturn(toSuccessResult(()))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.delete(id)(request)
        status(response) mustBe NO_CONTENT
      }
    }
  }
}
