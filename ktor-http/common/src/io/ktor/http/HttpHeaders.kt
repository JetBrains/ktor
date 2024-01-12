/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

@Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType", "MayBeConstant")
public object HttpHeaders {
    // Permanently registered standard HTTP headers
    // The list is taken from http://www.iana.org/assignments/message-headers/message-headers.xml#perm-headers

    public val Accept: String = "Accept"
    public val AcceptCharset: String = "Accept-Charset"
    public val AcceptEncoding: String = "Accept-Encoding"
    public val AcceptLanguage: String = "Accept-Language"
    public val AcceptRanges: String = "Accept-Ranges"
    public val Age: String = "Age"
    public val Allow: String = "Allow"

    // Application-Layer Protocol Negotiation, HTTP/2
    public val ALPN: String = "ALPN"
    public val AuthenticationInfo: String = "Authentication-Info"
    public val Authorization: String = "Authorization"
    public val CacheControl: String = "Cache-Control"
    public val Connection: String = "Connection"
    public val ContentDisposition: String = "Content-Disposition"
    public val ContentEncoding: String = "Content-Encoding"
    public val ContentLanguage: String = "Content-Language"
    public val ContentLength: String = "Content-Length"
    public val ContentLocation: String = "Content-Location"
    public val ContentRange: String = "Content-Range"
    public val ContentType: String = "Content-Type"
    public val Cookie: String = "Cookie"

    // WebDAV Search
    public val DASL: String = "DASL"
    public val Date: String = "Date"

    // WebDAV
    public val DAV: String = "DAV"
    public val Depth: String = "Depth"

    public val Destination: String = "Destination"
    public val ETag: String = "ETag"
    public val Expect: String = "Expect"
    public val Expires: String = "Expires"
    public val From: String = "From"
    public val Forwarded: String = "Forwarded"
    public val Host: String = "Host"
    public val HTTP2Settings: String = "HTTP2-Settings"
    public val If: String = "If"
    public val IfMatch: String = "If-Match"
    public val IfModifiedSince: String = "If-Modified-Since"
    public val IfNoneMatch: String = "If-None-Match"
    public val IfRange: String = "If-Range"
    public val IfScheduleTagMatch: String = "If-Schedule-Tag-Match"
    public val IfUnmodifiedSince: String = "If-Unmodified-Since"
    public val LastModified: String = "Last-Modified"
    public val Location: String = "Location"
    public val LockToken: String = "Lock-Token"
    public val Link: String = "Link"
    public val MaxForwards: String = "Max-Forwards"
    public val MIMEVersion: String = "MIME-Version"
    public val OrderingType: String = "Ordering-Type"
    public val Origin: String = "Origin"
    public val Overwrite: String = "Overwrite"
    public val Position: String = "Position"
    public val Pragma: String = "Pragma"
    public val Prefer: String = "Prefer"
    public val PreferenceApplied: String = "Preference-Applied"
    public val ProxyAuthenticate: String = "Proxy-Authenticate"
    public val ProxyAuthenticationInfo: String = "Proxy-Authentication-Info"
    public val ProxyAuthorization: String = "Proxy-Authorization"
    public val PublicKeyPins: String = "Public-Key-Pins"
    public val PublicKeyPinsReportOnly: String = "Public-Key-Pins-Report-Only"
    public val Range: String = "Range"
    public val Referrer: String = "Referer"
    public val RetryAfter: String = "Retry-After"
    public val ScheduleReply: String = "Schedule-Reply"
    public val ScheduleTag: String = "Schedule-Tag"
    public val SecWebSocketAccept: String = "Sec-WebSocket-Accept"
    public val SecWebSocketExtensions: String = "Sec-WebSocket-Extensions"
    public val SecWebSocketKey: String = "Sec-WebSocket-Key"
    public val SecWebSocketProtocol: String = "Sec-WebSocket-Protocol"
    public val SecWebSocketVersion: String = "Sec-WebSocket-Version"
    public val Server: String = "Server"
    public val SetCookie: String = "Set-Cookie"

    // Atom Publishing
    public val SLUG: String = "SLUG"
    public val StrictTransportSecurity: String = "Strict-Transport-Security"
    public val TE: String = "TE"
    public val Timeout: String = "Timeout"
    public val Trailer: String = "Trailer"
    public val TransferEncoding: String = "Transfer-Encoding"
    public val Upgrade: String = "Upgrade"
    public val UserAgent: String = "User-Agent"
    public val Vary: String = "Vary"
    public val Via: String = "Via"
    public val Warning: String = "Warning"
    public val WWWAuthenticate: String = "WWW-Authenticate"

