# Windows 服务端管理终端使用手册

Windows 桌面服务端（`MapleServerDesktop`）内置了一个 **stdin 管理终端**：服务启动后，直接在运行窗口里敲命令即可对在线服务做管理操作。

命令分两层：

| 层 | 条数 | 是什么 |
|----|------|--------|
| 内置命令 | 10 条 | 控制台自己的高频运维命令（召唤/清理怪物、发公告、踢人、发物品/经验） |
| 迁移过来的 GM 命令 | 174 条 | 游戏内聊天框 `@`/`!` 那套 GM 命令的**免玩家版**，主体一律显式写成 地图id / 角色名 / 世界id |

- 实现文件：
  - 内置层：`MapleServerDesktop/src/main/java/desktop/console/ConsoleManager.java`
  - GM 层：`MapleServerDesktop/src/main/java/desktop/console/gm/`（`GmCommands` 聚合 → `GmCommandsLv0..Lv6` 按游戏内注册档位分档，`GmRegistry` / `GmCmd` / `GmContext` 是框架）
- 仅存在于 Windows 桌面端；安卓服务端没有控制台，不受影响。
- 两层都**复用 server-core 的核心 API**，不改动共享源码。
- 游戏内 `@`/`!` 入口**原样保留**，双入口并存：手机上照样能用 `@command`，控制台这一份只是把「以谁的身份执行」换成了「显式指定主体」。

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
>
> ✅ **关闭这个窗口会连带停掉整个服务端。** 当你有一个真实控制台时，关闭窗口（或在窗口里按 `Ctrl+Z` / `Ctrl+D` 让标准输入结束）会触发一次优雅停服：保存在线角色、逐频道关停、停掉定时器与线程池，最后退出进程，端口（8484 / 7575-7577）全部释放。若优雅停服超过 **10 秒**还没结束，进程会被强制 `halt` 掉，保证不会留下占用端口的僵尸进程。
>
> 反过来，**没有控制台**的后台启动（stdin 立即 EOF）只会让控制台线程静默退出，服务继续运行——不会因为读不到输入而误停服。

---

## 3. 命令总览

### 3.1 内置命令（10 条）

| 命令 | 语法 | 说明 |
|------|------|------|
| `help` | `help` / `help gm` / `help <关键字>` | 见 3.3 |
| `online` | `online` | 列出各世界/频道的在线角色（名字、等级、地图、角色 id） |
| `where` | `where <名字>` | 查看指定在线角色当前所在地图 id；别名 `whereis`、`getmap` |
| `spawn` | `spawn <mapid> <mobid> [qty]` | 在指定地图召唤怪物，`qty` 默认 1 |
| `clearmob` | `clearmob <mapid>` | 清空指定地图的全部怪物（**不掉落**）；别名 `killall`、`clear` |
| `announce` | `announce <文本...>` | 向所有在线玩家广播一条服务器公告 |
| `banner` | `banner <秒> <文本...>` | 在所有屏幕顶部横幅显示一段文字，持续 1~3600 秒 |
| `kick` | `kick <名字>` | 把指定在线角色强制断开 |
| `give` | `give <名字> <itemid> [qty]` | 给在线角色发物品，`qty` 默认 1 |
| `giveexp` | `giveexp <名字> <数值>` | 给在线角色加经验（自动处理升级） |

- 命令名**不区分大小写**。
- 参数以空格分隔；`announce` / `banner` 这类文本参数可含空格（其后所有内容作为一条消息）。
- 敲错命令或参数非法会打印提示，不会中断服务；单条命令抛异常也只回显一行 `命令 'xxx' 执行失败:`，控制台继续。

### 3.2 迁移过来的 GM 命令（174 条）

不逐条抄进这份手册——**现场查比查手册快**：

```
> help gm            （全部 174 条，带用法和说明）
> help drop          （只列名字里含 drop 的几条：drop / droprate / cleardrops / reloaddrops / whatdropsfrom / whodrops ...）
```

`help gm` 每条打印成 `命令名 用法` + 一句中文说明，有别名时多一行 `别名:`。详见 §5。

### 3.3 三层 help

| 输入 | 看到什么 |
|------|----------|
| `help` | 内置 10 条 + 一句「另有 174 条 GM 命令」的指引 |
| `help gm` | GM 层全部 174 条 |
| `help <关键字>` | 内置层按命令名匹配 + GM 层按**名字和别名**匹配 |

### 3.4 名字撞车：内置层永远赢

