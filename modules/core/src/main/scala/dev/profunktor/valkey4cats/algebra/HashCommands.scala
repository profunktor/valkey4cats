package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{
  ExpireCondition,
  ExpirySet,
  FieldCondition,
  HGetExExpiry
}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.ScanResult

/** Hash commands for Valkey/Redis
  *
  * Hashes are maps between string fields and string values,
  * making them perfect for representing objects.
  */
trait HashCommands[F[_], K, V] {

  /** Set field in the hash stored at key to value.
    * If key does not exist, a new key holding a hash is created.
    * If field already exists in the hash, it is overwritten.
    *
    * @param key The key of the hash
    * @param fieldValues Map of field-value pairs to set
    * @return The number of fields that were added
    */
  def hset(key: K, fieldValues: Map[K, V]): F[ValkeyResponse[Long]]

  /** Get the value of a hash field
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @return The value associated with field, or None when field is not present
    */
  def hget(key: K, field: K): F[ValkeyResponse[Option[V]]]

  /** Get all the fields and values in a hash
    *
    * @param key The key of the hash
    * @return Map of fields and their values stored in the hash
    */
  def hgetall(key: K): F[ValkeyResponse[Map[K, V]]]

  /** Get the values of all the given hash fields
    *
    * @param key The key of the hash
    * @param fields The fields in the hash
    * @return List of values associated with the given fields, in the same order
    */
  def hmget(key: K, fields: K*): F[ValkeyResponse[List[Option[V]]]]

  /** Delete one or more hash fields
    *
    * @param key The key of the hash
    * @param fields The fields to delete
    * @return The number of fields that were removed from the hash
    */
  def hdel(key: K, fields: K*): F[ValkeyResponse[Long]]

  /** Determine if a hash field exists
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @return true if the hash contains field, false otherwise
    */
  def hexists(key: K, field: K): F[ValkeyResponse[Boolean]]

  /** Get all the fields in a hash
    *
    * @param key The key of the hash
    * @return List of fields in the hash, or an empty list when key does not exist
    */
  def hkeys(key: K): F[ValkeyResponse[List[K]]]

  /** Get all the values in a hash
    *
    * @param key The key of the hash
    * @return List of values in the hash, or an empty list when key does not exist
    */
  def hvals(key: K): F[ValkeyResponse[List[V]]]

  /** Get the number of fields in a hash
    *
    * @param key The key of the hash
    * @return Number of fields in the hash, or 0 when key does not exist
    */
  def hlen(key: K): F[ValkeyResponse[Long]]

  /** Increment the integer value of a hash field by the given number
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @param increment The increment
    * @return The value at field after the increment
    */
  def hincrBy(key: K, field: K, increment: Long): F[ValkeyResponse[Long]]

  /** Increment the float value of a hash field by the given amount
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @param increment The increment
    * @return The value at field after the increment
    */
  def hincrByFloat(
      key: K,
      field: K,
      increment: Double
  ): F[ValkeyResponse[Double]]

  /** Set the value of a hash field, only if the field does not exist
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @param value The value to set
    * @return true if field is a new field in the hash and value was set,
    *         false if field already exists and the value was not set
    */
  def hsetnx(key: K, field: K, value: V): F[ValkeyResponse[Boolean]]

  /** Get the string length of the field value in the hash
    *
    * @param key The key of the hash
    * @param field The field in the hash
    * @return The string length of the value, or 0 when field is not present
    */
  def hstrlen(key: K, field: K): F[ValkeyResponse[Long]]

  /** Get one random field from a hash
    *
    * @param key The key of the hash
    * @return A random field from the hash, or None when key does not exist
    */
  def hrandfield(key: K): F[ValkeyResponse[Option[K]]]

  /** Get multiple random fields from a hash
    *
    * @param key The key of the hash
    * @param count The number of fields to return
    * @return List of random fields from the hash
    */
  def hrandfieldWithCount(key: K, count: Long): F[ValkeyResponse[List[K]]]

