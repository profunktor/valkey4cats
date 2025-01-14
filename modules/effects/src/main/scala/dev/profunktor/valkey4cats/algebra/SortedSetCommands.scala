package dev.profunktor.valkey4cats.algebra

import dev.profunktor.valkey4cats.arguments.{
  AggregateOption,
  LexBoundary,
  RangeQuery,
  ScoreBoundary,
  ScoreFilter,
  ZAddOptions
}
import dev.profunktor.valkey4cats.results.ScanResult
import dev.profunktor.valkey4cats.model.ValkeyResponse

/** Sorted Set commands for Valkey/Redis
  *
  * Sorted sets are collections of unique string elements where each element
  * has an associated score. Elements are ordered by score.
  */
trait SortedSetCommands[F[_], K, V] {

  /** Add one or more members to a sorted set, or update its score if it already exists
    *
    * @param key The key of the sorted set
    * @param membersScores Map of members to scores
    * @return The number of elements added to the sorted set (not including updates)
    */
  def zadd(key: K, membersScores: Map[V, Double]): F[ValkeyResponse[Long]]

  /** Add one or more members to a sorted set with options
    *
    * @param key The key of the sorted set
    * @param membersScores Map of members to scores
    * @param options ZADD options (NX, XX, GT, LT, CH)
    * @return The number of elements added/changed depending on options
    */
  def zadd(
      key: K,
      membersScores: Map[V, Double],
      options: ZAddOptions
  ): F[ValkeyResponse[Long]]

  /** Add or update a single member in a sorted set and return the new score (ZADD INCR mode).
    *
    * @param key The key of the sorted set
    * @param member The member
    * @param score The score increment
    * @return The new score of the member, or None if the operation was aborted (with NX/XX options)
    */
  def zaddIncr(
      key: K,
      member: V,
      score: Double
  ): F[ValkeyResponse[Option[Double]]]

  /** Remove one or more members from a sorted set
    *
    * @param key The key of the sorted set
    * @param members Members to remove
    * @return The number of members removed from the sorted set
    */
  def zrem(key: K, members: V*): F[ValkeyResponse[Long]]

  /** Get the specified range of elements in a sorted set by index.
    * Both start and stop are zero-based indexes.
    *
    * @param key The key of the sorted set
    * @param start Start index (inclusive)
    * @param stop Stop index (inclusive)
    * @return List of elements in the specified range
    */
  def zrange(key: K, start: Long, stop: Long): F[ValkeyResponse[List[V]]]

  /** Get the specified range of elements with scores in a sorted set by index.
    * Both start and stop are zero-based indexes.
    *
    * @param key The key of the sorted set
    * @param start Start index (inclusive)
    * @param stop Stop index (inclusive)
    * @return List of (element, score) pairs in the specified range
    */
  def zrangeWithScores(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[List[(V, Double)]]]

  /** Get the score associated with a member in a sorted set
    *
    * @param key The key of the sorted set
    * @param member The member
    * @return The score of the member, or None if member doesn't exist
    */
  def zscore(key: K, member: V): F[ValkeyResponse[Option[Double]]]

  /** Get the scores associated with multiple members in a sorted set
    *
    * @param key The key of the sorted set
    * @param members The members
    * @return List of scores (None for members that don't exist)
    */
  def zmscore(key: K, members: V*): F[ValkeyResponse[List[Option[Double]]]]

  /** Get the number of members in a sorted set
    *
    * @param key The key of the sorted set
    * @return The cardinality (number of elements) of the sorted set
    */
  def zcard(key: K): F[ValkeyResponse[Long]]

  /** Get the rank of member in the sorted set (ascending order, 0-based)
    *
    * @param key The key of the sorted set
    * @param member The member
    * @return The rank of the member, or None if member doesn't exist
    */
  def zrank(key: K, member: V): F[ValkeyResponse[Option[Long]]]

