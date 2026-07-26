package com.fintracker.tax.api.routes

import com.fintracker.tax.core.model.EventType
import com.fintracker.tax.core.model.TaxEvent
import com.fintracker.tax.core.ports.EventStorePort
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class StatementUploadResponse(
    val status: String,
    val message: String,
    val eventsIngested: Int = 0
)

@Serializable
data class IntegrityStatusResponse(
    val integrityValid: Boolean,
    val latestHash: String
)

fun Route.statementRoutes(eventStore: EventStorePort) {
    route("/api/v1/statements") {
        post("/upload") {
            var fileName = ""
            var password = ""
            var fileBytes: ByteArray? = null

            val multipart = call.receiveMultipart()
            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: "statement.pdf"
                        fileBytes = part.streamProvider().readBytes()
                    }
                    is PartData.FormItem -> {
                        if (part.name == "password") {
                            password = part.value
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileBytes == null) {
                call.respond(HttpStatusCode.BadRequest, StatementUploadResponse("ERROR", "No file uploaded."))
                return@post
            }

            // Write temporary file
            val tempFile = File.createTempFile("uploaded_", "_$fileName")
            tempFile.writeBytes(fileBytes!!)

            try {
                // Execute Python ephemeral CLI parser with optional password
                val args = mutableListOf(
                    "python3", "-m", "fintracker_parsers.cli",
                    "--file", tempFile.absolutePath,
                    "--type", if (fileName.endsWith(".csv", ignoreCase = true)) "broker_csv" else "cas"
                )
                if (password.isNotEmpty()) {
                    args.add("--password")
                    args.add(password)
                }

                val pb = ProcessBuilder(args).directory(File("parsers"))
                pb.environment()["PYTHONPATH"] = "${File("parsers/src").absolutePath}:${File("/app/parsers/src").absolutePath}"
                val process = pb.start()

                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                process.waitFor()

                if (stdout.contains("\"status\": \"SUCCESS\"")) {
                    val root = Json.parseToJsonElement(stdout).jsonObject
                    val eventsArray = root["events"]?.jsonArray ?: kotlinx.serialization.json.buildJsonArray {}
                    val ingested = mutableListOf<TaxEvent>()

                    for (elem in eventsArray) {
                        val obj = elem.jsonObject
                        ingested.add(
                            TaxEvent(
                                id = obj["id"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString(),
                                assetId = obj["assetId"]?.jsonPrimitive?.content ?: "ASSET",
                                assetName = obj["assetName"]?.jsonPrimitive?.content ?: "Asset Name",
                                isin = obj["isin"]?.jsonPrimitive?.content,
                                eventType = EventType.valueOf(obj["eventType"]?.jsonPrimitive?.content ?: "ACQUISITION"),
                                eventDate = LocalDate.parse(obj["eventDate"]?.jsonPrimitive?.content ?: "2026-01-01"),
                                units = java.math.BigDecimal(obj["units"]?.jsonPrimitive?.content ?: "0"),
                                pricePerUnit = java.math.BigDecimal(obj["pricePerUnit"]?.jsonPrimitive?.content ?: "0"),
                                grossAmount = java.math.BigDecimal(obj["grossAmount"]?.jsonPrimitive?.content ?: "0"),
                                sourceDocumentId = fileName,
                                ingestedAt = Clock.System.now()
                            )
                        )
                    }

                    val insertedHashes = eventStore.appendEvents(ingested)

                    call.respond(
                        HttpStatusCode.OK,
                        StatementUploadResponse(
                            status = "SUCCESS",
                            message = "Ingested ${ingested.size} transactions into DuckDB ledger.",
                            eventsIngested = ingested.size
                        )
                    )
                } else {
                    val msg = if (stderr.isNotEmpty()) stderr else "Password might be incorrect or PDF unreadable."
                    call.respond(
                        HttpStatusCode.OK,
                        StatementUploadResponse(
                            status = "ERROR",
                            message = msg,
                            eventsIngested = 0
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    StatementUploadResponse("ERROR", "Parser failed: ${e.message}")
                )
            } finally {
                tempFile.delete()
            }
        }
    }

    route("/api/v1/events") {
        get {
            val events = eventStore.getAllEvents()
            call.respond(events)
        }

        get("/integrity") {
            val isIntact = eventStore.verifyLedgerIntegrity()
            call.respond(
                IntegrityStatusResponse(
                    integrityValid = isIntact,
                    latestHash = (eventStore.getLatestEventHash() ?: "GENESIS")
                )
            )
        }
    }
}
