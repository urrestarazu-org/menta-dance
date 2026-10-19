package com.menta.android.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedRefreshTokenStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = EncryptedRefreshTokenStore(context)

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun persistsRefreshTokenEncryptedAcrossStoreInstances() {
        val refreshToken = "1eb290ea-c132-42eb-869f-fd57c44c0f54"

        store.replace(refreshToken)

        assertEquals(refreshToken, EncryptedRefreshTokenStore(context).read())
        assertFalse(
            context.getSharedPreferences("encrypted_refresh_token", Context.MODE_PRIVATE)
                .all
                .values
                .contains(refreshToken),
        )
    }

    @Test
    fun replacesThePreviousRefreshToken() {
        store.replace("1eb290ea-c132-42eb-869f-fd57c44c0f54")
        store.replace("c4018fd5-b642-4a52-a331-a54ac95cd752")

        assertEquals("c4018fd5-b642-4a52-a331-a54ac95cd752", store.read())
        assertFalse(
            context.getSharedPreferences("encrypted_refresh_token", Context.MODE_PRIVATE)
                .all
                .values
                .contains("1eb290ea-c132-42eb-869f-fd57c44c0f54"),
        )
    }

    @Test
    fun clearsTheEncryptedRefreshToken() {
        store.replace("1eb290ea-c132-42eb-869f-fd57c44c0f54")

        store.clear()

        assertNull(store.read())
        assertFalse(
            context.getSharedPreferences("encrypted_refresh_token", Context.MODE_PRIVATE)
                .contains("ciphertext"),
        )
    }
}
