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

package utils.errors

/**
  * Not found error.
  */
abstract class NotFoundError(
  code: String,
  message: String
) extends ApplicationError(code, message)

object NotFoundError {
  case class User(id: Long) extends NotFoundError("NOTFOUND-USER", s"Can't find user with id:$id")

  case class Group(id: Long) extends NotFoundError("NOTFOUND-GROUP", s"Can't find group with id:$id")

  case class Form(id: Long) extends NotFoundError("NOTFOUND-FORM", s"Can't find form with id:$id")

  case class Project(id: Long) extends NotFoundError("NOTFOUND-PROJECT", s"Can't find project with id:$id")

  case class ProjectRelation(id: Long) extends NotFoundError("NOTFOUND-RELATION", s"Can't find relation with id:$id")

  case class Event(id: Long) extends NotFoundError("NOTFOUND-EVENT", s"Can't find event with id:$id")

  case class EventJob(id: Long) extends NotFoundError("NOTFOUND-EVENTJOB", s"Can't find event job with id:$id")

  case class Template(id: Long) extends NotFoundError("NOTFOUND-TEMPLATE", s"Can't find template with id:$id")

  case class Assessment(eventId: Long, projectId: Long, accountId: Long)
    extends NotFoundError(
      "NOTFOUND-ASSESSMENT",
      s"There is no assessment objects for event: $eventId; project: $projectId; user: $accountId"
    )

  case class Invite(code: String) extends NotFoundError("NOTFOUND-INVITE", s"Can't find invite with code $code")

  case class ActiveProject(id: Long)
    extends NotFoundError("NOTFOUND-ACTIVEPROJECT", s"Can't find active project with id:$id")

  case class Competence(id: Long) extends NotFoundError("NOTFOUND-COMPETENCE", s"Can't find competence with id:$id")

  case class CompetenceGroup(id: Long)
    extends NotFoundError("NOTFOUND-COMPETENCEGROUP", s"Can't find competence group with id:$id")
}
