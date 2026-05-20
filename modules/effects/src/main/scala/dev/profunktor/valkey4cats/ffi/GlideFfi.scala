package dev.profunktor.valkey4cats.ffi

import java.lang.foreign.*
import java.lang.invoke.MethodHandle

private[valkey4cats] object GlideFfi:

  private val lib: SymbolLookup =
    val libPath = sys.props.getOrElse("valkey4cats.native.lib",
      sys.env.getOrElse("GLIDE_FFI_LIB", "libglide_ffi"))
    if libPath.contains("/") || libPath.contains("\\") then
      SymbolLookup.libraryLookup(java.nio.file.Path.of(libPath), Arena.global())
    else
      SymbolLookup.libraryLookup(libPath, Arena.global())

  private val linker: Linker = Linker.nativeLinker()

  // create_client(connection_request_bytes: *const u8, connection_request_len: usize,
  //               client_type: *const ClientType, pubsub_callback: PubSubCallback) -> *const ConnectionResponse
  val createClient: MethodHandle = linker.downcallHandle(
    lib.find("create_client").orElseThrow(),
    FunctionDescriptor.of(
      ValueLayout.ADDRESS,     // return: *const ConnectionResponse
      ValueLayout.ADDRESS,     // connection_request_bytes: *const u8
      ValueLayout.JAVA_LONG,   // connection_request_len: usize
      ValueLayout.ADDRESS,     // client_type: *const ClientType
      ValueLayout.ADDRESS      // pubsub_callback: PubSubCallback (fn ptr)
    )
  )

  // close_client(client_adapter_ptr: *const c_void)
  val closeClient: MethodHandle = linker.downcallHandle(
    lib.find("close_client").orElseThrow(),
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
  )

  // command(client_adapter_ptr: *const c_void, channel: usize, command_type: RequestType,
  //         arg_count: c_ulong, args: *const usize, args_len: *const c_ulong,
  //         route_bytes: *const u8, route_bytes_len: usize) -> *mut CommandResult
  // For async client, returns null immediately (result comes via callback)
  val command: MethodHandle = linker.downcallHandle(
    lib.find("command").orElseThrow(),
    FunctionDescriptor.of(
      ValueLayout.ADDRESS,     // return: *mut CommandResult (null for async)
      ValueLayout.ADDRESS,     // client_adapter_ptr
      ValueLayout.JAVA_LONG,   // channel (callback id)
      ValueLayout.JAVA_INT,    // command_type: RequestType (u32 enum)
      ValueLayout.JAVA_LONG,   // arg_count: c_ulong
      ValueLayout.ADDRESS,     // args: *const usize (array of pointers)
      ValueLayout.ADDRESS,     // args_len: *const c_ulong (array of lengths)
      ValueLayout.ADDRESS,     // route_bytes: *const u8 (null for standalone)
      ValueLayout.JAVA_LONG    // route_bytes_len: usize
    )
  )

  // free_command_response(command_response_ptr: *mut CommandResponse)
  val freeCommandResponse: MethodHandle = linker.downcallHandle(
    lib.find("free_command_response").orElseThrow(),
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
  )

  // free_connection_response(connection_response_ptr: *const ConnectionResponse)
  val freeConnectionResponse: MethodHandle = linker.downcallHandle(
    lib.find("free_connection_response").orElseThrow(),
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
  )

  // SuccessCallback: fn(index_ptr: usize, message: *const CommandResponse) -> ()
  val successCallbackDescriptor: FunctionDescriptor = FunctionDescriptor.ofVoid(
    ValueLayout.JAVA_LONG,   // index_ptr (callback id)
    ValueLayout.ADDRESS      // message: *const CommandResponse
  )

  // FailureCallback: fn(index_ptr: usize, error_message: *const c_char, error_type: RequestErrorType) -> ()
  val failureCallbackDescriptor: FunctionDescriptor = FunctionDescriptor.ofVoid(
    ValueLayout.JAVA_LONG,   // index_ptr (callback id)
    ValueLayout.ADDRESS,     // error_message: *const c_char (null-terminated)
    ValueLayout.JAVA_INT     // error_type: RequestErrorType (C enum = i32)
  )

  // ClientType layout (repr(C) tagged union):
  //   tag: 8 bytes (discriminant aligned to pointer size; 0 = AsyncClient, 1 = SyncClient)
  //   success_callback: 8 bytes (function pointer)
  //   failure_callback: 8 bytes (function pointer)
  // Total: 24 bytes
  val CLIENT_TYPE_LAYOUT: StructLayout = MemoryLayout.structLayout(
    ValueLayout.JAVA_LONG.withName("tag"),
    ValueLayout.ADDRESS.withName("success_callback"),
    ValueLayout.ADDRESS.withName("failure_callback")
  )

  // ConnectionResponse layout (repr(C)):
  //   conn_ptr: *const c_void (8 bytes)
  //   connection_error_message: *const c_char (8 bytes)
  val CONNECTION_RESPONSE_LAYOUT: StructLayout = MemoryLayout.structLayout(
    ValueLayout.ADDRESS.withName("conn_ptr"),
    ValueLayout.ADDRESS.withName("connection_error_message")
  )
