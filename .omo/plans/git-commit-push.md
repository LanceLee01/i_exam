# Plan: 提交代码到 Git

## 检查当前状态
先查看 git 状态，确认要提交什么。

## 操作
1. `git status` 看修改
2. `git diff --stat` 看文件统计
3. `git add .` 添加所有
4. `git commit -m "..."` 提交
5. `git push` 推送

## 提交信息建议
按 Wave 拆分几个 commit：
- 修复 Tavily 联网搜索导致 LLM 被干扰（搜索结果只做参考展示）
- 解决 100% 匹配题目 L1 不命中问题（contains + 括号规范化 + 多题匹配）
- 重构 L1 显示题号 + 跨层级组合答案
- 顶栏来源按行显示

或者一个总 commit 包含所有改动。
