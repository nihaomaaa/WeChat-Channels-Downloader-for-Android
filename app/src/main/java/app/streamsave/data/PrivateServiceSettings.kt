package app.streamsave.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.streamsave.parser.PrivateConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PrivateServiceState(val endpoint: String = "", val configured: Boolean = false)

/** Only the private service access token is stored here; the Tencent session stays on the server. */
class PrivateServiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("private_service", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(readState())
    val state = mutable.asStateFlow()
    private fun readState() = PrivateServiceState(prefs.getString("endpoint", "").orEmpty(), prefs.contains("token"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("private-service-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("private-service-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun connection(): PrivateConnection? {
        val endpoint = prefs.getString("endpoint", "").orEmpty()
        val value = prefs.getString("token", null) ?: return null
        return try {
            val parts = value.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            PrivateConnection(endpoint, String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) {
            throw java.io.IOException("私人服务访问密钥无法读取，请在设置中重新保存")
        }
    }
    @Synchronized fun save(endpoint: String, newToken: String) {
        val normalized = PrivateConnection.normalizeEndpoint(endpoint)
        if (normalized != mutable.value.endpoint && newToken.isBlank())
            throw IllegalArgumentException("更换服务地址时，请填写对应的访问密钥")
        val token = newToken.trim().ifBlank { connection()?.token.orEmpty() }
        require(Regex("[A-Za-z0-9._~-]{24,256}").matches(token)) { "访问密钥需要 24–256 位字母、数字或 . _ ~ -；请使用部署时生成的密钥" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("endpoint", normalized).putString("token", encoded).commit()) { "无法保存配置，请检查手机存储空间" }
        mutable.value = readState()
    }
    @Synchronized fun clear() {
        check(prefs.edit().clear().commit()) { "无法清除配置" }
        mutable.value = readState()
    }
}

