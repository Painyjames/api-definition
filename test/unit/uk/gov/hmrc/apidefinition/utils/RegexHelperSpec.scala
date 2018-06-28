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

package uk.gov.hmrc.apidefinition.utils

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.apidefinition.utils.RegexHelper.extractPathParameters

class RegexHelperSpec extends UnitSpec {

  "extractPathParameters()" should {

    "return an empty sequence on an empty url" in {
      extractPathParameters("") shouldBe Seq()
    }

    "return an empty sequence if the url has no path parameters" in {
      extractPathParameters("-abc_") shouldBe Seq()
    }

    "not recognise path parameters that are not wrapped with curly brackets" in {
      extractPathParameters("/hello/:friend") shouldBe Seq()
    }

    "return all path parameters sorted" in {
      extractPathParameters("welcome/{name}/{surname}/---/{city}") shouldBe Seq("name", "surname", "city")
    }
  }
}