分发顺序是「先查内置表，再查 GM 表」（`ConsoleManager.loop()`），所以撞名时走内置实现。实际撞名的有 **5 个**：

| 撞名 | 内置版本 | GM 版本 |
|------|----------|---------|
| `help` | 三层 help | `help [命令名]`（游戏内那条） |
| `online` | 全世界全频道，含 GM 角色 | `online [世界id]`，只列非 GM 玩家 |
| `spawn` | `spawn <mapid> <mobid> [qty]` | `spawn <地图id> <怪物id> [数量=1]`（行为等价） |
| `banner` | `banner <秒> <文字>` | `banner <秒> <文字>`（行为等价） |
| `killall` | 是内置 `clearmob` 的别名 | `killall <地图id>`（不掉东西，友好怪/HornTail 阶段怪不动） |

功能上内置版本覆盖 GM 版本，只有两点要绕：

- 想**只看某个世界**的在线：用 `online2 [世界id]`（名字没被占用）。
- 想要 GM 版 `killall` 那条更细的「友好怪/HornTail 阶段怪不动」判定：内置 `clearmob` 不含这条判定，先 `help killall` 看清差别。

---

## 4. 内置命令详解与示例

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
- 只想发**一个世界**：`servermessage <世界id> [文字...]`；只要顶部横幅：`banner <秒> <文字...>`。

### banner — 顶部横幅

```
> banner 30 服务器 10 分钟后维护
```

- `<秒>` 必须是 1~3600，越界或不是数字都只回一行用法提示，不会发任何东西。

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

## 5. 迁移过来的 GM 命令

### 5.1 对账口径（迁全了没有）

| 口径 | 数字 |
|------|------|
| 游戏内命令类（`server-core/client/command/commands/gm0..gm6`） | **175 个** |
| 游戏内在 `CommandsExecutor` 注册的命令名（含别名） | **179 个** |
| 控制台 GM 层注册键 | **179 个**（一一对上，无遗漏、无多余） |
| `help gm` 列出的条数 | **174 条**（5 个别名折进主人那行的「别名:」） |

折进别名的 5 个名字：`commands`→`help`、`unhide`→`hide`、`summon`→`warphere`、`reach` 和 `follow`→`warpto`。

控制台按游戏内注册档位分 7 个文件，键数：Lv0 **27** / Lv1 **6** / Lv2 **40** / Lv3 **60** / Lv4 **24** / Lv5 **6** / Lv6 **16** = 179。

> 少数命令的**源文件目录**与**注册档位**本来就不一致（`bosshp`/`mobhp` 源在 gm1、注册在 Lv0 表；`devtest` 源在 gm6、注册在 Lv0 表；`warpmap`/`warparea` 源在 gm2、注册在 Lv3 表），迁移时的文件归属只是就近安排。**对账请以 `help gm` 的名字清单为准，不要以「它在哪个 Lv 文件里」为准。**

### 5.2 主体约定（免玩家版的关键）

游戏内 `@level 30` 里的「我」在控制台中不存在，所以每条命令的主体都写成显式参数。看 `help gm` 的用法串就知道该给什么：

| 主体写法 | 含义 | 例子 |
|----------|------|------|
| `<地图id> ...` | 直接给数字 mapid，不再依赖「我当前站的图」 | `whereami <地图id>`、`inmap <地图id>`、`mapplayers <地图id>`、`buffmap <地图id>`、`healmap <地图id>`、`cleardrops <地图id>`、`droplimit <地图id>`、`mobhp <地图id>`、`bosshp <地图id>`、`mobskill <地图id> <技能id> <等级>`、`drop <地图id> <道具id> [数量\|天数]`、`debuff <地图id> <SLOW\|SEDUCE\|...>`、`openportal <地图id> <门名>`、`pmob <地图id> <mobid> <世界id> [mobtime] [角色名]`、`reloadmap <地图id>` |
| `<角色名> ...` | 目标必须**当前在线**，否则回 `! 没有在线的角色叫 xxx` | `heal <角色名>`、`showrates <角色名>`、`hide <角色名>`、`level <角色名> <新等级>`、`job <角色名> <职业id>`、`item <角色名> <道具id> [数量\|天数]`、`givems <角色名> <数量\|max\|min>`、`clearslot <角色名> <all\|equip\|use\|setup\|etc\|cash>`、`jail <角色名> [分钟=5]`、`ban <角色名> <原因...>` |
| `<世界id> ...` | 世界是数字，不带就默认 0 或「全世界」 | `servermessage <世界id> [文字...]`、`exprate <世界id> <倍率>`、`droprate <世界id> <倍率>`、`endevent <世界id> [频道id]`、`ranks [世界id=0]`、`online2 [世界id]` |
| 不带主体 | 作用对象是全服/所有频道/内存状态 | `saveall`、`dcall`、`reloadevents`、`reloadportals`、`reloaddrops`、`reloadshops`、`uptime`、`showsessions`、`iplist`、`clearquestcache`、`eraseallpnpcs`、`addworld` |
| 两个角色名 | 传送/参照类需要「谁」和「参照谁」 | `warp <角色名> <地图id>`、`warpto <移动角色> <目标角色>`（别名 `reach`/`follow`）、`warphere <被传送角色> <参照角色>`（别名 `summon`）、`warpworld <角色名> <世界id>`、`warparea <角色名> <目标地图id> [半径=50000]` |

