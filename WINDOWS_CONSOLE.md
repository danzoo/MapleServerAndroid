# Windows 服务端管理终端使用手册

Windows 桌面服务端（`MapleServerDesktop`）内置了一个 **stdin 管理终端**：服务启动后，直接在运行窗口里敲命令即可对在线服务做管理操作（召唤/清理怪物、发公告、踢人、发物品/经验等）。

- 实现文件：`MapleServerDesktop/src/main/java/desktop/console/ConsoleManager.java`
- 仅存在于 Windows 桌面端；安卓服务端没有控制台，不受影响。
- 该功能**复用 server-core 的核心 API**，不改动共享源码。

---

## 1. 前置条件

1. 已用 JDK 21 编译出分发（见仓库根目录脚本）：

   ```bat
   build-windows.bat
   ```

   产出：`MapleServerDesktop\build\install\MapleServerDesktop\`。

2. `data\files\config.yaml` 里的 `HOST` / `LANHOST` 已改成本机局域网 IP（手机要从局域网连入时必须；`LOCALHOST` 保持 `127.0.0.1`）。

---

## 2. 启动服务端（关键：必须有真实 stdin）

在**一个真实的命令行窗口**里前台运行：

```bat
run-windows.bat
```

看到如下两行说明控制台已就绪：

```
Cosmic is now online after xxx ms
Admin console ready. Type 'help' for commands.
>
```

在 `>` 提示符后输入命令，回车执行。

> ⚠️ **控制台依赖标准输入。** 若用后台方式启动（nohup、`javaw`、无窗口的服务、CI 等），`System.in` 会立刻 EOF，控制台线程静默退出——服务照常运行，但**无法输入命令**。要用管理终端，就必须让它跑在一个能键盘输入的窗口里。

---

## 3. 命令总览

| 命令 | 语法 | 说明 |
|------|------|------|
| `help` | `help` | 列出全部命令 |
| `online` | `online` | 列出各世界/频道的在线角色（名字、等级、地图、角色 id） |
| `where` | `where <名字>` | 查看指定在线角色当前所在地图 id；别名 `whereis`、`getmap` |
| `spawn` | `spawn <mapid> <mobid> [qty]` | 在指定地图召唤怪物，`qty` 默认 1 |
| `clearmob` | `clearmob <mapid>` | 清空指定地图的全部怪物（**不掉落**）；别名 `killall`、`clear` |
| `announce` | `announce <文本...>` | 向所有在线玩家广播一条服务器公告 |
| `kick` | `kick <名字>` | 把指定在线角色强制断开 |
| `give` | `give <名字> <itemid> [qty]` | 给在线角色发物品，`qty` 默认 1 |
| `giveexp` | `giveexp <名字> <数值>` | 给在线角色加经验（自动处理升级） |

- 命令名**不区分大小写**。
- 参数以空格分隔；`announce` 的文本可含空格（其后所有内容作为一条消息）。
- 敲错命令或参数非法会打印提示，不会中断服务。

---

## 4. 命令详解与示例

### online — 查看在线情况

```
> online
World 0 / Channel 1:
    Dan  Lv30  map=100000000  id=30000