  /** Get the rank of member in the sorted set (descending order, 0-based)
    *
    * @param key The key of the sorted set
    * @param member The member
    * @return The rank of the member, or None if member doesn't exist
    */
  def zrevrank(key: K, member: V): F[ValkeyResponse[Option[Long]]]

  /** Increment the score of a member in a sorted set
    *
    * @param key The key of the sorted set
    * @param increment The amount to increment
    * @param member The member
    * @return The new score of the member
    */
  def zincrby(key: K, increment: Double, member: V): F[ValkeyResponse[Double]]

  /** Count the members in a sorted set with scores within the given range
    *
    * @param key The key of the sorted set
    * @param min Minimum score (inclusive)
    * @param max Maximum score (inclusive)
    * @return The number of elements in the specified score range
    */
  def zcount(key: K, min: Double, max: Double): F[ValkeyResponse[Long]]

  /** Remove and return the member with the lowest score from a sorted set
    *
    * @param key The key of the sorted set
    * @return The removed (member, score) pair, or None if the sorted set is empty
    */
  def zpopmin(key: K): F[ValkeyResponse[Option[(V, Double)]]]

  /** Remove and return up to count members with the lowest scores from a sorted set
    *
    * @param key The key of the sorted set
    * @param count The number of members to pop
    * @return List of removed (member, score) pairs
    */
  def zpopminCount(key: K, count: Long): F[ValkeyResponse[List[(V, Double)]]]

  /** Remove and return the member with the highest score from a sorted set
    *
    * @param key The key of the sorted set
    * @return The removed (member, score) pair, or None if the sorted set is empty
    */
  def zpopmax(key: K): F[ValkeyResponse[Option[(V, Double)]]]

  /** Remove and return up to count members with the highest scores from a sorted set
    *
    * @param key The key of the sorted set
    * @param count The number of members to pop
    * @return List of removed (member, score) pairs
    */
  def zpopmaxCount(key: K, count: Long): F[ValkeyResponse[List[(V, Double)]]]

  /** Get one random member from a sorted set
    *
    * @param key The key of the sorted set
    * @return A random member, or None if the sorted set is empty
    */
  def zrandmember(key: K): F[ValkeyResponse[Option[V]]]

  /** Get one or more random members from a sorted set
    *
    * @param key The key of the sorted set
    * @param count The number of members to return
    * @return List of random members
    */
  def zrandmemberCount(key: K, count: Long): F[ValkeyResponse[List[V]]]

  /** Get one or more random members with scores from a sorted set
    *
    * @param key The key of the sorted set
    * @param count The number of members to return
    * @return List of random (member, score) pairs
    */
  def zrandmemberWithScores(
      key: K,
      count: Long
  ): F[ValkeyResponse[List[(V, Double)]]]

  /** Remove all members in a sorted set within the given range of ranks (indices).
    *
    * @param key The key of the sorted set
    * @param start Start rank (inclusive, 0-based)
    * @param stop Stop rank (inclusive, negative counts from end)
    * @return The number of elements removed
    */
  def zremrangebyrank(
      key: K,
      start: Long,
      stop: Long
  ): F[ValkeyResponse[Long]]

  /** Remove all members in a sorted set within the given score range.
    *
    * @param key The key of the sorted set
    * @param min Minimum score boundary
    * @param max Maximum score boundary
    * @return The number of elements removed
    */
  def zremrangebyscore(
      key: K,
      min: ScoreBoundary,
      max: ScoreBoundary
  ): F[ValkeyResponse[Long]]

  /** Return the difference between the first sorted set and all the successive sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @return List of members in the difference
    */
  def zdiff(keys: K*): F[ValkeyResponse[List[V]]]

  /** Store the difference between the first sorted set and all successive sorted sets in destination.
    *
    * @param destination The destination key
    * @param keys The keys of the sorted sets
    * @return The number of elements in the resulting sorted set
    */
  def zdiffstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Return the union of all given sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @return List of members in the union
    */
  def zunion(keys: K*): F[ValkeyResponse[List[V]]]