另外三条通用规矩：

- **角色名保留大小写**。游戏内入口会把命令名和它后面的参数一起 `toLowerCase()`（`CommandsExecutor` 第 104-105 行），控制台不会——所以 `item Dan 4000000` 里的 `Dan` 要照在线名单原样写（先 `online` 抄名字）。
- 回显前缀：`= ` 表示成功，`! ` 表示失败或缺参；缺参时还会顺带打印正确用法。
- 参数按空格切，带空格的文本（公告、缺陷描述）就是「其余全部」。

### 5.3 没有权限等级这一说

游戏内 GM 命令按 Lv0~Lv6 逐级鉴权；控制台本身就是最高权限入口，**所有 174 条都能直接跑，不看操作者等级**。所以危险动作自己把关：

- `shutdown [<分钟>|NOW]`、`dcall`（断开全服所有非 GM 玩家）、`ban`、`clearslot <角色名> <all|...>`（清空背包栏）、`eraseallpnpcs`、`removechannel <世界id>`、`warpmap`（一次搬走一张图上的所有人）—— 敲之前确认参数。`saveall` 只是立刻存档，不算危险。
- 绝大多数命令**跑完才给条数，不会先问你一句**：`reloadevents` 回 `已重载 X/Y 个频道`、`warpmap` 回搬运人数、`reloadmap` 回搬运玩家数。只有 `eraseallpnpcs` 和 `removechannel` 会**先打印影响范围再执行**——但那也只是打印，不是确认提示，回车落下去就生效。

### 5.4 按类别速查（名字索引）

语法一律用 `help <关键字>` 现场取。下面只列名字，方便知道「这类事有这么一条」：

- **在线与状态查询**：`online2`、`whereami`、`inmap`、`mapplayers`、`getacc`、`showsessions`、`iplist`、`monitors`、`uptime`、`credits`、`ranks`
- **资源名 ↔ id 查询**：`search`（String.wz/物品/任务表按名字搜）、`id`（按名字查道具大类 id）、`gachalist`
- **传送**：`warp`、`goto`、`warpworld`、`warpto`(`reach`/`follow`)、`warphere`(`summon`)、`warpmap`、`warparea`、`changel`
- **地图上的怪/NPC 与地图效果**：`pmob`、`pmobremove`、`npc`、`pnpc`、`pnpcremove`、`eraseallpnpcs`、`playernpc`、`playernpcremove`、`mobhp`、`bosshp`、`mobskill`、`debuff`、`unbug`、`healmap`、`night`、`mutemap`（地图禁言）、`music`、`bomb`
- **传送门**：`openportal`、`closeportal`、`reloadportals`
- **掉落与物品**：`item`、`proitem`、`drop`、`cleardrops`、`loot`、`itemvac`、`forcevac`、`whatdropsfrom`、`whodrops`、`dispose`、`droplimit`、`togglecoupon`、`supplyratecoupon`
- **角色养成**：`level`、`job`、`ap`、`sp`、`str`、`dex`、`int`、`luk`、`fame`、`maxstat`、`maxhpmp`、`hpmp`、`maxenergy`、`maxskill`、`resetskill`、`setstat`、`seteqstat`、`equiplv`、`empowerme`、`buff`、`buffme`、`face`、`hair`、`heal`、`hurt`、`checkdmg`、`toggleexp`、`fly`、`hide`(`unhide`)
- **货币与点数**：`givems`、`givenx`、`giverp`、`givevp`、`points`、`recharge`
- **任务与活动**：`startquest`、`completequest`、`resetquest`、`clearquest`、`clearquestcache`、`startevent`、`endevent`、`startmapevent`、`stopmapevent`、`joinevent`、`leaveevent`、`reloadevents`
- **世界与频道**：`addworld`、`addchannel`、`removechannel`、`servermessage`、`notice`、`gm`（黄字广播给在线 GM）、`saveall`、`dcall`、`clearsavelocs`
- **账号与封禁**：`jail`、`unjail`、`ban`、`unban`、`setgmlevel`、`enableauth`、`togglewhitechat`、`mylawn`、`dc`（强制断开某角色）
- **倍率**（内存态，见 §7）：`rates`、`showrates`、`exprate`、`droprate`、`mesorate`、`questrate`、`fishrate`、`travelrate`、`bossdroprate`、`expeds`
- **计时器**：`timer`、`timermap`、`timerall`
- **Boss 与彩蛋**：`zakum`、`horntail`、`pinkbean`、`pianus`、`cake`、`gacha`、`gachalist`、`seed`（花种田）
- **重载资源**：`reloadmap`、`reloaddrops`、`reloadshops`、`reloadportals`、`reloadevents`
- **调试**：`debug`、`devtest`、`set`（`ServerConstants.DEBUG_VALUES` 的 10 格整数槽）、`showpackets`、`showmovelife`、`reportbug`

