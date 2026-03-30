# Thunder Note Android

*为闪记提供登录、闪记、聊天、收藏、合集与资料管理主链的 Android Java 客户端。*

---

## 目录

- [简介](#简介)
- [特性](#特性)
- [当前实现状态](#当前实现状态)
- [架构](#架构)
- [快速开始](#快速开始)
- [运行配置](#运行配置)
- [开发指南](#开发指南)
- [项目结构](#项目结构)
- [常见问题和解答](#常见问题和解答)

---

## 简介

`thunder-note-android` 是 Thunder Note 当前真实 Android 客户端实现，主代码包为 `com.flashnote.java`，采用 `Activity/Fragment + ViewBinding + ViewModel` 的传统 Android 结构。

当前客户端已经接通登录注册、闪记列表、聊天消息、联系人、合集、收藏、资料与设置等主线能力，并与当前后端 API 保持联调。它描述的是当前已可编译、可运行、可继续演进的 Java 客户端主模块，而不是未来多端规划的完整终态。

这个仓库当前只有 Android Java 客户端，不包含 Web、iOS 或管理后台实现。

## 特性

- 认证主线：支持启动检查、登录、注册、令牌持久化与刷新链路。
- 闪记主线：支持闪记列表、创建、编辑、删除、进入聊天和收集箱入口。
- 聊天能力：支持文本消息、媒体消息、卡片消息、分页加载、失败重试与搜索跳转。
- 联系人会话：支持联系人列表、好友请求、搜索联系人、删除联系人与联系人聊天。
- 合集能力：支持合集列表、编辑、删除与闪记归属收口。
- 收藏能力：支持收藏列表、媒体预览、卡片预览与长按菜单操作。
- 文件能力：支持图片、视频、音频、文件上传下载和本地缓存预览。
- 资料与设置：支持资料读取更新、头像修改、手动同步、手势锁、登出和服务地址设置。
- 本地能力：使用 `Room` 管理本地数据，使用 `WorkManager` 处理部分后台任务。
- 传统可维护结构：优先使用 Java、Fragment 和 ViewBinding，便于继续调试与演进。

## 当前实现状态

- 当前仓库是 Android Java 客户端主线，不是演示壳层。
- `auth`、`flashnote`、`chat`、`collection`、`favorite`、`contact` 已形成真实主链。
- `profile` 已接真实资料、同步、文件、退出登录等能力，但页面形态仍偏操作台式。
- `sync`、`file` 已进入真实链路，但当前定位仍是 MVP 最小闭环，不等同于完整离线优先体系。
- 当前 debug 包默认 `BASE_URL` 指向局域网联调地址，需要按实际本地服务修改。
- 当前 Android 真实联调接口前缀是 `/api/...`，不是旧文档中的 `/api/v1/...`。

## 架构

### 技术栈

- `Android SDK 34`
- `minSdk 26`
- `Java 17`
- `ViewBinding`
- `ViewModel + LiveData`
- `Navigation Fragment`
- `Room`
- `Retrofit + OkHttp + Gson`
- `WorkManager`
- `Media3`
- `Glide`

### 应用结构

- `FlashNoteApp`：Application 与依赖装配入口。
- `MainActivity`：宿主 Activity，负责主导航与页面切换。
- `ui/auth`：启动页、登录页、注册页。
- `ui/main`：主壳层、闪记、合集、收藏、资料等 Tab 页面。
- `ui/chat`：聊天页、卡片编辑、消息操作辅助逻辑。
- `data/repository`：仓储层，承接本地数据、网络接口和主线业务协调。
- `data/remote`：Retrofit API、DTO 与网络客户端。
- `data/local`：Room 数据库与本地实体。
- `ui/settings`：密码、手势锁、服务地址与调试相关设置。

### 当前主页面范围

- `Splash`
- `Login`
- `Register`
- `MainShell`
- `FlashNoteTab`
- `CollectionTab`
- `FavoriteTab`
- `ProfileTab`
- `Chat`
- `Settings`

## 快速开始

### 环境要求

- `Android Studio`（建议使用较新稳定版）
- `Android SDK 34`
- `JDK 17`
- 可访问的 Thunder Note Server 服务

### 1. 打开项目

使用 Android Studio 打开 `thunder-note-android/` 目录，等待 Gradle 同步完成。

### 2. 检查服务地址

当前 debug 构建默认地址定义在 `app/build.gradle.kts`：

```kotlin
buildConfigField("String", "BASE_URL", "\"http://192.168.3.7:8080/\"")
```

如果你的本地后端地址不同，需要先改成实际可访问地址再运行。

### 3. 构建调试包

```bash
./gradlew assembleDebug
```

### 4. 运行测试

```bash
./gradlew test
./gradlew lint
```

## 运行配置

当前最重要的运行配置是服务端基址：

- `debug`：默认指向本地局域网联调地址
- `release`：默认指向线上地址 `https://thunder.suixince.com/`

如果你在本地联调，通常只需要修改 `app/build.gradle.kts` 中 `debug` 的 `BASE_URL`。

当前应用还包含以下运行期能力和依赖：

- `networkSecurityConfig`：允许当前开发环境下的网络访问策略。
- `FileProvider`：支持图片、视频、文件分享与预览。
- `READ_MEDIA_*`、`CAMERA`、`RECORD_AUDIO`：用于媒体选择、拍摄、录音等能力。

## 开发指南

- 判断某个页面是否已经真实实现，优先检查对应 `repository` 是否接了真实网络或本地持久化，而不是只看页面是否存在。
- Android 与后端联调时，应以后端 controller 的真实路径与 DTO 为准。
- 当前真实接口前缀是 `/api/...`，不要按照旧文档写成 `/api/v1/...`。
- 如果登录后立刻需要读取鉴权资源，必须先确保 token 已落盘并可读取。
- 当前项目优先保持 Java 代码直接、可读、易调试，不追求过度抽象。

## 项目结构

```text
thunder-note-android/
├── app/
│   ├── src/main/java/com/flashnote/java/
│   │   ├── data/
│   │   ├── security/
│   │   ├── ui/
│   │   ├── FlashNoteApp.java
│   │   └── MainActivity.java
│   ├── src/main/res/
│   └── build.gradle.kts
├── gradle/
├── gradlew
└── settings.gradle.kts
```

## 常见问题和解答

<!-- 预留 -->
