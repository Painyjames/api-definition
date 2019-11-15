/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.validators

import cats.data.Validated.{Invalid, Valid}
import org.mockito.Mockito.{verifyZeroInteractions, when}
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.apidefinition.repository.APIDefinitionRepository
import uk.gov.hmrc.apidefinition.services.APIDefinitionService
import uk.gov.hmrc.apidefinition.validators.ApiContextValidator
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.successful

class ApiContextValidatorSpec extends UnitSpec with MockitoSugar {

  trait Setup {
    def testAPIDefinition(serviceName: String = "money-service", context: String = "money") =
      APIDefinition(
        serviceName = serviceName,
        serviceBaseUrl = "http://www.money.com",
        name = "Money API",
        description = "API for checking payments",
        context = context,
        versions =
          Seq(
            APIVersion(
              "1.0",
              APIStatus.PROTOTYPED,
              Some(PublicAPIAccess()),
              Seq(Endpoint("/today", "Get Today's Date", HttpMethod.GET, AuthType.NONE, ResourceThrottlingTier.UNLIMITED)))),
        requiresTrust = Some(false))

    def fetchByContextWillReturn(context: String, apiDefinitionToReturn: Option[APIDefinition]) =
      when(mockAPIDefinitionService.fetchByContext(context)).thenReturn(successful(apiDefinitionToReturn))

    def fetchByServiceNameWillReturn(serviceName: String, apiDefinitionToReturn: Option[APIDefinition]) =
      when(mockAPIDefinitionRepository.fetchByServiceName(serviceName)).thenReturn(successful(apiDefinitionToReturn))

    def verifyValidationPassed(result: validatorUnderTest.HMRCValidated[String], expectedContext: String) = {
      result.isValid should be (true)
      val Valid(validatedContext) = result
      validatedContext should be (expectedContext)
    }

    def verifyValidationFailed(result: validatorUnderTest.HMRCValidated[String], expectedErrors: Seq[String]) = {
      val Invalid(errors) = result
      errors.size should be (expectedErrors.size)
      errors.toList should contain allElementsOf expectedErrors
    }

    val mockAPIDefinitionService: APIDefinitionService = mock[APIDefinitionService]
    val mockAPIDefinitionRepository: APIDefinitionRepository = mock[APIDefinitionRepository]

    val validatorUnderTest: ApiContextValidator = new ApiContextValidator(mockAPIDefinitionService, mockAPIDefinitionRepository)
  }

  "ApiContextValidator" should {
    lazy val errorContext: String = "for API"

    "pass validation for new API with legitimate context" in new Setup {
      lazy val context: String = "individuals/money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "pass validation for existing API with legitimate context" in new Setup {
      lazy val context: String = "money"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      fetchByContextWillReturn(context, Some(apiDefinition))
      fetchByServiceNameWillReturn(serviceName, Some(apiDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationPassed(result, context)
    }

    "fail if context is empty" in new Setup {
      lazy val context: String = ""
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not be empty $errorContext"))
    }

    "fail validation when the context starts with '/' " in new Setup {
      lazy val context: String = "/hi"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not start with '/' $errorContext"))
    }

    "fail validation when the context ends with '/' " in new Setup {
      lazy val context: String = "hi/"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not end with '/' $errorContext"))
    }

    "fail validation when the context contains '//' " in new Setup {
      val context: String = "hi//aloha"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
      verifyValidationFailed(result, Seq(s"Field 'context' should not have empty path segments $errorContext"))
    }

    val prohibitedChars = List(
      ' ', '@', '%', '£', '*', '\\', '|', '$', '~', '^', ';', '=', '\'',
      '<', '>', '"', '?', '!', ',', '.', ':', '&', '[', ']', '(' ,')'
    )
    ('{' :: '}' :: prohibitedChars).foreach { char: Char =>
      s"fail validation if the API contains '$char' in the context" in new Setup {
        lazy val badContext = s"my-context_$char"
        lazy val apiDefinition: APIDefinition = testAPIDefinition(context = badContext)

        val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(badContext))

        verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)
        verifyValidationFailed(result, Seq(s"Field 'context' should match regular expression '^[a-zA-Z0-9_\\-\\/]+$$' $errorContext"))
      }
    }

    "fail validation when context has been changed" in new Setup {
      lazy val context: String = "individuals/money"
      lazy val oldContext: String = "individuals/old-money"
      lazy val serviceName: String = "money-service"
      val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)
      val oldAPIDefinition: APIDefinition = testAPIDefinition(serviceName, oldContext)

      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, Some(oldAPIDefinition))

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must not be changed $errorContext"))
    }

    "fail validation when context already exist for another API" in new Setup {
      lazy val context: String = "money"
      lazy val serviceName: String = "money-service"
      lazy val otherServiceName: String = "other-service"

      val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)
      val otherAPIDefinition: APIDefinition = testAPIDefinition(otherServiceName, context)

      fetchByContextWillReturn(context, Some(otherAPIDefinition))
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must be unique $errorContext"))
    }

    "accumulate multiple validation errors together" in new Setup {
      lazy val context: String = "/hi//there/"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(context = context)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyZeroInteractions(mockAPIDefinitionService, mockAPIDefinitionRepository)

      verifyValidationFailed(
        result,
        Seq(
          s"Field 'context' should not start with '/' $errorContext",
          s"Field 'context' should not end with '/' $errorContext",
          s"Field 'context' should not have empty path segments $errorContext"))
    }

    val permittedTopLevelContexts = List("agents", "customs", "mobile", "individuals", "organisations", "test")
    permittedTopLevelContexts.foreach(topLevelContext =>
      s"pass validation with a top level context of $topLevelContext" in new Setup {
        lazy val context: String = s"$topLevelContext/foo"
        lazy val serviceName: String = "money-service"
        lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

        fetchByContextWillReturn(context, None)
        fetchByServiceNameWillReturn(serviceName, None)

        val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

        verifyValidationPassed(result, context)
      }
    )

    "fail validation when new API does not use correct top level context" in new Setup {
      lazy val context: String = "foo/bar"
      lazy val serviceName: String = "money-service"
      lazy val apiDefinition: APIDefinition = testAPIDefinition(serviceName, context)

      val formattedTopLevelContexts: String = permittedTopLevelContexts.sorted.mkString("'","', '", "'")

      fetchByContextWillReturn(context, None)
      fetchByServiceNameWillReturn(serviceName, None)

      val result: validatorUnderTest.HMRCValidated[String] = await(validatorUnderTest.validate(errorContext, apiDefinition)(context))

      verifyValidationFailed(result, Seq(s"Field 'context' must start with one of $formattedTopLevelContexts $errorContext"))
    }
  }
}
