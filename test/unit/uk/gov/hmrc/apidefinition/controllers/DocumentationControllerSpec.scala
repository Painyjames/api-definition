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

package unit.uk.gov.hmrc.apidefinition.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.HeaderNames.CONTENT_TYPE
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import uk.gov.hmrc.apidefinition.controllers.DocumentationController
import uk.gov.hmrc.apidefinition.services.DocumentationService
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DocumentationControllerSpec extends UnitSpec with ScalaFutures with MockitoSugar with WithFakeApplication {

  trait Setup {
    implicit val mat: Materializer = fakeApplication.materializer
    val request = FakeRequest()
    val documentationService = mock[DocumentationService]
    val hc = HeaderCarrier()
    val serviceName = "api-example-microservice"
    val version = "1.0"
    val resourceName = "application.raml"
    val body = Array[Byte](0x1, 0x2, 0x3)
    val contentType = "application/text"

    val underTest = new DocumentationController(documentationService)

    def theDocumentationServiceWillReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.successful(Results.Ok(body).withHeaders(CONTENT_TYPE -> contentType)))
    }

    def theDocumentationServiceWillFailToReturnTheResource = {
      when(documentationService.fetchApiDocumentationResource(anyString, anyString, anyString)(any[HeaderCarrier]))
        .thenReturn(Future.failed(new RuntimeException))
    }
  }

  trait RegistrationSetup extends Setup {
    val versions = Seq("1.0", "1.1", "2.0")
    val versionsJsonString = versions.map(v => s""""$v"""").mkString(",")
    val url = "https://abc.example.com"
    val registrationRequestBody =
      s"""{
         |  "serviceName": "$serviceName",
         |  "serviceUrl": "$url",
         |  "serviceVersions": [$versionsJsonString]
         }""".stripMargin
    val registrationRequest = FakeRequest().withBody(Json.parse(registrationRequestBody))

  }

  "fetchApiDocumentationResource" should {

    "call the service to get the resource" in new Setup {
      theDocumentationServiceWillReturnTheResource

      await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      verify(documentationService).fetchApiDocumentationResource(eqTo(serviceName), eqTo(version), eqTo(resourceName))(any[HeaderCarrier])
    }

    "return the resource with a Content-type header when the content type is known" in new Setup {
      theDocumentationServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK

      val resultData = sourceToArray(result.body.dataStream)
      resultData.toList shouldBe body.toList

      result.header.headers(CONTENT_TYPE) shouldBe contentType
    }

    "return the resource with no Content-type header when the content type is unknown" in new Setup {
      theDocumentationServiceWillReturnTheResource

      val result = await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))

      status(result) shouldBe OK

      sourceToArray(result.body.dataStream).toList shouldBe body
    }

    "fail when the service fails to return the resource" in new Setup {
      theDocumentationServiceWillFailToReturnTheResource

      intercept[RuntimeException] {
        await(underTest.fetchApiDocumentationResource(serviceName, version, resourceName)(request))
      }
    }
  }

  def sourceToArray(dataStream: Source[ByteString, _])(implicit mat: Materializer) = {
    await(dataStream
      .runWith(Sink.reduce[ByteString](_ ++ _))
      .map { r: ByteString => r.toArray[Byte] })
  }
}