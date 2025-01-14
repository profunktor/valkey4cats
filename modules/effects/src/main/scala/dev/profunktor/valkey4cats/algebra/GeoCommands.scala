package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{
  GeoAddOptions,
  GeoPosition,
  GeoSearchBy,
  GeoSearchFrom,
  GeoSearchResultOptions,
  GeoUnit
}
import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Geospatial index commands (GEOADD, GEODIST, GEOHASH, GEOPOS, GEOSEARCH) */
trait GeoCommands[F[_], K, V] {

  /** Add one or more geospatial items (longitude, latitude, member) to the specified key.
    *
    * @param key The key of the sorted set
    * @param members Map of member to GeoPosition (longitude, latitude)
    * @return The number of elements added to the sorted set (not including updated elements)
    */
  def geoAdd(
      key: K,
      members: Map[V, GeoPosition]
  ): F[ValkeyResponse[Long]]

  /** Add one or more geospatial items with options.
    *
    * @param key The key of the sorted set
    * @param members Map of member to GeoPosition
    * @param options GeoAdd options (condition, changed flag)
    * @return The number of elements added (or changed, if CH option is set)
    */
  def geoAdd(
      key: K,
      members: Map[V, GeoPosition],
      options: GeoAddOptions
  ): F[ValkeyResponse[Long]]

  /** Return the distance between two members in the geospatial index.
    *
    * @param key The key of the sorted set
    * @param member1 First member
    * @param member2 Second member
    * @return The distance, or None if one or both members are missing
    */
  def geoDist(
      key: K,
      member1: V,
      member2: V
  ): F[ValkeyResponse[Option[Double]]]

  /** Return the distance between two members in the specified unit.
    *
    * @param key The key of the sorted set
    * @param member1 First member
    * @param member2 Second member
    * @param unit The unit of distance
    * @return The distance, or None if one or both members are missing
    */
  def geoDist(
      key: K,
      member1: V,
      member2: V,
      unit: GeoUnit
  ): F[ValkeyResponse[Option[Double]]]

  /** Return valid Geohash strings representing the position of one or more members.
    *
    * @param key The key of the sorted set
    * @param members The members to get geohashes for
    * @return List of geohash strings (None for members that don't exist)
    */
  def geoHash(key: K, members: V*): F[ValkeyResponse[List[Option[String]]]]

  /** Return the positions (longitude, latitude) of one or more members.
    *
    * @param key The key of the sorted set
    * @param members The members to get positions for
    * @return List of positions (None for members that don't exist)
    */
  def geoPos(
      key: K,
      members: V*
  ): F[ValkeyResponse[List[Option[GeoPosition]]]]

  /** Return the members of a sorted set populated with geospatial information,
    * which are within the specified area.
    *
    * @param key The key of the sorted set
    * @param from The origin to search from (member or coordinates)
    * @param by The shape to search by (radius or box)
    * @return List of members within the specified area
    */
  def geoSearch(
      key: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy
  ): F[ValkeyResponse[List[V]]]

  /** Return the members of a sorted set within the specified area, with result options.
    *
    * @param key The key of the sorted set
    * @param from The origin to search from
    * @param by The shape to search by
    * @param resultOptions Sort order, count limit, and ANY flag
    * @return List of members within the specified area
    */
  def geoSearch(
      key: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy,
      resultOptions: GeoSearchResultOptions
  ): F[ValkeyResponse[List[V]]]

  /** Search for members within an area and store the results in a destination key.
    *
    * @param destination The key to store results in
    * @param source The key of the sorted set to search
    * @param from The origin to search from
    * @param by The shape to search by
    * @return The number of elements stored in the destination
    */
  def geoSearchStore(
      destination: K,
      source: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy
  ): F[ValkeyResponse[Long]]

  /** Search for members within an area and store the results with options.
    *
    * @param destination The key to store results in
    * @param source The key of the sorted set to search
    * @param from The origin to search from
    * @param by The shape to search by
    * @param resultOptions Sort order, count limit
    * @return The number of elements stored in the destination
    */
  def geoSearchStore(
      destination: K,
      source: K,
      from: GeoSearchFrom[K],
      by: GeoSearchBy,
      resultOptions: GeoSearchResultOptions
  ): F[ValkeyResponse[Long]]
}
