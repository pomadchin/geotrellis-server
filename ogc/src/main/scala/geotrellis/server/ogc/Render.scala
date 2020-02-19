/*
 * Copyright 2020 Azavea
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

package geotrellis.server.ogc

import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.render.png._

import scala.collection.mutable
import scala.util.Try

object Render {
  def rgb(mbtile: MultibandTile, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) =>
        style.renderImage(mbtile, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png =>
            OutputFormat.Png(Some(RgbPngEncoding)).render(mbtile.color())
          case OutputFormat.Jpg =>
            mbtile.color().renderJpg.bytes
          case format =>
            throw new IllegalArgumentException(s"$format is not a valid output format")
        }
    }

  def rgba(mbtile: MultibandTile, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) =>
        style.renderImage(mbtile, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png =>
            OutputFormat.Png(Some(RgbaPngEncoding)).render(mbtile.color())
          case OutputFormat.Jpg =>
            mbtile.color().renderJpg.bytes
          case format =>
            throw new IllegalArgumentException(s"$format is not a valid output format")
        }
    }

  def singleband(mbtile: MultibandTile, maybeStyle: Option[OgcStyle], format: OutputFormat, hists: List[Histogram[Double]]): Array[Byte] =
    maybeStyle match {
      case Some(style) =>
        style.renderImage(mbtile, format, hists)
      case None =>
        format match {
          case format: OutputFormat.Png =>
            format.render(mbtile.band(bandIndex = 0))
          case OutputFormat.Jpg =>
            mbtile.band(bandIndex = 0).renderJpg.bytes
          case format =>
            throw new IllegalArgumentException(s"$format is not a valid output format")
        }
    }

  def linearInterpolationBreaks(breaks: Array[Double], numStops: Int): Array[Double] = {
    val length = breaks.length
    val lengthBetween = numStops.toDouble / length // number of colors between each edge

    val listBuffer: mutable.ListBuffer[Double] = mutable.ListBuffer()
    var counter = 0
    while (counter < (length - 1)) {
      val (currentEdge, nextEdge) = breaks(counter) -> breaks(counter + 1)
      val step = (nextEdge - currentEdge) / lengthBetween
      val points: List[Double] =
        if (step == 0) List.empty[Double]
        else Range.BigDecimal.inclusive(currentEdge, nextEdge, step).map(_.toDouble).toList

      val append = if (counter == 0) points else points.tail
      listBuffer ++= append

      counter = counter + 1
    }

    listBuffer.toArray
  }
}
