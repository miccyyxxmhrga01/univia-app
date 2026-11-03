package com.example.kayara.data

import com.example.kayara.AdminApplicationModel
import com.google.firebase.Timestamp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DocumentValuesTest {
    @Test fun applicationDocumentsAndPreciseTimestampsSurviveTheDatabaseBoundary() {
        val json = JSONObject("""{
            "uid":"applicant", "fullName":"Test Applicant", "status":"pending",
            "documents":[{"key":"constitution","label":"Constitution","url":"https://example.test/doc.pdf","publicId":"upload-id"}],
            "createdAt":{"__type":"timestamp","seconds":1793590000,"nanoseconds":123456000}
        }""")
        val application = DocumentJson.model(DocumentJson.decode(json), AdminApplicationModel::class.java) as AdminApplicationModel
        assertEquals("applicant", application.uid)
        assertEquals("admin", application.role) // missing fields keep the screen model's defaults
        assertEquals("https://example.test/doc.pdf", application.documents.single().url)
        assertEquals("Constitution", application.documents.single().label)
        assertEquals(Timestamp(1793590000, 123456000), application.createdAt)
    }

    @Test fun timestampsNullsAndNestedListsRoundTripWithoutLosingTypes() {
        val original = mapOf("timestamp" to Timestamp(1793590000, 999999999), "optional" to null,
            "documents" to listOf(mapOf("name" to "資料 📄", "size" to 9007199254740991L)))
        val decoded = DocumentJson.decode(DocumentJson.encode(original))
        assertEquals(original, decoded)
    }

    @Test fun serverTransformsAreSentAsOperationsInsteadOfClientComputedValues() {
        val data = DocumentJson.encode(mapOf("createdAt" to FieldValue.serverTimestamp(),
            "commentCount" to FieldValue.increment(-1), "position" to FieldValue.delete())) as JSONObject
        assertEquals("timestamp", data.getJSONObject("createdAt").getString("__op"))
        assertEquals(-1, data.getJSONObject("commentCount").getInt("amount"))
        assertEquals("delete", data.getJSONObject("position").getString("__op"))
    }
}
