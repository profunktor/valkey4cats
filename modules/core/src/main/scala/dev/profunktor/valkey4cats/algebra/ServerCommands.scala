package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{FlushMode, InfoSection}
import dev.profunktor.valkey4cats.model.ValkeyResponse
import dev.profunktor.valkey4cats.results.ServerTime

/** Server management command algebra */
trait ServerCommands[F[_], K, V] {

  /** Get information and statistics about the server
    *
    * @return Server information as a string
    */
  def info: F[ValkeyResponse[String]]

  /** Get specific sections of server information
    *
    * @param sections Set of information sections to retrieve
    * @return Server information for requested sections
    */
  def info(sections: Set[InfoSection]): F[ValkeyResponse[String]]

  /** Rewrite the configuration file with the in-memory configuration */
  def configRewrite: F[ValkeyResponse[Unit]]

  /** Reset server statistics */
  def configResetStat: F[ValkeyResponse[Unit]]

  /** Get configuration parameters
    *
    * @param parameters Configuration parameter names to retrieve
    * @return Map of parameter names to values
    */
  def configGet(parameters: Set[String]): F[ValkeyResponse[Map[String, String]]]

  /** Set configuration parameters
    *
    * @param parameters Map of parameter names to values to set
    */
  def configSet(parameters: Map[String, String]): F[ValkeyResponse[Unit]]

  def time: F[ValkeyResponse[ServerTime]]

  /** Get timestamp of the last successful save to disk
    *
    * @return Unix timestamp in seconds
    */
  def lastSave: F[ValkeyResponse[Long]]

  /** Remove all keys from all databases */
  def flushAll: F[ValkeyResponse[Unit]]

  /** Remove all keys from all databases with specified mode
    *
    * @param mode Flush mode (sync or async)
    */
  def flushAll(mode: FlushMode): F[ValkeyResponse[Unit]]

  /** Remove all keys from the current database */
  def flushDB: F[ValkeyResponse[Unit]]

  /** Remove all keys from the current database with specified mode
    *
    * @param mode Flush mode (sync or async)
    */
  def flushDB(mode: FlushMode): F[ValkeyResponse[Unit]]

  /** Display server version art (Easter egg command)
    *
    * @return ASCII art version display
    */
  def lolwut: F[ValkeyResponse[String]]

  /** Display server version art with specific version
    *
    * @param version Version number for the art
    * @return ASCII art version display
    */
  def lolwut(version: Int): F[ValkeyResponse[String]]

  /** Display server version art with version and optional parameters
    *
    * @param version Version number for the art
    * @param parameters Optional parameters for the art display
    * @return ASCII art version display
    */
  def lolwut(version: Int, parameters: List[Int]): F[ValkeyResponse[String]]

  /** Get the number of keys in the currently selected database
    *
    * @return Number of keys
    */
  def dbSize: F[ValkeyResponse[Long]]
}