### 5.5 典型工作流

```
> online                                  （拿到角色名和 mapid）
> mapplayers 100000000                    （这张图上都有谁）
> buffmap 100000000                       （给图上所有人上一层 buff）
> whereami 100000000                      （这张图的概览）
> drop 100000000 4000000 5                （往这张图上丢 5 个 4000000，归属给图上第一个在线角色）
> mobskill 100000000 100 3                （给这张图的怪挂 3 级 100 号怪技能）
> exprate 0 2                             （世界 0 经验翻倍）
> showrates Dan                           （确认 Dan 看到的有效倍率）
> servermessage 0 10 分钟后维护
> saveall
```

清场/补怪用内置的 `spawn` / `clearmob` 更短；要对**多个频道**逐个动手时用 GM 层带世界id 的命令（`pmob` / `mobhp` 等）。

---

## 6. 目标地图 / 频道是怎么选的

`spawn` 和 `clearmob` **必须显式给 mapid**（控制台没有「当前地图」概念）。同一张地图可能在多个频道各有实例，服务端按以下优先级挑一个频道执行，并在结果里回显 `[world X/ch Y]`：

1. **已加载该地图、且图上有玩家**的频道（最常用：你人站在哪张图，就作用在哪张图）；
2. 否则，任一**已加载**该地图的频道；
3. 否则，第一个频道（此时会按需从 wz 把地图加载进来）。

迁移过来的 GM 命令走同一套定位（`GmContext.channelFor(mapid)` / `requireMap(mapid)`），但有两点不同：

- 不带 mapid 的命令按世界 / 频道遍历执行：`exprate <世界id> <倍率>` 作用整个世界，`reloadevents` 逐频道重载，`online2 [世界id]` 按世界过滤。
- `warpmap <源地图id> <目标地图id>` **只在同一频道内搬运**（源图和目标图必须是同一频道上那张），跨频道同名 mapid 不会被一起带走。

---

## 7. 注意事项

- `clearmob` 不掉落物品；要在图上**丢东西**用 `drop <地图id> <道具id> [数量|天数]`。
- `kick` / `give` / `giveexp` 以及所有主体是「角色名」的 GM 命令，只对**当前在线**的角色生效。
- **倍率类命令只改内存**：`exprate`、`droprate`、`mesorate`、`questrate`、`fishrate`、`travelrate`、`bossdroprate` 都只写 `World` 对象上的倍率，不写 `config.yaml`，重启就回到配置文件里的值。想永久改就改 `data\files\config.yaml` 再重启。另外每改一次都会向该世界在线玩家广播一条 `[Rate] ...` 公告。用 `rates <角色名>` / `showrates <角色名>` 看当前生效值。
- `drop` 产生的掉落需要**归属者**：目标地图上必须至少有一个在线角色，否则命令直接报缺主体，不会扔出一件无主物品。
- `reload*` 系列各有作用域，看清楚再敲：
  - `reloadportals` 只重载**传送点脚本**（`PortalScriptManager`），不改地图数据；
  - `reloadmap <地图id>` 重建这张图，并把图上玩家 `changeMap` 搬进新图——每人都会经历一次切图；
  - `reloaddrops` 清掉全部掉落缓存后重载，`reloadshops` 重载弹窗商店与 NPC 商店；
  - `reloadevents` 逐频道重载事件脚本，遇到尚未初始化事件管理器的频道会跳过并计数。
