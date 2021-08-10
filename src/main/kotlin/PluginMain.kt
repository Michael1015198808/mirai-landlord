package michael.landlord

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
import net.mamoe.mirai.utils.info

object LandlordConfig : AutoSavePluginConfig("landlord") {
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

object taskManageCommand : CompositeCommand(
    owner = PluginMain,
    "斗地主",
    description = "斗地主管理命令",
) {
    @SubCommand("开启", "on")
    @Description("在本群启动斗地主")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.on() {
        LandlordConfig.add(fromEvent.group.id)
        fromEvent.group.sendMessage("本群${fromEvent.group.id}已开启斗地主功能！")
    }
    @SubCommand("关闭", "off")
    @Description("在本群关闭斗地主")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.off() {
        LandlordConfig.remove(fromEvent.group.id)
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
}

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.landlord",
        name = "mirai斗地主插件",
        version = "0.3.3"
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
        LandlordConfig.reload()
        taskManageCommand.register()
        globalStatisticsData.reload()
        //配置文件目录 "${dataFolder.absolutePath}/"
        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent>{
            if(LandlordConfig.contains(group.id)) {
                michael.landlord.main.bot = bot
                Casino.game(true, group.id, sender.id, message.contentToString())
            }
            return@subscribeAlways
        }
        eventChannel.subscribeAlways<NewFriendRequestEvent>{
            //自动同意好友申请
            accept()
        }
    }
}
