package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.ScanResult

/** Set commands for Valkey/Redis
  *
  * Sets are unordered collections of unique string elements.
  */
trait SetCommands[F[_], K, V] {

  /** Add the specified members to the set stored at key.
    * Members that are already a member of the set are ignored.
    *
    * @param key The key of the set
    * @param members Members to add
    * @return The number of elements that were added to the set
    */
  def sadd(key: K, members: V*): F[ValkeyResponse[Long]]

  /** Remove the specified members from the set stored at key.
    * Members that are not a member of the set are ignored.
    *
    * @param key The key of the set
    * @param members Members to remove
    * @return The number of members that were removed from the set
    */
  def srem(key: K, members: V*): F[ValkeyResponse[Long]]

  /** Get all the members of the set stored at key
    *
    * @param key The key of the set
    * @return Set of all members
    */
  def smembers(key: K): F[ValkeyResponse[Set[V]]]

  /** Check if member is a member of the set stored at key
    *
    * @param key The key of the set
    * @param member The member to check
    * @return true if the element is a member of the set, false otherwise
    */
  def sismember(key: K, member: V): F[ValkeyResponse[Boolean]]

  /** Check if multiple values are members of the set stored at key
    *
    * @param key The key of the set
    * @param members The members to check
    * @return List of booleans, one for each member in the same order
    */
  def smismember(key: K, members: V*): F[ValkeyResponse[List[Boolean]]]

  /** Get the number of members in the set stored at key
    *
    * @param key The key of the set
    * @return The cardinality (number of elements) of the set, or 0 if key does not exist
    */
  def scard(key: K): F[ValkeyResponse[Long]]

  /** Get the union of all the given sets
    *
    * @param keys The keys of the sets
    * @return Set containing the union of all sets
    */
  def sunion(keys: K*): F[ValkeyResponse[Set[V]]]

  /** Store the union of all the given sets in destination
    *
    * @param destination The destination key
    * @param keys The keys of the sets to union
    * @return The number of elements in the resulting set
    */
  def sunionstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Get the intersection of all the given sets
    *
    * @param keys The keys of the sets
    * @return Set containing the intersection of all sets
    */
  def sinter(keys: K*): F[ValkeyResponse[Set[V]]]

  /** Store the intersection of all the given sets in destination
    *
    * @param destination The destination key
    * @param keys The keys of the sets to intersect
    * @return The number of elements in the resulting set
    */
  def sinterstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Get the difference between the first set and all the successive sets
    *
    * @param keys The keys of the sets
    * @return Set containing the difference
    */
  def sdiff(keys: K*): F[ValkeyResponse[Set[V]]]

  /** Store the difference between the first set and all successive sets in destination
    *
    * @param destination The destination key
    * @param keys The keys of the sets
    * @return The number of elements in the resulting set
    */
  def sdiffstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Remove and return one random member from the set
    *
    * @param key The key of the set
    * @return The removed member, or None if the set is empty
    */
  def spop(key: K): F[ValkeyResponse[Option[V]]]

  /** Remove and return one or more random members from the set
    *
    * @param key The key of the set
    * @param count The number of members to pop
    * @return Set of removed members
    */
  def spopCount(key: K, count: Long): F[ValkeyResponse[Set[V]]]

  /** Get one random member from the set
    *
    * @param key The key of the set
    * @return A random member, or None if the set is empty
    */
  def srandmember(key: K): F[ValkeyResponse[Option[V]]]

  /** Get one or more random members from the set
    *
    * @param key The key of the set
    * @param count The number of members to return
    * @return List of random members (may contain duplicates if count is negative)
    */
  def srandmemberCount(key: K, count: Long): F[ValkeyResponse[List[V]]]

  /** Move member from the set at source to the set at destination
    *
    * @param source The source key
    * @param destination The destination key
    * @param member The member to move
    * @return true if the element was moved, false otherwise
    */
  def smove(source: K, destination: K, member: V): F[ValkeyResponse[Boolean]]

  /** Get the number of elements in the intersection of the given sets
    *
    * @param keys The keys of the sets
    * @return The cardinality of the intersection
    */
  def sintercard(keys: K*): F[ValkeyResponse[Long]]

  /** Get the number of elements in the intersection of the given sets, with a limit
    *
    * @param keys The keys of the sets
    * @param limit Maximum number of elements to count (0 = no limit)
    * @return The cardinality of the intersection (at most limit)
    */
  def sintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]]

  /** Incrementally iterate over members of a set.
    *
    * @param key The key of the set
    * @param cursor The cursor (use "0" to start a new scan)
    * @return (nextCursor, Set of members). nextCursor is "0" when iteration is complete.
    */
  def sscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[Set[V]]]]
}
