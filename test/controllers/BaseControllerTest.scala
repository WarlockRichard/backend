package controllers

import com.mohiva.play.silhouette.api.actions.{SecuredAction, UnsecuredAction, UserAwareAction}
import com.mohiva.play.silhouette.api.{Env, Environment, LoginInfo, SilhouetteProvider}
import com.mohiva.play.silhouette.test._
import models.user.{User => UserModel}
import org.scalatest.ParallelTestExecution
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import silhouette.DefaultEnv
import testutils.{AsyncHelper, MockitoHelper, ServiceResultHelper}

/**
  * Base trait for controllers test.
  *
  */
trait BaseControllerTest
  extends PlaySpec
  with ScalaCheckDrivenPropertyChecks
  with GuiceOneAppPerSuite
  with AsyncHelper
  with MockitoSugar
  with ServiceResultHelper
  with MockitoHelper
  with ParallelTestExecution {

  implicit val ec = scala.concurrent.ExecutionContext.global
  val cc = stubControllerComponents()

  /**
    * Returns silhouette with given environment.
    *
    * @param env environment
    */
  def getSilhouette[E <: Env](env: Environment[E]): SilhouetteProvider[E] = {
    new SilhouetteProvider(
      env,
      app.injector.instanceOf[SecuredAction],
      app.injector.instanceOf[UnsecuredAction],
      app.injector.instanceOf[UserAwareAction]
    )
  }

  /**
    * Returns fake silhouette environment.
    *
    * @param user user to be logged in
    */
  def fakeEnvironment(user: UserModel): FakeEnvironment[DefaultEnv] =
    FakeEnvironment[DefaultEnv](Seq(fakeLoginInfo -> user))

  /**
    * Adds authentication to request.
    *
    * @param request fake request
    * @param env fake environment
    * @return authenticated request
    */
  def authenticated[A](request: FakeRequest[A], env: FakeEnvironment[DefaultEnv]): FakeRequest[A] = {
    request.withAuthenticator(fakeLoginInfo)(env)
  }

  /**
    * Fake login info for silhouette.
    */
  private val fakeLoginInfo = LoginInfo("", "")

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(sizeRange = 15, workers = 4)
}
