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
import controllers.api.event.{ApiEvent, ApiPartialEvent}
import models.ListWithTotal
import models.event.Event
import org.mockito.Mockito._
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.event.EventService
import silhouette.DefaultEnv
import testutils.fixture.UserFixture
import testutils.generator.EventGenerator
import utils.errors.NotFoundError
import utils.listmeta.ListMeta

/**
  * Test for event controller.
  */
class EventControllerTest extends BaseControllerTest with EventGenerator {

  private case class TestFixture(
    silhouette: Silhouette[DefaultEnv],
    eventServiceMock: EventService,
    controller: EventController
  )

  private def getFixture(environment: FakeEnvironment[DefaultEnv]) = {
    val silhouette = getSilhouette(environment)
    val eventServiceMock = mock[EventService]
    val controller = new EventController(silhouette, eventServiceMock, cc, ec)
    TestFixture(silhouette, eventServiceMock, controller)
  }

  private val admin = UserFixture.admin

  "GET /events/id" should {
    "return not found if event not found" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.getById(id)).thenReturn(toErrorResult[Event](NotFoundError.Event(id)))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id).apply(request)
        status(response) mustBe NOT_FOUND
      }
    }

    "return json with event" in {
      forAll { (id: Long, event: Event) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.getById(id)).thenReturn(toSuccessResult(event))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.getById(id)(request)
        status(response) mustBe OK
        val eventJson = contentAsJson(response)
        eventJson mustBe Json.toJson(ApiEvent(event)(UserFixture.admin))
      }
    }
  }

  "GET /events" should {
    "return events list from service" in {
      forAll {
        (
          projectId: Option[Long],
          optStatus: Option[Event.Status],
          total: Int,
          events: Seq[Event]
        ) =>
          val env = fakeEnvironment(admin)
          val fixture = getFixture(env)
          when(fixture.eventServiceMock.list(optStatus, projectId)(ListMeta.default))
            .thenReturn(toSuccessResult(ListWithTotal(total, events)))
          val request = authenticated(FakeRequest(), env)

          val response =
            fixture.controller.getList(optStatus.map(ApiEvent.EventStatus(_)), projectId)(request)

          status(response) mustBe OK
          val eventsJson = contentAsJson(response)
          val expectedJson = Json.toJson(
            Response.List(Response.Meta(total, ListMeta.default), events.map(ApiEvent(_)(UserFixture.admin)))
          )
          eventsJson mustBe expectedJson
      }
    }
  }

  "PUT /events" should {
    "update events" in {
      forAll { (event: Event) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.update(event.copy(isPreparing = false))).thenReturn(toSuccessResult(event))

        val partialEvent = ApiPartialEvent(
          event.description,
          event.start,
          event.end,
          event.notifications.map(ApiEvent.NotificationTime(_)(admin))
        )
        val request = authenticated(
          FakeRequest("PUT", "/events")
            .withBody[ApiPartialEvent](partialEvent)
            .withHeaders(CONTENT_TYPE -> "application/json"),
          env
        )

        val response = fixture.controller.update(event.id).apply(request)
        val responseJson = contentAsJson(response)
        val expectedJson = Json.toJson(ApiEvent(event)(UserFixture.admin))

        status(response) mustBe OK
        responseJson mustBe expectedJson
      }
    }
  }

  "POST /events" should {
    "create events" in {
      forAll { (event: Event) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.create(event.copy(id = 0, isPreparing = false)))
          .thenReturn(toSuccessResult(event))

        val partialEvent = ApiPartialEvent(
          event.description,
          event.start,
          event.end,
          event.notifications.map(ApiEvent.NotificationTime(_)(admin))
        )

        val request = authenticated(
          FakeRequest("POST", "/events")
            .withBody[ApiPartialEvent](partialEvent)
            .withHeaders(CONTENT_TYPE -> "application/json"),
          env
        )

        val response = fixture.controller.create.apply(request)
        val responseJson = contentAsJson(response)
        val expectedJson = Json.toJson(ApiEvent(event)(UserFixture.admin))

        status(response) mustBe CREATED
        responseJson mustBe expectedJson
      }
    }
  }

  "DELETE /events" should {
    "delete events" in {
      forAll { (id: Long) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.delete(id)).thenReturn(toSuccessResult(()))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.delete(id)(request)
        status(response) mustBe NO_CONTENT
      }
    }
  }

  "POST /events/id/clone" should {
    "clone event" in {
      forAll { (id: Long, event: Event) =>
        val env = fakeEnvironment(admin)
        val fixture = getFixture(env)
        when(fixture.eventServiceMock.cloneEvent(id)).thenReturn(toSuccessResult(event))
        val request = authenticated(FakeRequest(), env)

        val response = fixture.controller.cloneEvent(id)(request)
        status(response) mustBe OK
        val eventJson = contentAsJson(response)
        eventJson mustBe Json.toJson(ApiEvent(event)(UserFixture.admin))
      }
    }
  }
}
