package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{BitmapIndexType, BitwiseOperation}
import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Bit-level string operation commands (SETBIT, GETBIT, BITCOUNT, BITOP, BITPOS) */
trait BitmapCommands[F[_], K, V] {

  /** Set or clear the bit at offset in the string value stored at key.
    *
    * @param key The key
    * @param offset The bit offset (0-based)
    * @param value The bit value (0 or 1)
    * @return The original bit value stored at offset
    */
  def setbit(key: K, offset: Long, value: Long): F[ValkeyResponse[Long]]

  /** Get the bit value at offset in the string value stored at key.
    *
    * @param key The key
    * @param offset The bit offset (0-based)
    * @return The bit value (0 or 1)
    */
  def getbit(key: K, offset: Long): F[ValkeyResponse[Long]]

  /** Count the number of set bits (population counting) in a string.
    *
    * @param key The key
    * @return The number of bits set to 1
    */
  def bitcount(key: K): F[ValkeyResponse[Long]]

  /** Count the number of set bits in a range of bytes.
    *
    * @param key The key
    * @param start Start byte offset (inclusive)
    * @param end End byte offset (inclusive)
    * @return The number of bits set to 1 in the range
    */
  def bitcount(key: K, start: Long, end: Long): F[ValkeyResponse[Long]]

  /** Count the number of set bits in a range with specified index type.
    *
    * @param key The key
    * @param start Start offset (inclusive)
    * @param end End offset (inclusive)
    * @param indexType Whether offsets are byte or bit indices
    * @return The number of bits set to 1 in the range
    */
  def bitcount(
      key: K,
      start: Long,
      end: Long,
      indexType: BitmapIndexType
  ): F[ValkeyResponse[Long]]

  /** Find the position of the first bit set to the specified value.
    *
    * @param key The key
    * @param bit The bit value to search for (0 or 1)
    * @return The position of the first bit, or -1 if not found
    */
  def bitpos(key: K, bit: Long): F[ValkeyResponse[Long]]

  /** Find the position of the first bit set to the specified value, starting from a byte offset.
    *
    * @param key The key
    * @param bit The bit value to search for (0 or 1)
    * @param start Start byte offset
    * @return The position of the first bit, or -1 if not found
    */
  def bitpos(key: K, bit: Long, start: Long): F[ValkeyResponse[Long]]

  /** Find the position of the first bit set to the specified value in a byte range.
    *
    * @param key The key
    * @param bit The bit value to search for (0 or 1)
    * @param start Start byte offset (inclusive)
    * @param end End byte offset (inclusive)
    * @return The position of the first bit, or -1 if not found
    */
  def bitpos(
      key: K,
      bit: Long,
      start: Long,
      end: Long
  ): F[ValkeyResponse[Long]]

  /** Find the position of the first bit set to the specified value in a range with index type.
    *
    * @param key The key
    * @param bit The bit value to search for (0 or 1)
    * @param start Start offset (inclusive)
    * @param end End offset (inclusive)
    * @param indexType Whether offsets are byte or bit indices
    * @return The position of the first bit, or -1 if not found
    */
  def bitpos(
      key: K,
      bit: Long,
      start: Long,
      end: Long,
      indexType: BitmapIndexType
  ): F[ValkeyResponse[Long]]

  /** Perform a bitwise operation between strings stored at keys and store the result.
    *
    * @param operation The bitwise operation (AND, OR, XOR, NOT)
    * @param destkey The destination key
    * @param keys The source keys (NOT requires exactly one source key)
    * @return The size of the string stored in the destination key (longest input string length)
    */
  def bitop(
      operation: BitwiseOperation,
      destkey: K,
      keys: K*
  ): F[ValkeyResponse[Long]]
}