- 管理终端里的操作**即时生效、不可撤销**（尤其 `kick`、`dcall`、`clearmob`、`ban`、`shutdown`），敲之前确认目标 mapid / 角色名。
- **关闭控制台窗口即停服**：有真实控制台时，关窗（或 `Ctrl+Z` / `Ctrl+D`）会走一次优雅停服——保存角色、逐频道关停、停定时器/线程池后退出，端口全部释放；若 10 秒内没停完则强制 `halt`，不留僵尸进程。无控制台的后台启动不受影响，stdin EOF 只让控制台线程静默退出，服务照常运行。

---

## 8. 怎么核对「迁全了」（开发者）

不启服务端也能验证——用主机 JVM 探针（`F:\tmp\probe\ConsoleGmProbe.java`），它起一个真的 `Server` + World + 3 个 Channel，然后 `GmCommands.registerAll(gm)`。

编译（纯语法检查，`-d` / `-cp` 必须写成 Windows 风格路径）：

```bash
cd /f/Code/MapleAndroid/MapleServerAndroid
export JAVA_HOME="D:/AndroidDependency/.jdks/jdk-21.0.12.1+1"
"$JAVA_HOME/bin/javac.exe" -nowarn -encoding UTF-8 -d F:/tmp/gmout \
  -cp "MapleServerDesktop/build/install/MapleServerDesktop/lib/*" \
  $(cat /f/tmp/srcs_all.txt)
```

跑探针要补上分发包启动脚本里那条 `--add-exports`（打包进来的 pre-JPMS `RhinoScriptEngine` 需要它，漏了会在 `reloadportals` 处抛 `IllegalAccessError`）：

```bash
"$JAVA_HOME/bin/java.exe" -cp "F:/tmp/gmout;MapleServerDesktop/build/install/MapleServerDesktop/lib/*" \
  --add-exports=java.base/sun.security.action=ALL-UNNAMED ConsoleGmProbe --coverage
```

各模式与通过判据（本轮实测值）：

| 模式 | 查什么 | 本轮结果 |
|------|--------|----------|
| `--list` | 打印全部注册键 `命令名 用法`，用来和 `help gm` 对版 | 179 键 |
| `--coverage` | 拿 `CommandsExecutor` 的游戏内名字清单和 GM 层键做双向差集 | **缺 0 / 多 0** |
| `--sweep` | 每条命令用「最坏输入」（空参、乱码）跑一遍，看会不会抛 | **threw=0** |
| `--autoargs` | 按用法串自动造合法参数真跑一遍，看有没有抛或**静默无输出** | **threw=0 / silent=0** |
| `--script <文件>` | 跑一份手写的破坏性命令清单 | ran=12 / threw=0 / unknown=2（那 2 条正是 §3.4 里该被内置层接管的 `killall` 系列） |

`--sweep` / `--autoargs` 内置一份 `NO_SWEEP` 名单（16 条带踢人、停服、清库语义的命令）跳过实跑，只做语法与定位检查。

改完正式出包仍要 `build-windows.bat`（即 `gradlew installDist`），并确认产物 jar 的 mtime 晚于所有源文件。

---

## 9. 扩展新命令（开发者）

### 9.1 加一条 GM 风格命令（推荐）

在对应的 `GmCommandsLvN.java` 里往注册表挂一条 lambda，主体全部从参数来：

```java
r.add("mycmd", "<地图id> <角色名>", "一句话说明", (ctx, args) -> {
    MapleMap map = ctx.requireMap(ctx.num(args, 0, "地图id"));
    Character c = ctx.require(ctx.rest(args, 1));
    ctx.done(c.getName() + " 已在 " + map.getId());
});
```

要别名就换成名字数组（**第一个是主人，其余折进「别名:」行**）：

```java
r.add(new String[]{"mycmd", "mycmd2"}, "<地图id>", "说明", (ctx, args) -> { ... });
```

`GmRegistry` 对重复键**保留第一次注册**，所以谁拥有这个名字由 `GmCommands.registerAll()` 里的调用顺序决定；撞内置层（`help/online/spawn/banner/killall`）时永远内置赢。

逻辑复杂、需要独立 `name()/aliases()/usage()/description()/run()` 的，就继承 `GmCmd` 再 `r.add(cmd)`。

`GmContext` 里可直接用的取主体 / 输出助手：

