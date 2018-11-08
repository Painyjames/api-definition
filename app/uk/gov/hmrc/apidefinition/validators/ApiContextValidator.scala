/*
 * Copyright 2018 HM Revenue & Customs
 *
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

package uk.gov.hmrc.apidefinition.validators

import cats.data.Validated.Invalid
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.apidefinition.models.APIDefinition
import uk.gov.hmrc.apidefinition.services.APIDefinitionService

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.matching.Regex

@Singleton
class ApiContextValidator @Inject()(apiDefinitionService: APIDefinitionService) extends Validator[String] {

  private val contextRegex: Regex = "^[a-zA-Z0-9_\\-\\/]+$".r

  def validate(errorContext: String, apiDefinition: APIDefinition)(implicit context: String): Future[HMRCValidated[String]] = {
    val validated = validateThat(_.nonEmpty, _ => s"Field 'context' should not be empty $errorContext").andThen(validateContext(errorContext)(_))
    validated match {
      case Invalid(_) => successful(validated)
      case _ => validateFieldNotAlreadyUsed(apiDefinitionService.fetchByContext(apiDefinition.context),
        s"Field 'context' must be unique $errorContext")(context, apiDefinition)
    }
  }

  private def validateContext(errorContext: String)(implicit context: String): HMRCValidated[String] = {
    (
      validateThat(!_.startsWith("/"), _ => s"Field 'context' should not start with '/' $errorContext"),
      validateThat(!_.endsWith("/"), _ => s"Field 'context' should not end with '/' $errorContext"),
      validateThat(!_.contains("//"), _ => s"Field 'context' should not have empty path segments $errorContext"),
      validateThat(_.matches(contextRegex), _ => s"Field 'context' should match regular expression '$contextRegex' $errorContext")
    ).mapN((_,_,_,_) => context)
  }

}