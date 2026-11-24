#!/usr/bin/env python3
"""核对 iosMain 里用到的符号是不是从**正确的包**导入的。

## 为什么需要它

`iosMain` 只有 macOS 能编译（Windows 上 `compileKotlinIosArm64` 直接 SKIPPED），
一个写错的 import 要等一次完整的 CI 往返（排队 5–15 分钟 + 构建 5 分钟）才知道。
2026-11-25 一晚上因为这一条失败了三次：

- `DormRepairStore` 没导入（`dormRepairStore()` 的返回类型）
- `IosQiekjCredentialStore` / `IosWenquSessionStore` 导到了 `data.qiekj` / `data.wenqu`，
  实际都在 `data.session`
- `IosQiekjExpiryMarker` 反过来导到了 `data.session`，实际在 `data.qiekj`

三类都是「符号存在、包写错」，编译器要等真正编译才报，而**包路径在本机就能查**。

## 它查什么

1. 扫描 `shared/src/iosMain` 与 `shared/src/commonMain` 下所有 `.kt`，建立
   「类 / 对象 / 接口名 → 所在包」的表；**同名出现在多个包里的记为歧义、直接跳过**
   （那种情况只能靠编译器判）；
2. 对 `iosMain` 的每个文件，收集正文里用到的、以大写字母开头的标识符；
3. 逐个核对：该名字要么在同包（不需要 import），要么 import 的包与声明处一致；
4. 报出「未导入」与「导错包」两类问题。

带上 `commonMain` 是为了抓「用了共享层的类型但忘了 import」——2026-11-25 那三次里
有一次就是 `DormRepairStore` 漏导入。

## 它查不到什么

- `:app` 与 Foundation/Security 等平台库的符号——前者靠 `:app:compileDebugKotlin` 兜，
  后者 cinterop 的包名规则不同，纳入只会制造误报；
- 泛型/重载解析、签名不匹配这类**语义**错误——只有编译器能判。

## 用法

    python scripts/check_ios_symbols.py            # 有问题时以 1 退出

注释（`//` 与 KDoc）里的引用不算——它们不参与编译，缺 import 也不报错。
"""

from __future__ import annotations

import io
import os
import re
import sys

IOS_MAIN = "shared/src/iosMain"
DECL_ROOTS = ("shared/src/commonMain", IOS_MAIN)

# 声明关键字前缀（`data class` / `sealed interface` 之类都要认）
_DECL_RE = re.compile(
    r"^(?:internal |private |public |protected |abstract |sealed |open |data |enum |"
    r"annotation |value |inline |expect |actual )*(?:class|object|interface) (\w+)",
    re.M,
)


def strip_comments(src: str) -> str:
    """去掉行注释与块注释（KDoc 也算），字符串字面量里的内容原样保留即可。"""
    out = []
    i, n = 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j < 0 else j + 3
            continue
        if src[i] == '"':
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                i += 1
            continue
        out.append(src[i])
        i += 1
    return "".join(out)


def main() -> int:
    if not os.path.isdir(IOS_MAIN):
        print(f"找不到 {IOS_MAIN}（请在仓库根目录运行）")
        return 2

    # 1) 声明表（commonMain + iosMain；同名多包记为歧义、跳过）
    decl: dict[str, str] = {}
    ambiguous: set[str] = set()
    files: list[str] = []
    for root_dir in DECL_ROOTS:
        if not os.path.isdir(root_dir):
            print(f"找不到 {root_dir}（请在仓库根目录运行）")
            return 2
        for root, _dirs, names in os.walk(root_dir):
            for name in names:
                if not name.endswith(".kt"):
                    continue
                path = os.path.join(root, name)
                if root_dir == IOS_MAIN:
                    files.append(path)
                src = io.open(path, encoding="utf-8").read()
                pkg = re.search(r"^package ([\w.]+)", src, re.M)
                if not pkg:
                    continue
                for m in _DECL_RE.finditer(src):
                    symbol, package = m.group(1), pkg.group(1)
                    if symbol in decl and decl[symbol] != package:
                        ambiguous.add(symbol)
                    decl[symbol] = package

    # 2) 逐文件核对
    problems: list[str] = []
    for path in sorted(files):
        src = io.open(path, encoding="utf-8").read()
        pkg_m = re.search(r"^package ([\w.]+)", src, re.M)
        own_pkg = pkg_m.group(1) if pkg_m else ""
        imports: dict[str, str] = {}
        for m in re.finditer(r"^import ([\w.]+)\.(\w+)$", src, re.M):
            imports[m.group(2)] = m.group(1)
        body = strip_comments(src)
        body = "\n".join(l for l in body.split("\n") if not l.startswith("import "))
        used = set(re.findall(r"\b([A-Z]\w+)\b", body))
        for symbol in sorted(used):
            if symbol in ambiguous:
                continue  # 同名出现在多个包，只有编译器能判
            actual = decl.get(symbol)
            if actual is None:
                continue  # 不是共享层声明的（:app 或 Foundation/Security）
            if actual == own_pkg:
                continue  # 同包，不需要 import
            got = imports.get(symbol)
            if got is None:
                problems.append(f"{path}: 用到 {symbol}（在 {actual}）但没有 import")
            elif got != actual:
                problems.append(f"{path}: {symbol} 实际在 {actual}，却从 {got} 导入")

    for line in problems:
        print(line)
    print(f"checked={len(files)} problems={len(problems)} ambiguous={len(ambiguous)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
