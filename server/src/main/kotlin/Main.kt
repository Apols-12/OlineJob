/*
package com.apols

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import kotlinx.io.buffered
import java.io.File

suspend fun main() {
    val client = HttpClient(Apache) {
        defaultRequest {
            basicAuth("apolsng@4gmail.com", "1234qwerty")
        }
    }

    val id = "e17aa164-80d2-4a09-ba70-2044f326bb93"

    try {

        val file = File("property_image.png")
        val name = file.name
        val respond = client.post("http://localhost:6800/properties/images/$id") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("description", "Property Image")
                        append(
                            "image",
                            InputProvider {
                                file.inputStream().asInput().buffered()
                            },
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                            }
                        )
                    },
                    boundary = "PropertyImageBoundary"
                )
            )
            onUpload { bytesSentTotal, contentLength ->
                println("Send $bytesSentTotal bytes from $contentLength")
            }
        }
        println(respond.status)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}*/
