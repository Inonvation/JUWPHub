package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.session.CasEnsureResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 统一认证**前置登录**对话框（DESIGN §4.27，2026-09-28 用户提议的口径）：
 *
 * 「需要教务数据的窗口（课表导入 / 报修 / 成绩单），进窗时若本地没有凭据，
 * 先在 App 内输入学号密码保存好，再自动登录，之后一直自动登录。」
 *
 * 与「手登成功后补存」（`SaveCredentialDialog`，仍在）形成两层闭环：
 * 进窗先问一次（本组件）→ 用户跳过、在网页里手登成功 → 再给一次补存机会。
 * 前置层验证通过后 OkHttp 会话即就绪，WebView 落 CAS 页时由既有的自动填表
 * （`JwAutoLogin`，凭据已存）接管提交，用户全程无需再输。
 *
 * 落库纪律与补存层一致：**先经 `cas.tryLogin` 验证、通过才保存**——坏凭据存进去
 * 会让自动续登反复撞 CAS 失败计数直至停用，比不存更糟。
 *
 * 三处入口共用（教务导入 / 学工表单 / 成绩单授权）；「先跳过」的处置由调用方定
 * （一般是本窗口不再问，手登成功后的补存弹窗也不再弹）。
 *
 * ## 为什么验证与落库是回调，而不是 `CasSession` / `CredentialVault` 参数
 *
 * 那两个类还在 `:app`（`data/session/`，介质是 OkHttp 与 `EncryptedSharedPreferences`），
 * 共享层看不见；而这个弹层本身是纯 UI。收成两个函数参数后，弹层只剩「输入 → 验证 → 落库 →
 * 回调宿主」四步，iOS 侧届时接自己的传输层与 Keychain 即可，不用改这里一行。
 *
 * [onVerify] 必须**原样抛取消异常**（结构化取消纪律）：宿主实现里不要用 `runCatching`。
 */
@Composable
fun CasLoginDialog(
    title: String,
    description: String,
    /** 验证一对凭据；宿主接自己的传输层（Android = `CasSession.tryLogin`）。 */
    onVerify: suspend (username: String, password: String) -> CasEnsureResult,
    /** 验证通过后落库；宿主接自己的凭证仓（Android = `CredentialVault.saveCas`）。 */
    onSave: (username: String, password: String) -> Unit,
    onSaved: () -> Unit,
    onSkip: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** 验证失败后的冷却（秒）：验证码/网络类失败不计闸门，没有冷却就能无限连点打 CAS。 */
    var cooldown by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onSkip() },
        title = { Text(title) },
        text = {
            DialogWindowFadeEffect()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim() },
                    label = { Text("学号") },
                    singleLine = true,
                    enabled = !busy,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("统一认证密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !busy,
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && cooldown <= 0 && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        // 不用 runCatching：它会连 CancellationException 一起吞（窗口销毁后
                        // 登录链还在后台跑完）。取消必须原样抛（结构化取消纪律）
                        val result = try {
                            onVerify(username, password)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            CasEnsureResult.Failed("登录异常，请检查网络")
                        }
                        busy = false
                        when (result) {
                            is CasEnsureResult.Ready -> {
                                onSave(username, password)
                                onSaved()
                            }
                            // 文案按结果分类（口径对齐引导页 JwStep）：密码错 / 网络不通 /
                            // 验证码 / 停用是四种不同的下一步动作，全吞成「密码错」会把
                            // 用户往错误方向带（开着 VPN 改十遍密码也没用）
                            is CasEnsureResult.Failed -> {
                                error = result.message
                                cooldown = 3
                            }
                            is CasEnsureResult.NeedsManualLogin -> {
                                error = "${result.message}（也可能密码本身有误）。可先跳过，在网页里手动登录。"
                                cooldown = 3
                            }
                            CasEnsureResult.Suspended -> {
                                error = "密码已连续错误多次，自动登录被暂停。确认密码正确后再试，或先跳过。"
                                cooldown = 5
                            }
                            else -> {
                                error = "验证没通过，请确认学号与统一认证密码"
                                cooldown = 3
                            }
                        }
                    }
                },
            ) {
                Text(if (busy) "验证中…" else if (cooldown > 0) "重试（${cooldown}s）" else "保存并登录")
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onSkip) { Text("先跳过") }
        },
    )
}
