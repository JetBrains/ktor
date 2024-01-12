/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios.certificates

@Deprecated(
    "Please use 'Darwin' engine instead",
    replaceWith = ReplaceWith("CertificatePinner", "io.ktor.client.engine.darwin.certificates.CertificatePinner"),
    level = DeprecationLevel.ERROR
)
public typealias CertificatePinner = io.ktor.client.engine.darwin.certificates.CertificatePinner

@Deprecated(
    "Please use 'Darwin' engine instead",
    replaceWith = ReplaceWith("PinnedCertificate", "io.ktor.client.engine.darwin.certificates.PinnedCertificate"),
    level = DeprecationLevel.ERROR
)
public typealias PinnedCertificate = io.ktor.client.engine.darwin.certificates.PinnedCertificate
