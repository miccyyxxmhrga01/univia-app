package com.example.kayara.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.kayara.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Supabase Postgres access through the Firebase-authenticated bbco-data Edge Function. */
class SupabaseDatabase private constructor() {
    private val io = Executors.newFixedThreadPool(6)
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var foreground = true
    @Volatile private var snapshotCache: SnapshotCache? = null

    fun initializeCache(context: Context) {
        if (snapshotCache != null) return
        snapshotCache = SnapshotCache(File(context.applicationContext.cacheDir, "dashboard-snapshots-v1"))
        val auth = FirebaseAuth.getInstance()
        var previousUser = auth.currentUser?.uid
        auth.addAuthStateListener {
            val user = it.currentUser?.uid
            if (user != previousUser) snapshotCache?.invalidate()
            previousUser = user
        }
    }

    fun setForeground(value: Boolean) { foreground = value }

    companion object {
        private val shared by lazy { SupabaseDatabase() }
        fun getInstance() = shared
    }

    fun collection(path: String) = CollectionReference(this, validPath(path, document = false))
    fun collectionGroup(name: String): Query {
        require(name.isNotBlank() && '/' !in name)
        return Query(this, name, group = true)
    }
    fun batch() = WriteBatch(this)

    fun accountAccess(): Task<AccountAccess> = async {
        AccountAccess.fromJson(request("access", JSONObject()))
    }

    fun emailCodeStatus(): Task<EmailCodeReply> = async {
        EmailCodeReply.fromJson(request("otp_status", JSONObject()), status = true)
    }
    fun sendEmailCode(): Task<EmailCodeReply> = async {
        EmailCodeReply.fromJson(request("otp_send", JSONObject()), status = false)
    }
    fun verifyEmailCode(code: String): Task<EmailCodeReply> = async {
        EmailCodeReply.fromJson(request("otp_verify", JSONObject().put("code", code)), status = false)
    }
    fun emailSenderStatus(): Task<EmailSenderStatus> = async {
        EmailSenderStatus.fromJson(request("otp_sender", JSONObject()))
    }
    fun configureEmailSender(email: String, password: String): Task<EmailSenderStatus> = async {
        EmailSenderStatus.fromJson(request("otp_configure", JSONObject().put("email", email).put("password", password)))
    }

    fun watchAccountAccess(callback: (AccountAccess?, Exception?) -> Unit): ListenerRegistration = listen({
        val result = request("access", JSONObject())
        result.toString() to AccountAccess.fromJson(result)
    }, callback)

    internal fun <T> async(work: () -> T): Task<T> = Tasks.call(io, work)

