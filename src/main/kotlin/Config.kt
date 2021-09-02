package michael.landlord

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value

object Config: AutoSavePluginConfig("config") {
    @ValueDescription(
        "消息截断长度。当消息超过长度时按行进行截断。\n" +
            "当机器人发送长消息被吞时启用。"
    )
    var length by value(1000)
    @ValueDescription("计算农民分数加成时，每加成1%所需分差。")
    var factor by value(200)
    @ValueDescription("是否启用反抢")
    var 反抢 by value(false)
    @ValueDescription("是否打印详细信息。为防止被封禁，请尽量关闭。")
    var verbose by value(false)
    @ValueDescription("出牌超时提醒，以毫秒为单位")
    var timeout by value(5000L)
}