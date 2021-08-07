package michael.landlord

import michael.landlord.main.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandSenderOnMessage
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
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
    }
    @SubCommand("关闭", "off")
    @Description("在本群关闭斗地主")
    suspend fun CommandSenderOnMessage<GroupMessageEvent>.off() {
        LandlordConfig.remove(fromEvent.group.id)
    }
    @SubCommand("信息", "info")
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
            打牌一次奖励${CONFIG_PLAY_BONUS}分。
            中途退出（弃牌）、挂机（抢地主、加倍${CONFIG_TIME_BOSS}秒，出牌${CONFIG_TIME_GAME}秒）倒扣${CONFIG_SURRENDER_PENALTY}分。
            每局游戏的标准分计算方法为：初始值${CONFIG_INIT_SCORE}分，最高最低分玩家的积分差额每有50分，
            标准分加${CONFIG_BOTTOM_SCORE }分，但标准分不会超过${CONFIG_TOP_SCORE}分。
            分数下限为负5亿，上限为正5亿。
    """.trimIndent())
    }
}

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.landlord",
        name = "mirai斗地主插件",
        version = "0.2.9"
    ) {
        author("鄢振宇https://github.com/michael1015198808")
        info("mirai的斗地主插件")
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }
        LandlordConfig.reload()
        taskManageCommand.register()
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
