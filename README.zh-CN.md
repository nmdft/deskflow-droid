# Deskflow Droid

**通过局域网，用电脑的鼠标和键盘操作 Android 手机。**

[English (default)](README.md) · [下载 APK](https://github.com/nmdft/deskflow-droid/releases) · [安全说明](SECURITY.md)

这是基于 [ITESaurabh/deskflow-droid](https://github.com/ITESaurabh/deskflow-droid) 的独立实验性修改版，配合电脑端 [Deskflow](https://github.com/deskflow/deskflow) 服务端使用，**不是 Deskflow 官方版本**。电脑是服务端，Android 是客户端；不是让 Android 反向共享鼠标给电脑，也不是投屏或文件传输软件。

> **实验版提醒：** v1.1.0 为预发布版。构建和自动化检查已通过，但新包名的签名 APK 尚未完成新一轮真机交互验收。此前 Redmi K40 / Android 13 的检查使用的是旧测试包，不能直接当作本安装包的验收结果；其他手机、Android 版本及电脑服务端版本没有完整兼容性矩阵。

## 有什么用、有什么特点？

- **桌面一套键鼠操作手机：** 将 Android 加入 Deskflow 屏幕布局，鼠标跨越配置的边缘后，由客户端显示指针，并将点击、拖动、滚动转换为无障碍手势。
- **保留手机原输入法：** Android 13+ 在支持的文本编辑框中使用无障碍输入连接，不必切换默认输入法。它不是系统级硬件键盘注入。
- **适配手机支架方向：** 可选跟随画面、横向、竖向；固定方向可反转 180°，只调整指针坐标，不强制所有应用旋转。
- **鼠标移入亮屏可开关：** 默认开启，修改立即保存；只有收到移入事件才尝试亮屏，不会解锁或绕过锁屏。
- **正常使用不依赖 root / ADB：** 不需要旧辅助脚本、账号或云端服务。实现了尽力而为的文本剪贴板交换及可选的证书指纹固定 TLS，具体限制见下文。

## 下载与安装

1. 打开 [Releases 下载页](https://github.com/nmdft/deskflow-droid/releases)，选择 **v1.1.0 实验版**，下载 `deskflow-droid-v1.1.0.apk`。GitHub 自动生成的 **Source code** 压缩包是源码，不是安装包。
2. 最低可安装 Android 11（API 30）；**推荐 Android 13+，增强编辑输入需要 Android 13+**。Android 11/12 使用能力更有限的无障碍文本回退，本项目未完成其真机验证。
3. 为打开 APK 的应用允许“安装未知应用”，安装并启动 Deskflow Droid。这是侧载 APK，不是 Play 商店版本。
4. 在系统设置中启用 Deskflow Droid 无障碍服务。部分手机对侧载应用限制此权限：先了解源码和权限风险，再按系统提供的入口允许“受限设置”。如请求通知权限，可允许以查看连接状态。
5. 电脑安装 Deskflow 并启用**服务端模式**，在屏幕布局中加入 Android 屏幕。手机填写的客户端名称须与布局里的名称一致，默认是 `android`。
6. 手机填写电脑的局域网地址和端口，默认 `24800`；两端 TLS 设置必须一致。本客户端 TLS **默认关闭**，建议双方开启，并独立核对首次出现的证书指纹后再信任。
7. 连接后，将电脑指针移过布局中的边缘，在普通文本框里测试点击和输入。

只在可信局域网 / VPN 使用，不要把 24800 端口暴露到公网。Android 和厂商后台限制可能中断连接，必要时检查本应用的电池限制设置；不能保证永久保活。

### 版本、升级与签名

| 项目 | 已发布 v1.1.0 APK |
| --- | --- |
| 状态 | 实验性预发布 |
| 应用包名 | `cn.nmdft.deskflowclient` |
| 版本名称 / versionCode | `1.1.0` / `11` |
| Android 要求 | 最低 Android 11；增强编辑输入 Android 13+ |
| 签名 | 维护者自有发布密钥，非上游签名、非 debug 签名 |
| 安装包对应源码 | [v1.1.0 标签](https://github.com/nmdft/deskflow-droid/tree/v1.1.0) |

可以与上游及旧 `.fix` 测试包共存，不自动迁移旧设置、TLS 身份或信任指纹。自行编译的 debug 包为 `cn.nmdft.deskflowclient.debug`，签名也不同。**应用界面目前仍为中英混合；双语 README 不代表应用已经完整双语化。**

目前这种自有密钥签名可以用于分发 APK，不等于 Play 认证或安全背书。后续直接覆盖升级需要保持同包名、同签名并满足版本号要求。私钥必须单独妥善备份，不能上传仓库；丢失后不能正常签名升级。不同签名的包不能直接覆盖，卸载重装会丢失本地设置和身份。本应用禁用了备份和设备迁移。

**APK 签名证书 SHA-256**（与每台设备的 TLS 指纹不是一回事）：

```text
c1b88911e69d37bad79053e122120061f48b3ea14f8352b5784e8f1f9fc01fdc
```

下载页由维护者上传的附件只有 APK 和 `SHA256SUMS.txt`。将二者放在同一文件夹后校验：

```sh
shasum -a 256 -c SHA256SUMS.txt
# Linux 也可用：sha256sum -c SHA256SUMS.txt
```

安装 Android SDK 后，可用 `apksigner verify --print-certs deskflow-droid-v1.1.0.apk` 查看签名证书。校验和用于检查文件是否变化；若同一下载来源遭篡改，它本身不能独立证明真实性。

## 能做什么、不能保证什么？

| 功能 | 实际边界 |
| --- | --- |
| 鼠标 | 指针覆盖层及无障碍手势，不是底层硬件鼠标注入；游戏、自绘画布、任意多点触控不保证。 |
| 键盘 | Android 13+ 的支持型文本框效果更好；实现了部分编辑、导航、复制/剪切/粘贴操作，不代表任意硬件快捷键、按键抬起行为或所有应用都支持。 |
| 中文 / 输入法 | 保留手机默认输入法；结果依赖电脑提交的文本与应用编辑框，不能保证用电脑按键驱动手机输入法的拼音组词。 |
| 剪贴板 | 仅文本且有大小限制。实现电脑到手机接收；手机到电脑依赖获准的剪贴板读取或本客户端复制/剪切选区路径；任意后台复制、图片和文件不保证。 |
| 亮屏 | 服务、Wi-Fi 和连接仍有效时尽力执行；深度休眠、省电限制和进程被杀可能导致收不到移入事件；不会绕过锁屏。 |
| 服务端 | 目标是 Deskflow；处理相关协议不等于已验证兼容 Barrier、Synergy、Input Leap 或所有 Deskflow 版本。 |

无障碍权限可读取焦点文本并执行手势，剪贴板文本可能发送到你配置的电脑。复制/剪切选区转发路径会检查并避免转发密码选区，这不代表所有输入路径都排除了密码框；本项目未经正式独立安全审计。TLS 身份私钥和信任指纹存于应用私有偏好设置，并非 Android Keystore；已禁用备份。处理敏感内容前请阅读 [SECURITY.md](SECURITY.md)。

## 常见问题

- **连不上：** 检查电脑是否为服务端、屏幕名称、局域网地址、端口、防火墙及两端 TLS 设置；不要用开放公网端口来解决。
- **已连接但不能输入：** 检查无障碍服务，手动解锁后换普通文本框；网络连通不代表目标应用支持编辑输入。
- **证书发生变化：** 先独立确认电脑端证书，再决定忘记并重新信任；不要盲目接受新指纹。
- **方向不对：** 调整指针方向；固定方向下可以反转 180°。
- **移入不亮屏：** 检查“鼠标移入时亮屏”是否开启，以及息屏后连接是否仍有效；亮屏不等于解锁。
- **更新无法安装：** 核对包名、签名、versionCode；上游包、旧测试包与自行编译包不能视为同一升级渠道。

## 架构与目录

```text
电脑 Deskflow 服务端
  └─ 可信局域网 / VPN：TCP 或可选 TLS
      └─ DeskflowClient + Protocol：连接、输入和剪贴板消息
          └─ InjectorService：Android 无障碍服务
              ├─ 指针覆盖层 / 手势派发 / PointerSpace
              ├─ AccessibilityKeyboard / ImeKeys：Android 13+ 编辑输入
              ├─ ClipboardSync：尽力而为的文本交换
              └─ ScreenWake：读取并执行鼠标移入亮屏设置
```

`MainActivity` 为连接和设置界面，`KeepAliveService` 提供前台服务通知，`Tls` 负责身份和服务端指纹信任。正式源码位于 `app/src/main`，JVM 回归测试在 `app/src/test`，设备测试在 `app/src/androidTest`。

当前分支移除了未使用的旧 shell/UHID 辅助程序、启动脚本、无调用的输入类，以及探测型/模板测试；保留正常无障碍实现、构建配置、资源、有用回归测试、许可证和原作者署名。**已发布 v1.1.0 APK 没有替换，其标签仍完整保留对应源码，包括旧辅助程序。** 删除当前分支文件不等于抹掉历史。签名材料、设备状态和私有测试录屏不应公开。

## 构建与验证

使用仓库的 Gradle wrapper、JDK 25 工具链，以及包含 `platform-tools`、`platforms;android-37.0`、`build-tools;37.0.0` 的 Android SDK。设置 `ANDROID_HOME`，或用不提交的 `local.properties` 指定 SDK。Gradle 可下载配置的 daemon 工具链；离线构建需事先准备依赖。

```sh
sh gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
```

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
- Release 输出：`app/build/outputs/apk/release/`；未提供签名环境变量时为未签名包，**不是下载页里的签名安装包**。
- 本地签名可私下提供 `KEYSTORE_PATH`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`，不要提交、打印或把 keystore 放在仓库里。
- GitHub Actions 只执行检查和构建，不存放签名凭证，也不会自动发布签名版本。
- 真机测试需要合适的设备，部分还需显式配置测试服务端；编译 instrumentation APK 不等于运行测试。亮屏测试可能主动熄灭设备屏幕。

各版本已验证内容和待验收项见 [AUDIT.md](AUDIT.md)。自动化构建通过不等于日常使用已稳定。

## 反馈与许可证

反馈时提供应用版本、手机型号和 Android 版本、电脑服务端版本及复现步骤。日志需去除地址、设备标识、剪贴板内容、证书私钥和令牌。安全问题按 [SECURITY.md](SECURITY.md) 私下报告。

基于 ITESaurabh 原项目，来源版本 `28f80c7bbe340ce883df04ac1e5317acd4fa7702`；修改和来源说明见 [NOTICE.md](NOTICE.md)。采用 **GPL-2.0-only WITH LicenseRef-OpenSSL-Exception**，保留原 [LICENSE](LICENSE) 和 [例外条款](LICENSES/LicenseRef-OpenSSL-Exception.txt)。不主张 Deskflow 官方关联或商标所有权。