  /** Store the union of all given sorted sets in destination.
    *
    * @param destination The destination key
    * @param keys The keys of the sorted sets
    * @return The number of elements in the resulting sorted set
    */
  def zunionstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Return the intersection of all given sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @return List of members in the intersection
    */
  def zinter(keys: K*): F[ValkeyResponse[List[V]]]

  /** Store the intersection of all given sorted sets in destination.
    *
    * @param destination The destination key
    * @param keys The keys of the sorted sets
    * @return The number of elements in the resulting sorted set
    */
  def zinterstore(destination: K, keys: K*): F[ValkeyResponse[Long]]

  /** Get the number of elements in the intersection of the given sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @return The cardinality of the intersection
    */
  def zintercard(keys: K*): F[ValkeyResponse[Long]]

  /** Get the number of elements in the intersection of the given sorted sets, with a limit.
    *
    * @param limit Maximum number of elements to count (0 = no limit)
    * @param keys The keys of the sorted sets
    * @return The cardinality of the intersection (at most limit)
    */
  def zintercard(limit: Long, keys: K*): F[ValkeyResponse[Long]]

  /** Get the rank and score of a member in the sorted set (ascending order, 0-based)
    *
    * @param key The key of the sorted set
    * @param member The member
    * @return (rank, score) pair, or None if member doesn't exist
    */
  def zrankWithScore(
      key: K,
      member: V
  ): F[ValkeyResponse[Option[(Long, Double)]]]

  /** Get the rank and score of a member in the sorted set (descending order, 0-based)
    *
    * @param key The key of the sorted set
    * @param member The member
    * @return (rank, score) pair, or None if member doesn't exist
    */
  def zrevrankWithScore(
      key: K,
      member: V
  ): F[ValkeyResponse[Option[(Long, Double)]]]

