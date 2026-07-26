package com.fintracker.tax.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationTest {

    @Test
    fun `test integrity endpoint returns valid genesis hash`() = testApplication {
        application {
            module()
        }

        client.get("/api/v1/events/integrity").apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = bodyAsText()
            assertTrue(body.contains("integrityValid"))
        }
    }
}