    internal fun request(action: String, request: JSONObject): JSONObject {
        val user = FirebaseAuth.getInstance().currentUser
        try {
            val token = user?.let { Tasks.await(it.getIdToken(false), 20, TimeUnit.SECONDS).token }
            val connection = URL(BuildConfig.SUPABASE_URL + "/functions/v1/bbco-data")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                val bytes = JSONObject().put("action", action).put("request", request)
                    .toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val result = if (body.isBlank()) JSONObject() else JSONObject(body)
                if (status !in 200..299) {
                    val code = when {
                        result.optString("code") == "40001" -> DatabaseException.Code.CONFLICT
                        status == 401 || status == 403 -> DatabaseException.Code.PERMISSION_DENIED
                        status == 400 || status == 409 -> DatabaseException.Code.INVALID_ARGUMENT
                        status >= 500 -> DatabaseException.Code.UNAVAILABLE
                        else -> DatabaseException.Code.UNKNOWN
                    }
                    throw DatabaseException(result.optString("error", "Database request failed ($status)."), code,
                        serverCode = result.optString("code").takeIf { it.isNotBlank() },
                        retryAfterSeconds = result.optInt("retry_after", 0).coerceIn(0, 86_400))
                }
                if (FirebaseAuth.getInstance().currentUser?.uid != user?.uid) {
                    throw DatabaseException("Your sign-in session changed. Please try again.", DatabaseException.Code.PERMISSION_DENIED)
                }
                return result
            } finally { connection.disconnect() }
        } catch (error: SocketTimeoutException) {
            throw DatabaseException("The database request timed out.", DatabaseException.Code.DEADLINE_EXCEEDED, error)
        } catch (error: IOException) {
            throw DatabaseException("Unable to reach the database. Check your connection.", DatabaseException.Code.UNAVAILABLE, error)
        }
    }

    @Suppress("UNCHECKED_CAST")
    internal fun documents(response: JSONObject): List<DocumentSnapshot> {
        val rows = response.getJSONArray("documents")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            DocumentSnapshot(
                DocumentReference(this, row.getString("path")),
                DocumentJson.decode(row.getJSONObject("data")) as Map<String, Any?>,
                row.getLong("version")
            )
        }
    }

    internal fun get(reference: DocumentReference): DocumentSnapshot =
        documents(request("read", JSONObject().put("path", reference.path))).firstOrNull()
            ?: DocumentSnapshot(reference, null)

    internal fun commit(writes: List<JSONObject>, reads: Map<String, Long> = emptyMap()) {
        require(writes.size <= 500) { "A batch supports at most 500 writes." }
        request("commit", JSONObject().put("request_id", UUID.randomUUID().toString())
            .put("writes", JSONArray(writes))
            .put("reads", JSONArray(reads.map { (path, version) ->
                JSONObject().put("path", path).put("version", version)
            })))
        snapshotCache?.invalidate()
    }

    fun <T> runTransaction(work: (Transaction) -> T): Task<T> = async {
        for (attempt in 0 until 5) {
            val transaction = Transaction(this)
            val result = work(transaction)
            try {
                commit(transaction.writes, transaction.reads)
                return@async result
            } catch (error: DatabaseException) {
                if (error.code != DatabaseException.Code.CONFLICT || attempt == 4) throw error
            }
        }
        error("Transaction retry limit exceeded.")
    }

    /** Foreground listeners refresh every three seconds and emit only changed snapshots. */
    internal fun <T> listen(
        load: () -> Pair<String, T>,
        callback: (T?, Exception?) -> Unit,
        codec: SnapshotCodec<T>? = null
    ): ListenerRegistration {
        val active = AtomicBoolean(true)
        val busy = AtomicBoolean(false)
        var fingerprint: String? = null
        var failureMessage: String? = null
        var firstLoad = true
        var serverLoaded = false
        var savedFingerprint: String? = null
        var savedRevision: Long? = null
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val scheduled = scheduler.scheduleWithFixedDelay({
            if (!foreground || !active.get() || !busy.compareAndSet(false, true)) return@scheduleWithFixedDelay
            io.execute {
                try {
                    if (FirebaseAuth.getInstance().currentUser?.uid != uid) return@execute
                    val cache = snapshotCache
                    val revision = cache?.revision()
                    if (firstLoad) {
                        firstLoad = false
                        if (codec != null && cache != null && uid != null) {
                            val cached = cache.read(uid, codec.key)?.let { payload ->
                                runCatching { codec.decode(payload) }.getOrNull()
                            }
                            if (cached != null) {
                                fingerprint = cached.first
                                main.post {
                                    if (active.get() && FirebaseAuth.getInstance().currentUser?.uid == uid &&
                                        cache.revision() == revision) callback(cached.second, null)
                                }
                            }
                        }
                    }
                    val (nextFingerprint, value) = load()
                    if (!serverLoaded || nextFingerprint != fingerprint || failureMessage != null) {
                        serverLoaded = true
                        fingerprint = nextFingerprint
                        failureMessage = null
                        main.post {
                            if (active.get() && FirebaseAuth.getInstance().currentUser?.uid == uid) callback(value, null)
                        }
                    }
                    // Deliver the server result before optional disk work. Cache failures are harmless.
                    if (codec != null && cache != null && uid != null && revision != null &&
                        active.get() && FirebaseAuth.getInstance().currentUser?.uid == uid &&
                        (savedFingerprint != nextFingerprint || savedRevision != revision)) {
                        runCatching { cache.write(uid, codec.key, codec.encode(value), revision) }
                        savedFingerprint = nextFingerprint
                        savedRevision = revision
                    }
                } catch (error: Exception) {
                    if (error is DatabaseException && error.code == DatabaseException.Code.PERMISSION_DENIED) {
                        snapshotCache?.invalidate()
                    }
                    if (error.message != failureMessage) {
                        failureMessage = error.message
                        main.post {
                            if (active.get() && FirebaseAuth.getInstance().currentUser?.uid == uid) callback(null, error)
                        }
                    }
                } finally { busy.set(false) }
            }
        }, 0, 3, TimeUnit.SECONDS)
        return ListenerRegistration { active.set(false); scheduled.cancel(false) }
    }
}

