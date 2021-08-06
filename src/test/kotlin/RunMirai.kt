package michael.landlord

import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.enable
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.load
import net.mamoe.mirai.console.terminal.MiraiConsoleTerminalLoader
import michael.landlord.main.Desk
import michael.landlord.main.Util
import michael.landlord.main.flag

fun getMyCardTypeTestUtil(s: String): Pair<String, String> {
    val (model, rank) = Desk(0L).getMycardType(Util.stringToCards(s))
    return Pair(model, flag[rank])
}

suspend fun main() {
    MiraiConsoleTerminalLoader.startAsDaemon()

    //如果是Kotlin
    PluginMain.load()
    PluginMain.enable()

    val tests = listOf<Triple<String, String, String>>(
        Triple("22", "对子", "2"),
        Triple("222", "三张", "2"),
        Triple("2222", "炸弹", "2"),
        Triple("222大王", "3带1", "2"),
        Triple("33344", "3带2", "3"),
        Triple("44433", "3带2", "4"),
        Triple("2222小王大王", "4带2", "2"),
        Triple("2222AAKK", "4带2对", "2"),
        Triple("34567", "顺子", "3"),
        Triple("47865", "顺子", "4"),
        Triple("334455", "2连顺", "3"),
        Triple("334455667788991010JJQQKKAA", "2连顺", "3"),
        Triple("333444", "3连顺", "3"),
        Triple("333444555666777", "3连顺", "3"),
        Triple("3334445566", "飞机带2翅膀", "3"),
        Triple("3334455566", "", "3"),
        Triple("44445556", "", "3"),
        Triple("3344455566", "飞机带2翅膀", "4"),
        Triple("555666777223", "飞机带3翅膀", "5"),
        Triple("444555666888", "飞机带3翅膀", "4"),
        Triple("333555666777", "飞机带3翅膀", "5"),
        Triple("334555666777", "飞机带3翅膀", "5"),
        Triple("小王大王", "王炸", "小王"),
        Triple("大王小王", "王炸", "小王"),
    )
    for ((s, model, rank) in tests) {
        if(getMyCardTypeTestUtil(s) != Pair(model, rank)) {
            System.err.println("$s 的牌型应为$model 大小应为$rank")
            System.err.println("getMycardType的返回值为${getMyCardTypeTestUtil(s)}")
        }
    }
}