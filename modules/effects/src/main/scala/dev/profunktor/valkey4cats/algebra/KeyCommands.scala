package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.ExpireCondition
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.{
  ClusterScanCursor,
  ClusterScanResult,
  ScanResult
}

/** Key management command algebra */
trait KeyCommands[F[_], K, V] {

  /** Delete one or more keys
    *
    * @return Number of keys deleted
    */
  def del(keys: K*): F[ValkeyResponse[Long]]

  /** Check if a key exists
    *
    * @return true if key exists, false otherwise
    */
  def exists(key: K): F[ValkeyResponse[Boolean]]

  /** Check if multiple keys exist
    *
    * @return Number of keys that exist
    */
  def existsMany(keys: K*): F[ValkeyResponse[Long]]

  /** Remove one or more keys asynchronously (non-blocking DEL)
    *
    * @return Number of keys unlinked
    */
  def unlink(keys: K*): F[ValkeyResponse[Long]]

  /** Set a timeout on a key (in seconds)
    *
    * @return true if the timeout was set, false if key does not exist
    */
  def expire(key: K, seconds: Long): F[ValkeyResponse[Boolean]]

  /** Set a timeout on a key (in seconds) with a condition
    *
    * @return true if the timeout was set
    */
  def expire(
      key: K,
      seconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]]

  /** Set a timeout on a key (in milliseconds)
    *
    * @return true if the timeout was set, false if key does not exist
    */
  def pexpire(key: K, milliseconds: Long): F[ValkeyResponse[Boolean]]

  /** Set a timeout on a key (in milliseconds) with a condition
    *
    * @return true if the timeout was set
    */
  def pexpire(
      key: K,
      milliseconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]]

  /** Set a key's expiration as a Unix timestamp (seconds)
    *
    * @return true if the timeout was set, false if key does not exist
    */
  def expireAt(key: K, unixSeconds: Long): F[ValkeyResponse[Boolean]]

  /** Set a key's expiration as a Unix timestamp (seconds) with a condition
    *
    * @return true if the timeout was set
    */
  def expireAt(
      key: K,
      unixSeconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]]

  /** Set a key's expiration as a Unix timestamp (milliseconds)
    *
    * @return true if the timeout was set, false if key does not exist
    */
  def pexpireAt(key: K, unixMilliseconds: Long): F[ValkeyResponse[Boolean]]

  /** Set a key's expiration as a Unix timestamp (milliseconds) with a condition
    *
    * @return true if the timeout was set
    */
  def pexpireAt(
      key: K,
      unixMilliseconds: Long,
      condition: ExpireCondition
  ): F[ValkeyResponse[Boolean]]

  /** Get the remaining time to live of a key (in seconds)
    *
    * @return TTL in seconds, -2 if key does not exist, -1 if no expiry set
    */
  def ttl(key: K): F[ValkeyResponse[Long]]

  /** Get the remaining time to live of a key (in milliseconds)
    *
    * @return TTL in milliseconds, -2 if key does not exist, -1 if no expiry set
    */
  def pttl(key: K): F[ValkeyResponse[Long]]

  /** Get the absolute Unix timestamp (seconds) at which the key will expire
    *
    * @return Timestamp in seconds, -2 if key does not exist, -1 if no expiry set
    */
  def expireTime(key: K): F[ValkeyResponse[Long]]

  /** Get the absolute Unix timestamp (milliseconds) at which the key will expire
    *
    * @return Timestamp in milliseconds, -2 if key does not exist, -1 if no expiry set
    */
  def pexpireTime(key: K): F[ValkeyResponse[Long]]

  /** Remove the expiration from a key
    *
    * @return true if the timeout was removed, false if key does not exist or has no expiry
    */
  def persist(key: K): F[ValkeyResponse[Boolean]]

  /** Rename a key
    *
    * @return Unit on success; raises error if source key does not exist
    */
  def rename(key: K, newKey: K): F[ValkeyResponse[Unit]]

  /** Rename a key only if the new key does not already exist
    *
    * @return true if the key was renamed, false if newKey already exists
    */
  def renameNx(key: K, newKey: K): F[ValkeyResponse[Boolean]]

  /** Get the type of the value stored at key
    *
    * @return Type as string (string, list, set, zset, hash, stream), or "none" if key does not exist
    */
  def typeOf(key: K): F[ValkeyResponse[String]]

  /** Get the encoding of the value stored at key
    *
    * @return Internal encoding as string, or None if key does not exist
    */
  def objectEncoding(key: K): F[ValkeyResponse[Option[String]]]

  /** Alters the last access time of one or more keys
    *
    * @return Number of keys that were touched
    */
  def touch(keys: K*): F[ValkeyResponse[Long]]

  /** Copy the value of a key to a destination key
    *
    * @return true if the key was copied, false if destination already exists
    */
  def copy(source: K, destination: K): F[ValkeyResponse[Boolean]]

  /** Return a random key from the currently selected database
    *
    * @return A random key, or None if the database is empty
    */
  def randomKey: F[ValkeyResponse[Option[K]]]

  /** Get the access frequency of a key (requires maxmemory-policy with LFU)
    *
    * @return The access frequency, or None if the key does not exist
    */
  def objectFreq(key: K): F[ValkeyResponse[Option[Long]]]

  /** Get the idle time of a key in seconds (requires maxmemory-policy with LRU)
    *
    * @return The idle time in seconds, or None if the key does not exist
    */
  def objectIdletime(key: K): F[ValkeyResponse[Option[Long]]]

  /** Get the reference count of the value stored at key
    *
    * @return The reference count, or None if the key does not exist
    */
  def objectRefcount(key: K): F[ValkeyResponse[Option[Long]]]

  /** Sort the elements in a list, set, or sorted set
    *
    * @param key The key to sort
    * @return List of sorted elements
    */
  def sort(key: K): F[ValkeyResponse[List[V]]]

  /** Sort the elements and store the result in destination
    *
    * @param key The key to sort
    * @param destination The destination key
    * @return The number of elements stored
    */
  def sortStore(key: K, destination: K): F[ValkeyResponse[Long]]

  /** Sort the elements (read-only, does not modify)
    *
    * @param key The key to sort
    * @return List of sorted elements
    */
  def sortReadOnly(key: K): F[ValkeyResponse[List[V]]]

  /** Serialize the value stored at key (for backup/migration)
    *
    * @param key The key to dump
    * @return The serialized value as a byte array, or None if key does not exist
    */
  def dump(key: K): F[ValkeyResponse[Option[Array[Byte]]]]

  /** Restore a key from a serialized value (from dump)
    *
    * @param key The key to restore to
    * @param ttlMillis TTL in milliseconds (0 = no expiry)
    * @param serializedValue The serialized value
    */
  def restore(
      key: K,
      ttlMillis: Long,
      serializedValue: Array[Byte]
  ): F[ValkeyResponse[Unit]]

  /** Wait for the synchronous replication of all preceding write commands.
    *
    * @param numReplicas The number of replicas to wait for
    * @param timeout The timeout in milliseconds (0 to block indefinitely)
    * @return The number of replicas that acknowledged the writes
    */
  def waitReplicas(
      numReplicas: Long,
      timeout: Long
  ): F[ValkeyResponse[Long]]

  /** Move a key to another database.
    *
    * @param key The key to move
    * @param db The target database index
    * @return true if the key was moved, false if not found or already exists in target db
    */
  def move(key: K, db: Long): F[ValkeyResponse[Boolean]]

  def scan(cursor: String): F[ValkeyResponse[ScanResult[List[K]]]]

  def scan(
      cursor: String,
      matchPattern: String,
      count: Long
  ): F[ValkeyResponse[ScanResult[List[K]]]]

  def clusterScan(
      cursor: ClusterScanCursor
  ): F[ValkeyResponse[ClusterScanResult[List[K]]]]

  def clusterScan(
      cursor: ClusterScanCursor,
      matchPattern: String,
      count: Long
  ): F[ValkeyResponse[ClusterScanResult[List[K]]]]
}
