/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth.ldap

import org.apache.directory.api.ldap.codec.api.LdapApiService
import org.apache.directory.api.util.*
import org.apache.directory.server.annotations.*
import org.apache.directory.server.core.api.*
import org.apache.directory.server.core.factory.*
import org.apache.directory.server.core.integ.*
import org.apache.directory.server.factory.*
import org.apache.directory.server.ldap.*
import org.junit.jupiter.api.extension.*

@Target(AnnotationTarget.CLASS)
@ExtendWith(LDAPServerExtension::class)
annotation class LDAPServerExtensionTest

/**
 * Starts and stops LDAP server before all tests begin and end.
 *
 * This is an adaption of the LDAP functionality found in
 * `org.apache.directory.server.core.integ.FrameworkRunner` for JUnit5.
 */
@CreateLdapServer(transports = [ CreateTransport(protocol = "LDAP") ])
class LDAPServerExtension : BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private var directoryService: DirectoryService? = null
    private var ldapServer: LdapServer? = null

    override fun beforeAll(context: ExtensionContext?) {
        val dsServiceFactory = DefaultDirectoryServiceFactory()
        // this is required defaults don't resolve when using annotation constructors
        val createServer = LDAPServerExtension::class.annotations.first() as CreateLdapServer
        dsServiceFactory.init(context?.displayName)

        directoryService = dsServiceFactory.directoryService
        ldapServer = ServerAnnotationProcessor.instantiateLdapServer(createServer, directoryService)

        // notice: it is just a test: never keep user password but message digest or hash with salt
        IntegrationUtils.apply(
            directoryService,
            IntegrationUtils.getUserAddLdif(
                "uid=user-test,ou=users,ou=system",
                "test".toByteArray(),
                "Test user",
                "test"
            )
        )
    }

    override fun afterAll(context: ExtensionContext?) {
        ldapServer?.stop()
        directoryService?.apply {
            shutdown()
            FileUtils.deleteDirectory(instanceLayout.instanceDirectory)
        }
    }

    override fun supportsParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Boolean {
        val param = parameterContext?.parameter ?: return false
        return param.type == Int::class.java ||
            param.type == LdapApiService::class.java
    }

    override fun resolveParameter(parameterContext: ParameterContext?, extensionContext: ExtensionContext?): Any =
        when (parameterContext?.parameter?.type) {
            Int::class.java -> ldapServer!!.port
            LdapApiService::class.java -> directoryService!!.ldapCodecService
            else -> throw IllegalArgumentException("Unexpected parameter: ${parameterContext?.parameter}")
        }
}
