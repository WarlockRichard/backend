package controllers

import play.api.libs.json.{Json, Writes}
import play.api.mvc._
import utils.errors.{ApplicationError, ErrorHelper}


/**
  * Base class for controllers.
  */
trait BaseController extends Controller {

  /**
    * Converts given value to JSON and returns Ok result.
    *
    * @param value  value to return
    * @param writes writes to convert value to JSON
    */
  def result[T](value: T)(implicit writes: Writes[T]): Result = {
    Ok(Json.toJson(value))
  }

  /**
    * Converts given error to JSON and returns result with appropriate status code.
    *
    * @param error error to return
    */
  def result[E <: ApplicationError](error: E): Result = {
    ErrorHelper.getResult(error)
  }

  /**
    * Converts given value to either error or successful result.
    *
    * @param res    either result or error
    * @param writes writes to convert result to JSON
    */
  def result[E <: ApplicationError, T](res: => Either[E, T])(implicit writes: Writes[T]): Result = {
    res match {
      case Left(error) => result(error)
      case Right(data) => result(data)
    }
  }
}
