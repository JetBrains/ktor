package io.ktor.server.host.cio

import io.ktor.application.*
import io.ktor.host.*
import io.ktor.response.*
import io.ktor.routing.*

object CIO : ApplicationHostFactory<CoroutinesHttpHost> {
    override fun create(environment: ApplicationHostEnvironment): CoroutinesHttpHost {
        return CoroutinesHttpHost(environment)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val s = embeddedServer(CIO, 9096) {
            routing {
                get("/") {
                    call.respondText("Hello, World!")
                }
            }
        }

        s.start(true)
    }
}