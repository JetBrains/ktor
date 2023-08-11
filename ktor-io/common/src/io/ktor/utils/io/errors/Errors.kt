package io.ktor.utils.io.errors

public expect open class IOException(message: String, cause: Throwable?) : Exception {
    public constructor(message: String)
}

public expect open class EOFException(message: String) : IOException

public expect open class UnknownServiceException(message: String) : IOException
