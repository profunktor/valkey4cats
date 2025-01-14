package dev.profunktor.valkey4cats.connection

import glide.api.BaseClient

/** ADT representing a connection to either a standalone or clustered Valkey server.
  *
  * Replaces `Either[ValkeyClient, ValkeyClusterClient]` with semantically meaningful types.
  */
sealed trait ValkeyConnection {
  private[valkey4cats] def baseClient: BaseClient
}

object ValkeyConnection {

  final case class Standalone private[valkey4cats] (
      client: ValkeyClient
  ) extends ValkeyConnection {
    private[valkey4cats] def baseClient: BaseClient = client.underlying
  }

  final case class Clustered private[valkey4cats] (
      client: ValkeyClusterClient
  ) extends ValkeyConnection {
    private[valkey4cats] def baseClient: BaseClient = client.underlying
  }
}
