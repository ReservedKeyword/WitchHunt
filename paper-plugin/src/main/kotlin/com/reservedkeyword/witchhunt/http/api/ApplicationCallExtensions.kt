package com.reservedkeyword.witchhunt.http.api

import com.reservedkeyword.witchhunt.models.http.api.SuccessResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

suspend fun ApplicationCall.handleRequest(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        e.printStackTrace()

        respondError(
            statusCode = HttpStatusCode.InternalServerError,
            message = e.message ?: "Unknown error"
        )
    }
}

suspend fun ApplicationCall.respondError(statusCode: HttpStatusCode, message: String) {
    respond(
        statusCode, SuccessResponse(
            success = false,
            message = message
        )
    )
}

suspend fun ApplicationCall.respondSuccess(message: String) {
    respond(
        HttpStatusCode.OK, SuccessResponse(
            success = true,
            message = message
        )
    )
}
