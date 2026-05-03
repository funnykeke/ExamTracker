# 报考掌中宝 - 体制内考试全流程管理

专为考公、考编、国企招聘考生打造的报考管理工具 —— AI 智能解析公告，关键节点日历提醒，告别错过报名、漏掉缴费、忘记打准考证。

本项目由 [Claude Code](https://claude.ai/code) + DeepSeek-V4-Pro 驱动开发。

## 解决什么问题？

体制内招聘考试流程漫长、环节繁多：公告发布 → 报名 → 缴费 → 打印准考证 → 笔试 → 成绩查询 → 资格复审 → 面试 → 体检……每个环节都有截止日期，且公告经常分批次发布（今天出笔试公告，两周后又出面试公告）。光靠人脑记、Excel 列，稍不留神就错过了。

**报考掌中宝**把这件事自动化了：

1. **贴个公告链接，AI 帮你读完** — 不用手动逐字段填写，大模型自动提取单位、岗位、时间节点等所有关键信息
2. **时间线一目了然** — 从报名到入职，全流程可视化
3. **同步到手机日历** — 关键节点自动创建日历提醒，提前 1 小时 / 1 天 / 7 天通知
4. **公告分批发布也不怕** — 自己随时增删时间点，面试材料审核、体检等后续环节想加就加

## 功能一览

### 智能公告解析
粘贴考试公告网页链接，调用 AI 自动提取：
- 招考单位、岗位名称、岗位代码、招聘人数
- 报名时间、缴费时间、笔试时间、面试时间等全部关键节点
- 考试科目、成绩计算方式、面试形式等详细信息

### 内置搜索
不知道公告链接？App 内直接搜索，内嵌浏览器打开必应，找到公告后一键确认，自动开始提取。

### 全流程时间线
自动生成报名截止、缴费截止、打印准考证、笔试、成绩公布、资格复审、面试等关键节点，按时间轴展示，一目了然。

### 自定义时间点
公告分批发？手动添加/编辑/删除任意时间点（如面试材料审核、体检、政审等），每个时间点可独立同步到手机日历。

### 系统日历同步
- 一键将整场考试所有时间点同步到手机日历
- 或单独同步某个自定义时间点
- 自动设置多级提醒（提前 1 小时、1 天、3 天、7 天等）
- 修改时间点后再次同步，自动替换旧的日历事件

### 报名信息记录
保存报名账号、密码，以及实际报考的岗位名称和代码，换设备登录报名系统时不再手忙脚乱。

### 多视图管理
- **列表视图**：卡片式总览所有考试
- **表格视图**：横向对比各考试信息
- **全局时间线**：跨考试查看所有即将到来的节点

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM (ViewModel + StateFlow) |
| 数据库 | Room (SQLite) |
| 网络请求 | OkHttp |
| 网页解析 | Jsoup |
| AI 提取 | SiliconFlow API (DeepSeek-V3) |
| 序列化 | kotlinx-serialization |
| 配置存储 | DataStore Preferences |
| 日历同步 | CalendarContract ContentProvider |

## 下载安装

从 [Releases](https://github.com/funnykeke/ExamTracker/releases) 页面下载最新 APK 直接安装。

或自行构建：

```bash
git clone git@github.com:funnykeke/ExamTracker.git
cd ExamTracker
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**环境要求**：Android Studio Hedgehog+ | JDK 17 | Android SDK 34

## API Key 配置

AI 公告解析功能需要 SiliconFlow API Key。在应用内设置页面填入即可。
注册地址：[siliconflow.cn](https://siliconflow.cn)（新用户有免费额度）

不使用 AI 解析功能不影响其他功能正常使用。

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 搜索公告和 API 调用 |
| READ/WRITE CALENDAR | 考试时间节点同步到系统日历 |

## License

MIT
