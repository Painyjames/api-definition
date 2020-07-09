/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.apidefinition.models.wiremodel

import org.raml.v2.api.model.v10.datamodel.{ExampleSpec => RamlExampleSpec, TypeDeclaration => RamlTypeDeclaration}
import org.raml.v2.api.model.v10.datamodel.{StringTypeDeclaration => RamlStringTypeDeclaration}
import scala.collection.JavaConverters._
import  RamlSyntax._

case class DocumentationItem(title: String, content: String)

case class SecurityScheme(`type`: String, scope: Option[String])

case class Response(
  code: String,
  body: List[TypeDeclaration],
  headers: List[TypeDeclaration],
  description: Option[String])

case class Group(name: String, description: String)

case class TypeDeclaration(
  name: String,
  displayName: String,
  `type`: String,
  required: Boolean,
  description: Option[String],
  examples: List[ExampleSpec],
  enumValues: List[String],
  pattern: Option[String]){
    val example : Option[ExampleSpec] = examples.headOption
  }

object TypeDeclaration {
  def apply(td: RamlTypeDeclaration): TypeDeclaration = {
    val examples =
      if(td.example != null)
        List(ExampleSpec(td.example))
      else
        td.examples.asScala.toList.map(ExampleSpec.apply)

    val enumValues = td match {
      case t: RamlStringTypeDeclaration => t.enumValues().asScala.toList
      case _                            => List()
    }

    val patterns = td match {
      case t: RamlStringTypeDeclaration => Some(t.pattern())
      case _                            => None
    }

    TypeDeclaration(
      td.name,
      SafeValueAsString(td.displayName),
      td.`type`,
      td.required,
      SafeValue(td.description),
      examples,
      enumValues,
      patterns
    )
  }
}

case class ExampleSpec(
  description: Option[String],
  documentation: Option[String],
  code: Option[String],
  value: Option[String]
)

object ExampleSpec {
  import RamlSyntax._

  def apply(example : RamlExampleSpec) : ExampleSpec = {

    val description: Option[String] = {
      example.structuredValue.property("description", "value")
    }

    val documentation: Option[String] = {
      example.annotation("(documentation)")
    }

    val code: Option[String] = {
      example.structuredValue.property("value", "code")
      .orElse(example.structuredValue.property("code"))
    }

    val value: Option[String] = {
      example.structuredValue.property("value")
      .orElse(SafeValue(example))
    }

    ExampleSpec(description, documentation, code, value)
  }
}

case class ApiSpecification (
  title: String,
  version: String,
  deprecationMessage: Option[String],
  documentationItems: List[DocumentationItem],
  resourceGroups: List[ResourceGroup],
  types: List[TypeDeclaration],
  isFieldOptionalityKnown: Boolean
)

object ApiSpecification {
  def apply(raml: RAML.RAML) : ApiSpecification = {

    def title: String = SafeValueAsString(raml.title)

    def version: String = raml.version.value

    def deprecationMessage: Option[String] = raml.annotation("(deprecationMessage)")

    def documentationItems: List[DocumentationItem] =
      raml.documentation.asScala.toList.map(item => DocumentationItem(
        SafeValueAsString(item.title), SafeValueAsString(item.content)
      ))

    def output: List[Output] = raml.resources.asScala.toList.map(Resource.process)

    lazy val resources = output.map(_.resource)

    lazy val groupMap = Output.flatten(output.map(_.groupMap))

    def resourceGroups: List[ResourceGroup] = ResourceGroup.generateFrom(resources, groupMap)

    def types: List[TypeDeclaration] = (raml.types.asScala.toList ++ raml.uses.asScala.flatMap(_.types.asScala)).map(TypeDeclaration.apply)

    def isFieldOptionalityKnown: Boolean = !raml.hasAnnotation("(fieldOptionalityUnknown)")

    ApiSpecification(
      title,
      version,
      deprecationMessage,
      documentationItems,
      resourceGroups,
      types,
      isFieldOptionalityKnown
    )
  }
}

object SafeValue {
  // Handle nulls from RAML
  // Convert nulls and empty strings to Option.None

  def apply(nullableString: String): Option[String] = {
    Option(nullableString).filter(_.nonEmpty)
  }

  def apply(nullableObj: {def value(): String}): Option[String] = {
    Option(nullableObj).flatMap(obj => Option(obj.value())).filter(_.nonEmpty)
  }
}

object SafeValueAsString {
  // Handle nulls from RAML

  def apply(nullableString: String): String = SafeValue(nullableString).getOrElse("")

  def apply(nullableObj: {def value(): String}): String = SafeValue(nullableObj).getOrElse("")

}