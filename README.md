# ExamTracker - 考试追踪助手

一个帮助追踪中国体制内考试（公务员、事业单位、国企等）全流程的 Android 应用。

## 功能

- **公告智能解析**：输入考试公告网址，通过 AI 自动提取关键字段（单位、岗位、时间节点等），无需手动填写
- **WebView 搜索**：内嵌浏览器搜索公告，找到后一键确认开始智能提取
- **全流程时间线**：自动生成报名截止、缴费截止、打印准考证、笔试、成绩公布、资格复审、面试等关键时间节点
- **自定义时间点**：支持用户自行添加/编辑/删除时间点（如面试材料审核、体检等），灵活应对分开发布的公告
- **系统日历同步**：批量或逐条同步时间点到系统日历，附带多级提醒（1小时、1天、3天、7天等）
- **报名信息管理**：保存报名账号/密码、实际报考岗位名称和代码
- **Excel 表格视图**：所有考试数据以表格形式总览，方便对比筛选
- **全局时间线**：跨考试的时间线视图，按时间排序查看所有即将到来的节点
- **点击复制**：详情页文本字段点击即可复制到剪贴板
- **链接跳转**：公告链接、报名网址点击跳转系统浏览器

## 技术栈

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose + Material3 |
| 架构 | MVVM (ViewModel + StateFlow) |
| 数据库 | Room (SQLite) |
| 网络 | OkHttp + Jsoup |
| AI 提取 | SiliconFlow API (DeepSeek-V3) |
| 序列化 | kotlinx-serialization |
| 偏好存储 | DataStore Preferences |
| 日历 | CalendarContract ContentProvider |

## 项目结构

```
app/src/main/java/com/examtracker/
├── MainActivity.kt              # 入口 Activity
├── ExamTrackerApp.kt            # Application，持有数据库实例
├── data/
│   ├── api/
│   │   ├── SiliconFlowApi.kt    # AI 公告解析接口
│   │   └── WebFetcher.kt        # 网页抓取工具
│   ├── db/
│   │   ├── ExamEntity.kt        # 考试实体
│   │   ├── CustomTimelineEvent.kt  # 自定义时间点实体
│   │   ├── ExamDao.kt           # 考试 DAO
│   │   ├── CustomTimelineEventDao.kt  # 自定义时间点 DAO
│   │   └── ExamDatabase.kt      # Room 数据库（版本 3）
│   ├── repository/
│   │   ├── ExamRepository.kt    # 数据仓库
│   │   └── SeedData.kt          # 示例数据
│   └── SettingsStore.kt         # DataStore 配置（API Key）
├── navigation/
│   └── NavGraph.kt              # 导航图
├── ui/
│   ├── add/                     # 添加考试页面
│   ├── detail/                  # 考试详情页
│   ├── list/                    # 考试列表页
│   ├── table/                   # 表格视图
│   ├── timeline/                # 全局时间线
│   ├── components/              # 公共组件（时间线、卡片等）
│   └── theme/                   # Material3 主题
└── util/
    └── CalendarSync.kt          # 系统日历同步工具
```

## 构建运行

### 环境要求

- Android Studio Hedgehog (2023.1) 或更高
- JDK 17
- Android SDK 34
- Kotlin 1.9+

### 步骤

```bash
# 克隆仓库
git clone https://github.com/nichuzhishi/ExamTracker.git
cd ExamTracker

# 构建
./gradlew assembleDebug

# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### API Key 配置

应用使用 SiliconFlow API 进行公告智能提取，首次使用需在应用内设置页面填入 API Key。
可在 [siliconflow.cn](https://siliconflow.cn) 注册获取。

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 网络搜索和 API 调用 |
| READ_CALENDAR | 读取已同步的日历事件 |
| WRITE_CALENDAR | 创建/删除日历提醒 |

## License

MIT
