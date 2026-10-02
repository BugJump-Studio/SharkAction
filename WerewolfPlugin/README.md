# 狼人杀战斗模式插件 (WerewolfPlugin)

一个实时对战动作的Minecraft狼人杀游戏插件，支持500秒限时自由对战和多样化技能系统。

## 功能特性

- ⚔️ **实时对战**: 所有玩家可互相攻击，身份未知
- 🎭 **身份隐藏**: 游戏开始前无法知晓其他玩家身份
- ⏱️ **限时对战**: 严格限制500秒游戏时长
- 🎯 **技能系统**: 每个角色拥有独特技能和冷却机制
- 🏆 **胜负判定**: 狼人全灭/好人全灭/时间到平局
- 🎨 **Web编辑器**: 可视化角色编辑和脚本编写
- 🔐 **权限系统**: 服主/管理员分级管理

## 游戏规则

1. **自由对战**: 所有玩家可互相攻击，身份未知
2. **技能系统**: 每个角色有2个独特技能，有冷却时间
3. **游戏时长**: 严格限制500秒（8分20秒）
4. **胜利条件**:
   - 狼人阵营全灭 → 好人阵营获胜
   - 好人阵营全灭 → 狼人阵营获胜
   - 时间到未分胜负 → 平局
5. **身份揭晓**: 游戏结束时揭晓所有玩家身份

## 角色技能

### 🐺 狼人 (Werewolf)
- **技能1 - 狂暴**: 伤害提升50%，持续10秒 (冷却20秒)
- **技能2 - 嗜血**: 恢复30%生命值 (冷却30秒)
- **属性**: 高攻击(8)，高生命(120)

### 🔮 预言家 (Seer)
- **技能1 - 洞察**: 查看最近玩家的身份和阵营 (冷却15秒)
- **技能2 - 预知**: 获得3秒无敌状态 (冷却25秒)
- **属性**: 低攻击(4)，低生命(80)，信息优势

### 🧙 女巫 (Witch)
- **技能1 - 治疗药水**: 生命值全满 (限1次，冷却60秒)
- **技能2 - 毒药**: 对最近敌人造成30点伤害 (限1次，冷却45秒)
- **属性**: 平衡攻击(5)，生命(90)

### 🏹 猎人 (Hunter)
- **技能1 - 精准射击**: 射程和伤害提升30%，持续8秒 (冷却18秒)
- **技能2 - 陷阱**: 放置陷阱，敌人靠近触发 (冷却35秒)
- **属性**: 高攻击(7)，死亡时复仇造成20点伤害

### 👨 村民 (Villager)
- **技能1 - 团结**: 受到伤害减少50%，持续5秒 (冷却12秒)
- **技能2 - 逃跑**: 移动速度提升，持续5秒 (冷却20秒)
- **属性**: 平衡攻击(5)，生命(100)

## 技术栈

- **服务端**: Paper 1.19+ / Leaf 1.19+
- **Web服务**: Spark Java
- **脚本引擎**: GraalJS
- **权限加密**: BCrypt
- **前端**: 原生HTML/CSS/JS

## 项目结构

```
WerewolfPlugin/
├── src/main/java/com/werewolf/
│   ├── plugin/
│   │   ├── WerewolfPlugin.java      # 插件主类
│   │   ├── PlayerListener.java      # 玩家事件监听
│   │   └── CombatListener.java      # 战斗监听
│   ├── auth/
│   │   ├── AdminSession.java        # 管理员会话
│   │   ├── AuthManager.java         # 认证管理
│   │   └── SessionManager.java      # 会话管理
│   ├── commands/
│   │   └── WerewolfCommand.java     # 指令处理器
│   ├── config/
│   │   └── ConfigManager.java       # 配置管理
│   ├── game/
│   │   ├── GameManager.java         # 游戏管理器
│   │   └── GameState.java           # 游戏状态枚举
│   ├── player/
│   │   ├── PlayerData.java          # 玩家数据
│   │   └── PlayerDataManager.java   # 玩家数据管理
│   ├── role/
│   │   ├── Role.java                # 角色实体
│   │   └── RoleManager.java         # 角色管理器
│   ├── script/
│   │   └── ScriptEngine.java        # 脚本引擎
│   ├── web/
│   │   └── WebServer.java           # Web服务器
│   └── events/
│       └── GameEvent.java           # 游戏事件
├── src/main/resources/
│   ├── plugin.yml                   # 插件配置
│   ├── config.yml                   # 主配置
│   ├── auth.yml                     # 权限配置
│   └── web/static/                  # Web前端文件
│       ├── login.html               # 登录页面
│       └── editor.html              # 角色编辑器
└── pom.xml                          # Maven配置
```

