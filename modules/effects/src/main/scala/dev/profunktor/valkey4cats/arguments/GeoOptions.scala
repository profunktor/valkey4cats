package dev.profunktor.valkey4cats.arguments

import glide.api.models.commands.geospatial.{
  GeoUnit => GlideGeoUnit,
  GeoAddOptions => GlideGeoAddOptions,
  GeoSearchOrigin => GlideGeoSearchOrigin,
  GeoSearchShape => GlideGeoSearchShape,
  GeoSearchResultOptions => GlideGeoSearchResultOptions,
  GeospatialData => GlideGeospatialData
}
import glide.api.models.commands.{
  ConditionalChange,
  SortOrder => GlideSortOrder
}

/** Unit of distance for geospatial commands */
sealed trait GeoUnit {
  def toGlide: GlideGeoUnit = this match {
    case GeoUnit.Meters     => GlideGeoUnit.METERS
    case GeoUnit.Kilometers => GlideGeoUnit.KILOMETERS
    case GeoUnit.Miles      => GlideGeoUnit.MILES
    case GeoUnit.Feet       => GlideGeoUnit.FEET
  }
}

object GeoUnit {

  /** Distance in meters */
  case object Meters extends GeoUnit

  /** Distance in kilometers */
  case object Kilometers extends GeoUnit

  /** Distance in miles */
  case object Miles extends GeoUnit

  /** Distance in feet */
  case object Feet extends GeoUnit
}

/** A longitude/latitude coordinate pair for geospatial storage */
case class GeoPosition(longitude: Double, latitude: Double) {
  def toGlide: GlideGeospatialData =
    new GlideGeospatialData(longitude, latitude)
}

/** Conditional mode for GEOADD command */
sealed trait GeoAddCondition {
  def toGlide: ConditionalChange = this match {
    case GeoAddCondition.OnlyIfExists       => ConditionalChange.ONLY_IF_EXISTS
    case GeoAddCondition.OnlyIfDoesNotExist =>
      ConditionalChange.ONLY_IF_DOES_NOT_EXIST
  }
}

object GeoAddCondition {

  /** Only update elements that already exist (XX) */
  case object OnlyIfExists extends GeoAddCondition

  /** Only add new elements; do not update existing ones (NX) */
  case object OnlyIfDoesNotExist extends GeoAddCondition
}

/** Options for the GEOADD command including conditional mode and change tracking */
case class GeoAddOptions(
    condition: Option[GeoAddCondition] = None,
    changed: Boolean = false
) {
  def toGlide: GlideGeoAddOptions =
    (condition, changed) match {
      case (Some(c), ch) => new GlideGeoAddOptions(c.toGlide, ch)
      case (None, true)  => new GlideGeoAddOptions(true)
      case (None, false) => new GlideGeoAddOptions(false)
    }
}

/** Origin point for GEOSEARCH commands */
sealed trait GeoSearchFrom[K] {
  def toGlide(
      encode: K => glide.api.models.GlideString
  ): GlideGeoSearchOrigin.SearchOrigin = this match {
    case GeoSearchFrom.FromMember(member) =>
      new GlideGeoSearchOrigin.MemberOriginBinary(encode(member))
    case GeoSearchFrom.FromCoord(position) =>
      new GlideGeoSearchOrigin.CoordOrigin(position.toGlide)
  }
}

object GeoSearchFrom {

  /** Search from the position of an existing member */
  case class FromMember[K](member: K) extends GeoSearchFrom[K]

  /** Search from an explicit longitude/latitude coordinate */
  case class FromCoord[K](position: GeoPosition) extends GeoSearchFrom[K]
}

/** Shape used to bound a GEOSEARCH query */
sealed trait GeoSearchBy {
  def toGlide: GlideGeoSearchShape = this match {
    case GeoSearchBy.ByRadius(radius, unit) =>
      new GlideGeoSearchShape(radius, unit.toGlide)
    case GeoSearchBy.ByBox(width, height, unit) =>
      new GlideGeoSearchShape(width, height, unit.toGlide)
  }
}

object GeoSearchBy {

  /** Search within a circular area defined by radius and unit */
  case class ByRadius(radius: Double, unit: GeoUnit) extends GeoSearchBy

  /** Search within a rectangular area defined by width, height, and unit */
  case class ByBox(width: Double, height: Double, unit: GeoUnit)
      extends GeoSearchBy
}

/** Sort order for result sets */
sealed trait SortOrder {
  def toGlide: GlideSortOrder = this match {
    case SortOrder.Asc  => GlideSortOrder.ASC
    case SortOrder.Desc => GlideSortOrder.DESC
  }
}

object SortOrder {

  /** Sort from nearest to farthest (ascending) */
  case object Asc extends SortOrder

  /** Sort from farthest to nearest (descending) */
  case object Desc extends SortOrder
}

/** Options controlling sort order and count limits for GEOSEARCH results */
case class GeoSearchResultOptions(
    sortOrder: Option[SortOrder] = None,
    count: Option[Long] = None,
    any: Boolean = false
) {
  def toGlide: GlideGeoSearchResultOptions =
    (sortOrder, count, any) match {
      case (Some(order), Some(c), a) =>
        new GlideGeoSearchResultOptions(order.toGlide, c, a)
      case (Some(order), None, _) =>
        new GlideGeoSearchResultOptions(order.toGlide)
      case (None, Some(c), a) =>
        new GlideGeoSearchResultOptions(c, a)
      case (None, None, _) =>
        new GlideGeoSearchResultOptions(GlideSortOrder.ASC)
    }
}
