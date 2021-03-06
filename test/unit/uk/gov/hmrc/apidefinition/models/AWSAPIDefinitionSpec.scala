/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.uk.gov.hmrc.apidefinition.models

import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.apidefinition.models.AWSAPIDefinition.awsApiGatewayName
import uk.gov.hmrc.apidefinition.models._
import uk.gov.hmrc.play.test.UnitSpec

class AWSAPIDefinitionSpec extends UnitSpec with MockitoSugar {


  "awsApiGatewayName" should {

    "replace '/' in context with '--'" in {

      val apiDefinition = mock[APIDefinition]
      when(apiDefinition.context).thenReturn("my/calendar")

      awsApiGatewayName("1.0", apiDefinition) shouldBe "my--calendar--1.0"
    }

  }

}
