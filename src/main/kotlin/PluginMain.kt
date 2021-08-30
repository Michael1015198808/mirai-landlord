package michael.landlord

import kotlinx.coroutines.sync.withLock
import michael.landlord.landlord.PlayerInfo
import michael.landlord.main.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.console.plugin.version
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMembers
import java.util.Collections.frequency

object enableGroups : AutoSavePluginConfig("groups") {
    @ValueDescription("")
    private var groups: MutableSet<Long> by value(mutableSetOf())

    // 屏蔽LandlordConfig的内部实现，以方便可能的分布式存储，并简化对该对象的操作
    fun add(element: Long): Boolean {
        return groups.add(element)
    }
    fun contains(element: Long): Boolean {
        return groups.contains(element)
    }
    fun remove(element: Long): Boolean {
        return groups.remove(element)
    }
}

object LandlordConfig : AutoSavePluginConfig("config") {
    @ValueDescription(
        "消息截断长度。当消息超过长度时按行进行截断。\n" +
        "当机器人发送长消息被吞时启用。\n"
    )
    var length by value(1000)
}

object taskManageCommand : CompositeCommand(
    owner = PluginMain,
    "斗地主",
    description = "斗地主管理命令",
) {
    @SubCommand("开启", "on")
    @Description("在本群启动斗地主")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.on() {
        enableGroups.add(fromEvent.group.id)
        fromEvent.group.sendMessage("本群${fromEvent.group.id}已开启斗地主功能！")
    }
    @SubCommand("关闭", "off")
    @Description("在本群关闭斗地主")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.off() {
        enableGroups.remove(fromEvent.group.id)
        fromEvent.group.sendMessage("本群${fromEvent.group.id}已关闭斗地主功能！")
    }
    @SubCommand("信息", "info", "版本", "version")
    @Description("显示该项目信息")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.info() {
        fromEvent.group.sendMessage("""
            斗地主${PluginMain.version}
            源代码与更新履历：https://github.com/Michael1015198808/mirai-landlord
            移植自（基于酷Q的C++斗地主）：https://github.com/doowzs/CQDouDiZhu
            原作者与2.0.1源代码：https://github.com/lsjspl/CQDouDiZhu
            """.trimIndent())
    }
    @SubCommand("规则", "rules")
    @Description("显示斗地主规则")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.rules() {
        fromEvent.group.sendMessage("""
            斗地主规则：
            中途退出（弃牌）、挂机（抢地主、加倍${CONFIG_TIME_BOSS}秒，出牌${CONFIG_TIME_GAME}秒）倒扣${CONFIG_SURRENDER_PENALTY}分。
            每局游戏的标准分为${CONFIG_INIT_SCORE}分。
            每一局游戏会计算三名玩家积分的平均分。
            农民积分每比平均分低100，失败时分数少扣1%，胜利时分数多加1%。
            农民积分每比平均分高100，失败时分数多扣1%，胜利时分数少加1%。
            上限为原本的120%，下限为原本的80%。
            分数下限为负5亿，上限为正5亿。
            """.trimIndent())
    }
    @SubCommand("命令", "指令", "操作")
    @Description("显示斗地主指令")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.commands() {
        fromEvent.group.sendMessage("""
            斗地主命令列表（*号表示支持后带符号）：
            /斗地主 版本：查看游戏版本号、GitHub链接与原作者信息
            上桌|打牌：加入游戏
            出|打 或直接出牌：出牌 比如 "出23456"、"334455"
            过(牌)|不要|pass：过牌
            抢(地主)|不抢：是否抢地主
            加(倍)|不加(倍)：是否加倍
            开始|启动|GO：是否开始游戏
            下桌|不玩了：退出游戏，只能在准备环节使用
            玩家列表：当前在游戏中得玩家信息
            明牌：显示自己的牌给所有玩家，明牌会导致积分翻倍，只能在发完牌后以及出牌之前使用。
            弃牌：放弃本局游戏，当地主或者两名农民弃牌游戏结束。农民玩家弃牌赢了不得分（有弃牌惩罚），输了双倍扣分
            我的信息：查看我的战绩与积分信息
            加入观战|观战：暗中观察
            退出观战：光明正大地看打牌
            重置斗地主：删除所有配置。重置后可重新设定管理员
            强制结束：结束当前群的游戏，正式版将移除
            """.trimIndent())
    }
    private val members = LandlordConfig::class.declaredMembers
    @SubCommand("设置", "set")
    @Description("设置斗地主选项")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.set(option: String, arg: Int) {
        val field = members.find { it.name == option }
        if (field != null) {
            val mp = field as KMutableProperty1<LandlordConfig, Int>
            mp.set(LandlordConfig, arg)
            fromEvent.group.sendMessage("已将${option}设置为$arg！")
        } else {
            fromEvent.group.sendMessage("不存在属性$option！")
        }
    }
    @SubCommand("当前设置", "settings")
    @Description("打印斗地主设置")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.settings() {
        fromEvent.group.sendMessage(
            members.joinToString("\n") { field ->
                val mp = field as KProperty<Int>
                """
                ${field.name}：${mp.call(LandlordConfig)}
                    ${field.annotations.filterIsInstance<ValueDescription>().joinToString { it.value }}
                """.trimIndent()
            })
    }
}

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.landlord",
        name = "mirai斗地主插件",
        version = "0.4.2"
    ) {
        author("鄢振宇https://github.com/michael1015198808")
        info("mirai的斗地主插件")
    }
) {
    val playerInfoCache: MutableMap<Long, PlayerInfo> = mutableMapOf()
    object globalStatisticsData: AutoSavePluginData("global") {
        var landlord_wins: Long by value()
        var landlord_loses: Long by value()
    }
    fun getPlayerInfo(playerId: Long): PlayerInfo {
        if (! playerInfoCache.contains(playerId)) {
            val info = PlayerInfo(playerId)
            info.reload()
            playerInfoCache[playerId] = info
        }
        return playerInfoCache.getValue(playerId)
    }
    fun readScore(playerId: Long): Long {
        return getPlayerInfo(playerId).score
    }
    fun readWin(playerId: Long): Long {
        return getPlayerInfo(playerId).wins
    }
    fun readLose(playerId: Long): Long {
        return getPlayerInfo(playerId).loses
    }

    fun addScore(playerId: Long, score: Long) {
        var oldScore = readScore(playerId); //这里使用desk里的函数
        getPlayerInfo(playerId).score = Math.max(-500000000L, Math.min(oldScore + score, 500000000L))
    }
    fun addWin(playerId: Long, isBoss: Boolean) {
        ++getPlayerInfo(playerId).wins
        if(isBoss)
            ++getPlayerInfo(playerId).landlord_wins
        else
            ++getPlayerInfo(playerId).farmer_wins
    }

    fun addLose(playerId: Long, isBoss: Boolean) {
        ++getPlayerInfo(playerId).loses
        if(isBoss)
            ++getPlayerInfo(playerId).landlord_loses
        else
            ++getPlayerInfo(playerId).farmer_loses
    }
    override fun onEnable() {
        logger.info { "Plugin loaded" }
        enableGroups.reload()
        taskManageCommand.register()
        globalStatisticsData.reload()
        //配置文件目录 "${dataFolder.absolutePath}/"
        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent>{
            if(enableGroups.contains(group.id) && message[1] is PlainText) {
                michael.landlord.main.bot = bot
                val groupId = group.id
                val playerId = sender.id
                var msg = message[1].toString().trim().uppercase()

                val desk = Casino.getOrCreatDesk(groupId);
                desk.mutex.withLock {

                    if (playerId == 80000000L) {
                        desk.msg += "匿名用户不能参加斗地主！"
                    } else if (msg.startsWith("上桌") || msg.startsWith("上座")  || msg.startsWith("上机") || msg.startsWith("打牌")) {
                        desk.join(playerId);
                    } else if ((desk.state >= STATE_READYTOGO) &&
                        (msg.startsWith("过") || msg.startsWith("不出") ||
                            msg.startsWith("没有") || msg.startsWith("打不出") || msg.startsWith("要不起") ||
                            msg.startsWith("不要") || msg.startsWith("PASS") )) {//跳过出牌阶段
                        desk.discard(playerId);
                    } else if ((desk.state >= STATE_READYTOGO) &&
                        (msg.startsWith("出") || msg.startsWith("打") || Regex("([3456789JQKA2]|10|小王|大王|王炸)+").matches(msg))) {//出牌阶段
                        if(msg.startsWith("出") || msg.startsWith("打")) {
                            msg = msg.substring(1)
                        }
                        desk.play(playerId, msg)
                    } else if (msg.startsWith("退桌") || msg.startsWith("下桌")
                        || msg.startsWith("不玩了"))  {//结束游戏
                        desk.exit(playerId);
                    } else if (msg == "玩家列表") {
                        desk.listPlayers(1);
                    } else if (msg.startsWith("GO") || msg.startsWith("启动")) {
                        if(desk.getPlayerId(playerId) != -1) {
                            desk.startGame();
                        } else {
                            desk.msg += "非玩家不能开始游戏！"
                        }
                    } else if ((msg.startsWith("抢") || msg.startsWith("要")) && desk.state == STATE_BOSSING) {
                        desk.getLandlord(playerId);
                    } else if (msg.startsWith("不") && desk.state == STATE_BOSSING) {
                        desk.dontBoss(playerId);
                    } else if (msg == "反抢" && desk.state == STATE_MULTIPLING && !desk.isForceBoss) {
                        desk.forceLandlord(playerId)
                    } else if (msg.startsWith("加") && desk.state == STATE_MULTIPLING) {
                        desk.setMultiple(playerId, true)
                    } else if (msg.startsWith("不") && desk.state == STATE_MULTIPLING) {
                        desk.setMultiple(playerId, false)
                    } else if (msg.startsWith("明牌")) {
                        desk.openCard(playerId);
                    } /* else if ((msg.startsWith("弃牌"))
                    && desk.state >= STATE_BOSSING) {
                    desk.surrender(playerId);
                } */
                    else if (msg == "记牌器") {
                        val player = desk.getPlayer(playerId)
                        if (player == null) {
                            desk.msg += "你不是玩家！"
                            return@subscribeAlways
                        }
                        if (player.counterUsed) {
                            desk.msg += "您已使用过记牌器，一局只能使用一次！"
                        } else {
                            player.counterUsed = true
                            val cards = desk.players.flatMap {
                                if (it.number == playerId)
                                    listOf()
                                else
                                    it.card
                            }
                            player.msg += flag.joinToString("\n") {
                                "$it：${frequency(cards, it)}张"
                            }
                        }
                    }
                    else if (Regex("统计(信息|数据|战绩)").matches(msg)) {
                        desk.msg += """
                        共进行游戏${globalStatisticsData.landlord_wins+globalStatisticsData.landlord_loses}场
                        地主方获胜${globalStatisticsData.landlord_wins}
                        农民方获胜${globalStatisticsData.landlord_loses}
                        地主方胜率${(globalStatisticsData.landlord_wins * 100) / (globalStatisticsData.landlord_wins+globalStatisticsData.landlord_loses)}%
                        """.trimIndent()
                    }
                    else if (Regex("我的(信息|数据|战绩)").matches(msg)) {
                        desk.detailedInfo(playerId)
                    }
                    else if (msg.startsWith("加入观战") || msg.startsWith("观战")) {
                        if(desk.joinWatching(playerId)) {
                            desk.sendWatchingMsg_Join(playerId)
                        }
                    } else if (msg.startsWith("退出观战")) {
                        desk.exitWatching(playerId)
                    } /*
        //else if (msg.find(L"举报") == 0 || msg.find(L"挂机") == 0 || msg.find(L"AFK") == 0) {
        //	desk->AFKHandle(playNum);
        //} */
                    else if (msg == "强制结束") {
                        if (true || Admin.isAdmin(playerId)) {
                            desk.msg += "管理员强制结束本桌游戏。\n"
                            Casino.gameOver(groupId)
                            Casino.desks.remove(desk)
                        } else {
                            desk.msg += "你根本不是管理员！"
                            desk.breakLine()
                        }
                    } else {
                        // desk.msg += "命令解析失败！\n"
                        // desk.msg += "输入\"强制结束\"以退出斗地主模式"
                        return@subscribeAlways
                    }
                    if (desk.msg.trim() != "") {
                        val last = desk.msg.trim().split('\n').reduce {cumulation, line ->
                            if (cumulation.length + line.length < LandlordConfig.length) {
                                cumulation + "\n" + line
                            } else {
                                group.sendMessage(cumulation.deserializeMiraiCode())
                                line
                            }
                        }
                        group.sendMessage(last.deserializeMiraiCode())
                        desk.msg = ""
                    }
                    desk.sendPlayerMsg()
                    desk.sendWatcherMsg()
                }
            }
            return@subscribeAlways
        }
        eventChannel.subscribeAlways<NewFriendRequestEvent>{
            //自动同意好友申请
            accept()
        }
    }
}
