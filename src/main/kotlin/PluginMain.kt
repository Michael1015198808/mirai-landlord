package michael.landlord

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.EventChannel
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.utils.info
import michael.landlord.main.Casino

object PluginMain : KotlinPlugin(
    JvmPluginDescription(
        id = "mirai.landlord",
        name = "mirai斗地主插件",
        version = "0.2.8"
    ) {
        author("鄢振宇https://github.com/michael1015198808")
        info("mirai的斗地主插件")
    }
) {
    override fun onEnable() {
        logger.info { "Plugin loaded" }
        //配置文件目录 "${dataFolder.absolutePath}/"
        val eventChannel = GlobalEventChannel.parentScope(this)
        eventChannel.subscribeAlways<GroupMessageEvent>{
            if(michael.landlord.main.bot == null) {
                michael.landlord.main.bot = bot
            }
            if(message.contentToString().startsWith("斗地主") || Casino.getDesk(group.id) != -1) {
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
