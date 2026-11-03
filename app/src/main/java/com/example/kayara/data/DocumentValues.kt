package com.example.kayara.data

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Date

/** Values shared by the Supabase document API and the existing screen models. */
sealed class FieldValue {
    internal abstract fun json(): JSONObject

    private class Transform(val operation: String, val amount: Number? = null) : FieldValue() {
        override fun json() = JSONObject().put("__op", operation).apply {
            amount?.let { put("amount", it) }
        }
    }

    companion object {
        fun serverTimestamp(): FieldValue = Transform("timestamp")
        fun delete(): FieldValue = Transform("delete")
        fun increment(value: Long): FieldValue = Transform("increment", value)
        fun increment(value: Double): FieldValue = Transform("increment", value)
    }
}

class SetOptions private constructor() {
    companion object { fun merge() = SetOptions() }
}

class DatabaseException(message: String, val code: Code, cause: Throwable? = null,
    val serverCode: String? = null, val retryAfterSeconds: Int = 0) :
    Exception(message, cause) {
    enum class Code { UNAVAILABLE, DEADLINE_EXCEEDED, PERMISSION_DENIED, CONFLICT, INVALID_ARGUMENT, UNKNOWN }
}

internal object DocumentJson {
    fun encode(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is FieldValue -> value.json()
        is Timestamp -> JSONObject().put("__type", "timestamp")
            .put("seconds", value.seconds).put("nanoseconds", value.nanoseconds)
        is Date -> encode(Timestamp(value))
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (key, item) -> put(key as String, encode(item)) }
        }
        is Iterable<*> -> JSONArray().apply { value.forEach { put(encode(it)) } }
        is Array<*> -> JSONArray().apply { value.forEach { put(encode(it)) } }
        is String, is Number, is Boolean -> value
        else -> throw IllegalArgumentException("Unsupported database value: ${value.javaClass.simpleName}")
    }

    fun decode(value: Any?): Any? = when {
        value == null || value === JSONObject.NULL -> null
        value is JSONObject && value.optString("__type") == "timestamp" ->
            Timestamp(value.getLong("seconds"), value.getInt("nanoseconds"))
        value is JSONObject -> value.keys().asSequence().associateWith { decode(value.get(it)) }
        value is JSONArray -> (0 until value.length()).map { decode(value.get(it)) }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    fun model(value: Any?, type: Type): Any? {
        if (value == null) return null
        if (type is ParameterizedType && type.rawType == List::class.java) {
            return (value as List<*>).map { model(it, type.actualTypeArguments[0]) }
        }
        val klass = type as? Class<*> ?: return value
        if (klass.isInstance(value)) return value
        if (value is Number) return when (klass) {
            Int::class.java, Int::class.javaObjectType -> value.toInt()
            Long::class.java, Long::class.javaObjectType -> value.toLong()
            Double::class.java, Double::class.javaObjectType -> value.toDouble()
            Float::class.java, Float::class.javaObjectType -> value.toFloat()
            else -> value
        }
        if (value !is Map<*, *>) return value
        // Screen DTOs have default constructors. Preserve their defaults for absent fields.
        return klass.getDeclaredConstructor().newInstance().also { instance ->
            klass.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    if (value.containsKey(field.name) && value[field.name] != null) {
                        field.isAccessible = true
                        field.set(instance, model(value[field.name], field.genericType))
                    }
                }
        }
    }
}

class DocumentSnapshot internal constructor(
    val reference: DocumentReference,
    val data: Map<String, Any?>?,
    internal val version: Long = 0
) {
    val id: String get() = reference.id
    fun exists() = data != null
    fun contains(field: String): Boolean {
        val keys = field.split('.')
        var current: Any? = data
        for (key in keys) {
            val map = current as? Map<*, *> ?: return false
            if (!map.containsKey(key)) return false
            current = map[key]
        }
        return true
    }
    fun get(field: String): Any? = field.split('.').fold(data as Any?) { current, key ->
        (current as? Map<*, *>)?.get(key)
    }
    fun getString(field: String) = get(field) as? String
    fun getBoolean(field: String) = get(field) as? Boolean
    fun getLong(field: String) = (get(field) as? Number)?.toLong()
    fun getDouble(field: String) = (get(field) as? Number)?.toDouble()
    fun getTimestamp(field: String) = get(field) as? Timestamp
    // There are no local pending timestamp estimates: every snapshot comes from Postgres.
    enum class ServerTimestampBehavior { NONE, ESTIMATE, PREVIOUS }
    fun getTimestamp(field: String, behavior: ServerTimestampBehavior) = getTimestamp(field)

    @Suppress("UNCHECKED_CAST")
    fun <T> toObject(klass: Class<T>): T? = DocumentJson.model(data, klass) as T?
}

class QuerySnapshot internal constructor(val documents: List<DocumentSnapshot>) : Iterable<DocumentSnapshot> {
    val isEmpty: Boolean get() = documents.isEmpty()
    fun size() = documents.size
    override fun iterator() = documents.iterator()
}

fun interface ListenerRegistration { fun remove() }
