/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents an HTTP status code and description.
 * @param value is a numeric code.
 * @param description is free form description of a status.
 */
@Suppress("unused")
public data class HttpStatusCode(val value: Int, val description: String) : Comparable<HttpStatusCode> {
    override fun toString(): String = "$value $description"

    override fun equals(other: Any?): Boolean = other is HttpStatusCode && other.value == value

    override fun hashCode(): Int = value.hashCode()

    /**
     * Returns a copy of `this` code with a description changed to [value].
     */
    public fun description(value: String): HttpStatusCode = copy(description = value)

    override fun compareTo(other: HttpStatusCode): Int = value - other.value

    @Suppress("KDocMissingDocumentation", "PublicApiImplicitType")
    public companion object {
        // =============================================================================================================
        // Disclaimer
        // Adding a new status code here please remember [allStatusCodes] as well
        //

        public val Continue: HttpStatusCode = HttpStatusCode(100, "Continue")
        public val SwitchingProtocols: HttpStatusCode = HttpStatusCode(101, "Switching Protocols")
        public val Processing: HttpStatusCode = HttpStatusCode(102, "Processing")

        public val OK: HttpStatusCode = HttpStatusCode(200, "OK")
        public val Created: HttpStatusCode = HttpStatusCode(201, "Created")
        public val Accepted: HttpStatusCode = HttpStatusCode(202, "Accepted")

        public val NonAuthoritativeInformation: HttpStatusCode =
            HttpStatusCode(203, "Non-Authoritative Information")

        public val NoContent: HttpStatusCode = HttpStatusCode(204, "No Content")
        public val ResetContent: HttpStatusCode = HttpStatusCode(205, "Reset Content")
        public val PartialContent: HttpStatusCode = HttpStatusCode(206, "Partial Content")
        public val MultiStatus: HttpStatusCode = HttpStatusCode(207, "Multi-Status")

        public val MultipleChoices: HttpStatusCode = HttpStatusCode(300, "Multiple Choices")
        public val MovedPermanently: HttpStatusCode = HttpStatusCode(301, "Moved Permanently")
        public val Found: HttpStatusCode = HttpStatusCode(302, "Found")
        public val SeeOther: HttpStatusCode = HttpStatusCode(303, "See Other")
        public val NotModified: HttpStatusCode = HttpStatusCode(304, "Not Modified")
        public val UseProxy: HttpStatusCode = HttpStatusCode(305, "Use Proxy")
        public val SwitchProxy: HttpStatusCode = HttpStatusCode(306, "Switch Proxy")
        public val TemporaryRedirect: HttpStatusCode = HttpStatusCode(307, "Temporary Redirect")
        public val PermanentRedirect: HttpStatusCode = HttpStatusCode(308, "Permanent Redirect")

        public val BadRequest: HttpStatusCode = HttpStatusCode(400, "Bad Request")
        public val Unauthorized: HttpStatusCode = HttpStatusCode(401, "Unauthorized")
        public val PaymentRequired: HttpStatusCode = HttpStatusCode(402, "Payment Required")
        public val Forbidden: HttpStatusCode = HttpStatusCode(403, "Forbidden")
        public val NotFound: HttpStatusCode = HttpStatusCode(404, "Not Found")
        public val MethodNotAllowed: HttpStatusCode = HttpStatusCode(405, "Method Not Allowed")
        public val NotAcceptable: HttpStatusCode = HttpStatusCode(406, "Not Acceptable")

        public val ProxyAuthenticationRequired: HttpStatusCode =
            HttpStatusCode(407, "Proxy Authentication Required")

        public val RequestTimeout: HttpStatusCode = HttpStatusCode(408, "Request Timeout")
        public val Conflict: HttpStatusCode = HttpStatusCode(409, "Conflict")
        public val Gone: HttpStatusCode = HttpStatusCode(410, "Gone")
        public val LengthRequired: HttpStatusCode = HttpStatusCode(411, "Length Required")
        public val PreconditionFailed: HttpStatusCode = HttpStatusCode(412, "Precondition Failed")
        public val PayloadTooLarge: HttpStatusCode = HttpStatusCode(413, "Payload Too Large")
        public val RequestURITooLong: HttpStatusCode = HttpStatusCode(414, "Request-URI Too Long")

        public val UnsupportedMediaType: HttpStatusCode = HttpStatusCode(415, "Unsupported Media Type")

        public val RequestedRangeNotSatisfiable: HttpStatusCode =
            HttpStatusCode(416, "Requested Range Not Satisfiable")

        public val ExpectationFailed: HttpStatusCode = HttpStatusCode(417, "Expectation Failed")
        public val UnprocessableEntity: HttpStatusCode = HttpStatusCode(422, "Unprocessable Entity")
        public val Locked: HttpStatusCode = HttpStatusCode(423, "Locked")
        public val FailedDependency: HttpStatusCode = HttpStatusCode(424, "Failed Dependency")
        public val TooEarly: HttpStatusCode = HttpStatusCode(425, "Too Early")
        public val UpgradeRequired: HttpStatusCode = HttpStatusCode(426, "Upgrade Required")
        public val TooManyRequests: HttpStatusCode = HttpStatusCode(429, "Too Many Requests")

        public val RequestHeaderFieldTooLarge: HttpStatusCode =
            HttpStatusCode(431, "Request Header Fields Too Large")

        public val InternalServerError: HttpStatusCode = HttpStatusCode(500, "Internal Server Error")
        public val NotImplemented: HttpStatusCode = HttpStatusCode(501, "Not Implemented")
        public val BadGateway: HttpStatusCode = HttpStatusCode(502, "Bad Gateway")
        public val ServiceUnavailable: HttpStatusCode = HttpStatusCode(503, "Service Unavailable")
        public val GatewayTimeout: HttpStatusCode = HttpStatusCode(504, "Gateway Timeout")

        public val VersionNotSupported: HttpStatusCode =
            HttpStatusCode(505, "HTTP Version Not Supported")

        public val VariantAlsoNegotiates: HttpStatusCode = HttpStatusCode(506, "Variant Also Negotiates")
        public val InsufficientStorage: HttpStatusCode = HttpStatusCode(507, "Insufficient Storage")

        /**
         * All known status codes
         */
        public val allStatusCodes: List<HttpStatusCode> = allStatusCodes()

        private val statusCodesMap: Map<Int, HttpStatusCode> = allStatusCodes.associateBy { it.value }

        /**
         * Creates an instance of [HttpStatusCode] with the given numeric value.
         */
        public fun fromValue(value: Int): HttpStatusCode {
            return statusCodesMap[value] ?: HttpStatusCode(value, "Unknown Status Code")
        }
    }
}

