# sbssh

一款 Android 原生 SSH/SFTP 客户端，本地加密存储，安全可靠。

## ✨ 功能特性

### 🔐 安全基础
- **主密码保护**：首次启动设置主密码，每次打开应用需输入
- **PBKDF2 密钥派生**：100,000 次迭代，256 位密钥
- **SQLCipher 加密数据库**：AES-256 全库加密，敏感数据不泄露
- **生物识别解锁**：支持指纹/面部识别快速解锁

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

## 📱 系统要求

- Android 8.0 (API 26) 及以上
- 建议 2GB+ RAM

## 🛠️ 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM |
| 数据库 | Room + SQLCipher |
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

1. **首次启动**：设置主密码（用于加密本地数据库）
2. **添加服务器**：点击右下角 `+` 按钮，填写 VPS 信息
3. **连接终端**：在服务器列表点击 SSH 图标
4. **文件管理**：在服务器列表点击 SFTP 图标
5. **生物识别**：在主密码界面可开启指纹/面部解锁

## ⚠️ 安全提示

- 主密码一旦忘记，数据将无法恢复，请妥善保管
- 首次连接服务器时选择 `StrictHostKeyChecking=no`，后续版本将支持主机密钥验证
- 密码和私钥存储在 SQLCipher 加密数据库中，但请确保设备本身安全

## 📄 License

MIT License
