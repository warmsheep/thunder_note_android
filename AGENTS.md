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
- 当前 `auth`、`flashnote`、`chat`、`collection`、`favorite` 已有真实主链；`profile` 已接真实资料/sync/file/logout，但仍是操作台式页面。
- 判断 Android 是否已真实接通后端时，优先检查 repository 是否仍只返回内存数据。

## 当前成熟度
- `AuthRepositoryImpl` 已有真实网络调用基础层。
- `FlashNoteRepositoryImpl`、`MessageRepositoryImpl`、`CollectionRepositoryImpl`、`FavoriteRepositoryImpl` 已接真实接口，不再是纯内存种子数据主链。
- `ProfileTabFragment` 已接 `UserRepositoryImpl`，并提供 bio 编辑、手动 sync/file/logout 入口，但仍不是完整个人中心。
- `SyncRepositoryImpl`、`FileRepositoryImpl` 已进入 Java 主链，当前定位是 MVP 最小闭环，不是完整离线体系。

## 重构记录
- `ui/FragmentUiSafe.java`：统一 Fragment UI 线程安全执行逻辑，所有 Tab Fragment 的 `runIfUiAlive` 已委托至此工具类，消除重复代码。
- `AuthViewModel.register()`：`RegisterFragment` 已接入 ViewModel，auth 模块内部消除回调嵌套模式，与 `LoginFragment` 保持一致。
- `BaseTabPlaceholderFragment`、`PlaceholderTabFragment`、`fragment_tab_placeholder.xml`：已删除，属于迁移历史残余。
- `FlashNoteTabFragment` popup：两处 `LayoutInflater.inflate + findViewById` 已升级为 ViewBinding。

## 反模式
- 不要把 Java 页面存在直接当成后端已接通的证据。

## 备注
- 当前项目使用纯 Java 客户端，不涉及其他语言。
- 若要判断 MVP 是否还有缺口，先看 `docs/Android客户端Java重启基线.md` 中的“模块成熟度快照”和“页面与导航范围快照”，再回到具体 repository/controller 核实。
