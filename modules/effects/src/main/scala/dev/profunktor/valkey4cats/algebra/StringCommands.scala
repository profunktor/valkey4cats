package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{GetExExpiry, SetOptions}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.SetResult

/** String/Key-Value command algebra */
trait StringCommands[F[_], K, V] {

  /** Get the value of a key
    *
    * @param key The key to get
    * @return Some(value) if key exists, None otherwise
    */
  def get(key: K): F[ValkeyResponse[Option[V]]]

  /** Set the value of a key
    *
    * @param key The key to set
    * @param value The value to set
    */
  def set(key: K, value: V): F[ValkeyResponse[Unit]]

  /** Set the value of a key with options
    *
    * @param key The key to set
    * @param value The value to set
    * @param options Set options (expiry, conditional set, etc.)
    * @return SetResult indicating outcome
    */
  def set(
      key: K,
      value: V,
      options: SetOptions
  ): F[ValkeyResponse[SetResult[V]]]

  /** Get multiple values by keys
    *
    * @param keys Set of keys to get
    * @return Map of key-value pairs for keys that exist
    */
  def mGet(keys: Set[K]): F[ValkeyResponse[Map[K, V]]]

  /** Set multiple key-value pairs
    *
    * @param keyValues Map of key-value pairs to set
    */
  def mSet(keyValues: Map[K, V]): F[ValkeyResponse[Unit]]

  /** Increment a key's integer value by 1
    *
    * @param key The key to increment
    * @return The new value after increment
    */
  def incr(key: K): F[ValkeyResponse[Long]]

  /** Increment a key's integer value by a specific amount
    *
    * @param key The key to increment
    * @param amount The amount to increment by
    * @return The new value after increment
    */
  def incrBy(key: K, amount: Long): F[ValkeyResponse[Long]]

  /** Decrement a key's integer value by 1
    *
    * @param key The key to decrement
    * @return The new value after decrement
    */
  def decr(key: K): F[ValkeyResponse[Long]]

  /** Decrement a key's integer value by a specific amount
    *
    * @param key The key to decrement
    * @param amount The amount to decrement by
    * @return The new value after decrement
    */
  def decrBy(key: K, amount: Long): F[ValkeyResponse[Long]]

  /** Append a value to a key
    *
    * @param key The key to append to
    * @param value The value to append
    * @return The length of the string after the append
    */
  def append(key: K, value: V): F[ValkeyResponse[Long]]

  /** Get the length of a string value
    *
    * @param key The key to get length for
    * @return The length of the string, or 0 if key doesn't exist
    */
  def strlen(key: K): F[ValkeyResponse[Long]]

  /** Get the value of a key and optionally set its expiration
    *
    * @return Some(value) if key exists, None otherwise
    */
  def getEx(key: K): F[ValkeyResponse[Option[V]]]

  /** Get the value of a key and set its expiration
    *
    * @param expiry Expiry options (seconds, millis, unix timestamp, or persist)
    * @return Some(value) if key exists, None otherwise
    */
  def getEx(key: K, expiry: GetExExpiry): F[ValkeyResponse[Option[V]]]

  /** Get the value of a key and delete the key
    *
    * @return Some(value) if key existed, None otherwise
    */
  def getDel(key: K): F[ValkeyResponse[Option[V]]]

  /** Increment a key's floating-point value
    *
    * @param amount The amount to increment by
    * @return The new value after increment
    */
  def incrByFloat(key: K, amount: Double): F[ValkeyResponse[Double]]

  /** Set a key only if it does not already exist
    *
    * @return true if the key was set, false if it already existed
    */
  def setNx(key: K, value: V): F[ValkeyResponse[Boolean]]

  /** Set multiple key-value pairs only if none of the keys exist
    *
    * @return true if all keys were set, false if no keys were set (at least one existed)
    */
  def mSetNx(keyValues: Map[K, V]): F[ValkeyResponse[Boolean]]

  /** Get a substring of the string value stored at key.
    *
    * @param key The key
    * @param start Start offset (inclusive, 0-based, negative counts from end)
    * @param end End offset (inclusive, negative counts from end)
    * @return The substring
    */
  def getRange(key: K, start: Long, end: Long): F[ValkeyResponse[V]]

  /** Overwrite part of the string stored at key starting at the specified offset.
    *
    * @param key The key
    * @param offset The byte offset to start writing at
    * @param value The value to write
    * @return The length of the string after the modification
    */
  def setRange(key: K, offset: Long, value: V): F[ValkeyResponse[Long]]

  /** Get the longest common substring between two string values stored at key1 and key2.
    *
    * @param key1 First key
    * @param key2 Second key
    * @return The longest common substring
    */
  def lcs(key1: K, key2: K): F[ValkeyResponse[V]]

  /** Get the length of the longest common substring between two string values.
    *
    * @param key1 First key
    * @param key2 Second key
    * @return The length of the longest common substring
    */
  def lcsLen(key1: K, key2: K): F[ValkeyResponse[Long]]
}