## 编译安装

### 环境要求
- Java 17+
- Maven 3.6+

### 编译步骤

```bash
# 进入项目目录
cd WerewolfPlugin

# 编译打包
mvn clean package

# 生成的jar文件位于 target/WerewolfPlugin-1.0.0.jar
```

### 安装到服务器

1. 将 `target/WerewolfPlugin-1.0.0.jar` 复制到服务器的 `plugins/` 目录
2. 启动服务器，插件会自动生成配置文件
3. 编辑 `plugins/WerewolfPlugin/auth.yml` 添加管理员
4. 重启服务器

## 配置说明

### 添加管理员

编辑 `plugins/WerewolfPlugin/auth.yml`:

```yaml
admins:
  你的游戏ID: "$2a$10$..."  # BCrypt加密的密码

permissions:
  levels:
    你的游戏ID: owner  # owner 或 admin
```

生成BCrypt密码可以使用在线工具：https://bcrypt-generator.com/

### 主配置 (config.yml)

```yaml
# 游戏配置
game:
  min-players: 4          # 最少玩家数
  max-players: 12         # 最多玩家数
  duration: 500           # 游戏时长（秒）- 严格限制500秒

# 技能冷却配置（秒）
skills:
  werewolf:
    skill1: 20            # 狂暴
    skill2: 30            # 嗜血
  seer:
    skill1: 15            # 洞察
    skill2: 25            # 预知
  witch:
    skill1: 60            # 治疗药水
    skill2: 45            # 毒药
  hunter:
    skill1: 18            # 精准射击
    skill2: 35            # 陷阱
  villager:
    skill1: 12            # 团结
    skill2: 20            # 逃跑
```

## 游戏指令

### 玩家指令
- `/ww join` - 加入游戏
- `/ww leave` - 离开游戏
- `/ww status` - 查看游戏状态（显示剩余时间）
- `/ww players` - 查看玩家列表（只显示存活状态，不显示身份）
- `/ww skill <1|2>` - 使用技能（1或2）

### 管理员指令
- `/ww login <密码>` - 管理员登录
- `/ww logout` - 登出
- `/ww start` - 开始游戏
- `/ww stop` - 停止游戏
- `/ww admin add <用户名> <密码> <owner|admin>` - 添加管理员
- `/ww admin remove <用户名>` - 移除管理员
- `/ww admin list` - 列出管理员
- `/ww reload` - 重载配置

## 游戏界面

游戏中ActionBar会显示：
- 🔴 剩余时间（格式：M:SS）
- 🟢 当前生命值
- 🟡 技能1冷却状态
- 🟡 技能2冷却状态

## Web编辑器

访问 `http://服务器IP:8080` 打开管理后台。

### 功能
- 可视化编辑角色属性
- 编写JavaScript技能脚本
- 管理自定义角色
- 查看游戏状态

### 脚本API

```javascript
// 游戏相关
game.getStage()           // 获取当前游戏阶段
game.getAlivePlayers()    // 获取存活玩家列表
game.broadcast(msg)       // 广播消息
game.getRemainingTime()   // 获取剩余时间

// 玩家选择器
selector.random(camp)     // 随机选择某阵营玩家
selector.byRole(roleName) // 按角色名选择玩家
selector.nearest(player, range)  // 选择最近的玩家

// 狼人杀技能
wolf.kill(target)         // 击杀目标
wolf.poison(target)       // 毒杀目标
wolf.guard(target)        // 守护目标
wolf.check(target)        // 查验目标阵营

// MC指令
mc.command(cmd)           // 执行MC指令
mc.title(player, title, subtitle)  // 发送标题
mc.message(player, msg)   // 发送消息
mc.actionbar(player, msg) // 发送ActionBar

// 可用变量
player                    // 当前玩家
eventType                 // 事件类型
```

## 角色配置示例

```yaml
name: werewolf
display-name: 狼人
type: WEREWOLF
camp: 狼人阵营
description: 强大的战士，拥有狂暴和嗜血技能
custom: false
events:
  game_start:
    script: |
      mc.message(player, '§c你是狼人！消灭所有好人阵营玩家！');
```

## 注意事项

1. **游戏时长**: 严格限制500秒，时间到自动判定平局
2. **身份隐藏**: 游戏中无法查看其他玩家身份，只能通过预言家技能或游戏结束揭晓
3. **技能冷却**: 注意技能冷却时间，合理使用技能
4. **PVP模式**: 所有玩家可互相攻击，需谨慎判断敌友
5. **兼容性**: 插件使用Paper API，支持Paper/Leaf/Purpur等服务端

## 许可证

MIT License
