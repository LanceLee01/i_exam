# Fix: regex 缺少右中括号

## 问题
`SolvePipeline.kt:471` 的 `parseL4Answer` 中的 regex 最后一个字符类 `[\s.、:：)）]` 缺少 `]`，导致整个正则无效，编译报错 "missing closing bracket"。

## 修复

在 `[\s.、:：)）]` 之后加上缺失的 `]`：

```kotlin
val pattern = Regex("""(?:[\\[【第]?(\d+)[\\]】题]?[\s.、:：)）]*)([^\\[【第\n]+?)(?=\s*[\\[【第]?\d+[\\]】题]?[\s.、:：)）]|$)""", RegexOption.MULTILINE)
```

改为：
```kotlin
val pattern = Regex("""(?:[\\[【第]?(\d+)[\\]】题]?[\s.、:：)）]*)([^\\[【第\n]+?)(?=\s*[\\[【第]?\d+[\\]】题]?[\s.、:：)）]|$)""", RegexOption.MULTILINE)
```

## TODOs

- [ ] 1. **补上缺失的 `]`**

  改 `SolvePipeline.kt:471`：
  `[\s.、:：)）]` 后面加上 `]`，变成 `[\s.、:：)）]`

  编译验证 + 安装。
