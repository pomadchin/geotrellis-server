/*
 * Copyright 2019 Azavea
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

package geotrellis.server.ogc.wcs

import geotrellis.server.ogc.params.ParamError
import geotrellis.server.ogc.ows.OwsDataRecord
import geotrellis.server.utils._

import org.backuity.ansi.AnsiFormatter.FormattedHelper
import org.http4s.scalaxml._
import org.http4s._
import org.http4s.dsl.io._
import cats.effect._
import cats.data.Validated
import cats.syntax.option._
import opengis.ows.{AllowedValues, AnyValue, DomainType, ValueType}
import org.log4s.getLogger
import opengis._
import scalaxb._

import java.net._

class WcsView(wcsModel: WcsModel, serviceUrl: URL) {
  val logger = getLogger

  val extendedRGBParameters: List[DomainType] = {
    val channels = "Red" :: "Green" :: "Blue" :: Nil

    def clamp(band: String): List[DomainType] = DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"clampMin$band")
      )
    ) :: DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"clampMax$band")
      )
    ) :: Nil

    def normalize(band: String): List[DomainType] = DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"normalizeOldMin$band")
      )
    ) :: DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"normalizeOldMax$band")
      )
    ) :: DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"normalizeNewMin$band")
      )
    ) :: DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"normalizeNewMax$band")
      )
    ) :: Nil

    def rescale(band: String): List[DomainType] = DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"rescaleNewMin$band")
      )
    ) :: DomainType(
      possibleValuesOption1 = OwsDataRecord(AnyValue()),
      attributes = Map(
        "@name" -> DataRecord(s"rescaleNewMax$band")
      )
    ) :: Nil

    channels.flatMap { b => clamp(b) ::: normalize(b) ::: rescale(b) }
  }

  val extendedParameters: List[DomainType] = DomainType(
    possibleValuesOption1 = OwsDataRecord(
      AllowedValues(
        OwsDataRecord(
          ValueType("all")
        ) :: OwsDataRecord(
          ValueType("data")
        ) :: OwsDataRecord(
          ValueType("nodata")
        ) :: Nil)
    ),
    DefaultValue = ValueType("all").some,
    attributes = Map(
      "@name" -> DataRecord("target")
    )
  ) :: DomainType(
    possibleValuesOption1 = OwsDataRecord(AnyValue()),
    attributes = Map(
      "@name" -> DataRecord("zFactor")
    )
  ) :: DomainType(
    possibleValuesOption1 = OwsDataRecord(AnyValue()),
    attributes = Map(
      "@name" -> DataRecord("azimuth")
    )
  ) :: DomainType(
    possibleValuesOption1 = OwsDataRecord(AnyValue()),
    attributes = Map(
      "@name" -> DataRecord("altitude")
    )
  ) :: Nil

  private def handleError[Result: EntityEncoder[IO, *]](result: Either[Throwable, Result]): IO[Response[IO]] = result match {
    case Right(res) =>
      logger.info(s"response ${res.toString}")
      Ok(res)
    case Left(err) =>
      logger.error(err.stackTraceString)
      InternalServerError(err.stackTraceString)
  }

  private val getCoverage = new GetCoverage(wcsModel)

  def responseFor(req: Request[IO])(implicit cs: ContextShift[IO]): IO[Response[IO]] = {
    WcsParams(req.multiParams) match {
      case Validated.Invalid(errors) =>
        val msg = ParamError.generateErrorMessage(errors.toList)
        logger.warn(s"""Error parsing parameters: ${msg}""")
        BadRequest(s"""Error parsing parameters: ${msg}""")

      case Validated.Valid(wcsParams) =>
        wcsParams match {
          case _: GetCapabilitiesWcsParams =>
            logger.debug(ansi"%bold{GetCapabilities: $serviceUrl}")
            Ok(new CapabilitiesView(wcsModel, serviceUrl, extendedParameters ::: extendedRGBParameters).toXML)

          case p: DescribeCoverageWcsParams =>
            logger.debug(ansi"%bold{DescribeCoverage: ${req.uri}}")
            Ok(CoverageView(wcsModel, serviceUrl, p).toXML)

          case p: GetCoverageWcsParams =>
            logger.debug(ansi"%bold{GetCoverage: ${req.uri}}")
            for {
              getCoverage <- getCoverage.build(p).attempt
              result <- handleError(getCoverage)
            } yield {
              logger.debug(s"getcoverage result: $result")
              result
            }
        }
    }
  }
}