internal fun validPath(path: String, document: Boolean): String {
    val segments = path.split('/')
    require(segments.none { it.isBlank() } && (segments.size % 2 == 0) == document) { "Invalid database path." }
    return path
}

open class Query internal constructor(
    internal val db: SupabaseDatabase,
    internal val path: String,
    private val group: Boolean = false,
    private val filters: Map<String, Any?> = emptyMap(),
    private val orderField: String? = null,
    private val descending: Boolean = false,
    private val count: Long? = null,
    private val last: Boolean = false
) {
    enum class Direction { ASCENDING, DESCENDING }
    fun whereEqualTo(field: String, value: Any?) =
        Query(db, path, group, filters + (field to value), orderField, descending, count, last)
    fun orderBy(field: String, direction: Direction = Direction.ASCENDING) =
        Query(db, path, group, filters, field, direction == Direction.DESCENDING, count, last)
    fun limit(value: Long): Query {
        require(value > 0)
        return Query(db, path, group, filters, orderField, descending, value)
    }
    fun limitToLast(value: Long): Query {
        require(value > 0 && orderField != null) { "A last-page query requires a positive limit and an order field." }
        return Query(db, path, group, filters, orderField, descending, value, last = true)
    }
    private fun load(): QuerySnapshot {
        val rows = mutableListOf<DocumentSnapshot>()
        do {
            val pageSize = minOf(500L, (count ?: Long.MAX_VALUE) - rows.size).toInt()
            if (pageSize <= 0) break
            val response = db.request("read", JSONObject()
                .put("collection", path).put("group", group)
                .put("filters", DocumentJson.encode(filters))
                .put("order_by", orderField ?: JSONObject.NULL).put("descending", descending.xor(last))
                .put("limit", pageSize).put("offset", rows.size))
            val page = db.documents(response)
            rows.addAll(page)
            if (page.size < pageSize) break
        } while (rows.size.toLong() < (count ?: Long.MAX_VALUE))
        return QuerySnapshot(if (last) rows.asReversed() else rows)
    }
    fun get(): Task<QuerySnapshot> = db.async { load() }
    fun addSnapshotListener(
        useCache: Boolean = false,
        callback: (QuerySnapshot?, Exception?) -> Unit
    ): ListenerRegistration = db.listen(
        load = { val snapshot = load(); snapshot.documents.joinToString { "${it.reference.path}:${it.version}" } to snapshot },
        callback = callback,
        codec = if (useCache) SnapshotCodec(
            key = JSONObject().put("collection", path).put("group", group)
                .put("filters", DocumentJson.encode(filters.toSortedMap()))
                .put("order_by", orderField ?: JSONObject.NULL).put("descending", descending)
                .put("limit", count ?: JSONObject.NULL).put("last", last).toString(),
            encode = { snapshot: QuerySnapshot -> encodeSnapshots(snapshot.documents) },
            decode = { payload ->
                val snapshot = QuerySnapshot(db.documents(JSONObject(payload)))
                snapshot.documents.joinToString { "${it.reference.path}:${it.version}" } to snapshot
            }
        ) else null
    )
}

class CollectionReference internal constructor(db: SupabaseDatabase, path: String) : Query(db, path) {
    val id: String get() = path.substringAfterLast('/')
    val parent: DocumentReference? get() = if ('/' in path) DocumentReference(db, path.substringBeforeLast('/')) else null
    fun document(id: String = UUID.randomUUID().toString()): DocumentReference {
        require(id.isNotBlank() && '/' !in id)
        return DocumentReference(db, "$path/$id")
    }
    fun add(data: Map<String, Any?>): Task<DocumentReference> = db.async {
        val ref = document()
        db.commit(listOf(write("set", ref, data)))
        ref
    }
}

