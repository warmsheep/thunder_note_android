# Android 端知识库

## 概览
`thunder-note-android/` 是当前真实的 Android Java 客户端实现。主结构以 `com.flashnote.java` 包为主，采用 Activity/Fragment + ViewBinding + ViewModel 的传统 Android 方案。

## 高频入口
| 任务 | 位置 | 说明 |
|------|------|------|
| 应用入口 | `app/src/main/java/com/flashnote/java/MainActivity.java` | Java 客户端宿主 |
| 应用装配 | `app/src/main/java/com/flashnote/java/FlashNoteApp.java` | Application 与依赖装配 |
| Token 存储 | `app/src/main/java/com/flashnote/java/TokenManager.java` | 加密存储 |
| 认证页面 | `app/src/main/java/com/flashnote/java/ui/auth/` | Splash / Login / Register |
| 主壳层 | `app/src/main/java/com/flashnote/java/ui/main/MainShellFragment.java` | 底部四 tab 主容器 |
| 聊天页面 | `app/src/main/java/com/flashnote/java/ui/chat/ChatFragment.java` | 当前 Java 聊天壳层 |

## 本模块特有约定
- 其他通用规则遵循根目录 `AGENTS.md`。
- 优先保持 Java 代码可读、传统、易调试。
- 页面流转优先看 `MainActivity` 与各 Fragment 之间的导航关系。
- 当前 `auth`、`flashnote`、`chat` 已有可编译主链；`collection`、`favorite`、`profile` 仍偏占位壳层。
- 判断 Android 是否已真实接通后端时，优先检查 repository 是否仍只返回内存数据。

## 当前成熟度
- `AuthRepositoryImpl` 已有真实网络调用基础层。
- `FlashNote`、`Chat` 当前仍主要依赖内存仓储种子数据。
- `Collection`、`Favorite`、`Profile` 仍是 Java 占位页。
- `Sync`、`File` 尚未进入 Java 主链。

## 反模式
- 不要把 Java 页面存在直接当成后端已接通的证据。

## 备注
- 当前项目使用纯 Java 客户端，不涉及其他语言。