Online: 1
```

先用它拿到玩家所在的 **mapid**，再对该地图做 `spawn` / `clearmob`。只想查某一个人时用 `where` 更快。

### where — 查某个玩家所在地图

```
> where Dan
Dan is on map 100000000  Lv30  [world 0/ch 1]
```

- `<名字>` 目标在线角色名；不在线时提示 `No online character named: xxx`。
- 回显该角色当前 **mapid**、等级，以及所在 `[world/ch]`（正好对应 `spawn`/`clearmob` 需要的 mapid 与作用频道）。
- 别名：`whereis <名字>`、`getmap <名字>` 等价。

### spawn — 召唤怪物

```
> spawn 100000000 1110100 5
Spawned 5x mob 1110100 on map 100000000 at (120,300) [world 0/ch 1]
```

- `<mapid>` 目标地图（**必填**）。
- `<mobid>` 怪物 id；无效 id 会提示 `Unknown mob id`。
- `[qty]` 数量，省略为 1。
- 落点：目标地图上**有玩家时落在该玩家脚下**，否则落在地图原点并自动吸附到地面。

### clearmob — 清理怪物

```
> clearmob 100000000
Cleared 5 mob(s) on map 100000000 [world 0/ch 1]
```

- 杀掉该地图全部怪物，**不产生掉落**（用于快速清场）。
- 别名：`killall <mapid>`、`clear <mapid>` 等价。

### announce — 全服公告

```
> announce 服务器 10 分钟后维护
Announced to all worlds: 服务器 10 分钟后维护
```

- 以游戏内「服务器公告」样式（serverNotice 类型 6）发给**所有世界**的在线玩家。

### kick — 踢下线

```
> kick Dan
Kicked: Dan
```

- 角色不在线时提示 `No online character named: xxx`。

### give — 发物品

```
> give Dan 4000000 10
Gave 10x item 4000000 to Dan
```

- `<itemid>` 无效时提示 `Unknown item id`。
- 直接进目标角色背包（管理员权限，不加交易限制标记）。

### giveexp — 发经验

```
> giveexp Dan 100000
Gave 100000 exp to Dan (now Lv31)
```

---

## 5. 目标地图 / 频道是怎么选的

`spawn` 和 `clearmob` **必须显式给 mapid**（控制台没有「当前地图」概念）。同一张地图可能在多个频道各有实例，服务端按以下优先级挑一个频道执行，并在结果里回显 `[world X/ch Y]`：

1. **已加载该地图、且图上有玩家**的频道（最常用：你人站在哪张图，就作用在哪张图）；
2. 否则，任一**已加载**该地图的频道；
3. 否则，第一个频道（此时会按需从 wz 把地图加载进来）。

---

## 6. 注意事项

- `clearmob` 不掉落物品；要「击杀爆装」请用游戏内 GM 命令，而非本控制台。
- `kick` / `give` / `giveexp` 只对**当前在线**的角色生效。
- 管理终端里的操作**即时生效、不可撤销**（尤其 `kick`、`clearmob`），敲之前确认目标 mapid / 角色名。
- 关闭窗口（Ctrl+C）即停服。

---

## 7. 扩展新命令（开发者）

所有命令集中在 `ConsoleManager` 构造函数里注册，新增一条只需两步：

```java
register("mycmd", "<arg1> [arg2]", "一句话说明", this::cmdMyCmd);
```

```java
private void cmdMyCmd(String[] args) {
    // 用 parseInt(args[0], "arg1") 读数字参数（非法会抛友好提示）
    // 用 findPlayer(name) / resolveChannelForMap(mapid) 定位目标
    System.out.println("结果回显");
}
```

可直接复用的核心 API（与游戏内 GM 命令同源）：

- 遍历频道：`Server.getInstance().getAllChannels()` → `Channel.getWorld()/getId()/getPlayerStorage()/getMapFactory()`
- 地图与怪物：`MapleMap.spawnMonsterOnGroundBelow(int,x,y)` / `killAllMonsters()` / `countMonsters()` / `getCharacters()`
- 广播：`Server.broadcastMessage(worldId, PacketCreator.serverNotice(6, text))`
- 背包：`InventoryManipulator.addById(client, itemId, qty)`
- 角色：`Character.gainExp(n, true, true, true)` / `getClient().disconnect(true, false)`

改完重新 `build-windows.bat` 即可。

---

## 8. 常见问题

- **敲命令没反应 / 没有 `>` 提示符**：多半是后台启动拿不到 stdin。请在真实窗口里前台运行 `run-windows.bat`。
- **提示 `Unknown command`**：命令名拼错，敲 `help` 看列表。
- **`spawn` 后游戏里看不到怪**：确认 mapid 是玩家当前所在地图（先 `online` 查），且作用频道与玩家频道一致（看回显的 `[world/ch]`）。
- **手机连不进服务器**：与本控制台无关，检查 Windows 防火墙放行规则与 `config.yaml` 的 `LANHOST`（详见部署说明）。
