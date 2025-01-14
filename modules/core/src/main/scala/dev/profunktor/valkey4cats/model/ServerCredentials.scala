package dev.profunktor.valkey4cats.model

import glide.api.models.configuration as G

/** Authentication credentials for Valkey server
  *
  * Supports:
  * - Password-only authentication (Redis 5.0+)
  * - Username+password authentication (Redis 6.0+/Valkey)
  * - AWS IAM authentication (ElastiCache/MemoryDB)
  */
sealed abstract class ServerCredentials { self =>

  private[valkey4cats] def toGlide: G.ServerCredentials =
    self match {
      case p: ServerCredentials.Password =>
        G.ServerCredentials
          .builder()
          .password(p.password)
          .build()
      case up: ServerCredentials.UsernamePassword =>
        G.ServerCredentials
          .builder()
          .username(up.username)
          .password(up.password)
          .build()
      case iam: ServerCredentials.IamAuth =>
        G.ServerCredentials
          .builder()
          .iamConfig(IamAuthConfig.toGlide(iam.config))
          .build()
    }
}

object ServerCredentials {

  /** Password-only authentication (Redis 5.0+) */
  sealed abstract class Password extends ServerCredentials {
    def password: String

    override def equals(obj: Any): Boolean = obj match {
      case other: Password => this.password == other.password
      case _               => false
    }

    override def hashCode(): Int = password.hashCode

    override def toString: String = "Password(***)"
  }

  object Password {
    private final class PasswordImpl(val password: String) extends Password

    def apply(password: String): Password = new PasswordImpl(password)

    def unapply(p: Password): Option[String] = Some(p.password)
  }

  /** Username and password authentication (Redis 6.0+/Valkey) */
  sealed abstract class UsernamePassword extends ServerCredentials {
    def username: String
    def password: String

    override def equals(obj: Any): Boolean = obj match {
      case other: UsernamePassword =>
        this.username == other.username && this.password == other.password
      case _ => false
    }

    override def hashCode(): Int = (username, password).hashCode()

    override def toString: String = s"UsernamePassword($username, ***)"
  }

  object UsernamePassword {
    private final class UsernamePasswordImpl(
        val username: String,
        val password: String
    ) extends UsernamePassword

    def apply(username: String, password: String): UsernamePassword =
      new UsernamePasswordImpl(username, password)

    def unapply(up: UsernamePassword): Option[(String, String)] =
      Some((up.username, up.password))
  }

  /** AWS IAM authentication (ElastiCache/MemoryDB) */
  sealed abstract class IamAuth extends ServerCredentials {
    def config: IamAuthConfig

    override def equals(obj: Any): Boolean = obj match {
      case other: IamAuth => this.config == other.config
      case _              => false
    }

    override def hashCode(): Int = config.hashCode()

    override def toString: String = s"IamAuth($config)"
  }

  object IamAuth {
    private final class IamAuthImpl(val config: IamAuthConfig) extends IamAuth

    def apply(config: IamAuthConfig): IamAuth = new IamAuthImpl(config)

    def unapply(iam: IamAuth): Option[IamAuthConfig] = Some(iam.config)
  }

  /** Convenience constructor for password-only auth */
  def password(pwd: String): ServerCredentials = Password(pwd)

  /** Convenience constructor for username+password auth */
  def usernamePassword(user: String, pwd: String): ServerCredentials =
    UsernamePassword(user, pwd)

  /** Convenience constructor for IAM auth */
  def iamAuth(config: IamAuthConfig): ServerCredentials = IamAuth(config)
}
