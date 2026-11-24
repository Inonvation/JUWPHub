package edu.jxslu.schedule.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS 的加密存储原语：Keychain（`kSecClassGenericPassword`）。
 *
 * ## 为什么是这个形状
 *
 * Android 的凭证落在 `EncryptedSharedPreferences`（`MasterKey` + Keystore），那是 Android
 * 专有能力；Kotlin/Native 上没有等价类。iOS 的对应物是 Keychain，而 Keychain 的 C API
 * （`SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete`）参数是 `CFDictionary`，
 * 直接散落在各个凭证实现里会让每个存储都重复一遍 CoreFoundation 的桥接代码。
 *
 * 所以这里只收成四个动作：**存、取、删单条、删整组**。上层（[IosSessionStores]）按
 * 「service + account」两个键拼自己的逻辑，一行 CF 代码都不用写。
 *
 * ## 安全属性
 *
 * `kSecAttrAccessibleAfterFirstUnlock`：设备首次解锁后即可读，**锁屏也能读**。
 * 这是必须的——后台同步与通知要读 token，而那时手机多半在锁屏。
 * 不用 `WhenUnlocked`（锁屏读不到），也不用 `Always`（已废弃且不加密迁移）。
 *
 * 这些条目**不进 iCloud 钥匙串**（没有 `kSecAttrSynchronizable`），与 Android 侧
 * 「凭证排除出云备份与设备迁移」的口径一致：凭证只留在这一台设备上。
 *
 * ## 失败怎么表现
 *
 * 四个动作都不抛异常：读不到回 `null`，写/删失败回 `false`。理由与共享层其他存储缝一致
 * ——凭证读失败等于「未登录」，页面据此显示登录表单，比崩掉好。
 * 调用方**不要**把 `false` 当成「用户没登录过」来清数据：写失败时旧值可能还在。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了（Kotlin/Native 的 Apple 目标需要 macOS），只能靠 CI 验证。
 * 用到的 API 面刻意压到最小：四个 `SecItem*`、`CFDictionaryCreateMutable` /
 * `CFDictionaryAddValue` / `CFStringCreateWithCString` / `CFDataCreate` 与 `CFRelease`。
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosKeychain {

    /** 读一个字符串；不存在或不是字符串回 `null`。 */
    fun get(service: String, account: String): String? = memScoped {
        val dict = CFDictionaryCreateMutable(null, 0, null, null) ?: return null
        val svc = newCFString(service)
        val acc = newCFString(account)
        try {
            CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dict, kSecAttrService, svc)
            CFDictionaryAddValue(dict, kSecAttrAccount, acc)
            CFDictionaryAddValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(dict, kSecMatchLimit, kSecMatchLimitOne)

            val holder = alloc<CFTypeRefVar>()
            if (SecItemCopyMatching(dict, holder.ptr) != errSecSuccess) return null
            val data: CFDataRef? = holder.value?.reinterpret()
            if (data == null) return null
            val length = CFDataGetLength(data).toInt()
            val bytes = CFDataGetBytePtr(data)
            try {
                if (bytes == null || length <= 0) return null
                bytes.readBytes(length).decodeToString()
            } finally {
                CFRelease(data)
            }
        } finally {
            CFRelease(dict)
            CFRelease(svc)
            CFRelease(acc)
        }
    }

    /**
     * 写一个字符串（覆盖写）。
     *
     * 实现是「先删再添」而不是 `SecItemUpdate`：少一条分支——`SecItemUpdate` 要先判断
     * 条目在不在，判断和更新之间还能被别的进程插一脚。删+添最坏的结果是「条目短暂不存在」，
     * 而写入发生在登录成功之后，那一瞬间没有读者。
     */
    fun set(service: String, account: String, value: String): Boolean {
        delete(service, account)
        return memScoped {
            val dict = CFDictionaryCreateMutable(null, 0, null, null) ?: return false
            val svc = newCFString(service)
            val acc = newCFString(account)
            val data = newCFData(value.encodeToByteArray())
            try {
                CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionaryAddValue(dict, kSecAttrService, svc)
                CFDictionaryAddValue(dict, kSecAttrAccount, acc)
                CFDictionaryAddValue(dict, kSecValueData, data)
                CFDictionaryAddValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
                SecItemAdd(dict, null) == errSecSuccess
            } finally {
                CFRelease(dict)
                CFRelease(svc)
                CFRelease(acc)
                CFRelease(data)
            }
        }
    }

    /** 删一个条目。删不存在的条目也算成功（幂等）。 */
    fun delete(service: String, account: String): Boolean = memScoped {
        val dict = CFDictionaryCreateMutable(null, 0, null, null) ?: return false
        val svc = newCFString(service)
        val acc = newCFString(account)
        try {
            CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dict, kSecAttrService, svc)
            CFDictionaryAddValue(dict, kSecAttrAccount, acc)
            // 不用看返回值：errSecItemNotFound 与 errSecSuccess 在这里等价
            SecItemDelete(dict)
            true
        } finally {
            CFRelease(dict)
            CFRelease(svc)
            CFRelease(acc)
        }
    }

    /**
     * 删掉某个 service 下的全部条目（不指定 account）。
     *
     * 「退出登录清干净」用它：一个功能的凭证键名以后可能增加，逐条删会漏。
     */
    fun deleteAll(service: String): Boolean = memScoped {
        val dict = CFDictionaryCreateMutable(null, 0, null, null) ?: return false
        val svc = newCFString(service)
        try {
            CFDictionaryAddValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(dict, kSecAttrService, svc)
            SecItemDelete(dict)
            true
        } finally {
            CFRelease(dict)
            CFRelease(svc)
        }
    }

    private fun newCFString(value: String): CFStringRef? = memScoped {
        // CFStringCreateWithCString 会把字节拷进去，memScoped 结束后 CFString 仍然有效
        CFStringCreateWithCString(null, value.cstr.ptr, kCFStringEncodingUTF8)
    }

    private fun newCFData(bytes: ByteArray): CFDataRef? {
        if (bytes.isEmpty()) return CFDataCreate(null, null, 0)
        return bytes.usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
        }
    }
}
