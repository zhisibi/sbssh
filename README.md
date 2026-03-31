# sbssh

一款 Android 原生 SSH/SFTP 客户端，本地加密存储，安全可靠。

## ✨ 功能特性

### 🔐 安全基础
- **主密码保护**：首次启动设置主密码，每次打开应用需输入
- **PBKDF2 密钥派生**：100,000 次迭代，256 位密钥
- **字段级 AES-GCM 加密**：密码/私钥等敏感字段加密存储
- **生物识别解锁**：支持指纹/面部识别快速解锁（便捷解锁方案）

### 🖥️ SSH 终端
- 密码认证 / 密钥认证（OpenSSH 格式）
- xterm-256color 终端模拟
- 快捷命令栏（常用 Linux 命令一键输入）
- 多标签终端会话支持

### 📁 SFTP 文件管理
- 远程目录浏览
- 文件上传 / 下载
- 文件/文件夹删除 / 重命名
- 创建文件夹
- 文件权限修改（chmod）

### 📋 VPS 管理
- 服务器信息增删改查
- 支持别名、端口自定义
- 密码 / 私钥两种认证方式

### 🧪 调试工具
- 应用内日志查看（Settings → Debug Log）
- 指纹 / 备份关键日志可直接在手机端查看

## 📱 系统要求

- Android 8.0 (API 26) 及以上
- 建议 2GB+ RAM

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM |
| 数据库 | Room |
| 本地加密 | PBKDF2 + AES-GCM |
| SSH/SFTP | JSch |
| 导航 | Jetpack Navigation Compose |
| 生物识别 | BiometricPrompt |

## 📦 安装

### 方式一：直接安装
下载仓库中的 `sbssh-debug.apk`，传输到手机安装。

### 方式二：源码编译
```bash
git clone https://github.com/zhisibi/sbssh.git
cd sbssh
# 需要 Android SDK + JDK 17+
# 国内环境已在 settings.gradle.kts 配置阿里云镜像
./gradlew assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk
```

### 方式三：Release 签名版
```bash
./gradlew assembleRelease
# APK 输出：app/build/outputs/apk/release/app-release.apk
```

## 🚀 使用说明

1. **首次启动**：设置主密码（用于衍生本地加密密钥）
2. **添加服务器**：点击右下角 `+` 按钮，填写 VPS 信息
3. **连接终端**：在服务器列表点击 SSH 图标
4. **文件管理**：在服务器列表点击 SFTP 图标
5. **生物识别**：设置页开启指纹/面部解锁，下次进入可直接解锁
6. **备份导出**：设置页导出备份文件（默认加密）

## 🧾 当前进度（截至 debug-10）

**已完成：**
- 主密码 + PBKDF2 派生密钥
- 字段级 AES-GCM 加密
- 指纹便捷解锁（UI 门禁）
- VPS CRUD
- SSH 连接（JSch）
- SFTP（基础操作）
- 备份/恢复（Downloads 导出 + 加密包）
- 应用内 Debug Log

**待完善/未完全解决：**
- **终端输入体验**：键盘遮挡、光标位置、Backspace 兼容性仍需打磨
- **终端 UI**：目前为文本拼接方案，后续建议接入成熟 Terminal 组件

## ⚠️ 安全提示

- 主密码一旦忘记，数据将无法恢复，请妥善保管
- 指纹解锁为便捷方案，依赖本地保存的派生密钥
- 密码和私钥字段均采用 AES-GCM 加密保存，但请确保设备本身安全

## 🧠 开发经验总结

- **JSch + 主线程发送会导致无响应**：必须在 IO 线程写入输出流。
- **虚拟输入框方案局限明显**：光标、退格、IME 遮挡等问题难以彻底解决。
- **建议替换为成熟终端组件**：如 xterm/VT100 实现，能正确处理光标、按键、滚动和控制序列。

## 📄 License

MIT License
