package com.menta.android.data.remote

import com.menta.android.domain.model.Role
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalEncodingApi::class)
class JwtRoleReaderTest {
    @Test
    fun reads_every_known_role() {
        assertEquals(Role.ADMIN, JwtRoleReader.roleOf(jwt("""{"role":"ADMIN"}""")))
        assertEquals(Role.INSTRUCTOR, JwtRoleReader.roleOf(jwt("""{"role":"INSTRUCTOR"}""")))
        assertEquals(Role.STUDENT, JwtRoleReader.roleOf(jwt("""{"sub":"1","role":"STUDENT","exp":1}""")))
        assertEquals(Role.RECEPTIONIST, JwtRoleReader.roleOf(jwt("""{"role":"RECEPTIONIST"}""")))
    }

    @Test
    fun missing_role_claim_is_null() {
        assertNull(JwtRoleReader.roleOf(jwt("""{"sub":"1"}""")))
    }

    @Test
    fun non_textual_role_claim_is_null() {
        assertNull(JwtRoleReader.roleOf(jwt("""{"role":1}""")))
    }

    @Test
    fun lowercase_role_is_null() {
        assertNull(JwtRoleReader.roleOf(jwt("""{"role":"student"}""")))
    }

    @Test
    fun well_formed_but_unknown_role_is_null() {
        assertNull(JwtRoleReader.roleOf(jwt("""{"role":"SUPER_USER"}""")))
    }

    @Test
    fun malformed_payload_json_is_null() {
        assertNull(JwtRoleReader.roleOf(jwt("not json")))
    }

    @Test
    fun token_with_two_parts_is_null() {
        assertNull(JwtRoleReader.roleOf("header." + encode("""{"role":"STUDENT"}""")))
    }

    @Test
    fun bad_base64url_payload_is_null() {
        assertNull(JwtRoleReader.roleOf("header.@@@.signature"))
    }

    private fun jwt(payload: String) = "header.${encode(payload)}.signature"

    private fun encode(payload: String) =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
}
