package dev.profunktor.valkey4cats.model

import glide.api.models.{configuration => G}

/** Configuration for AWS IAM authentication
  *
  * @param clusterName The name of the AWS ElastiCache or MemoryDB cluster
  * @param service The AWS service type (ElastiCache or MemoryDB)
  * @param region The AWS region where the cluster is located
  * @param refreshIntervalSeconds Optional interval in seconds to refresh IAM credentials
  */
final case class IamAuthConfig(
    clusterName: String,
    service: ServiceType,
    region: String,
    refreshIntervalSeconds: Option[Int] = None
)

object IamAuthConfig {

  private[valkey4cats] def toGlide(config: IamAuthConfig): G.IamAuthConfig = {
    val builder = G.IamAuthConfig
      .builder()
      .clusterName(config.clusterName)
      .service(config.service.toGlide)
      .region(config.region)

    config.refreshIntervalSeconds.foreach(interval =>
      builder.refreshIntervalSeconds(interval)
    )

    builder.build()
  }
}