  /** Blocking version of ZPOPMIN. Remove and return the member with the lowest score, or block until available.
    *
    * @param keys The keys of the sorted sets to pop from
    * @param timeout Timeout in seconds (0 to block indefinitely)
    * @return Some((key, member, score)) or None on timeout
    */
  def bzpopmin(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V, Double)]]]

  /** Blocking version of ZPOPMAX. Remove and return the member with the highest score, or block until available.
    *
    * @param keys The keys of the sorted sets to pop from
    * @param timeout Timeout in seconds (0 to block indefinitely)
    * @return Some((key, member, score)) or None on timeout
    */
  def bzpopmax(
      keys: List[K],
      timeout: Double
  ): F[ValkeyResponse[Option[(K, V, Double)]]]

  /** Return the difference between the first sorted set and all successive sorted sets, with scores.
    *
    * @param keys The keys of the sorted sets
    * @return List of (member, score) pairs in the difference
    */
  def zdiffWithScores(keys: K*): F[ValkeyResponse[List[(V, Double)]]]

  /** Return the union of all given sorted sets, with scores.
    *
    * @param keys The keys of the sorted sets
    * @return List of (member, score) pairs in the union
    */
  def zunionWithScores(keys: K*): F[ValkeyResponse[List[(V, Double)]]]

  /** Return the union of all given sorted sets with an aggregation function, with scores.
    *
    * @param keys The keys of the sorted sets
    * @param aggregate Aggregation function (SUM, MIN, MAX)
    * @return List of (member, score) pairs in the union
    */
  def zunionWithScores(
      keys: List[K],
      aggregate: AggregateOption
  ): F[ValkeyResponse[List[(V, Double)]]]

  /** Return the intersection of all given sorted sets, with scores.
    *
    * @param keys The keys of the sorted sets
    * @return List of (member, score) pairs in the intersection
    */
  def zinterWithScores(keys: K*): F[ValkeyResponse[List[(V, Double)]]]

  /** Return the intersection of all given sorted sets with an aggregation function, with scores.
    *
    * @param keys The keys of the sorted sets
    * @param aggregate Aggregation function (SUM, MIN, MAX)
    * @return List of (member, score) pairs in the intersection
    */
  def zinterWithScores(
      keys: List[K],
      aggregate: AggregateOption
  ): F[ValkeyResponse[List[(V, Double)]]]

  /** Count the number of members in a sorted set between a given lexicographic range.
    *
    * @param key The key of the sorted set
    * @param min Minimum lex boundary
    * @param max Maximum lex boundary
    * @return The number of elements in the specified range
    */
  def zlexcount(
      key: K,
      min: LexBoundary,
      max: LexBoundary
  ): F[ValkeyResponse[Long]]

  /** Remove all members in a sorted set between the given lexicographic range.
    *
    * @param key The key of the sorted set
    * @param min Minimum lex boundary
    * @param max Maximum lex boundary
    * @return The number of elements removed
    */
  def zremrangebylex(
      key: K,
      min: LexBoundary,
      max: LexBoundary
  ): F[ValkeyResponse[Long]]

  /** Store a range of members from a sorted set into a new key.
    *
    * @param destination The destination key
    * @param source The source key
    * @param rangeQuery The range query (by index, score, or lex)
    * @return The number of elements in the resulting sorted set
    */
  def zrangestore(
      destination: K,
      source: K,
      rangeQuery: RangeQuery
  ): F[ValkeyResponse[Long]]

  /** Store a range of members from a sorted set into a new key, with reverse option.
    *
    * @param destination The destination key
    * @param source The source key
    * @param rangeQuery The range query (by index, score, or lex)
    * @param reverse If true, reverse the range
    * @return The number of elements in the resulting sorted set
    */
  def zrangestore(
      destination: K,
      source: K,
      rangeQuery: RangeQuery,
      reverse: Boolean
  ): F[ValkeyResponse[Long]]

  /** Pop the member with the min or max score from one of the given sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @param filter Whether to pop the MIN or MAX score element
    * @return Some((key, (member, score))) or None if all sets are empty
    */
  def zmpop(
      keys: List[K],
      filter: ScoreFilter
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]]

  /** Pop up to count members with the min or max score from one of the given sorted sets.
    *
    * @param keys The keys of the sorted sets
    * @param filter Whether to pop the MIN or MAX score element
    * @param count Maximum number of elements to pop
    * @return Some((key, elements)) or None if all sets are empty
    */
  def zmpop(
      keys: List[K],
      filter: ScoreFilter,
      count: Long
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]]

  /** Blocking version of ZMPOP. Pop min/max scored element, or block until available.
    *
    * @param keys The keys of the sorted sets
    * @param filter Whether to pop the MIN or MAX score element
    * @param timeout Timeout in seconds (0 to block indefinitely)
    * @return Some((key, elements)) or None on timeout
    */
  def bzmpop(
      keys: List[K],
      filter: ScoreFilter,
      timeout: Double
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]]

  /** Blocking version of ZMPOP with count.
    *
    * @param keys The keys of the sorted sets
    * @param filter Whether to pop the MIN or MAX score element
    * @param timeout Timeout in seconds (0 to block indefinitely)
    * @param count Maximum number of elements to pop
    * @return Some((key, elements)) or None on timeout
    */
  def bzmpop(
      keys: List[K],
      filter: ScoreFilter,
      timeout: Double,
      count: Long
  ): F[ValkeyResponse[Option[(K, List[(V, Double)])]]]

  /** Incrementally iterate over members and scores of a sorted set.
    *
    * @param key The key of the sorted set
    * @param cursor The cursor (use "0" to start a new scan)
    * @return (nextCursor, List of (member, score) pairs). nextCursor is "0" when iteration is complete.
    */
  def zscan(
      key: K,
      cursor: String
  ): F[ValkeyResponse[ScanResult[List[(V, Double)]]]]
}
