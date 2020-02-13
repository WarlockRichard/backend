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
import controllers.api.user.ApiUser
import models.ListWithTotal
import models.user.User
import org.davidbild.tristate.Tristate
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.UserService
import silhouette.DefaultEnv
import testutils.fixture.UserFixture
import testutils.generator.{TristateGenerator, UserGenerator}
import utils.errors.NotFoundError
import utils.listmeta.ListMeta

import scala.concurrent.ExecutionContext

/**
  * Test for user controller.
  */
class UserControllerTest extends BaseControllerTest with UserGenerator with TristateGenerator {

  private case class TestFixture(
    silhouette: Silhouette[DefaultEnv],
    userServiceMock: UserService,
    controller: UserController
  )

  private def getFixture(environment: FakeEnvironment[DefaultEnv]) = {
    val silhouette = getSilhouette(environment)
    val userServiceMock = mock[UserService]
    val controller = new UserController(silhouette, userServiceMock, cc, ExecutionContext.Implicits.global)
    TestFixture(silhouette, userServiceMock, controller)
  }

  private val admin = UserFixture.admin

  "GET /admin/users/id" should {
    "return not found if user not found" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.userServiceMock.getById(id)).thenReturn(toErrorResult[User](NotFoundError.User(id)))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id).apply(request)
        status(response) mustBe NOT_FOUND
      }
    }

    "return json with user" in {
      forAll { (id: Long, user: User) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.userServiceMock.getById(id)).thenReturn(toSuccessResult(user))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id)(request)
        status(response) mustBe OK
        val userJson = contentAsJson(response)
        userJson mustBe Json.toJson(ApiUser(user))
      }
    }
  }

  "GET /admin/users" should {
    "return users list from service" in {
      forAll {
        (
          role: Option[User.Role],
          st: Option[User.Status],
          groupId: Tristate[Long],
          name: Option[String],
          total: Int,
          users: Seq[User]
        ) =>
          val env = fakeEnvironment(admin)
          val fixture = getFixture(env)
          when(fixture.userServiceMock.list(role, st, groupId, name)(ListMeta.default))
            .thenReturn(toSuccessResult(ListWithTotal(total, users)))
          val request = authenticated(FakeRequest(), env)

          val response = fixture.controller
            .getList(role.map(ApiUser.ApiRole(_)), st.map(ApiUser.ApiStatus(_)), groupId, name)(request)

          status(response) mustBe OK
          val usersJson = contentAsJson(response)
          val expectedJson = Json.toJson(
            Response.List(Response.Meta(total, ListMeta.default), users.map(ApiUser(_)))
          )
          usersJson mustBe expectedJson
      }
    }
    "return forbidden for non admin user" in {
      val env = fakeEnvironment(admin.copy(role = User.Role.User))
      val fixture = getFixture(env)
      val request = authenticated(FakeRequest(), env)
      val response = fixture.controller.getList(None, None, Tristate.Unspecified, None).apply(request)

      status(response) mustBe FORBIDDEN
    }
  }

  "PUT /admin/users" should {
    "update users" in {
      forAll { (id: Long, user: User) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        val userWithId = user.copy(id = id, pictureName = None)
        when(fixture.userServiceMock.update(userWithId)(admin)).thenReturn(toSuccessResult(userWithId))
        val request = authenticated(
          FakeRequest("POST", "/users")
            .withBody[ApiUser](ApiUser(user))
            .withHeaders(CONTENT_TYPE -> "application/json"),
          env
        )

        val response = fixture.controller.update(id).apply(request)
        val responseJson = contentAsJson(response)
        val expectedJson = Json.toJson(ApiUser(userWithId))

        status(response) mustBe OK
        responseJson mustBe expectedJson
      }
    }
  }

  "DELETE /admin/users" should {
    "delete users" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.userServiceMock.delete(id)).thenReturn(toSuccessResult(()))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.delete(id)(request)
        status(response) mustBe NO_CONTENT
      }
    }
  }
}