internal fun allStatusCodes(): List<HttpStatusCode> = listOf(
    HttpStatusCode.Continue,
    HttpStatusCode.SwitchingProtocols,
    HttpStatusCode.Processing,
    HttpStatusCode.OK,
    HttpStatusCode.Created,
    HttpStatusCode.Accepted,
    HttpStatusCode.NonAuthoritativeInformation,
    HttpStatusCode.NoContent,
    HttpStatusCode.ResetContent,
    HttpStatusCode.PartialContent,
    HttpStatusCode.MultiStatus,
    HttpStatusCode.MultipleChoices,
    HttpStatusCode.MovedPermanently,
    HttpStatusCode.Found,
    HttpStatusCode.SeeOther,
    HttpStatusCode.NotModified,
    HttpStatusCode.UseProxy,
    HttpStatusCode.SwitchProxy,
    HttpStatusCode.TemporaryRedirect,
    HttpStatusCode.PermanentRedirect,
    HttpStatusCode.BadRequest,
    HttpStatusCode.Unauthorized,
    HttpStatusCode.PaymentRequired,
    HttpStatusCode.Forbidden,
    HttpStatusCode.NotFound,
    HttpStatusCode.MethodNotAllowed,
    HttpStatusCode.NotAcceptable,
    HttpStatusCode.ProxyAuthenticationRequired,
    HttpStatusCode.RequestTimeout,
    HttpStatusCode.Conflict,
    HttpStatusCode.Gone,
    HttpStatusCode.LengthRequired,
    HttpStatusCode.PreconditionFailed,
    HttpStatusCode.PayloadTooLarge,
    HttpStatusCode.RequestURITooLong,
    HttpStatusCode.UnsupportedMediaType,
    HttpStatusCode.RequestedRangeNotSatisfiable,
    HttpStatusCode.ExpectationFailed,
    HttpStatusCode.UnprocessableEntity,
    HttpStatusCode.Locked,
    HttpStatusCode.FailedDependency,
    HttpStatusCode.TooEarly,
    HttpStatusCode.UpgradeRequired,
    HttpStatusCode.TooManyRequests,
    HttpStatusCode.RequestHeaderFieldTooLarge,
    HttpStatusCode.InternalServerError,
    HttpStatusCode.NotImplemented,
    HttpStatusCode.BadGateway,
    HttpStatusCode.ServiceUnavailable,
    HttpStatusCode.GatewayTimeout,
    HttpStatusCode.VersionNotSupported,
    HttpStatusCode.VariantAlsoNegotiates,
    HttpStatusCode.InsufficientStorage
)

/**
 * Checks if a given status code is a success code according to HTTP standards.
 *
 * Codes from 200 to 299 are considered to be successful.
 */
public fun HttpStatusCode.isSuccess(): Boolean = value in (200 until 300)
