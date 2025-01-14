package dev.profunktor.valkey4cats.syntax

import dev.profunktor.valkey4cats.model.ValkeyUri

object literals {
  extension (inline ctx: StringContext) {
    inline def valkey(inline args: Any*): ValkeyUri =
      ${ macros.ValkeyLiteral.apply('ctx, 'args) }
  }
}