- 输出：`println` / `printf` / `done("...")`（前缀 `= `）/ `fail("...")`（前缀 `! `）/ `syntax(...)` / `needArgs(...)` / `missing(args, n)`
- 参数：`num(args, i, "叫什么")` / `num(s, "叫什么")` / `num64` / `rest(args, from)` / `rest(args, from, to)`
- 角色：`channels()` / `allOnline()` / `online(name)` / `require(name)` / `requireClient(name)` / `printCharacter(c)` / `mapOf(c)` / `worldOf(c)` / `channelOf(c)` / `mapOfPlayer(name)` / `printPlayers(...)`
- 地图：`channelFor(mapid)` / `map(mapid)` / `requireMap(mapid)` / `loadedMapsWithPlayers()`
- 世界：`worlds()` / `world(id)` / `requireWorld(id)` / `worldIds()`

写「地图类」命令的用法提示时，把命令名一起传给帮助函数，这样操作者敲错第一个参数时提示还能读对（现成范例：`GmCommandsLv2.firstMapArg(ctx, "whereami", args)`）。

### 9.2 加一条内置命令

内置命令集中在 `ConsoleManager` 构造函数里注册，两步：

```java
register("mycmd", "<arg1> [arg2]", "一句话说明", this::cmdMyCmd);
commands.put("myalias", commands.get("mycmd"));   // 可选：别名直接塞表里
```

```java
private void cmdMyCmd(String[] args) {
    // 用 parseInt(args[0], "arg1") 读数字参数（非法会抛友好提示）
    // 用 findPlayer(name) / resolveChannelForMap(mapid) 定位目标
    System.out.println("结果回显");
}
```

### 9.3 可复用的核心 API（两层同源）

- 遍历频道：`Server.getInstance().getAllChannels()` → `Channel.getWorld()/getId()/getPlayerStorage()/getMapFactory()`
- 地图与怪物：`MapleMap.spawnMonsterOnGroundBelow(int,x,y)` / `killAllMonsters()` / `countMonsters()` / `getCharacters()` / `getAllPlayers()` / `getMapObjects()`
- 掉落：`MapleMap.spawnItemDrop(MapObject dropper, Character owner, Item item, android.graphics.Point pos, boolean ffa, boolean playerDrop)` —— 注意 `Point` 是 **android** 的那个（`Portal.getPosition()` 已经是），且 `dropper` / `owner` **不能为 null**
- 广播：`Server.broadcastMessage(worldId, PacketCreator.serverNotice(6, text))`
- 背包：`InventoryManipulator.addById(client, itemId, qty)`
- 角色：`Character.gainExp(n, true, true, true)` / `updateSingleStat(client.Stat.LEVEL, v)` / `getClient().disconnect(true, false)`
- 怪物技能：`MobSkillFactory.getMobSkill(type, level)`（返回 `Optional`；只有 `getMobSkillOrThrow` 才直接给对象）

改完重新 `build-windows.bat` 即可。

---

## 10. 常见问题

- **敲命令没反应 / 没有 `>` 提示符**：多半是后台启动拿不到 stdin。请在真实窗口里前台运行 `run-windows.bat`。
- **提示 `未知命令: xxx`**：命令名拼错。先 `help` 看内置，再 `help <关键字>` 在 174 条 GM 命令里搜；回显里会带上当前迁移总数。
- **`! 没有在线的角色叫 xxx`**：所有主体是角色名的 GM 命令都要求目标**当前在线**，且**大小写要和角色名一致**（控制台不做游戏内的 `toLowerCase()`）。用 `online` 抄名字。
- **不知道某条命令怎么给参数**：`help <关键字>` 会打印带尖括号和 `[可省=默认值]` 的用法串，照它给。注意 `showrates` / `rates` 要 `<角色名>`，`reloadportals` / `saveall` / `dcall` / `reloadevents` 是无参的。
- **离线验证时 `reloadportals` 抛 `IllegalAccessError`**：不是命令的 bug，是打包进来的 pre-JPMS Rhino 撞了 JDK 21 的模块封装。补 `--add-exports=java.base/sun.security.action=ALL-UNNAMED`（分发包启动脚本已经带了）。
- **`spawn` 后游戏里看不到怪**：确认 mapid 是玩家当前所在地图（先 `online` 查），且作用频道与玩家频道一致（看回显的 `[world/ch]`）。
- **手机连不进服务器**：与本控制台无关，检查 Windows 防火墙放行规则与 `config.yaml` 的 `LANHOST`（详见部署说明）。