    // CORS
    public val AccessControlAllowOrigin: String = "Access-Control-Allow-Origin"
    public val AccessControlAllowMethods: String = "Access-Control-Allow-Methods"
    public val AccessControlAllowCredentials: String = "Access-Control-Allow-Credentials"
    public val AccessControlAllowHeaders: String = "Access-Control-Allow-Headers"

    public val AccessControlRequestMethod: String = "Access-Control-Request-Method"
    public val AccessControlRequestHeaders: String = "Access-Control-Request-Headers"
    public val AccessControlExposeHeaders: String = "Access-Control-Expose-Headers"
    public val AccessControlMaxAge: String = "Access-Control-Max-Age"

    // Unofficial de-facto headers
    public val XHttpMethodOverride: String = "X-Http-Method-Override"
    public val XForwardedHost: String = "X-Forwarded-Host"
    public val XForwardedServer: String = "X-Forwarded-Server"
    public val XForwardedProto: String = "X-Forwarded-Proto"
    public val XForwardedFor: String = "X-Forwarded-For"

    public val XForwardedPort: String = "X-Forwarded-Port"

    public val XRequestId: String = "X-Request-ID"
    public val XCorrelationId: String = "X-Correlation-ID"
    public val XTotalCount: String = "X-Total-Count"

    /**
     * Check if [header] is unsafe. Header is unsafe if listed in [UnsafeHeadersList]
     */
    public fun isUnsafe(header: String): Boolean = UnsafeHeadersArray.any { it.equals(header, ignoreCase = true) }

    private val UnsafeHeadersArray: Array<String> = arrayOf(TransferEncoding, Upgrade)

    @Deprecated(
        "Use UnsafeHeadersList instead.",
        replaceWith = ReplaceWith("HttpHeaders.UnsafeHeadersList"),
        level = DeprecationLevel.ERROR
    )
    public val UnsafeHeaders: Array<String>
        get() = UnsafeHeadersArray.copyOf()

    /**
     * A list of header names that are not safe to use unless it is low-level engine implementation.
     */
    public val UnsafeHeadersList: List<String> = UnsafeHeadersArray.asList()

    /**
     * Validates header [name] throwing [IllegalHeaderNameException] when the name is not valid.
     */
    public fun checkHeaderName(name: String) {
        name.forEachIndexed { index, ch ->
            if (ch <= ' ' || isDelimiter(ch)) {
                throw IllegalHeaderNameException(name, index)
            }
        }
    }

    /**
     * Validates header [value] throwing [IllegalHeaderValueException] when the value is not valid.
     */
    public fun checkHeaderValue(value: String) {
        value.forEachIndexed { index, ch ->
            if (ch < ' ' && ch != '\u0009') {
                throw IllegalHeaderValueException(value, index)
            }
        }
    }
}

/**
 * Thrown when an attempt to set unsafe header detected. A header is unsafe if listed in [HttpHeaders.UnsafeHeadersList].
 */
public class UnsafeHeaderException(header: String) : IllegalArgumentException(
    "Header(s) $header are controlled by the engine and " +
        "cannot be set explicitly"
)

/**
 * Thrown when an illegal header name was used.
 * A header name should only consist from visible characters
 * without delimiters "double quote" and the following characters: `(),/:;<=>?@[\]{}`.
 * @property headerName that was tried to use
 * @property position at which validation failed
 */
public class IllegalHeaderNameException(public val headerName: String, public val position: Int) :
    IllegalArgumentException(
        "Header name '$headerName' contains illegal character '${headerName[position]}'" +
            " (code ${(headerName[position].code and 0xff)})"
    )

/**
 * Thrown when an illegal header value was used.
 * A header value should only consist from visible characters, spaces and/or HTAB (0x09).
 * @property headerValue that was tried to use
 * @property position at which validation failed
 */
public class IllegalHeaderValueException(public val headerValue: String, public val position: Int) :
    IllegalArgumentException(
        "Header value '$headerValue' contains illegal character '${headerValue[position]}'" +
            " (code ${(headerValue[position].code and 0xff)})"
    )

private fun isDelimiter(ch: Char): Boolean = ch in "\"(),/:;<=>?@[\\]{}"
