package dev.profunktor.valkey4cats.arguments

/** Unit of distance for geospatial commands */
sealed trait GeoUnit

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
case class GeoPosition(longitude: Double, latitude: Double)

/** Conditional mode for GEOADD command */
sealed trait GeoAddCondition

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
)

/** Origin point for GEOSEARCH commands */
sealed trait GeoSearchFrom[K]

object GeoSearchFrom {

  /** Search from the position of an existing member */
  case class FromMember[K](member: K) extends GeoSearchFrom[K]

  /** Search from an explicit longitude/latitude coordinate */
  case class FromCoord[K](position: GeoPosition) extends GeoSearchFrom[K]
}

/** Shape used to bound a GEOSEARCH query */
sealed trait GeoSearchBy

object GeoSearchBy {

  /** Search within a circular area defined by radius and unit */
  case class ByRadius(radius: Double, unit: GeoUnit) extends GeoSearchBy

  /** Search within a rectangular area defined by width, height, and unit */
  case class ByBox(width: Double, height: Double, unit: GeoUnit)
      extends GeoSearchBy
}

/** Sort order for result sets */
sealed trait SortOrder

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
)
