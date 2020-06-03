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

package geotrellis.server.ogc.conf

import cats.effect.IO
import geotrellis.server.ogc
import geotrellis.server.ogc.{MapAlgebraSource, OgcSource, RasterOgcSource, ows}
import geotrellis.server.ogc.wms.WmsParentLayerMeta
import geotrellis.server.ogc.wmts.GeotrellisTileMatrixSet
import geotrellis.stac.raster._
import geotrellis.store.query.Repository
import org.http4s.client.Client

/**
 * Each service has its own unique configuration requirements (see the below instances)
 *  but share certain basic behaviors related to layer management. This trait encodes
 *  those expectations
 */
sealed trait OgcServiceConf {
  def layerDefinitions: List[OgcSourceConf]
  def layerSources(rasterOgcSources: List[RasterOgcSource]): Repository[List, OgcSource] = {
    val rasterLayers: List[RasterOgcSource] =
      layerDefinitions.collect { case rsc @ RasterSourceConf(_, _, _, _, _, _, _) => rsc.toLayer }
    val mapAlgebraLayers: List[MapAlgebraSource] =
      layerDefinitions.collect { case masc @ MapAlgebraSourceConf(_, _, _, _, _, _, _) => masc.model(rasterOgcSources) }

    ogc.OgcSourceRepository(rasterLayers ++ mapAlgebraLayers)
  }

  def layerSourcesWithStac(rasterOgcSources: List[RasterOgcSource], client: Client[IO]): Repository[List, OgcSource] = {
    val rasterLayers: List[RasterOgcSource] =
      layerDefinitions.collect { case rsc @ RasterSourceConf(_, _, _, _, _, _, _) => rsc.toLayer }
    val mapAlgebraLayers: List[MapAlgebraSource] =
      layerDefinitions.collect { case masc @ MapAlgebraSourceConf(_, _, _, _, _, _, _) => masc.modelOpt(rasterOgcSources) }.flatten

    val stacLayers: List[StacSourceConf] = layerDefinitions.collect { case ssc @ StacSourceConf(_, _, _, _, _, _, _, _, _) => ssc }
    val mapAlgebraConfLayers: List[MapAlgebraSourceConf] = layerDefinitions.collect { case masc @ MapAlgebraSourceConf(_, _, _, _, _, _, _) => masc }

    OgcRepositories(
      ogc.OgcSourceRepository(rasterLayers ++ mapAlgebraLayers) ::
      StacOgcRepositories(stacLayers, client) ::
      MapAlgberaStacLayerOgcRepositories(mapAlgebraConfLayers, stacLayers, client) :: Nil
    )
  }
}

/** WMS Service configuration */
case class WmsConf(
  parentLayerMeta: WmsParentLayerMeta,
  serviceMetadata: opengis.wms.Service,
  layerDefinitions: List[OgcSourceConf]
) extends OgcServiceConf

/** WMTS Service configuration */
case class WmtsConf(
  serviceMetadata: ows.ServiceMetadata,
  layerDefinitions: List[OgcSourceConf],
  tileMatrixSets: List[GeotrellisTileMatrixSet]
) extends OgcServiceConf

/** WCS Service configuration */
case class WcsConf(
  serviceMetadata: ows.ServiceMetadata,
  layerDefinitions: List[OgcSourceConf]
) extends OgcServiceConf