  /** Get multiple random fields with their values from a hash
    *
    * @param key The key of the hash
    * @param count The number of fields to return
    * @return List of (field, value) pairs
    */
  def hrandfieldWithCountWithValues(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(K, V)]]]

  /** Incrementally iterate over fields and values of a hash.
    *
    * @param key The key of the hash
    * @param cursor The cursor (use "0" to start a new scan)
    * @return (nextCursor, List of (field, value) pairs). nextCursor is "0" when iteration is complete.
    */
  def hscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[List[(K, V)]]]]

  // ==================== Hash Field Expiration (Valkey 8.0+) ====================

  /** Set expiration (in seconds) on hash fields.
    *
    * @param key The key of the hash
    * @param seconds TTL in seconds
    * @param fields The fields to set expiration on
    * @return List of results per field: 0 = field doesn't exist/no expiry set,
    *         1 = expiry set, 2 = field doesn't exist, -1 = no such field
    */
  def hexpire(key: K, seconds: Long, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Set expiration (in seconds) on hash fields with condition.
    *
    * @param key The key of the hash
    * @param seconds TTL in seconds
    * @param condition Expiration condition (NX, XX, GT, LT)
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hexpire(
      key: K,
      seconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration (in milliseconds) on hash fields.
    *
    * @param key The key of the hash
    * @param milliseconds TTL in milliseconds
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hpexpire(
      key: K,
      milliseconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration (in milliseconds) on hash fields with condition.
    *
    * @param key The key of the hash
    * @param milliseconds TTL in milliseconds
    * @param condition Expiration condition (NX, XX, GT, LT)
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hpexpire(
      key: K,
      milliseconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration on hash fields as Unix timestamp (seconds).
    *
    * @param key The key of the hash
    * @param unixSeconds Unix timestamp in seconds
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hexpireAt(
      key: K,
      unixSeconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration on hash fields as Unix timestamp (seconds) with condition.
    *
    * @param key The key of the hash
    * @param unixSeconds Unix timestamp in seconds
    * @param condition Expiration condition (NX, XX, GT, LT)
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hexpireAt(
      key: K,
      unixSeconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration on hash fields as Unix timestamp (milliseconds).
    *
    * @param key The key of the hash
    * @param unixMilliseconds Unix timestamp in milliseconds
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hpexpireAt(
      key: K,
      unixMilliseconds: Long,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Set expiration on hash fields as Unix timestamp (milliseconds) with condition.
    *
    * @param key The key of the hash
    * @param unixMilliseconds Unix timestamp in milliseconds
    * @param condition Expiration condition (NX, XX, GT, LT)
    * @param fields The fields to set expiration on
    * @return List of results per field
    */
  def hpexpireAt(
      key: K,
      unixMilliseconds: Long,
      condition: ExpireCondition,
      fields: K*
  ): F[ValkeyResponse[List[Long]]]

  /** Get the remaining TTL (in seconds) for hash fields.
    *
    * @param key The key of the hash
    * @param fields The fields to query
    * @return List of TTLs per field: -1 = no expiry, -2 = field doesn't exist
    */
  def httl(key: K, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Get the remaining TTL (in milliseconds) for hash fields.
    *
    * @param key The key of the hash
    * @param fields The fields to query
    * @return List of TTLs per field: -1 = no expiry, -2 = field doesn't exist
    */
  def hpttl(key: K, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Get the expiration time (Unix seconds) for hash fields.
    *
    * @param key The key of the hash
    * @param fields The fields to query
    * @return List of expiration timestamps per field: -1 = no expiry, -2 = field doesn't exist
    */
  def hexpireTime(key: K, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Get the expiration time (Unix milliseconds) for hash fields.
    *
    * @param key The key of the hash
    * @param fields The fields to query
    * @return List of expiration timestamps per field: -1 = no expiry, -2 = field doesn't exist
    */
  def hpexpireTime(key: K, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Remove expiration from hash fields.
    *
    * @param key The key of the hash
    * @param fields The fields to persist
    * @return List of results per field: 1 = expiry removed, -1 = no expiry, -2 = field doesn't exist
    */
  def hpersist(key: K, fields: K*): F[ValkeyResponse[List[Long]]]

  /** Get the values of hash fields and optionally set their expiration.
    *
    * @param key The key of the hash
    * @param expiry Expiration to set on the fields
    * @param fields The fields to get
    * @return List of values (None for non-existent fields)
    */
  def hgetex(
      key: K,
      expiry: HGetExExpiry,
      fields: K*
  ): F[ValkeyResponse[List[Option[V]]]]

  /** Set field-value pairs in a hash with a per-field expiration (Valkey 8.1+).
    *
    * @param key The key of the hash
    * @param fieldValues Map of field-value pairs to set
    * @param expiry Expiration to set on the fields
    * @return The number of fields that were added (not updated)
    */
  def hsetex(
      key: K,
      fieldValues: Map[K, V],
      expiry: ExpirySet
  ): F[ValkeyResponse[Long]]

  /** Set field-value pairs in a hash with a per-field expiration and condition (Valkey 8.1+).
    *
    * @param key The key of the hash
    * @param fieldValues Map of field-value pairs to set
    * @param expiry Expiration to set on the fields
    * @param condition Only set if all/none of the fields exist
    * @return The number of fields that were added (not updated)
    */
  def hsetex(
      key: K,
      fieldValues: Map[K, V],
      expiry: ExpirySet,
      condition: FieldCondition
  ): F[ValkeyResponse[Long]]
}