class DocumentReference internal constructor(internal val db: SupabaseDatabase, val path: String) {
    init { validPath(path, document = true) }
    val id: String get() = path.substringAfterLast('/')
    val parent: CollectionReference get() = CollectionReference(db, path.substringBeforeLast('/'))
    fun collection(name: String) = db.collection("$path/$name")
    fun get(): Task<DocumentSnapshot> = db.async { db.get(this) }
    /** Register a fresh profile without ever replacing an existing account record. */
    fun create(data: Map<String, Any?>): Task<Unit> = db.async {
        try {
            db.commit(listOf(write("set", this, data)), mapOf(path to 0L))
        } catch (error: DatabaseException) {
            if (error.code == DatabaseException.Code.CONFLICT) {
                throw DatabaseException("An account profile already exists. Please sign in.", DatabaseException.Code.INVALID_ARGUMENT)
            }
            throw error
        }
    }
    fun set(data: Map<String, Any?>, options: SetOptions? = null): Task<Unit> = db.async {
        db.commit(listOf(write("set", this, data, options != null)))
    }
    fun update(data: Map<String, Any?>): Task<Unit> = db.async { db.commit(listOf(write("update", this, data))) }
    fun update(field: String, value: Any?, vararg rest: Any?): Task<Unit> = update(fieldMap(field, value, rest))
    fun delete(): Task<Unit> = db.async { db.commit(listOf(write("delete", this))) }
    fun addSnapshotListener(
        useCache: Boolean = false,
        callback: (DocumentSnapshot?, Exception?) -> Unit
    ): ListenerRegistration = db.listen(
        load = { val snapshot = db.get(this); snapshot.version.toString() to snapshot }, callback = callback,
        codec = if (useCache) SnapshotCodec(
            key = "document:$path",
            encode = { snapshot: DocumentSnapshot -> encodeSnapshots(if (snapshot.exists()) listOf(snapshot) else emptyList()) },
            decode = { payload ->
                val snapshot = db.documents(JSONObject(payload)).firstOrNull() ?: DocumentSnapshot(this, null)
                snapshot.version.toString() to snapshot
            }
        ) else null
    )
}

open class WriteBatch internal constructor(internal val db: SupabaseDatabase) {
    internal val writes = mutableListOf<JSONObject>()
    fun set(ref: DocumentReference, data: Map<String, Any?>, options: SetOptions? = null): WriteBatch = apply {
        writes.add(write("set", ref, data, options != null))
    }
    fun update(ref: DocumentReference, data: Map<String, Any?>): WriteBatch = apply { writes.add(write("update", ref, data)) }
    fun update(ref: DocumentReference, field: String, value: Any?, vararg rest: Any?) = update(ref, fieldMap(field, value, rest))
    fun delete(ref: DocumentReference): WriteBatch = apply { writes.add(write("delete", ref)) }
    fun commit(): Task<Unit> = db.async { db.commit(writes.toList()) }
}

class Transaction internal constructor(db: SupabaseDatabase) : WriteBatch(db) {
    internal val reads = mutableMapOf<String, Long>()
    fun get(ref: DocumentReference): DocumentSnapshot {
        check(writes.isEmpty()) { "Read documents before writing in a transaction." }
        return db.get(ref).also { reads[ref.path] = it.version }
    }
}

private fun write(op: String, ref: DocumentReference, data: Map<String, Any?> = emptyMap(), merge: Boolean = false) =
    JSONObject().put("op", op).put("path", ref.path).put("data", DocumentJson.encode(data)).put("merge", merge)

private fun encodeSnapshots(documents: List<DocumentSnapshot>): String = JSONObject()
    .put("documents", JSONArray(documents.map { snapshot ->
        JSONObject().put("path", snapshot.reference.path).put("version", snapshot.version)
            .put("data", DocumentJson.encode(snapshot.data))
    })).toString()

private fun fieldMap(field: String, value: Any?, rest: Array<out Any?>): Map<String, Any?> {
    require(rest.size % 2 == 0)
    return linkedMapOf(field to value).apply {
        rest.toList().chunked(2).forEach { (key, item) -> put(key as String, item) }
    }
}
