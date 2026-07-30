package com.fintracker.tax.api

import com.fintracker.tax.api.routes.reportRoutes
import com.fintracker.tax.api.routes.statementRoutes
import com.fintracker.tax.persistence.DuckDbEventStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val eventStore = DuckDbEventStore()

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    routing {
        statementRoutes(eventStore)
        reportRoutes(eventStore)
        
        // Serve Web Cockpit UI directly from root http://127.0.0.1:8080/
        val webCockpitDir = listOf(File("web-cockpit"), File("../web-cockpit"), File("../../web-cockpit")).firstOrNull { it.exists() }
        if (webCockpitDir != null) {
            staticFiles("/", webCockpitDir) {
                default("index.html")
            }
        }
    }
}
