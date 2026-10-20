<p align="center">
  <img src="assets/banner.svg" alt="Gh0stra1n Banner" width="100%" />
</p>

# Gh0stra1n

针对Android设备的OverlayFS分区挂载与修改工具。通过OverlayFS在system、vendor、product等只读系统分区上叠加可写层，不改动底层分区。

## 主要功能

- 可写层：lowerdir与upperdir叠加，底层只读分区保持不变。
- 镜像管理：ext4稀疏镜像创建、在线resize2fs扩容、收缩、格式化与删除。
- 挂载选项：noatime减少闪存写入，挂载前自动e2fsck修复。
- 备份与恢复：修改层打包并生成SHA-256清单，恢复前校验清单。
- 内置工具：修改层文件浏览器（文本与图片预览）、Treemap磁盘占用分析。
- 界面：毛玻璃底部栏、三语言支持与实时控制台日志。

## 目录结构

- `/data/local/gh0stra1n-overlayfs/`：工作主目录
- `/data/local/gh0stra1n-overlayfs/manifest.json`：镜像状态与配置清单
- `/data/local/gh0stra1n-overlayfs/*.img`：各分区ext4镜像文件
- `/data/local/gh0stra1n-overlayfs/mnt_<part>/u/`：修改层（upperdir）
- `/sdcard/Gh0stra1n_Backup/`：备份归档目录

## 编译与安装

环境要求：JDK 17+与Android SDK，Gradle由项目自带的wrapper提供，无需另行安装。

```bash
# 编译
./gradlew assembleDebug

# 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 确保设备已获取Root权限（目前支持[ghostlock-PD2339FA-Neo9SPro-Root](https://github.com/LemonFan-maker/ghostlock-PD2339FA-Neo9SPro-Root)，Magisk方案，APatch，KernelSU等方案属于实验性，若出现问题，欢迎提交日志反馈）。
2. 在“分区”页面为目标分区创建ext4镜像。
3. 在“控制”页面点击“挂载OverlayFS”挂载对应分区。
4. 可通过内置文件浏览器向upperdir添加或替换文件。
5. 在“设置”页面备份全部修改层或从归档恢复。

## AIDL 跨进程通信 (IPC)

Gh0stra1n提供AIDL服务，供受信任的客户端调用：

- **Action**: `com.orionisli.gh0stra1n.OVERLAY_SERVICE`
- **Package**: `com.orionisli.gh0stra1n`
- **Interface**: `com.orionisli.gh0stra1n.ipc.IGh0stOverlayService`

### 安全模型

- Service 需要跨应用绑定，因此保持 `exported=true`，但由 `signature` 级自定义权限保护。
- 每次 Binder 事务都会校验调用方：
  1. 调用方必须与 Gh0stra1n 使用同一签名证书；
  2. 调用方 UID 对应的包名必须在 `Gh0stOverlayService.ALLOWED_CALLER_PACKAGES` 白名单中。
- 当前白名单仅包含 `com.orionisli.ovl0ad3r`。新增第三方集成时，必须同时加入白名单并确保使用同一签名证书。
- AIDL 不再提供任意 `executeRootCommand(String)`。需要新的特权操作时，应新增参数受约束的窄接口，而不是转发整条 shell 命令。
- 发布 APK 必须使用固定的 release keystore，不能使用 Android 默认 debug key；debug 签名可以被任何人伪造，会直接破坏签名级鉴权。

### 接口方法说明

| 方法名 | 返回值 | 说明 |
| :--- | :--- | :--- |
| `isRootAlive()` | `boolean` | 探测Root是否可用 |
| `getPartitionStatus(String partitionName)` | `int` | 查询分区挂载状态（`1`: 已挂载 LIVE, `0`: 离线 OFFLINE, `-1`: 未知分区） |
| `getUpperDir(String partitionName)` | `String` | 获取指定分区的可写修改层（upperdir）物理路径 |
| `getPartitionFreeSpace(String partitionName)` | `long` | 获取指定分区修改层剩余可用空间（字节） |
| `mountPartition(String partitionName)` | `boolean` | 挂载指定分区的OverlayFS修改层 |
| `unmountPartition(String partitionName)` | `boolean` | 卸载指定分区的OverlayFS修改层 |
| `syncStorage()` | `void` | 缓存数据落盘（sync） |
| `restartZygote()` | `boolean` | 重启Zygote进程使修改生效 |

### 客户端绑定示例 (Kotlin)

```kotlin
val intent = Intent("com.orionisli.gh0stra1n.OVERLAY_SERVICE").apply {
    setPackage("com.orionisli.gh0stra1n")
}

val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val overlayService = IGh0stOverlayService.Stub.asInterface(service)
        val isRoot = overlayService.isRootAlive
        val isSystemMounted = overlayService.getPartitionStatus("system") == 1
        val upperDir = overlayService.getUpperDir("system")
        // ...
    }

    override fun onServiceDisconnected(name: ComponentName?) {}
}

context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
```

## 许可证

[Apache-2.0 License](LICENSE)
