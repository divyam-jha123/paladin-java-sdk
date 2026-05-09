# Paladin Java SDK — Architecture & Implementation Guide

This is a study guide. Read it once end-to-end and you will be able to answer any interview
question about the SDK confidently. Each section is self-contained — you can jump in.

**Table of contents**

1. [Background: what Paladin is, what an SDK is](#1-background)
2. [JSON-RPC 2.0 — the wire protocol](#2-json-rpc-20--the-wire-protocol)
3. [Bird's-eye architecture diagram](#3-birds-eye-architecture)
4. [Layer 1 — PaladinClient (the entry point)](#4-layer-1--paladinclient-the-entry-point)
5. [Layer 2 — JsonRpcTransport (the wire)](#5-layer-2--jsonrpctransport-the-wire)
6. [Layer 3 — RpcModule base + the 9 namespaces](#6-layer-3--rpcmodule-base--the-9-namespaces)
7. [Layer 4 — Types (records vs builder POJOs)](#7-layer-4--types)
8. [The Query DSL](#8-the-query-dsl)
9. [The WebSocket subscription client](#9-the-websocket-subscription-client)
10. [Exception hierarchy](#10-exception-hierarchy)
11. [Testing strategy](#11-testing-strategy)
12. [Build & release pipeline](#12-build--release-pipeline)
13. [Bugs we found by running against a real node](#13-bugs-found-by-running-against-a-real-node)
14. [What's NOT implemented yet (be honest with the mentor)](#14-whats-not-implemented-yet)
15. [Likely interview questions and how to answer](#15-likely-interview-questions)
16. [Glossary](#16-glossary)

---

## 1. Background

### What Paladin is

Paladin is a **programmable privacy** layer for EVM-compatible blockchains, hosted by
[LF Decentralized Trust](https://www.lfdecentralizedtrust.org/projects/paladin). It lets you
build private smart contracts and private tokens that settle on-chain without exposing
business data publicly.

The privacy is implemented through three "domains":

- **Noto** — notarized confidential tokens (a notary signs over hidden state)
- **Zeto** — zero-knowledge tokens (Groth16 proofs)
- **Pente** — private EVM smart contracts (off-chain execution + on-chain proofs)

Each Paladin node runs as a process that:
- Talks to an Ethereum-compatible chain (Besu in the dev stack)
- Runs the privacy domains
- Exposes a **JSON-RPC API** over HTTP and WebSocket for clients

### What an SDK is

An SDK is just a thin client library that wraps the network protocol so application
developers don't have to hand-write HTTP requests and parse JSON. The SDK's job is:

1. Marshal Java method calls into the wire format the server expects.
2. Send them over HTTP (or WebSocket).
3. Parse the response back into Java objects.
4. Surface errors as Java exceptions.

Paladin already has SDKs for **Go** ([`sdk/go/pkg/pldclient/`](https://github.com/LFDT-Paladin/paladin/tree/main/sdk/go/pkg/pldclient))
and **TypeScript** ([`sdk/typescript/src/paladin.ts`](https://github.com/LFDT-Paladin/paladin/blob/main/sdk/typescript/src/paladin.ts)).
This POC is a **port to Java**, designed to drop in as `paladin/sdk/java/`.

---

## 2. JSON-RPC 2.0 — the wire protocol

This is the single most important thing to understand. **Paladin is NOT REST.** It uses
JSON-RPC 2.0 — a different RPC convention where every call POSTs to a single URL with a JSON
body that names the method.

### Request shape

Every call looks like:

```json
{
  "jsonrpc": "2.0",
  "id": 42,
  "method": "ptx_sendTransaction",
  "params": [ { "type": "private", "from": "alice", ... } ]
}
```

- `method` — namespace + `_` + action (e.g. `ptx_sendTransaction`, `pgroup_createGroup`)
- `params` — always an **array** of positional arguments
- `id` — caller-chosen number, echoed back so async clients can correlate

### Response shape

Success:
```json
{ "jsonrpc": "2.0", "id": 42, "result": "tx-abc-123" }
```

Failure:
```json
{ "jsonrpc": "2.0", "id": 42, "error": { "code": -32000, "message": "PD012345 ..." } }
```

Both use **HTTP 200 OK** even on `error` — you check the `error` field, not the HTTP status
code. (HTTP errors only happen for transport-level problems like 502/503.)

### Paladin's RPC namespaces

Each namespace maps onto an SDK module:

| Namespace    | What it does                                           | Java module          |
|--------------|--------------------------------------------------------|----------------------|
| `ptx_*`      | Transactions — send, prepare, query, receipts, ABI     | `PtxModule`          |
| `pgroup_*`   | Privacy groups — create, send, call, messages          | `PgroupModule`       |
| `keymgr_*`   | Keys, wallets, signing                                 | `KeyManagerModule`   |
| `pstate_*`   | Schemas, states, nullifiers                            | `PStateModule`       |
| `bidx_*`     | Block index — query indexed blocks/txs/events          | `BlockIndexModule`   |
| `reg_*`      | Registry queries                                       | `RegistryModule`     |
| `transport_*`| Node identity, peer info, transport status             | `TransportModule`    |
| `domain_*`   | Installed privacy domains                              | `DomainModule`       |
| `debug_*`    | Debug-only inspection                                  | `DebugModule`        |

### WebSocket variant

For event streams (transaction receipts, blockchain events, privacy-group messages), Paladin
uses **JSON-RPC over WebSocket** with a subscribe/notify/ack protocol. Same JSON-RPC envelope,
just over a long-lived WebSocket connection on a separate port. Details in [§9](#9-the-websocket-subscription-client).

---

## 3. Bird's-eye architecture

```
                                   ┌─────────────────────────────────────────┐
                                   │             PaladinClient                │
                                   │   (facade — composes transport + modules)│
                                   └────────────────────┬────────────────────┘
                                                        │
                              ┌─────────────────────────┼─────────────────────────┐
                              ↓                         ↓                         ↓
                  ┌─────────────────────┐   ┌─────────────────────┐   ┌─────────────────────┐
                  │   JsonRpcTransport  │   │   9 RPC modules     │   │   types/* / query/* │
                  │  (interface)        │   │  ptx, pgroup, …      │   │  records, builders  │
                  └──────────┬──────────┘   └──────────┬──────────┘   └─────────────────────┘
                             ↓ implementation                  ↓ each module calls
                  ┌─────────────────────┐                 ┌─────────────────────┐
                  │ HttpJsonRpcTransport│                 │   transport.call(   │
                  │  java.net.HttpClient│ ←──────POST─────┤    method, params,  │
                  │  Basic auth         │                 │    resultType)      │
                  └─────────────────────┘                 └─────────────────────┘

                  ┌──────────────────────────────────────────────────────────────────┐
                  │   PaladinWebSocketClient                                         │
                  │   java.net.http.WebSocket. Subscribes via ptx_subscribe /        │
                  │   pgroup_subscribe, ack/nack each batch, auto-reconnect.         │
                  └──────────────────────────────────────────────────────────────────┘
```

The client is a thin facade. The real work happens in the transport.

---

## 4. Layer 1 — PaladinClient (the entry point)

[`PaladinClient.java`](../src/main/java/io/lfdt/paladin/sdk/PaladinClient.java)

What it does:
1. Holds one `JsonRpcTransport` (the wire) and **9 module instances** (one per Paladin namespace).
2. Exposes them via fluent accessors: `client.ptx()`, `client.pgroup()`, `client.keymgr()`, …
3. Provides a **builder** so you don't have to construct the transport yourself.
4. Implements `AutoCloseable` so it works in `try-with-resources`.

```java
PaladinClient client = PaladinClient.builder()
    .url("http://localhost:31548")
    .basicAuth("paladin", "paladin")
    .build();

String txId = client.ptx().sendTransaction(input);
```

### Why a builder?

The `PaladinClient` constructor needs URL, auth, an `HttpClient`, an `ObjectMapper`, two
timeouts. That's six arguments. Builders are the idiomatic Java way to handle this:

- callers only set what they care about
- defaults are sane (`HttpClient.newBuilder()`, fresh Jackson `ObjectMapper`)
- adding new options is non-breaking

### What `defaultMapper()` does and why

```java
private static ObjectMapper defaultMapper() {
  ObjectMapper m = new ObjectMapper();
  m.registerModule(new JavaTimeModule());                       // 1
  m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);    // 2
  m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 3
  return m;
}
```

1. `JavaTimeModule` — teaches Jackson about `Instant`, `LocalDate`, etc. Without it,
   anything `java.time.*` blows up at deserialization.
2. `WRITE_DATES_AS_TIMESTAMPS` off — makes Jackson emit ISO-8601 strings (`2026-05-09T00:00:00Z`)
   instead of unix epoch numbers. This matches what Paladin sends.
3. `FAIL_ON_UNKNOWN_PROPERTIES` off — **critical for forward compatibility**. If Paladin adds
   a new field to a response (e.g. a `created` timestamp on `ReceiptListener`), our SDK should
   silently accept it, not crash. We learned this the hard way — see [§13](#13-bugs-found-by-running-against-a-real-node).

---

## 5. Layer 2 — JsonRpcTransport (the wire)

### The interface

[`JsonRpcTransport.java`](../src/main/java/io/lfdt/paladin/sdk/rpc/JsonRpcTransport.java)

```java
public interface JsonRpcTransport {
  <T> T call(String method, List<Object> params, JavaType resultType);
  <T> CompletableFuture<T> callAsync(String method, List<Object> params, JavaType resultType);
  ObjectMapper mapper();
}
```

**Why an interface and not a concrete class?**

- **Testability** — tests can swap in a stub transport without firing up an HTTP server.
- **Future flexibility** — you could write a batched transport, a queued/retrying transport,
  or one that goes through a connection pool, all without touching any module code.
- **It's the right boundary** — modules shouldn't know how the wire works.

### The HTTP implementation

[`HttpJsonRpcTransport.java`](../src/main/java/io/lfdt/paladin/sdk/rpc/HttpJsonRpcTransport.java)

What it does, step by step:

1. **Build the JSON-RPC request** as a `JsonRpcRequest` record:
   ```java
   JsonRpcRequest body = JsonRpcRequest.of(id, method, params);
   ```
   `id` is from an `AtomicLong` so concurrent calls don't collide.

2. **Serialize to JSON** via Jackson:
   ```java
   byte[] payload = mapper.writeValueAsBytes(body);
   ```

3. **Send via Java 11+ `java.net.http.HttpClient`**:
   ```java
   HttpRequest.Builder rb = HttpRequest.newBuilder(endpoint)
       .timeout(requestTimeout)
       .header("Accept", "application/json")
       .header("Content-Type", "application/json")
       .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
   if (authHeader != null) rb.header("Authorization", authHeader);
   ```
   We use `sendAsync` and return a `CompletableFuture` so callers can do non-blocking IO.
   The sync `call()` method just blocks on the future.

4. **Decode the response**:
   ```java
   JsonRpcResponse decoded = mapper.readValue(body, JsonRpcResponse.class);
   if (decoded.isError()) throw new PaladinRpcException(method, decoded.error());
   return mapper.treeToValue(decoded.result(), resultType);
   ```
   Note the **two-pass** parse: we first parse into the JSON-RPC envelope (which has
   `result` typed as `JsonNode`), then convert just the `result` subtree into the requested
   Java type. This lets us reuse one envelope record across all calls.

### Authentication: HTTP Basic, not Bearer

```java
this.authHeader = (b.username != null && b.password != null)
    ? "Basic " + Base64.getEncoder()
        .encodeToString((b.username + ":" + b.password).getBytes(UTF_8))
    : null;
```

Paladin uses **HTTP Basic auth** — `Authorization: Basic <base64(user:pass)>`. Not Bearer
tokens, not API keys. This is one of the things our original POC got wrong; we corrected
it after reading the actual Paladin source.

---

## 6. Layer 3 — RpcModule base + the 9 namespaces

### Why a base class

Every module is structurally identical: take a transport, expose typed methods that wrap
specific RPC method names. Without a base class, each module would have repetitive
boilerplate. So we factor it out:

[`RpcModule.java`](../src/main/java/io/lfdt/paladin/sdk/internal/RpcModule.java)

```java
public abstract class RpcModule {
  protected final JsonRpcTransport transport;

  protected RpcModule(JsonRpcTransport transport) {
    this.transport = transport;
  }

  protected <T> T call(String method, List<Object> params, Class<T> resultType) {
    JavaType jt = transport.mapper().getTypeFactory().constructType(resultType);
    return transport.call(method, params, jt);
  }

  protected <T> T call(String method, List<Object> params, TypeReference<T> resultType) { ... }
  protected void callVoid(String method, List<Object> params) { ... }
}
```

So a module method becomes a one-liner:

```java
public String sendTransaction(TransactionInput tx) {
  return call("ptx_sendTransaction", List.of(tx), String.class);
}
```

### The 9 modules — one per namespace

Each module is a thin facade over the transport, with strongly-typed methods that match
Paladin's actual RPC method names 1:1.

#### 6.1 `PtxModule` — transactions (the biggest)

[`PtxModule.java`](../src/main/java/io/lfdt/paladin/sdk/ptx/PtxModule.java)

This is where most user code will live. It groups methods by purpose:

| Group              | Methods                                                                        |
|--------------------|--------------------------------------------------------------------------------|
| Submission         | `sendTransaction`, `sendTransactions`, `prepareTransaction`, `prepareTransactions`, `updateTransaction`, `call(TransactionCall)` |
| Queries            | `getTransaction`, `getTransactionFull`, `getTransactionByIdempotencyKey`, `queryTransactions`, `queryTransactionsFull`, `queryPendingTransactions` |
| Receipts           | `getTransactionReceipt`, `getTransactionReceiptFull`, `getDomainReceipt`, `getStateReceipt`, `queryTransactionReceipts`, `getTransactionDependencies`, `pollForReceipt` |
| Prepared txs       | `getPreparedTransaction`, `queryPreparedTransactions`                          |
| ABI                | `storeABI`, `getStoredABI`, `queryStoredABIs`, `decodeCall`, `decodeEvent`, `decodeError` |
| Key resolution     | `resolveVerifier`                                                              |
| Receipt listeners  | `createReceiptListener`, `queryReceiptListeners`, `getReceiptListener`, `startReceiptListener`, `stopReceiptListener`, `deleteReceiptListener` |

Note `pollForReceipt` is a **convenience helper** the wire doesn't have — it polls
`getTransactionReceipt` until either the receipt materializes or a timeout elapses. Modeled
after the same helper in the TS SDK (`pollForReceipt` in `paladin.ts`).

The only methods that return `Optional<T>` are the ones that map to Paladin endpoints
returning HTTP 404 / null when the resource doesn't exist (`getTransaction`,
`getTransactionReceipt`, etc.). Everything else returns the value directly or throws.

#### 6.2 `PgroupModule` — privacy groups

Privacy groups are how Pente / Noto / Zeto host their private state. The module covers:
create / get / query groups, send transactions / call view methods inside a group, and send
group messages.

The transaction shapes (`IPrivacyGroupEVMTXInput`, `IPrivacyGroupEVMCall`) are passed as
`JsonNode` for now rather than typed records — the wire shape includes `BigNumberish`
fields that don't have a clean Java equivalent yet. (See [§14](#14-whats-not-implemented-yet).)

#### 6.3 `KeyManagerModule` — keys, wallets, signing

`wallets()`, `resolveKey()`, `resolveEthAddress()`, `reverseKeyLookup()`, `queryKeys()`, `sign()`.
`resolveKey` is what tells you "what's the public key/Ethereum address for the identity called
'alice' under algorithm `ecdsa:secp256k1`?"

#### 6.4 `PStateModule` — state store

Schemas + states + nullifiers. Used by domains internally.

#### 6.5 `BlockIndexModule` — chain indexing

Wraps Paladin's block indexer (`bidx_*`). The one quirk: `getConfirmedBlockHeight` returns
a hex string on the wire (`"0x18"`), so we deserialize as `String` and `Long.decode` it.

#### 6.6 `RegistryModule` — registry queries

Paladin nodes register themselves so they can find each other. This module queries that registry.

#### 6.7 `TransportModule` — node identity / peers

`nodeName()`, `localTransports()`, `peers()`. The one renamed in the facade is `transportRpc()`
because `transport()` was already used for the underlying wire transport.

#### 6.8 `DomainModule` — installed privacy domains

Lists and inspects the privacy domains a node runs. `listDomains()` returns
`["noto", "pente", "zeto"]` against the dev stack.

#### 6.9 `DebugModule` — debug-only

Just `getTransactionStatus` for now. Mostly here to round out the surface.

---

## 7. Layer 4 — Types

We have two flavors of type, used for different roles:

### 7.1 Records — for **read-only response shapes**

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionReceipt(
    long blockNumber, String id, long sequence, boolean success,
    String transactionHash, String source, String domain, String contractAddress,
    JsonNode states, JsonNode domainReceipt, String domainReceiptError, String failureMessage) {}
```

Why records?
- **Immutable** — Java records are final, can't be mutated
- **Concise** — one line replaces a constructor + N getters + equals/hashCode/toString
- **Right semantic** — receipts come back from the server, you read them, that's it

### 7.2 Mutable POJOs with builders — for **input shapes**

```java
public final class TransactionInput {
  private TransactionType type;
  private String domain;
  private String from;
  private String to;
  private Map<String, Object> data;
  // ... getters ...

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private final TransactionInput tx = new TransactionInput();
    public Builder type(TransactionType v)  { tx.type = v;   return this; }
    public Builder from(String v)           { tx.from = v;   return this; }
    public Builder data(Map<String, ?> v)   { tx.data = (Map<String, Object>) v; return this; }
    // ...
    public TransactionInput build() {
      if (tx.type == null) throw new IllegalStateException("type is required");
      // ...
      return tx;
    }
  }
}
```

Why mutable + builder for inputs?
- `TransactionInput` has 11 optional fields. A constructor with 11 parameters is unreadable.
- Builder gives you optional, named arguments at the call site:
  ```java
  TransactionInput.builder()
      .type(TransactionType.PRIVATE)
      .from("alice")
      .domain("noto")
      .build();
  ```
- The `build()` validation enforces required fields before serialization.

### Key Jackson annotations to know

- `@JsonInclude(JsonInclude.Include.NON_NULL)` — don't emit `null` fields. Without this, an
  unset `to` field would serialize as `"to": null` and confuse the server.
- `@JsonIgnoreProperties(ignoreUnknown = true)` — accept and silently ignore fields we don't
  model. We also turn this on globally on the `ObjectMapper`.
- `@JsonValue` on enums — controls the wire form (`SubscriptionType.RECEIPTS.wire()` → `"receipts"`).
- `@JsonProperty("public")` on enum constants — when the wire uses lowercase but Java uses uppercase.

### A subtle Java generics gotcha we hit

The first version of `TransactionInput.Builder.data(Map<String, Object>)` failed to compile
when users wrote:

```java
.data(Map.of("to", "bob", "amount", "100"))
```

Because `Map.of("to","bob","amount","100")` returns `Map<String, String>`, and Java generics
are **invariant** — `Map<String, String>` is NOT a subtype of `Map<String, Object>`.

Fix: widen the parameter to `Map<String, ?>` and cast:

```java
@SuppressWarnings("unchecked")
public Builder data(Map<String, ?> v) { tx.data = (Map<String, Object>) v; return this; }
```

The cast is safe because Jackson never relies on the runtime type — it serializes by
introspection.

---

## 8. The Query DSL

[`Query.java`](../src/main/java/io/lfdt/paladin/sdk/query/Query.java)

Paladin's filter query is a JSON object that mirrors a SQL-like `WHERE` clause. Every clause
type (`equal`, `in`, `lessThan`, etc.) is an array of `{field, value}` objects:

```json
{
  "equal": [{ "field": "from", "value": "alice" }],
  "greaterThan": [{ "field": "created", "value": "2026-01-01" }],
  "limit": 50,
  "sort": ["-created"]
}
```

We expose this as a Java builder:

```java
Query q = Query.builder()
    .equal("from", "alice")
    .greaterThan("created", "2026-01-01")
    .limit(50)
    .sort("-created")
    .build();
```

The `@JsonInclude(JsonInclude.Include.NON_EMPTY)` annotation on the `Query` class is
important: it suppresses **empty** lists (e.g. if you don't add any `like` clauses, the
serialized JSON won't have `"like": []`).

---

## 9. The WebSocket subscription client

[`PaladinWebSocketClient.java`](../src/main/java/io/lfdt/paladin/sdk/ws/PaladinWebSocketClient.java)

This is the most intricate piece of code in the SDK. The wire dance:

```
Client                                         Server
  │                                              │
  │── HTTP upgrade to WebSocket ──────────────→  │
  │                                              │
  │── { method:"ptx_subscribe",                  │
  │     params:["receipts", "my-listener"],      │
  │     id:1 } ───────────────────────────────→  │
  │                                              │
  │  ←──────── { id:1, result:"sub-xyz" } ───────│  (subscription ID assigned)
  │                                              │
  │  ←── { method:"ptx_subscription",            │
  │       params:{                               │
  │         subscription:"sub-xyz",              │
  │         result:{ batchId:1, receipts:[...] } │
  │       }} ───────────────────────────────────│  (a batch arrives)
  │                                              │
  │── { method:"ptx_ack",                        │
  │     params:["sub-xyz"], id:2 } ───────────→  │  (must ack to get more)
  │                                              │
  │  ←── { method:"ptx_subscription", … } ──────│  (next batch)
  │                                              │
  │      ... repeat until close ...              │
```

Key rules:
- The server only sends the **next batch** after you ack the previous one. If you stop
  acking, the stream stalls.
- Subscriptions for receipts/blockchain events use `ptx_subscribe`/`ptx_ack`; subscriptions
  for privacy-group messages use `pgroup_subscribe`/`pgroup_ack`. Same protocol, different
  prefix.
- Each `ptx_subscribe` requires a **server-side persistent listener** to exist first
  (created via `ptx_createReceiptListener`). The listener stores configuration like "start
  from sequence 0" or "only receipts from domain X".

### Implementation walk-through

The client is built on Java 17's `java.net.http.WebSocket` API — **no extra runtime
dependencies**, just the JDK.

```java
public final class PaladinWebSocketClient implements AutoCloseable, WebSocketSender {
  // configuration
  private final URI uri;
  private final List<Subscription> subscriptions;
  private final SubscriptionListener listener;
  private final ObjectMapper mapper;

  // runtime state
  private final AtomicLong nextId = new AtomicLong(1);
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Map<Long, String>   pendingSubscribes    = new ConcurrentHashMap<>();
  private final Map<String, String> activeSubscriptions  = new ConcurrentHashMap<>();
  private volatile WebSocket socket;
```

The two maps are critical:
- `pendingSubscribes`: maps **request ID → subscription name** for in-flight subscribes.
  When the server replies with `{id:N, result:"sub-xyz"}`, we look up which subscribe that
  was for and record `sub-xyz → name`.
- `activeSubscriptions`: maps **server subscription ID → friendly name**. So when a
  notification comes in carrying `subscription:"sub-xyz"`, we can tell the user
  "this is your `my-receipt-listener` subscription firing."

### Receiving messages

```java
public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
  buffer.append(data);
  if (last) {
    String message = buffer.toString();
    buffer.setLength(0);
    handleMessage(message);
  }
  webSocket.request(1);   // re-arm; without this, no more messages arrive
  return null;
}
```

Note `webSocket.request(1)` — Java's WebSocket API uses **back-pressure**. After every
message you must call `request(N)` to ask for the next N. We always request 1 at a time.

### Auto-reconnect with exponential backoff

If the connection drops, we re-open with exponentially increasing delay:

```java
private void scheduleReconnect() {
  if (!reconnectEnabled || closed.get()) return;
  long delay = Math.min(
      reconnectInitialMs * (long) Math.pow(2, Math.min(reconnectAttempts, 16)),
      reconnectMaxMs);
  reconnectAttempts++;
  scheduler.schedule(this::openSocket, delay, TimeUnit.MILLISECONDS);
}
```

On reconnect, we re-issue every subscribe. The server-side listener persists across
disconnects (that's the whole point of the listener abstraction), so the client can resume
cleanly from where it left off.

### Why `org.java_websocket` only as a **test** dependency

The production WS client uses the JDK's built-in `HttpClient.WebSocket`. The
`org.java-websocket` library is only a **test scope** dep — we use it to spin up an
in-process WebSocket server in `PaladinWebSocketClientTest` so we can drive the full
subscribe → notify → ack loop without ever leaving the JVM.

---

## 10. Exception hierarchy

```
RuntimeException
   │
   └── PaladinException                  base for anything the SDK throws
         │
         └── PaladinRpcException         server returned a JSON-RPC error envelope
```

[`PaladinException.java`](../src/main/java/io/lfdt/paladin/sdk/PaladinException.java) /
[`PaladinRpcException.java`](../src/main/java/io/lfdt/paladin/sdk/PaladinRpcException.java)

Why both?
- **Transport-level failure** (HTTP 500, malformed JSON, connection refused, interrupt) →
  `PaladinException`. You may want to retry.
- **Server-level failure** (JSON-RPC `error` field, e.g. "PD012345 not found") →
  `PaladinRpcException` with `.code()` and `.rpcMessage()` so callers can branch on the code.

Both extend `RuntimeException` (unchecked) — checked exceptions on every SDK call would be
miserable to use.

---

## 11. Testing strategy

Three test classes types, three different mocking levels.

### 11.1 HTTP tests with `MockWebServer`

[`HttpJsonRpcTransportTest.java`](../src/test/java/io/lfdt/paladin/sdk/rpc/HttpJsonRpcTransportTest.java),
[`PtxModuleTest.java`](../src/test/java/io/lfdt/paladin/sdk/ptx/PtxModuleTest.java),
[`PgroupModuleTest.java`](../src/test/java/io/lfdt/paladin/sdk/pgroup/PgroupModuleTest.java)

We use OkHttp's `MockWebServer` — it spins up a real HTTP server on a random port, you
enqueue canned responses, and you can inspect what request the SDK actually sent:

```java
server.enqueue(new MockResponse()
    .setHeader("Content-Type", "application/json")
    .setBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"tx-123\"}"));

String txId = client.ptx().sendTransaction(input);

RecordedRequest req = server.takeRequest();
JsonNode body = mapper.readTree(req.getBody().readUtf8());
assertThat(body.get("method").asText()).isEqualTo("ptx_sendTransaction");
```

This catches the most important class of bug: **wire-format mistakes** (wrong method name,
wrong param order, wrong JSON shape).

### 11.2 Pure-Jackson tests

[`QueryTest.java`](../src/test/java/io/lfdt/paladin/sdk/query/QueryTest.java),
[`TransactionTypesTest.java`](../src/test/java/io/lfdt/paladin/sdk/types/TransactionTypesTest.java)

For type-only verification (does `Query.builder()...build()` produce the right JSON?, do
required-field checks throw?) we just round-trip through Jackson without any HTTP.

### 11.3 Real WebSocket tests

[`PaladinWebSocketClientTest.java`](../src/test/java/io/lfdt/paladin/sdk/ws/PaladinWebSocketClientTest.java)

Crucially, **we do not mock the WebSocket transport.** WebSocket protocol details (message
framing, ping/pong, back-pressure) are subtle enough that mocks would lie to us. Instead we
spin up a real WebSocket server using the `org.java-websocket` library, inside the same JVM:

```java
FakePaladinWsServer server = new FakePaladinWsServer(addr);
server.start();

PaladinWebSocketClient client = PaladinWebSocketClient.builder()
    .url("ws://127.0.0.1:" + server.actualPort())
    .subscribe(SubscriptionType.RECEIPTS, "my-receipts")
    .listener(...)
    .build();

client.connect().get(5, TimeUnit.SECONDS);

// Server side: see the subscribe request, reply with sub ID, push a notification
JsonNode req = server.firstSubscribe.poll(5, TimeUnit.SECONDS);
server.replyToSubscribe(req.get("id").asLong(), "sub-xyz");
server.deliverNotification("ptx_subscription", "sub-xyz", "{\"batchId\":1,\"receipts\":[…]}");

// Client side: assert the listener fired with the expected event, and that an ack was sent back
SubscriptionEvent ev = events.poll(5, TimeUnit.SECONDS);
assertThat(ev.subscriptionName()).isEqualTo("my-receipts");
JsonNode ack = server.firstAck.poll(5, TimeUnit.SECONDS);
assertThat(ack.get("method").asText()).isEqualTo("ptx_ack");
```

This test caught real bugs during development that a mock would have missed.

### 11.4 Live-node verification

Beyond unit tests, the [`QuickStart`](../examples/src/main/java/io/lfdt/paladin/examples/QuickStart.java)
example drives the SDK against an actual Paladin devnet. This is what produced the output
screenshot in the README's "Live-node verification" section, and it's what found the
real-world bugs documented in [§13](#13-bugs-found-by-running-against-a-real-node).

---

## 12. Build & release pipeline

### 12.1 Gradle layout

The repo is a multi-project Gradle build:

```
sdk-java/
├── build.gradle           ← root project: the SDK
├── settings.gradle        ← declares the multi-project layout
├── gradle.properties      ← group + version
└── examples/
    └── build.gradle       ← examples subproject (depends on root)
```

`settings.gradle` declares both:

```groovy
rootProject.name = 'paladin-sdk-java'
include 'examples'
```

### 12.2 Plugins on the root project

```groovy
plugins {
    id 'java-library'      // produces a library jar (vs an application)
    id 'maven-publish'     // can publish to a Maven repo
    id 'signing'           // GPG-signs artifacts for Maven Central
    id 'jacoco'            // code coverage
    id 'idea'              // IntelliJ project file generation
}
```

`java-library` means the build produces a library jar plus separate
`paladin-sdk-java-X.Y.Z-sources.jar` and `paladin-sdk-java-X.Y.Z-javadoc.jar` — both
required for Maven Central.

### 12.3 Why `api` vs `implementation`?

```groovy
dependencies {
    api "com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}"
    api "org.slf4j:slf4j-api:${slf4jVersion}"
    // ...
}
```

`api` exposes the dependency to consumers of our jar. `implementation` would hide it.
We use `api` for Jackson because our public types use `JsonNode`, and for SLF4J because
consumers pick their own logging implementation. Everything else (`testImplementation`,
`testRuntimeOnly`) is for tests only.

### 12.4 Publishing config

```groovy
publishing {
    publications {
        maven(MavenPublication) {
            from components.java
            pom { /* name, description, scm, license, developers, … */ }
        }
    }
    repositories {
        maven {
            name = 'central'
            url = version.toString().endsWith('SNAPSHOT')
                ? 'https://central.sonatype.com/repository/maven-snapshots/'
                : 'https://central.sonatype.com/api/v1/publisher'
            credentials { username = ... ; password = ... }
        }
    }
}
```

This wires `./gradlew publish` to upload to **Sonatype Central Portal** (the modern
replacement for the old OSSRH that gets you onto Maven Central).

The `pom { ... }` block fills in the metadata Maven Central requires: project name,
description, URL, license, developers, SCM. Without all of those, the upload is rejected.

### 12.5 GPG signing for releases

```groovy
signing {
    required = { gradle.taskGraph.hasTask('publish') && !version.toString().endsWith('SNAPSHOT') }
    def signingKey = findProperty('signingKey') ?: System.getenv('SIGNING_KEY')
    def signingPassword = findProperty('signingPassword') ?: System.getenv('SIGNING_PASSWORD')
    if (signingKey && signingPassword) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign publishing.publications.maven
}
```

Maven Central requires every artifact to be **GPG-signed**. We use the in-memory PGP key
form so the GitHub Actions runner can pass an ASCII-armored key as a secret — no need to
ship a `.gpg` file.

### 12.6 GitHub Actions workflows

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) — runs on push and PR:
- Checks out the repo
- Installs JDK 17 (Temurin)
- Sets up Gradle with caching
- Runs `./gradlew build` (compile + tests + jacoco + jars)
- Uploads the test report and coverage report as artifacts

[`.github/workflows/release.yml`](../.github/workflows/release.yml) — manually triggered
with a version input:
- Same build setup
- Updates `gradle.properties` to the requested version
- Runs `./gradlew publish` with the four signing/publishing secrets injected from
  GitHub repo settings
- Creates a git tag `v<version>` and pushes it

The two workflows together implement the mentor's "repeatable release process integrated
with the current release pipeline" deliverable.

---

## 13. Bugs found by running against a real node

This is the part to highlight in your application — it shows you went past mocks.

### Bug 1: hex-string block height

**Symptom:** `./gradlew :examples:run` against the live node failed with:
```
Cannot deserialize value of type `java.lang.Long` from String "0x14"
```

**Cause:** `bidx_getConfirmedBlockHeight` returns a `0x`-prefixed hex string on the wire,
not a JSON number. Our SDK had it typed as `Long`.

**Fix:** [`BlockIndexModule.java`](../src/main/java/io/lfdt/paladin/sdk/bidx/BlockIndexModule.java)
calls as `String` and decodes:

```java
public long getConfirmedBlockHeight() {
  String hex = call("bidx_getConfirmedBlockHeight", List.of(), String.class);
  return hex == null ? 0L : Long.decode(hex);
}
```

**Lesson:** Paladin (and Ethereum-adjacent APIs in general) often serialize numbers as
hex strings to avoid JSON's 53-bit integer limitation. Receipts use plain numbers. Block
heights and gas use hex. This inconsistency is on the wire format, not something we can
"fix" — we adapt.

### Bug 2: unknown JSON properties

**Symptom:** Second run of `:examples:run` failed on `ptx_getReceiptListener`:
```
Unrecognized field "created" (class ReceiptListener), not marked as ignorable
```

**Cause:** The first run created a listener; the second run tried to look it up. Paladin's
response includes a `created` timestamp field that our `ReceiptListener` class doesn't
model. By default Jackson treats unknown fields as errors.

**Fix:** Disable `FAIL_ON_UNKNOWN_PROPERTIES` globally on the default `ObjectMapper` in
[`PaladinClient.java`](../src/main/java/io/lfdt/paladin/sdk/PaladinClient.java):

```java
m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
```

**Why globally rather than per-class?** Forward compatibility. If Paladin adds any new
field to any response, our SDK should silently accept it — never crash. This is the
correct default for **client** SDKs talking to evolving APIs.

### Bug 3: WebSocket on a different port

**Symptom:** WebSocket connect attempts went into a reconnect loop:
```
Paladin WS reconnect in 2000ms (attempt 1)
Paladin WS reconnect in 4000ms (attempt 2)
```

**Cause:** Our example used `ws://localhost:31548/ws` (port 31548). Paladin's HTTP RPC is on
31548 but WebSocket is on a **separate** port 31549. The kind config that exposes both:

```yaml
- containerPort: 31548  # Paladin1 - JSON/RPC HTTP
  hostPort: 31548
- containerPort: 31549  # Paladin1 - JSON/RPC WebSockets
  hostPort: 31549
```

**Fix:** The example now derives the WS URL by changing the port:

```java
String wsUrl = url.replace(":31548", ":31549").replaceFirst("^http", "ws") + "/ws";
```

### Bug 4: listener idempotency

**Symptom:** Second `:examples:run` got `ws no batch within 15s (chain may be quiescent)`.

**Cause:** The listener already existed and had already replayed its history on the
first run. With nothing new on the chain, there was nothing to deliver.

**Fix:** Delete-and-recreate the listener at the start of each example run, so it always
replays from sequence 0:

```java
if (client.ptx().getReceiptListener(listenerName).isPresent()) {
    client.ptx().deleteReceiptListener(listenerName);
}
client.ptx().createReceiptListener(ReceiptListener.builder()
    .name(listenerName)
    .filters(new ReceiptListener.Filters(0L, null, null))
    .build());
```

This makes the demo deterministically reproducible — every run produces the same shape of
output.

---

## 14. What's NOT implemented yet

Be **honest** with the mentor about gaps. They reward this:

1. **No high-level domain helpers.** The TS SDK has [`noto.ts`](https://github.com/LFDT-Paladin/paladin/blob/main/sdk/typescript/src/domains/noto.ts),
   [`pente.ts`](https://github.com/LFDT-Paladin/paladin/blob/main/sdk/typescript/src/domains/pente.ts),
   [`zeto.ts`](https://github.com/LFDT-Paladin/paladin/blob/main/sdk/typescript/src/domains/zeto.ts)
   which provide high-level wrappers like `noto.deploy(...)`, `pente.privateContract(...)`.
   The Java SDK has the **low-level transport and types** to build those, but doesn't
   ship them yet. That's a follow-up deliverable.

2. **`pgroup` send-transaction shapes use `JsonNode`.** `IPrivacyGroupEVMTXInput` and
   `IPrivacyGroupEVMCall` have `BigNumberish` fields. Java's `BigInteger` is the obvious
   target type but Paladin sometimes sends them as hex strings, sometimes as numbers.
   Properly modeling needs a custom Jackson deserializer module. Deferred.

3. **No integration with Paladin's root Gradle build.** Right now this is a standalone
   repo. To slot into `paladin/sdk/java/` it needs a `build.gradle` that participates in
   Paladin's root build (similar to how `sdk/typescript/build.gradle` calls out to npm).

4. **No release has actually been published.** The publishing config is wired (workflow,
   signing, Sonatype repo URL) but not yet pointed at a real Sonatype account or signed
   with a real PGP key. Mechanically ready; needs accounts to be set up.

5. **Coverage of the `transport_*` and `reg_*` namespaces is shallow.** All the methods
   are wired, but the response types are typed as `JsonNode` rather than proper records.
   Would harden once we know what users actually consume from those namespaces.

6. **No retry policy.** A request that fails with a transient error (network blip, 503)
   will throw immediately. The Go SDK uses `go-retryablehttp`. Java equivalent would be
   `Failsafe` or hand-rolled retry on `CompletableFuture`. Deferred — it's an additive
   change.

---

## 15. Likely interview questions

### "Why JSON-RPC over HTTP and not gRPC?"

Because that's what Paladin exposes. The choice was made by the Paladin team. (Note:
Paladin uses gRPC for **node-to-node** transport — `transport_localTransports` returns
`["grpc"]` — but that's peer messaging between Paladin nodes, not the client-facing API.)

### "Walk me through what happens when I call `client.ptx().sendTransaction(input)`."

1. `PtxModule.sendTransaction` calls `transport.call("ptx_sendTransaction", List.of(input), String.class)`.
2. `HttpJsonRpcTransport` builds a `JsonRpcRequest` record with `id = nextId.getAndIncrement()`, `method = "ptx_sendTransaction"`, `params = [input]`.
3. Jackson serializes that to bytes. `input` becomes the JSON object — only the non-null fields, because `@JsonInclude(NON_NULL)`.
4. We POST to the Paladin URL with `Authorization: Basic …`, `Content-Type: application/json`.
5. Paladin replies `{ "jsonrpc": "2.0", "id": 42, "result": "tx-uuid" }`.
6. We parse the envelope into `JsonRpcResponse`. `error` is null, so we extract `result` and convert it to `String`.
7. The string transaction ID returns to the caller.
8. The caller can then poll for the receipt via `pollForReceipt(txId, Duration.ofSeconds(30))`, which loops `getTransactionReceipt(txId)` until it returns non-empty or times out.

### "How does the WebSocket subscription handle reconnects?"

The WS client tracks two things: a list of `Subscription` configs (type + name) and the
mapping between server-assigned subscription IDs and client-friendly names. On a disconnect:

1. `onClose` or `onError` schedules a reconnect with exponential backoff.
2. When the new connection opens, we **re-issue every `ptx_subscribe` from the list**.
3. The server-side **persistent listener** is stateful — it remembers the last sequence
   it delivered. So when we resubscribe, the server resumes the stream from where it
   stopped (subject to the listener's filter config).
4. We clear the old `activeSubscriptions` map and rebuild it as new subscribe responses
   arrive.

The user's `SubscriptionListener` keeps receiving events through reconnects — to them
the stream is logically continuous even though the underlying socket has been replaced.

### "Why use Java 17 specifically?"

- **Records** for response types — far less boilerplate than Java 8 classes.
- **`switch` expressions** — used in the WebSocket subscribe-method dispatch.
- **`HttpClient`** with full async `CompletableFuture` support (Java 11+) and stable
  WebSocket API (Java 11+).
- 17 is the **current LTS** that any maintained Java application will have.
- Aligns with what Paladin's Solidity tooling and Besu need (they're on 17+).

### "Why `java.net.http.HttpClient` instead of OkHttp or Apache?"

Zero runtime dependencies for HTTP. The JDK's HttpClient has been production-quality since
Java 11 and supports HTTP/2, WebSocket, async I/O. Adding OkHttp would ship megabytes of
extra classes for no functional gain. The only place we use OkHttp is in tests
(`MockWebServer`) — and that's `testImplementation` scope, never reaches downstream
consumers.

### "What was the hardest bug you found?"

The hex-string block height. It was hard not because the fix was complicated but because
it could only be discovered by **running the SDK against a real Paladin node** —
MockWebServer always returned what we asked it to. That experience reinforced why the
"live verification" step is non-negotiable for SDK work.

### "If you got accepted, what's the first thing you'd build on top of this POC?"

The Pente / Noto / Zeto domain helper classes. The TS SDK has them as
`paladin/sdk/typescript/src/domains/{noto,pente,zeto}.ts`. They're high-level abstractions
like:

```java
NotoToken token = new NotoToken(client, contractAddress);
String txId = token.transfer("alice", "bob", BigInteger.valueOf(100));
```

These are pure compositions over the existing `ptx`/`pgroup` modules — no new transport
work. Likely 200-500 lines per domain. Would also unlock the more interesting integration
tests (e.g., "deploy a Noto token, mint to alice, transfer to bob, verify state").

### "How do you test the WebSocket without flakiness?"

Three things:
1. The test spins up a real WebSocket server in-process using `org.java-websocket`,
   bound to `127.0.0.1:0` (auto-assigned port). No network, no port conflicts.
2. Synchronization is via `BlockingQueue.poll(timeout)` instead of `Thread.sleep` — the
   test wakes up immediately when the event arrives.
3. Reconnect is disabled in the test (`reconnect(false)`) so the test doesn't go into a
   backoff loop on a deliberately-closed socket.

### "Walk me through the Maven Central publish flow."

`./gradlew publish` triggers:

1. `compileJava` → `:test` → `:jar` → `:sourcesJar` → `:javadocJar`.
2. `signing` plugin GPG-signs every artifact (`.jar`, `-sources.jar`, `-javadoc.jar`,
   plus the `.pom`).
3. `maven-publish` uploads to the Sonatype Central Portal at
   `https://central.sonatype.com/api/v1/publisher` with HTTP Basic auth (user token + token).
4. Sonatype validates: groupId ownership, required POM fields, signature checks, javadoc
   sanity, no SNAPSHOT in the version.
5. With `autoPublish = true`, Sonatype releases to Maven Central immediately. Without it,
   the staged release sits in a holding bay you can promote manually.

The four GitHub secrets needed (`CENTRAL_USERNAME`, `CENTRAL_PASSWORD`, `SIGNING_KEY`,
`SIGNING_PASSWORD`) plug into both the workflow and the Gradle config via
`findProperty(...) ?: System.getenv(...)`.

---

## 16. Glossary

- **JSON-RPC 2.0** — RPC protocol where every call POSTs `{jsonrpc, id, method, params}`
  to a single URL. Errors come in a structured `error` field, not as HTTP status codes.
- **Privacy domain** — a Paladin plugin that implements a privacy strategy (Noto =
  notarized, Zeto = ZK, Pente = private EVM).
- **Privacy group** — a set of Paladin nodes that share private state. Created via
  `pgroup_createGroup`.
- **Receipt listener** — a server-side persistent subscription that the WebSocket client
  attaches to. Created via `ptx_createReceiptListener`. Stores filter config and the
  last-delivered sequence number.
- **ABI** — Application Binary Interface. The Solidity-style description of a contract's
  function signatures. Paladin stores and references them by content hash (`ptx_storeABI`).
- **Verifier** — an Ethereum-compatible identity (e.g. an ECDSA address) derived from a
  named key in the key manager.
- **NodePort** — a Kubernetes service type that exposes a pod's port on every cluster
  node's IP at a fixed port number. Used by Paladin's kind config to make
  `localhost:31548` reach Paladin node 1's RPC.
- **Sonatype Central Portal** — the modern Maven Central publishing path (replaces the
  older "OSSRH" / `oss.sonatype.org` flow that's being deprecated through 2026).
- **MockWebServer** — OkHttp's tiny in-process HTTP server used in tests. You enqueue
  responses, the SDK fires requests, you assert on what was sent.

---

## Reading order suggestion

If you have ~30 minutes total before an interview:

1. **§2 (JSON-RPC 2.0)** — 5 min. Get the protocol right or nothing else makes sense.
2. **§4–§6** — 10 min. PaladinClient → transport → modules. The whole call path.
3. **§9 (WebSocket)** — 8 min. The most novel part of the SDK.
4. **§13 (Bugs found)** — 5 min. Memorize one bug story; it's gold for "have you tested it" questions.
5. **§15 (Q&A)** — 5 min. Skim the questions, make sure you can answer them.

Skip §11 / §12 / §16 unless the interviewer specifically asks about testing or release.
