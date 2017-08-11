package controllers.api.assessment

import models.NamedEntity
import models.assessment.Answer
import play.api.libs.json.Json

/**
  * Partial API model for assessment form answer.
  */
case class ApiPartialFormAnswer(
  formId: Long,
  answers: Seq[ApiFormAnswer.ElementAnswer],
  isAnonymous: Boolean
) {
  def toModel = Answer.Form(
    NamedEntity(formId),
    answers.map(_.toModel).toSet,
    isAnonymous
  )
}

object ApiPartialFormAnswer {
  implicit val formReads = Json.reads[ApiPartialFormAnswer]
}
