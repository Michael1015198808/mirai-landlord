package michael.landlord.landlord

import michael.landlord.LandlordConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value

class PlayerInfo(playerId: Long): AutoSavePluginData("players/$playerId") {
    var score: Long by value()
    var wins: Long by value()
    var farmer_wins: Long by value()
    var landlord_wins: Long by value()
    var loses: Long by value()
    var farmer_loses: Long by value()
    var landlord_loses: Long by value()
}
