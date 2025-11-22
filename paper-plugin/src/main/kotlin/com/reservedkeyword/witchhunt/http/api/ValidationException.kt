package com.reservedkeyword.witchhunt.http.api

import io.ktor.http.*

data class ValidationException(
    val statueCode: HttpStatusCode,
    override val message: String
) : Exception()
