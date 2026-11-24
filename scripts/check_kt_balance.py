#!/usr/bin/env python3
"""Kotlin 源文件的括号平衡检查（纯词法，不解析）。

## 为什么需要它

`iosMain` 只有 CI 能编（Windows 上没有 Kotlin/Native 的 Apple 目标），一个多余的 `}`
要等一次完整的 macOS 往返才知道。2026-11-25 就是这样白等了一轮：合并 S17/S18 时
`MainViewController.kt` 留下两个重复的闭合括号，Kotlin/Native 报
`308:1 Syntax error: Expecting a top level declaration`，整个 iosMain 编不过。

这个脚本只做一件事：把注释与字符串/字符字面量抹掉，再数 `(){}[]` 是否配平。
它能抓「多一个括号 / 少一个括号」，抓不到「括号配平但放错位置」——那种只有编译器能判。

## 用法

    python scripts/check_kt_balance.py            # 扫整个仓库的 .kt
    python scripts/check_kt_balance.py shared/src/iosMain   # 只扫一个目录

不平衡时打印 `文件: 类型 左N vs 右M` 并以 1 退出。
"""

from __future__ import annotations

import io
import os
import sys

PAIRS = {"()": ("(", ")"), "{}": ("{", "}"), "[]": ("[", "]")}


def strip_comments_and_literals(src: str) -> str:
    """把注释、字符串、字符字面量替换成空格，保留其余字符的位置无关紧要。"""
    out: list[str] = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        # 行注释
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            i = n if j < 0 else j
            continue
        # 块注释（Kotlin 支持嵌套，这里按嵌套处理）
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            depth = 1
            i += 2
            while i < n and depth:
                if src.startswith("/*", i):
                    depth += 1
                    i += 2
                elif src.startswith("*/", i):
                    depth -= 1
                    i += 2
                else:
                    i += 1
            continue
        # 原始字符串 """..."""。结尾连续 4 个以上引号时，多出来的引号属于内容
        # （`Regex("""orisrc="([^"]+)"""")` 这种写法：正则要匹配一个引号），
        # 所以闭合取「连续引号串的最后三个」，整串一起吃掉。
        if src.startswith('"""', i):
            j = i + 3
            while True:
                k = src.find('"', j)
                if k < 0:
                    i = n
                    break
                run = k
                while run < n and src[run] == '"':
                    run += 1
                if run - k >= 3:
                    i = run
                    break
                j = run
            out.append(" ")
            continue
        # 普通字符串 / 字符字面量
        if c == '"' or c == "'":
            q = c
            i += 1
            while i < n:
                if src[i] == "\\":
                    i += 2
                    continue
                if src[i] == q:
                    i += 1
                    break
                i += 1
            out.append(" ")
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check(path: str) -> list[str]:
    with io.open(path, encoding="utf-8") as fh:
        stripped = strip_comments_and_literals(fh.read())
    problems = []
    for name, (left, right) in PAIRS.items():
        a, b = stripped.count(left), stripped.count(right)
        if a != b:
            problems.append(f"{path}: {name} 左{a} vs 右{b}")
    return problems


def main(argv: list[str]) -> int:
    roots = argv[1:] or ["."]
    skip = {".git", "build", ".gradle", ".agents", "node_modules"}
    all_problems: list[str] = []
    checked = 0
    for root in roots:
        if os.path.isfile(root):
            all_problems += check(root)
            checked += 1
            continue
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in skip]
            for name in filenames:
                if name.endswith(".kt"):
                    all_problems += check(os.path.join(dirpath, name))
                    checked += 1
    for line in all_problems:
        print(line)
    print(f"checked={checked} unbalanced={len(all_problems)}")
    return 1 if all_problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
