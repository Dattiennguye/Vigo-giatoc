package vn.vigo.nepnha.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/** Lỗi đã được dịch sang tiếng Việt, sẵn sàng hiển thị cho người dùng. */
class ApiException(message: String, val status: Int = 0, val code: String? = null) : Exception(message)

/**
 * Client REST tối giản cho Supabase (Auth + PostgREST), dùng HttpURLConnection có sẵn trong Android:
 * không thêm thư viện mạng nặng, phù hợp máy cấu hình thấp. Mọi lời gọi chạy trên Dispatchers.IO.
 *
 * Chỉ dùng anon key; quyền thực sự do RLS và hàm SECURITY DEFINER phía máy chủ quyết định.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String,
    private val store: SessionStore,
) {
    private val refreshMutex = Mutex()
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private data class Resp(val status: Int, val body: String)

    private fun open(method: String, path: String, token: String?, prefer: String?): HttpURLConnection {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("apikey", anonKey)
        c.setRequestProperty("Authorization", "Bearer ${token ?: anonKey}")
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (prefer != null) c.setRequestProperty("Prefer", prefer)
        return c
    }

    private suspend fun raw(method: String, path: String, body: String?, token: String?, prefer: String? = null): Resp =
        withContext(Dispatchers.IO) {
            val c = open(method, path, token, prefer)
            try {
                if (body != null) {
                    c.doOutput = true
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                Resp(code, text)
            } catch (_: SocketTimeoutException) {
                throw ApiException("Máy chủ phản hồi quá lâu. Vui lòng thử lại.")
            } catch (_: IOException) {
                throw ApiException("Không có kết nối mạng. Vui lòng kiểm tra Wi-Fi hoặc dữ liệu di động.")
            } finally {
                c.disconnect()
            }
        }

    // ------------------------------------------------------------------ Auth

    private fun parseSession(json: String): Session? {
        val o = JSONObject(json)
        val access = o.optString("access_token")
        if (access.isEmpty()) return null
        val user = o.optJSONObject("user") ?: return null
        return Session(
            accessToken = access,
            refreshToken = o.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000,
            userId = user.getString("id"),
            email = user.optString("email").takeIf { it.isNotEmpty() },
        )
    }

    private fun setSession(s: Session?) {
        if (s == null) store.clear() else store.save(s)
        _session.value = s
    }

    /** @return true nếu đăng nhập ngay; false nếu cần xác nhận email trước. */
    suspend fun signUp(email: String, password: String, displayName: String): Boolean {
        val body = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("display_name", displayName)).toString()
        val r = raw("POST", "/auth/v1/signup", body, null)
        if (r.status !in 200..299) throw authError(r)
        val s = parseSession(r.body)
        setSession(s)
        return s != null
    }

    suspend fun signIn(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val r = raw("POST", "/auth/v1/token?grant_type=password", body, null)
        if (r.status !in 200..299) throw authError(r)
        setSession(parseSession(r.body) ?: throw ApiException("Phản hồi đăng nhập không hợp lệ"))
    }

    suspend fun sendPasswordReset(email: String) {
        val r = raw("POST", "/auth/v1/recover", JSONObject().put("email", email).toString(), null)
        if (r.status !in 200..299) throw authError(r)
    }

    suspend fun signOut() {
        val s = _session.value
        if (s != null) {
            // Thu hồi phiên trên máy chủ; dù mạng lỗi vẫn xóa phiên trên máy.
            runCatching { raw("POST", "/auth/v1/logout?scope=local", null, s.accessToken) }
        }
        setSession(null)
    }

    private suspend fun validToken(): String {
        val s = _session.value ?: throw ApiException("Vui lòng đăng nhập", 401)
        if (s.expiresAt - System.currentTimeMillis() > 60_000) return s.accessToken
        return refresh(s.accessToken)
    }

    private suspend fun refresh(staleToken: String): String = refreshMutex.withLock { refreshLocked(staleToken) }

    private suspend fun refreshLocked(staleToken: String): String {
        val cur = _session.value ?: throw ApiException("Vui lòng đăng nhập", 401)
        // Một coroutine khác đã làm mới xong
        if (cur.accessToken != staleToken && cur.expiresAt - System.currentTimeMillis() > 60_000) return cur.accessToken
        val r = raw(
            "POST", "/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", cur.refreshToken).toString(), null,
        )
        if (r.status == 400 || r.status == 401) {
            setSession(null)
            throw ApiException("Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.", 401)
        }
        if (r.status !in 200..299) throw authError(r)
        val s = parseSession(r.body) ?: throw ApiException("Không làm mới được phiên đăng nhập")
        setSession(s)
        return s.accessToken
    }

    // ------------------------------------------------------------------ PostgREST

    private suspend fun authed(method: String, path: String, body: String?, prefer: String? = null): String {
        var token = validToken()
        var r = raw(method, path, body, token, prefer)
        if (r.status == 401) {
            token = refresh(token)
            r = raw(method, path, body, token, prefer)
        }
        if (r.status !in 200..299) throw restError(r)
        return r.body
    }

    /** Gọi hàm RPC; trả về chuỗi JSON thô. */
    suspend fun rpc(fn: String, args: JSONObject = JSONObject()): String =
        authed("POST", "/rest/v1/rpc/$fn", args.toString())

    suspend fun rpcArray(fn: String, args: JSONObject = JSONObject()): JSONArray {
        val t = rpc(fn, args).trim()
        return when {
            t.startsWith("[") -> JSONArray(t)
            t.startsWith("{") -> JSONArray().put(JSONObject(t))
            else -> JSONArray()
        }
    }

    suspend fun select(table: String, query: String): JSONArray =
        JSONArray(authed("GET", "/rest/v1/$table?$query", null).ifBlank { "[]" })

    suspend fun insert(table: String, row: JSONObject): JSONObject {
        val t = authed("POST", "/rest/v1/$table", row.toString(), prefer = "return=representation")
        return JSONArray(t).getJSONObject(0)
    }

    // Lưu ý: HttpURLConnection không hỗ trợ PATCH, nên mọi thao tác sửa đi qua hàm RPC phía máy chủ.

    // ------------------------------------------------------------------ Lỗi

    private fun authError(r: Resp): ApiException {
        val o = runCatching { JSONObject(r.body) }.getOrNull()
        val raw = listOfNotNull(
            o?.optString("error_description"), o?.optString("msg"), o?.optString("message"), o?.optString("error"),
        ).firstOrNull { it.isNotBlank() }.orEmpty()
        val code = o?.optString("error_code")?.takeIf { it.isNotBlank() }
        val msg = when {
            raw.contains("Invalid login credentials", true) -> "Sai email hoặc mật khẩu."
            raw.contains("already registered", true) || code == "user_already_exists" -> "Email này đã được đăng ký. Hãy đăng nhập."
            raw.contains("Email not confirmed", true) -> "Email chưa được xác nhận. Hãy mở thư xác nhận trong hộp thư của bạn."
            raw.contains("Password should be", true) || code == "weak_password" -> "Mật khẩu quá yếu: cần ít nhất 6 ký tự."
            raw.contains("valid email", true) || code == "validation_failed" -> "Email không hợp lệ."
            r.status == 429 -> "Thao tác quá nhiều lần. Vui lòng đợi ít phút rồi thử lại."
            r.status >= 500 -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
            else -> raw.ifBlank { "Lỗi đăng nhập (${r.status})" }
        }
        return ApiException(msg, r.status, code)
    }

    private fun restError(r: Resp): ApiException {
        val o = runCatching { JSONObject(r.body) }.getOrNull()
        val message = o?.optString("message").orEmpty()
        val code = o?.optString("code")?.takeIf { it.isNotBlank() }
        val msg = when {
            r.status == 401 -> "Vui lòng đăng nhập lại."
            code == "42501" || r.status == 403 -> message.ifBlank { "Bạn không có quyền thực hiện thao tác này." }
                .let { if (it.contains("permission denied", true) || it.contains("row-level security", true)) "Bạn không có quyền thực hiện thao tác này." else it }
            code == "40001" || message.startsWith("CONFLICT") ->
                "Dữ liệu đã được người khác sửa trước bạn. Vui lòng tải lại và kiểm tra trước khi lưu."
            code == "PGRST202" -> "Máy chủ chưa được cập nhật đầy đủ (thiếu hàm $message). Hãy chạy các file SQL mới nhất."
            r.status >= 500 && message.isBlank() -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
            // Thông báo do các hàm SQL của Nếp Nhà tạo ra đã là tiếng Việt
            message.isNotBlank() -> message
            else -> "Có lỗi xảy ra (${r.status})."
        }
        return ApiException(msg, r.status, code)
    }

    companion object {
        fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")
    }
}
